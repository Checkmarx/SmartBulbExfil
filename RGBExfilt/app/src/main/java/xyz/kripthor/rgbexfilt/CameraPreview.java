package xyz.kripthor.rgbexfilt;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.List;


public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    public PreviewCallback mCallback;
    public boolean fixed = false;
    public int WIDTH;
    public int HEIGHT;


    public CameraPreview(Context context, Camera camera, TextView statusView, TextView dataView, int w, int h) {
        super(context);
        mCamera = camera;
        this.WIDTH = w;
        this.HEIGHT = h;

        mCallback = new PreviewCallback(camera, this.WIDTH, this.HEIGHT);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setAllParams() {

        Camera.Parameters par = mCamera.getParameters();
        par.setPreviewSize(WIDTH,HEIGHT);
        par.setPreviewFormat(ImageFormat.NV21);
       // par.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
        par.setAntibanding(Camera.Parameters.ANTIBANDING_50HZ);

        if (fixed) {
            par.setAutoExposureLock(true);
            par.setAutoWhiteBalanceLock(true);
        } else {
            par.setAutoExposureLock(false);
            par.setAutoWhiteBalanceLock(false);
        }
        mCamera.setParameters(par);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            setAllParams();
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(mCallback);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d("RGBEX", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            setAllParams();
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(mCallback);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d("RGBEX", "Error starting camera preview: " + e.getMessage());
        }
    }


    public void resetStats() {
        mCallback.zeroStats();
        mCallback.setRunning(this.fixed);
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
        mCallback.setRunning(this.fixed);
    }

    public boolean getRunning() {
        return fixed;
    }
}