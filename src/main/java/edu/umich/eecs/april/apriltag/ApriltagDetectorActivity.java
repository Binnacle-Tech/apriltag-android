package edu.umich.eecs.april.apriltag;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

/**
 * Main activity: shows a live camera preview (via CameraX) and overlays detected
 * AprilTags. The AprilTag detector runs natively on a background thread.
 */
public class ApriltagDetectorActivity extends AppCompatActivity {
    private static final String TAG = "AprilTag";
    private DetectionThread mDetectionThread;
    private CameraController mCameraController;

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 77;
    private int has_camera_permissions = 0;
    private boolean mAskedForCamera = false;

    private void verifyPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        int nthreads = Integer.parseInt(sharedPreferences.getString("nthreads_value", "0"));
        if (nthreads <= 0) {
            int nproc = Runtime.getRuntime().availableProcessors();
            if (nproc <= 0) {
                nproc = 1;
            }
            Log.i(TAG, "available processors: " + nproc);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("nthreads_value", Integer.toString(nproc)).apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Add toolbar/actionbar. The title is a custom view in the layout (so the
        // display font applies on all API levels), so hide the default title.
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Make the screen stay awake
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Ensure we have permission to use the camera
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraAccess();
        } else {
            this.has_camera_permissions = 1;
        }
    }

    /** Request the camera permission, or send the user to Settings if it was
     *  permanently denied. */
    private void requestCameraAccess() {
        if (!mAskedForCamera
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            mAskedForCamera = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        } else {
            // Permanently denied — the system dialog won't appear, so open Settings.
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        }
    }

    /** Show the stop-state card over the preview. */
    private void showStopState(String title, String message, String buttonText, Runnable action) {
        ((TextView) findViewById(R.id.stopStateTitle)).setText(title);
        ((TextView) findViewById(R.id.stopStateMessage)).setText(message);
        Button button = findViewById(R.id.stopStateButton);
        button.setText(buttonText);
        button.setOnClickListener(v -> action.run());
        findViewById(R.id.stopState).setVisibility(View.VISIBLE);
    }

    private void hideStopState() {
        findViewById(R.id.stopState).setVisibility(View.GONE);
    }

    /** Release the camera when the application is exited */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopThreads();
        Log.i(TAG, "Finished destroying.");
    }

    /** Release the camera when application focus is lost */
    @Override
    protected void onPause() {
        super.onPause();
        stopThreads();
        Log.i(TAG, "Finished pause.");
    }

    private void stopThreads() {
        if (mCameraController != null) {
            mCameraController.stop();
            mCameraController = null;
        }
        if (mDetectionThread != null) {
            mDetectionThread.interrupt();
            mDetectionThread.destroy();
            try {
                mDetectionThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mDetectionThread = null;
        }
    }

    /** (Re-)initialize the camera */
    @Override
    protected void onResume() {
        super.onResume();

        // Check permissions — fail loud, gate clearly.
        if (this.has_camera_permissions == 0) {
            Log.w(TAG, "Missing camera permissions.");
            showStopState("Camera access needed",
                    "AprilTag Detector needs the camera to find and read tags.",
                    "Grant camera access", this::requestCameraAccess);
            return;
        }
        hideStopState();

        // DETECTION INIT
        // Re-initialize the Apriltag detector as settings may have changed
        verifyPreferences();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        double decimation = Double.parseDouble(sharedPreferences.getString("decimation_list", "8"));
        double sigma = Double.parseDouble(sharedPreferences.getString("sigma_value", "0"));
        int nthreads = Integer.parseInt(sharedPreferences.getString("nthreads_value", "4"));
        int max_hamming_error = Integer.parseInt(sharedPreferences.getString("max_hamming_error", "0"));
        boolean diagnosticsEnabled = sharedPreferences.getBoolean("diagnostics_enabled", false);
        String tagFamily = sharedPreferences.getString("tag_family_list", "tag36h11");
        Log.i(TAG, String.format("decimation: %f | sigma: %f | nthreads: %d | tagFamily: %s",
                decimation, sigma, nthreads, tagFamily));
        ApriltagNative.apriltag_init(tagFamily, max_hamming_error, decimation, sigma, nthreads);

        // DIAGNOSTICS — the telemetry well is machine data; show it only when enabled.
        findViewById(R.id.telemetryWell).setVisibility(diagnosticsEnabled ? View.VISIBLE : View.GONE);
        TextView tagFamilyText = findViewById(R.id.tagFamily);
        tagFamilyText.setText("Tag · " + tagFamily.substring(3));

        // THREAD INIT
        // Start the detection process on a separate thread
        android.view.TextureView detectionSurface = findViewById(R.id.tagView);
        TextView detectionFpsTextView = findViewById(R.id.detectionFpsTextView);
        mDetectionThread = new DetectionThread(detectionSurface, detectionFpsTextView);
        mDetectionThread.initialize();
        mDetectionThread.start();

        // Start the CameraX preview + analysis pipeline
        PreviewView previewView = findViewById(R.id.previewView);
        TextView previewFpsTextView = findViewById(R.id.previewFpsTextView);
        mCameraController = new CameraController(this, previewView, mDetectionThread, previewFpsTextView);
        mCameraController.setOnErrorListener(message ->
                showStopState("Camera unavailable", message, "Retry", () -> {
                    onPause();
                    onResume();
                }));
        mCameraController.start(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.settings) {
            verifyPreferences();
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.reset) {
            // Reset all shared preferences to default values
            PreferenceManager.getDefaultSharedPreferences(this).edit().clear().apply();

            // Restart the camera preview
            onPause();
            onResume();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "App GRANTED camera permissions");
                this.has_camera_permissions = 1;

                // Restart the camera
                onPause();
                onResume();
            } else {
                Log.i(TAG, "App DENIED camera permissions");
                this.has_camera_permissions = 0;
                showStopState("Camera access needed",
                        "AprilTag Detector needs the camera to find and read tags.",
                        "Grant camera access", this::requestCameraAccess);
            }
        }
    }
}
