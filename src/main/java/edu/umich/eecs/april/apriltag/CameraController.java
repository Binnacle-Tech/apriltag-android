package edu.umich.eecs.april.apriltag;

import android.content.Context;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the camera and live preview using CameraX. Camera frames are delivered
 * via an {@link ImageAnalysis} use case; the luminance (Y) plane of each frame is
 * packed into a contiguous grayscale buffer and enqueued in the {@link DetectionThread}
 * for asynchronous AprilTag detection.
 * <p>
 * This class also displays a text view with the current frames per second (FPS)
 * of the camera thread.
 * </p>
 */
public class CameraController {
    private static final String TAG = "CameraController";

    private final Context mContext;
    private final PreviewView mPreviewView;
    private final DetectionThread mDetectionThread;
    private final TextView mFpsTextView;

    private ProcessCameraProvider mCameraProvider;
    private ExecutorService mAnalysisExecutor;
    private OnErrorListener mErrorListener;

    private long mLastRender = System.currentTimeMillis();
    private int mFrameCount = 0;

    /** Notified (on the main thread) when the camera can't be opened or bound. */
    public interface OnErrorListener {
        void onError(String message);
    }

    public CameraController(Context context, PreviewView previewView,
                            DetectionThread detectionThread, TextView fpsTextView) {
        mContext = context.getApplicationContext();
        mPreviewView = previewView;
        mDetectionThread = detectionThread;
        mFpsTextView = fpsTextView;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mErrorListener = listener;
    }

    private void reportError(String message) {
        if (mErrorListener != null) {
            mErrorListener.onError(message);
        }
    }

    /** Asynchronously acquire the camera and bind the preview + analysis use cases. */
    public void start(final LifecycleOwner lifecycleOwner) {
        mAnalysisExecutor = Executors.newSingleThreadExecutor();
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(mContext);
        future.addListener(() -> {
            try {
                mCameraProvider = future.get();
                bindUseCases(lifecycleOwner);
            } catch (Exception e) {
                Log.e(TAG, "Couldn't open camera: " + e.getMessage());
                reportError("Couldn't open the camera. Another app may be using it.");
            }
        }, ContextCompat.getMainExecutor(mContext));
    }

    private void bindUseCases(LifecycleOwner lifecycleOwner) {
        mCameraProvider.unbindAll();

        // Bind preview and analysis to the SAME aspect ratio (4:3) so their fields
        // of view match; otherwise the detection overlay can't line up with the
        // preview. Analysis additionally requests the highest available resolution
        // so the detector has enough detail after decimation to find tags.
        ResolutionSelector previewSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build();

        ResolutionSelector analysisSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build();

        Preview preview = new Preview.Builder()
                .setResolutionSelector(previewSelector)
                .build();
        preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setResolutionSelector(analysisSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        imageAnalysis.setAnalyzer(mAnalysisExecutor, this::analyze);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        try {
            mCameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis);
            Log.i(TAG, "Camera preview start");
        } catch (Exception e) {
            Log.e(TAG, "Couldn't bind camera use cases: " + e.getMessage());
            reportError("Couldn't start the camera on this device.");
        }
    }

    private void analyze(@NonNull ImageProxy image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // The native detector only uses the luma channel as a grayscale image,
            // expecting the first width*height bytes with stride == width.
            ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int rowStride = yPlane.getRowStride();

            byte[] gray = new byte[width * height];
            if (rowStride == width) {
                yBuffer.get(gray, 0, width * height);
            } else {
                // Copy row by row to strip out per-row padding.
                for (int row = 0; row < height; row++) {
                    yBuffer.position(row * rowStride);
                    yBuffer.get(gray, row * width, width);
                }
            }

            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            mDetectionThread.enqueueCameraFrame(gray, new Size(width, height), rotationDegrees);
            previewFpsCallback();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while enqueuing camera frame: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error processing camera frame: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    private void previewFpsCallback() {
        long now = System.currentTimeMillis();
        long diff = now - mLastRender;
        mFrameCount++;
        if (diff >= 1000) {
            final double fps = 1000.0 / diff * mFrameCount;
            mFpsTextView.post(() -> mFpsTextView.setText(String.format("%.2f fps Camera", fps)));
            mLastRender = now;
            mFrameCount = 0;
        }
    }

    /** Release the camera. */
    public void stop() {
        if (mCameraProvider != null) {
            mCameraProvider.unbindAll();
        }
        if (mAnalysisExecutor != null) {
            mAnalysisExecutor.shutdown();
            mAnalysisExecutor = null;
        }
        Log.i(TAG, "Camera stop");
    }
}
