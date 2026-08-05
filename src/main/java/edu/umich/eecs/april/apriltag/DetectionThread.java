package edu.umich.eecs.april.apriltag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

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

    // Binnacle accent contract, resolved from theme tokens (respects light/dark).
    private final int mColorGood;   // clean decode
    private final int mColorNow;    // error-corrected: read, but less certain
    private final int mColorText;   // labels / orientation marker



    public DetectionThread(TextureView textureView, TextView fpsTextView) {
        mTextureView = textureView;
        mFpsTextView = fpsTextView;

        Context ctx = textureView.getContext();
        mColorGood = ContextCompat.getColor(ctx, R.color.bin_good);
        mColorNow = ContextCompat.getColor(ctx, R.color.bin_now);
        mColorText = ContextCompat.getColor(ctx, R.color.bin_text);
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
        double[] points = detection.p;
        if (points == null || points.length != 8) {
            Log.w(TAG, "invalid detection coordinates");
            return;
        }

        // Accent contract: a clean decode is "good" (cyan); an error-corrected
        // decode is "now" (amber) — read, but less certain. The distinction also
        // carries a non-colour channel (solid vs dashed border) so it survives the
        // greyscale test.
        boolean corrected = detection.hamming > 0;
        int accent = corrected ? mColorNow : mColorGood;

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(accent);
        fillPaint.setAlpha(90);
        fillPaint.setStyle(Paint.Style.FILL);

        // Convert detection points (analysis-image space) to canvas points.
        float[] xPointsCanvas = new float[4];
        float[] yPointsCanvas = new float[4];
        for (int i = 0; i < 4; i++) {
            float[] p = mapToCanvas(points[i * 2], points[i * 2 + 1], canvas);
            xPointsCanvas[i] = p[0];
            yPointsCanvas[i] = p[1];
        }

        // Shortest on-screen edge of the tag quad — used to scale the border and
        // text so they stay proportional to how big the tag appears.
        float minEdge = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float dx = xPointsCanvas[(i + 1) % 4] - xPointsCanvas[i];
            float dy = yPointsCanvas[(i + 1) % 4] - yPointsCanvas[i];
            minEdge = Math.min(minEdge, (float) Math.hypot(dx, dy));
        }

        // Render translucent fill so the tag stays visible underneath.
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

        // Border in the accent colour, thickness scaled to tag size. Corrected
        // reads use a dashed stroke — the non-colour channel for the distinction.
        float strokeWidth = Math.max(3f, minEdge * 0.06f);
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(strokeWidth);
        borderPaint.setColor(accent);
        if (corrected) {
            float dash = Math.max(6f, minEdge * 0.14f);
            borderPaint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0f));
        }
        canvas.drawPath(fillPath, borderPaint);

        // Orientation marker: a dot at the first corner encodes which way the tag
        // is turned, without relying on colour to signal it.
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(mColorText);
        canvas.drawCircle(xPointsCanvas[0], yPointsCanvas[0], Math.max(4f, strokeWidth * 1.4f), dotPaint);

        // Render the tag ID centered in the box, sized to fit, with a dark halo
        // so it stays legible over the tag and any background.
        String tagId = String.valueOf(detection.id);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        // Fit the number within the box in both height and width.
        float textSize = Math.max(10f, minEdge * 0.55f);
        textPaint.setTextSize(textSize);
        float measured = textPaint.measureText(tagId);
        float maxWidth = minEdge * 0.85f;
        if (measured > maxWidth && measured > 0) {
            textSize = Math.max(10f, textSize * maxWidth / measured);
            textPaint.setTextSize(textSize);
        }

        float[] center = mapToCanvas(detection.c[0], detection.c[1], canvas);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = center[1] - (fm.ascent + fm.descent) / 2f; // vertically center

        Paint outlinePaint = new Paint(textPaint);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(Color.BLACK);
        outlinePaint.setStrokeWidth(Math.max(2f, textSize * 0.14f));

        canvas.drawText(tagId, center[0], baseline, outlinePaint);
        canvas.drawText(tagId, center[0], baseline, textPaint);
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