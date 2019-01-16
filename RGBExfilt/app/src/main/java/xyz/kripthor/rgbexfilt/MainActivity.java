package xyz.kripthor.rgbexfilt;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.hardware.*;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;

import static java.lang.Thread.sleep;


public class MainActivity extends Activity  {

    private CameraPreview campreview;
    private Camera camera;
    private int CAMERA_ID = 0;
    private TextView dataView,statusView;
    public Handler mHandler;
    private int WIDTH,HEIGHT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusView = findViewById(R.id.textView4);
        dataView = findViewById(R.id.textView3);
        CAMERA_ID = findFrontFacingCamera();
        mHandler = new Handler();
        WIDTH = 640; HEIGHT = 480;
        new Thread(new Runnable(){
            @Override
            public void run () {
                try {
                    while(true) {
                        sleep(1000);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (campreview != null) {
                                    String statusString = campreview.mCallback.dt.getStatusString();
                                    String dataString = campreview.mCallback.dt.getDataString();
                                    if (statusString != null && statusString.length() > 1) statusView.setText(statusString);
                                    if (dataString != null && dataString.length() > 1 && campreview.getRunning()) dataView.setText(dataString);
                                    //statusView.invalidate();
                                    //dataView.invalidate();
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    protected void onResume() {

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG).show();
        } else {
            camera = Camera.open(CAMERA_ID);
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(CAMERA_ID, info);
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0: degrees = 0; break;
                case Surface.ROTATION_90: degrees = 90; break;
                case Surface.ROTATION_180: degrees = 180; break;
                case Surface.ROTATION_270: degrees = 270; break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }

            camera.setDisplayOrientation(result);
            campreview = new CameraPreview(this, camera, statusView,dataView, WIDTH, HEIGHT);
            FrameLayout preview = findViewById(R.id.camera_preview);
            preview.addView(campreview);
        }
        super.onResume();
    }

    protected void onPause() {

        camera.setPreviewCallback(null);
        FrameLayout preview = findViewById(R.id.camera_preview);
        preview.removeAllViews();
        if (camera != null) {
            camera.release();
            camera = null;
        }

        super.onPause();
    }

    private int findFrontFacingCamera() {
        int cameraId = 0;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }

    public void toogleClick(View v) {
        campreview.setFixed(((ToggleButton) findViewById(R.id.toggleButton2)).isChecked());
        campreview.setAllParams();
        campreview.resetStats();
    }

    public void cycleCamera(View v) {
        ToggleButton tg = findViewById(R.id.toggleButton2);
        if (tg.isChecked()) tg.toggle();
        int numberOfCameras = Camera.getNumberOfCameras();
        CAMERA_ID = CAMERA_ID + 1;
        if (CAMERA_ID >= numberOfCameras) {
            CAMERA_ID = 0;
        }
        this.onPause();
        this.onResume();

    }

    public void toogleResolution(View v) {
        if (WIDTH == 320) {
            WIDTH = 640; HEIGHT = 480;
        } else {
            WIDTH = 320; HEIGHT = 240;
        }
        ToggleButton tg = findViewById(R.id.toggleButton2);
        if (tg.isChecked()) tg.toggle();
        this.onPause();
        this.onResume();
    }

}