package com.zerry.mobisys16demo;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.graphics.PixelFormat;

import com.github.clans.fab.FloatingActionButton;
import com.kyleduo.switchbutton.SwitchButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private FloatingActionButton mFabBtnTake, mFabBtnYes, mFabBtnNo;
    private SwitchButton camSwitchBtn;
    private static int front1back0 = 0;
    private int orientCase;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private boolean mInPreview = false, mCameraConfigured = false;
    private Camera.Size size;
    private Camera.Size imgSize;
    private ImageView mImageView;
    private OrientationEventListener mOrientationListener;
    private byte[] mResultFrm;
    private static final String TAG = "MobiSys16Demo";
    private static FaceTagDet ftdetector;

    private String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/VisualPrivacy/";
    private String[] mkrNames = new String[]{"card.jpg", "privacy2.jpg", "hkust.jpg", "sunflower.jpg", "starsky.jpg"};

    static {
        System.loadLibrary("facetagdet");
    }

    private void initialize() {
        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
        File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
        if (!cascadeFile.exists()) {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            try {
                FileOutputStream os = new FileOutputStream(cascadeFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                try {
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    os.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        File dir = new File(DATA_PATH);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.v(TAG, "ERROR: Creation of directory " + DATA_PATH + " on sdcard failed");
                return;
            } else {
                Log.v(TAG, "Created directory " + DATA_PATH + " on sdcard");
            }
        }

        for (int i=0; i<mkrNames.length; i++) {
            String mkrname = mkrNames[i];
            if (!(new File(DATA_PATH + mkrname)).exists()) {
                try {
                    AssetManager assetManager = getAssets();
                    InputStream in = assetManager.open(mkrname);
                    OutputStream out = new FileOutputStream(DATA_PATH
                            + mkrname);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();
                    Log.v(TAG, "Copied " + mkrname);
                } catch (IOException e) {
                    Log.e(TAG, "Was unable to copy " + mkrname + e.toString());
                }
            }
        }

        if (ftdetector == null) {
            ftdetector = new FaceTagDet(cascadeFile.getAbsolutePath());
            for (int i=0; i<mkrNames.length; i++)
                ftdetector.extractTagFeatures(DATA_PATH+mkrNames[i]);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();

        mFabBtnTake = (FloatingActionButton) findViewById(R.id.fab_button_take_photo);
        mFabBtnTake.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCamera != null) {
                    mCamera.takePicture(null, null, null, mJpegCallback);
                    Toast.makeText(MainActivity.this, "Photo Taken", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(surfaceCallback);

        camSwitchBtn = (SwitchButton) findViewById(R.id.camswitch);
        camSwitchBtn.setChecked(front1back0 == 0);
        camSwitchBtn.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                switchCam();
            }

        });

        mImageView = (ImageView) findViewById(R.id.imageView);
        mImageView.setBackgroundColor(Color.rgb(0,0,0));
        mImageView.setVisibility(View.INVISIBLE);

        mFabBtnYes = (FloatingActionButton) findViewById(R.id.fab_button_yes);
        mFabBtnYes.setVisibility(View.GONE);
        mFabBtnYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageView.setVisibility(View.INVISIBLE);
                mFabBtnYes.setVisibility(View.GONE);
                mFabBtnNo.setVisibility(View.GONE);

                startPreview();
            }
        });

        mFabBtnNo = (FloatingActionButton) findViewById(R.id.fab_button_no);
        mFabBtnNo.setVisibility(View.GONE);
        mFabBtnNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageView.setVisibility(View.INVISIBLE);
                mFabBtnYes.setVisibility(View.GONE);
                mFabBtnNo.setVisibility(View.GONE);

                startPreview();
            }
        });

        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if ((orientation >= 0 && orientation <= 30) || (orientation >= 330 && orientation <= 360)) {
                    orientCase = 0;
                } else if (orientation >= 60 && orientation <= 120) {
                    orientCase = 1;
                } else if (orientation >= 150 && orientation <= 210) {
                    orientCase = 2;
                } else if (orientation >= 240 && orientation <= 300) {
                    orientCase = 3;
                } else {
                }
//                Log.i(TAG, "Orientation changed to " + orientation +
//                        ", case " + orientCase);
            }
        };
    }

    private Camera.PictureCallback mJpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            process(data);
            display();
        }
    };

    private void process(byte[] data) {
        if (ftdetector == null) initialize();
        mResultFrm = ftdetector.droidJPEGCalibrate(data, front1back0, orientCase);
        ftdetector.detectFaceTagFromJPEG(mResultFrm);

        int[] facepos = ftdetector.getBBXPos(0);
        int[] tagpos = ftdetector.getBBXPos(1);
        // mResultFrm = ftdetector.drawFacesPos(mResultFrm, facepos);
        mResultFrm = ftdetector.drawTagsPos(mResultFrm, tagpos);

        Log.i(TAG, "faces coordinates: " + Arrays.toString(facepos));
        // Log.i(TAG, "tags coordinates: " + Arrays.toString(tagpos));

        int facenum = facepos.length / 5;
        int[][] faceposArr = new int[facenum][4];
        boolean[] faceprocArr = new boolean[facenum];

        for(int i=0; i < facenum; i++) {
            faceposArr[i][0] = facepos[5*i];
            faceposArr[i][1] = facepos[5*i+1];
            faceposArr[i][2] = facepos[5*i+2];
            faceposArr[i][3] = facepos[5*i+3];

            faceprocArr[i] = facepos[5*i+4] > 0;
        }

        // faceposArr: int[facenum][4], facepos: int[facenum*4], faceprocArr: boolean[facenum]
        // tagpos: int[tagnum*8], 8 is 8 coordinates of 4 points, 8 is because
        //  they are not necessarily rectangle after affine projection
        // decision making: based on faceposArr(or facepos), tagpos, and handposArr,
        //  assign value to faceprocArr, true means will blur it

        // -- decision making start


        // -- decision making end

        mResultFrm = ftdetector.bbxProcess(mResultFrm, faceposArr, faceprocArr);
    }

    private void display() {
        mImageView.setVisibility(View.VISIBLE);
        mFabBtnYes.setVisibility(View.VISIBLE);
        mFabBtnNo.setVisibility(View.VISIBLE);

        Bitmap bitmap = BitmapFactory.decodeByteArray(mResultFrm, 0, mResultFrm.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(360 - 90 * orientCase);

        if (front1back0 == 1) {
            matrix.preScale(-1, 1);
        }

        bitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        mImageView.setImageBitmap(bitmap);
    }

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.i(TAG, "surfaceCreated() called...");
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.i(TAG, " surfaceChanged() called.");
            initPreview(width, height);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, " surfaceDestroyed() called.");
        }
    };


    private void initPreview(int width, int height) {
        Log.i(TAG, "initPreview() called");
        if (mCamera != null && mHolder.getSurface() != null) {
            if (!mCameraConfigured) {
                Camera.Parameters params = mCamera.getParameters();
                size = params.getPreviewSize();
                for (Camera.Size s : params.getSupportedPreviewSizes()) {   // get 3840x2160 for back cam
//                    Log.i(TAG, "Supported preview size: " + s.width + ", " + s.height);
                    if (s.width > size.width)
                        size = s;
                }
                params.setPreviewSize(size.width, size.height);
                Log.i(TAG, "Preview size: " + size.width + ", " + size.height);
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);

                imgSize = params.getPictureSize();
                Camera.Size targetSize = mCamera.new Size(0, 0);
                for (Camera.Size s : params.getSupportedPictureSizes()) {
//                    Log.i(TAG, "Supported image size: " + s.width + ", " + s.height);
                    if (s.width > targetSize.width && s.width < 2000)
                        targetSize = s;
                }
                imgSize = targetSize;
                params.setPictureFormat(PixelFormat.JPEG);
                params.setJpegQuality(100);
                params.setPictureSize(imgSize.width, imgSize.height);
                Log.i(TAG, "Image Size: " + imgSize.width + ", " + imgSize.height);

                mCamera.setParameters(params);
                mCameraConfigured = true;

                if (mOrientationListener.canDetectOrientation() == true) {
                    mOrientationListener.enable();
                }
            }

            try {
                mCamera.setPreviewDisplay(mHolder);
            } catch (Throwable t) {
                Log.e(TAG, "Exception in initPreview()", t);
            }

        }
    }

    private void startPreview() {
        Log.i(TAG, "startPreview() called");
        if (mCameraConfigured && mCamera != null) {
            mCamera.startPreview();
            mInPreview = true;
        }
    }

    @Override
    public void onResume() {
        Log.i(TAG, " onResume() called.");
        super.onResume();
        mCamera = Camera.open(front1back0);   // 0 for back, 1 for frontal
        mCamera.setDisplayOrientation(90);
        startPreview();
    }

    @Override
    public void onPause() {
        Log.i(TAG, " onPause() called.");
        if (mInPreview) {
            mCamera.stopPreview();
        }
        mCamera.release();
        mCamera = null;
        mInPreview = false;
        mCameraConfigured = false; // otherwise cannot refocus after onResume
        mOrientationListener.disable();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, " onDestroy() called.");
        super.onDestroy();
    }

    private void switchCam() {
        if (mCamera != null && mInPreview) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mInPreview = false;
            mCameraConfigured = false;
        }

        front1back0 = 1 - front1back0;
        mCamera = Camera.open(front1back0);   // 0 for back, 1 for frontal
        mCamera.setDisplayOrientation(90);
        initPreview(size.width, size.height);
        startPreview();
    }

}
