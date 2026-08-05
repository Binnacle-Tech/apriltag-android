package edu.umich.eecs.april.apriltag;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class DetectionThread extends Thread {

    private static final String TAG = "DetectionThread";
    private TextureView mTextureView;

    private final TextView mFpsTextView;
    private long mLastFPSRender = System.currentTimeMillis();
    private Size mCameraSize;
    // Clockwise rotation (degrees) needed to bring the analysis frame upright,
    // as reported by CameraX. Used to map detections onto the preview.
    private volatile int mRotationDegrees = 90;
    private static final int MAX_FRAME_QUEUE_SIZE = 1;

    private BlockingQueue<byte[]> mCameraFrameQueue = new LinkedBlockingQueue<>();
    private long mLastEnqueueFrameTime;
    private int mFrameCount = 0;
    private long mLastDetectLatency = 0;



    public DetectionThread(TextureView textureView, TextView fpsTextView) {
        mTextureView = textureView;
        mFpsTextView = fpsTextView;
        // The overlay sits on top of the camera PreviewView. A TextureView is opaque
        // by default, so its transparent (cleared) regions would render as solid black
        // and hide the camera feed. Blend instead so only the drawn detections show.
        mTextureView.setOpaque(false);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // Do nothing
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                // Do nothing
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Do nothing
            }
        });
    }

    public void destroy() {
        mCameraFrameQueue.clear();
        mCameraFrameQueue = null;
    }

    public void enqueueCameraFrame(byte[] data, Size cameraSize, int rotationDegrees) throws InterruptedException {
        mRotationDegrees = rotationDegrees;
        if (mCameraSize == null || mCameraSize.getWidth() != cameraSize.getWidth() || mCameraSize.getHeight() != cameraSize.getHeight()) {
            mCameraFrameQueue.clear();
            mCameraSize = cameraSize;
            Log.w(TAG, "Camera size changed during preview");
        }

        if (mCameraFrameQueue == null) {
            Log.w(TAG, "Camera frame queue is null, skipping frame");
            return;
        }

        if (mCameraFrameQueue.size() == MAX_FRAME_QUEUE_SIZE) {
            mCameraFrameQueue.clear();
            Log.w(TAG, "Camera frame queue is full, clearing buffer");
        }

        mCameraFrameQueue.put(data);
        mLastEnqueueFrameTime = System.currentTimeMillis();

        Log.i(TAG, "Buffer length: " + mCameraFrameQueue.size());
    }

    private void updateFps() {
        long now = System.currentTimeMillis();
        long diff = now - mLastFPSRender;
        mFrameCount++;
        if (diff >= 1000) {
            final double fps = 1000.0 / diff * mFrameCount;
            mFpsTextView.post(new Runnable() {
                @Override
                public void run() {
                    mFpsTextView.setText(String.format("%.2f fps Detect+Render\n%d ms Detect+Render Latency", fps, mLastDetectLatency));
                }
            });
            mLastFPSRender = now;
            mFrameCount = 0;
        }
    }

    private ArrayList<ApriltagDetection> processCameraFrame(byte[] data, Size cameraSize)  {
        try {
            return ApriltagNative.apriltag_detect_yuv(data, cameraSize.getWidth(), cameraSize.getHeight());
        } catch (Exception e) {
            Log.e(TAG, "Unhandled exception when detecting tags: " + e);
            return new ArrayList<>();
        }
    }

    private void renderDetection(ApriltagDetection detection, Canvas canvas) {
        Paint fillPaint = new Paint();
        fillPaint.setColor(Color.GREEN);
        fillPaint.setAlpha(128);
        fillPaint.setStyle(Paint.Style.FILL);

        Paint borderPaint = new Paint();
        final int[] borderColors = new int[]{Color.GREEN, Color.WHITE, Color.WHITE, Color.RED};
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(10);

        double[] points = detection.p;
        if (points == null || points.length != 8) {
            Log.w(TAG, "invalid detection coordinates");
            return;
        }

        // Convert detection points (analysis-image space) to canvas points.
        float[] xPointsCanvas = new float[4];
        float[] yPointsCanvas = new float[4];
        for (int i = 0; i < 4; i++) {
            float[] p = mapToCanvas(points[i * 2], points[i * 2 + 1], canvas);
            xPointsCanvas[i] = p[0];
            yPointsCanvas[i] = p[1];
        }

        // Render filled outline of detections
        Path fillPath = new Path();
        for (int i = 0; i < 4; i++) {
            if (i == 0) {
                fillPath.moveTo(xPointsCanvas[i], yPointsCanvas[i]);
            } else {
                fillPath.lineTo(xPointsCanvas[i], yPointsCanvas[i]);
            }
        }
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // Render stroke outline of detections
        int colorIndex = 0;
        for (int i = 0; i < 4; i++) {
            Path borderPath = new Path();
            borderPaint.setColor(borderColors[colorIndex++ % borderColors.length]);

            borderPath.moveTo(xPointsCanvas[i], yPointsCanvas[i]);
            borderPath.lineTo(xPointsCanvas[(i + 1) % 4], yPointsCanvas[(i + 1) % 4]);
            canvas.drawPath(borderPath, borderPaint);
        }

        // Render tag ID in the center of the detection box
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(100);
        String tagId = String.valueOf(detection.id);
        float textWidth = textPaint.measureText(tagId);
        float textHeight = textPaint.getFontMetrics().descent - textPaint.getFontMetrics().ascent;
        float[] center = mapToCanvas(detection.c[0], detection.c[1], canvas);
        float textX = center[0] - textWidth / 2;
        float textY = center[1] + textHeight / 2 - textPaint.getFontMetrics().descent;
        canvas.drawText(tagId, textX, textY, textPaint);
    }

    /**
     * Map a point from analysis-image coordinates onto the overlay canvas so it
     * lines up with the CameraX PreviewView. Applies the frame rotation, then a
     * uniform fit-center scale + centering — matching PreviewView's fitCenter
     * scaleType (back camera, no mirroring).
     */
    private float[] mapToCanvas(double x, double y, Canvas canvas) {
        int imgW = mCameraSize.getWidth();
        int imgH = mCameraSize.getHeight();
        int r = ((mRotationDegrees % 360) + 360) % 360;

        double xu, yu;   // upright-image coordinates
        int upW, upH;    // upright-image dimensions
        switch (r) {
            case 90:  xu = imgH - y; yu = x;         upW = imgH; upH = imgW; break;
            case 180: xu = imgW - x; yu = imgH - y;  upW = imgW; upH = imgH; break;
            case 270: xu = y;        yu = imgW - x;  upW = imgH; upH = imgW; break;
            default:  xu = x;        yu = y;         upW = imgW; upH = imgH; break; // 0
        }

        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        float scale = Math.min((float) cw / upW, (float) ch / upH); // fitCenter
        float offsetX = (cw - upW * scale) / 2f;
        float offsetY = (ch - upH * scale) / 2f;

        return new float[]{ offsetX + (float) xu * scale, offsetY + (float) yu * scale };
    }

    private void renderDetections(ArrayList<ApriltagDetection> detections) {
        Canvas canvas = mTextureView.lockCanvas();
        try {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            for (ApriltagDetection detection : detections) {
                renderDetection(detection, canvas);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error rendering detections: " + e.getMessage());
        } finally {
            if (canvas != null) {
                mTextureView.unlockCanvasAndPost(canvas);
            }
        }
    }

    public void initialize() {
        Log.i(TAG, "Detection thread initialize");
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            updateFps();

            if (mCameraFrameQueue == null) {
                continue;
            }

            byte[] data;
            try {
                data = mCameraFrameQueue.take();
            } catch (InterruptedException e) {
                Log.i(TAG, "Interrupted while waiting for camera frame: " + e.getMessage());
                break;
            }

            ArrayList<ApriltagDetection> detections = processCameraFrame(data, mCameraSize);
            renderDetections(detections);

            mLastDetectLatency = (System.currentTimeMillis() - mLastEnqueueFrameTime);
        }
    }
}