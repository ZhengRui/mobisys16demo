package com.zerry.mobisys16demo;

/**
 * Created by zerry on 6/8/16.
 */
public class FaceTagDet {
    public FaceTagDet(String cascadeFile) {
        nativeDetector = create(cascadeFile);
    }

    public void extractTagFeatures(String mkrpath) {
        extractTagFeatures(nativeDetector, mkrpath);
    }

    public void detectFaceTagFromRaw(int width, int height, byte[] frmdata, int front1orback0,
                        int orientCase) {
        detectFaceTagFromRaw(nativeDetector, width, height, frmdata, front1orback0, orientCase);
    }

    public byte[] droidJPEGCalibrate(byte[] jpegdata, int front1orback0, int orientCase) {
        return droidJPEGCalibrate(nativeDetector, jpegdata, front1orback0, orientCase);
    }

    public void detectFaceTagFromJPEG(byte[] jpegdata) {
        detectFaceTagFromJPEG(nativeDetector, jpegdata);
    }

    public int[] getBBXPos(int face0tag1) {
        return getBBXPos(nativeDetector, face0tag1);
    }

    public byte[] drawFacesPos(byte[] jpegdata, int[] facepos) {
        return drawFaces(jpegdata, facepos);
    }

    public byte[] drawTagsPos(byte[] jpegdata, int[] tagpos) {
        return drawTags(jpegdata, tagpos);
    }

    public byte[] drawHandsPos(byte[] jpegdata, int[][] handpos, String[] handtxt) {
        return drawHands(jpegdata, handpos, handtxt);
    }

    public byte[] bbxProcess(byte[] jpegdata, int[][] bbxposArr, boolean[] bbxprocArr) {
        return boxesProcess(jpegdata, bbxposArr, bbxprocArr);
    }

    private long nativeDetector = 0;
    private static native long create(String cascadeFile);
    private static native void extractTagFeatures(long thiz, String mkrPath);
    private static native void detectFaceTagFromRaw(long thiz, int width, int height,
                                       byte[] frmdata, int front1orback0, int orientCase);
    private static native byte[] droidJPEGCalibrate(long thiz, byte[] jpegdata, int front1orback0, int orientCase);
    private static native void detectFaceTagFromJPEG(long thiz, byte[] jpegdata);
    private static native int[] getBBXPos(long thiz, int face0tag1);
    private static native byte[] drawFaces(byte[] jpegdata, int[] facepos);
    private static native byte[] drawTags(byte[] jpegdata, int[] tagpos);
    private static native byte[] drawHands(byte[] jpegdata, int[][] handpos, String[] handtxt);
    private static native byte[] boxesProcess(byte[] jpegdata, int[][] bbxposArr, boolean[] bbxprocArr);

}
