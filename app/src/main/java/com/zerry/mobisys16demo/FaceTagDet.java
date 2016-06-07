package com.zerry.mobisys16demo;

/**
 * Created by zerry on 6/8/16.
 */
public class FaceTagDet {
    public FaceTagDet(String cascadeFile) {
        nativeDetector = create(cascadeFile);
    }

    public int[] detect(int width, int height, byte[] frmdata, int front1orback0,
                        int orientCase) {
        return detect(nativeDetector, width, height, frmdata, front1orback0, orientCase);
    }

    public byte[] droidJPEGCalibrate(byte[] jpegdata, int front1orback0, int orientCase) {
        return droidJPEGCalibrate(nativeDetector, jpegdata, front1orback0, orientCase);
    }

    public byte[] boxesProcess(byte[] jpegdata, int[][] bbxposArr, String[] bbxtxtArr,
                               boolean[] bbxprocArr, int[] bbxproctypeArr) {
        return boxesProcess(nativeDetector, jpegdata, bbxposArr, bbxtxtArr, bbxprocArr, bbxproctypeArr);
    }

    public byte[] detectAndBlurJPEG(byte[] jpegdata) {
        return detectAndBlurJPEG(nativeDetector, jpegdata);
    }

    public int[] getBbxPositions() {
        return getBbxPositions(nativeDetector);
    }

    private long nativeDetector = 0;
    private static native long create(String cascadeFile);
    private static native int[] detect(long thiz, int width, int height,
                                       byte[] frmdata, int front1orback0, int orientCase);
    private static native byte[] droidJPEGCalibrate(long thiz, byte[] jpegdata, int front1orback0, int orientCase);
    private static native byte[] boxesProcess(long thiz, byte[] jpegdata, int[][] bbxposArr, String[] bbxtxtArr,
                                              boolean[] bbxprocArr, int[] bbxproctypeArr);
    private static native byte[] detectAndBlurJPEG(long thiz, byte[] jpegdata);
    private static native int[] getBbxPositions(long thiz);

}
