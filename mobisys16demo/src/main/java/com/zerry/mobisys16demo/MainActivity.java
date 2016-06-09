package com.zerry.mobisys16demo;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private int xmax, ymax;
    private OrientationEventListener mOrientationListener;
    private byte[] mResultFrm;
    private static FaceTagDet ftdetector;

    private Socket mSocket;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private boolean isSocketTaskCompleted = false;

    private int[][] handposArr;
    private String[] handtxtArr;

    private static final String TAG = "MobiSys16Demo";
    private final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/VisualPrivacy/";
    private final String[] mkrNames = new String[]{"card.jpg", "privacy2.jpg", "hkust.jpg", "sunflower.jpg", "starsky.jpg"};

    private final String DST_ADDRESS = "10.89.159.44";
    private final int DST_PORT = 9999;

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
        mImageView.setBackgroundColor(Color.rgb(0, 0, 0));
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

        // create socket and send data to the server
        new socketTask(DST_ADDRESS, DST_PORT).execute(mResultFrm);

        // continue face and tag detection
        ftdetector.detectFaceTagFromJPEG(mResultFrm);

        int[] facepos = ftdetector.getBBXPos(0);
        int[] tagpos = ftdetector.getBBXPos(1);
//        mResultFrm = ftdetector.drawFacesPos(mResultFrm, facepos);
        mResultFrm = ftdetector.drawTagsPos(mResultFrm, tagpos);

        Log.i(TAG, "faces coordinates: " + Arrays.toString(facepos));
        Log.i(TAG, "tags coordinates: " + Arrays.toString(tagpos));

        int facenum = facepos.length / 4;
        int[][] faceposArr = new int[facenum][4];
        boolean[] faceprocArr = new boolean[facenum];

        for(int i=0; i < facenum; i++) {
            faceposArr[i][0] = facepos[4*i];
            faceposArr[i][1] = facepos[4*i+1];
            faceposArr[i][2] = facepos[4*i+2];
            faceposArr[i][3] = facepos[4*i+3];

            faceprocArr[i] = true;
        }

        int tagnum = tagpos.length / 8;
        int[][] tagposArr = new int[tagnum][8];

        for(int i=0; i < tagnum; i++) {
            tagposArr[i][0] = tagpos[8*i];
            tagposArr[i][1] = tagpos[8*i+1];
            tagposArr[i][2] = tagpos[8*i+2];
            tagposArr[i][3] = tagpos[8*i+3];
            tagposArr[i][4] = tagpos[8*i+4];
            tagposArr[i][5] = tagpos[8*i+5];
            tagposArr[i][6] = tagpos[8*i+6];
            tagposArr[i][7] = tagpos[8*i+7];
        }

        // faceposArr: int[facenum][4], facepos: int[facenum*4], faceprocArr: boolean[facenum]
        // tagpos: int[tagnum*8], 8 is 8 coordinates of 4 points, 8 is because
        //  they are not necessarily rectangle after affine projection
        // decision making: based on faceposArr(or facepos), tagpos, and handposArr,
        //  assign value to faceprocArr, true means will blur it

        // waiting for hand results form socketTask
        while (!isSocketTaskCompleted) {
            Log.i(TAG, "waiting for asyncTask result");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        isSocketTaskCompleted = false; // wait for next result

        // -- decision making start
        // Case 1: gesture "no" --> blurring
        // Case 2: gesture "yes" --> not blurring
        // Case 3: tag alone --> blurring

        // find nearest face for each yes or no gesture.
        // in gestureUser, key is the face index, value is "yes" or "no".
        // in tagUser, nearest face index is stored.
        Map<Integer, String> gestureUser = new HashMap<>();
        List<Integer> tagUser = new ArrayList<>();

        int[][] faceCenters = new int[facenum][2];
        for (int i = 0; i < facenum; i ++) {
            faceCenters[i] = getCenter(faceposArr[i]);
        }

        int handnum = handposArr.length;
        int[][] handCenters = new int[handnum][2];
        for (int i = 0; i < handnum; i++) {
            if (handtxtArr[i].contains("yes")) {
                gestureUser.put(getNearest(getCenter(handposArr[i]), faceCenters),  "yes");

            } else if (handtxtArr[i].contains("no")) {
                gestureUser.put(getNearest(getCenter(handposArr[i]), faceCenters), "no");
            }
        }

        // find nearest face for each tag
        int[][] tagCenters = new int[tagnum][2];
        for (int i = 0; i < tagnum; i++) {
            tagUser.add(getNearest(getCenter(tagposArr[i]), faceCenters));
        }

        // processing for each face, defalut proArr is false.
        // We put value no after yes, so if yes and no gesture find same nearest face, no will overwrite.
        // create the map for detailed case situation
        //Map<String, ArrayList<Integer>> results = new HashMap<>();
        //List<Integer> case1 = new ArrayList<>();
        //List<Integer> case2 = new ArrayList<>();
        //List<Integer> case3 = new ArrayList<>();

        for (int i = 0; i < facenum; i++) {
            if (gestureUser.containsKey(i) && gestureUser.get(i).contains("yes")) {
                    faceprocArr[i] = false;
            } else {
                if (!tagUser.contains(i)) { // no gesture or tag
                    faceprocArr[i] = false;
                }
            }
        }

        // -- decision making end



        mResultFrm = ftdetector.bbxProcess(mResultFrm, faceposArr, faceprocArr);
        // TODO: Draw hand
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

    private class socketTask extends AsyncTask<byte[], Void, Void> {
        String dstAddress;
        int dstPort;

        socketTask(String addr, int port) {
            this.dstAddress = addr;
            this.dstPort = port;
        }

        @Override
        protected Void doInBackground(byte[]... data) {
            // prepare package content
            // header (frmSize, 1 integer) | frmData
            int frmSize = data[0].length;
            Log.i(TAG, "data size: " + Integer.toString(frmSize));

            xmax = (orientCase == 0 || orientCase == 2) ? imgSize.height : imgSize.width;
            ymax = (orientCase == 0 || orientCase == 2) ? imgSize.width : imgSize.height;

            // allocate 4 byte for packetContent
            byte[] frmsize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(frmSize).array();
            byte[] packetContent = new byte[4 + frmSize];
            System.arraycopy(frmsize, 0, packetContent, 0, 4);
            System.arraycopy(data, 0, packetContent, 4, frmSize);

            // send package content and then receive data
            try {
                mSocket = new Socket(dstAddress, dstPort);
                Log.i(TAG, "Socket established");
                mOutputStream = mSocket.getOutputStream();
                mInputStream = mSocket.getInputStream();

                if (mOutputStream != null) {
                    try {
                        Log.i(TAG, "start sending...");
                        mOutputStream.write(packetContent);
                        mOutputStream.flush();
                        Log.i(TAG, "finish sending...");


                        // start receiving data
                        // header (4 bytes) | resData (dataSize bytes)
                        Log.i(TAG, "start receiving...");
                        byte[] header = new byte[4];
                        int readSize = 0;
                        readSize = mInputStream.read(header);
                        assert(readSize == 4);

                        int[] dataSize = byteToInt(header);
                        byte[] resData = new byte[dataSize[0]];
                        readSize = mInputStream.read(resData);
                        assert(readSize == dataSize[0]);

                        // parse hand result
                        int bbxNum = dataSize[0] / 24;
                        handposArr = new int[bbxNum][4];
                        handtxtArr = new String[bbxNum];
                        int ibbx = 0;

                        for (int i = 0; i < dataSize[0]; i += 24) {
                            int[] bbxpos = byteToInt(Arrays.copyOfRange(resData, i, i+16));
                            int bbxcls = byteToInt(Arrays.copyOfRange(resData, i+16, i+20))[0];
                            float scr = ByteBuffer.wrap(Arrays.copyOfRange(resData, i+20, i+24)).order(ByteOrder.LITTLE_ENDIAN).getFloat();
//                            Log.i(TAG, "hand bbx: " + Arrays.toString(bbxpos) + ", class: "
//                                    + bbxcls + ", score: " + scr);

                            bbxpos[0] = Math.max(bbxpos[0], 0);
                            bbxpos[1] = Math.max(bbxpos[1], 0);
                            bbxpos[2] = Math.min(bbxpos[2], xmax);
                            bbxpos[3] = Math.min(bbxpos[3], ymax);

                            handposArr[ibbx] = bbxpos;
                            handtxtArr[ibbx] = (bbxcls == 2 ? "yes" : (bbxcls == 3 ? "no" : "normal")) + " " + scr;

                            ibbx ++;
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        if (mSocket != null) {
                            Log.i(TAG, "Connection lost.");
                            try {
                                mOutputStream.close();
                                mSocket.close();
                                mOutputStream = null;
                                mSocket = null;
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            isSocketTaskCompleted = true;
            return null;
        }

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

    private int[] byteToInt(byte[] input) {
        int[] output = new int[input.length/4];

        for (int i = 0; i < input.length; i += 4) {
            output[i/4] = input[i] & 0xFF |
                    (input[i+1] & 0xFF) << 8 |
                    (input[i+2] & 0xFF) << 16 |
                    (input[i+3] & 0xFF) << 24;
        }
        return output;
    }

    private int[] getCenter(int[] coordinates) {
        int[] center = new int[2];

        return center;
    }

    private int getNearest(int[] a, int[][] b) {
        int index = -1;

        return index;
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

}
