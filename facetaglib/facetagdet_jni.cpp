
#include <jni.h>
#include "facetagdet.hpp"

#ifdef __cplusplus
extern "C" {
#endif

    JNIEXPORT jlong JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_create(JNIEnv* env, jclass, jstring cascadeFile) {
        // LOGD("native create() called.");
        jlong detector = 0;
        const char* cascadeFilePath = env->GetStringUTFChars(cascadeFile, NULL);
        detector = (jlong)new FaceDetector(std::string(cascadeFilePath));
        env->ReleaseStringUTFChars(cascadeFile, cascadeFilePath);
        return detector;
    }


    JNIEXPORT jlong JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_extractTagFeatures(JNIEnv* env, jclass, jlong thiz, jstring tagPath) {
        const char* tagFilePath = env->GetStringUTFChars(tagPath, NULL);
        ((FaceDetector*)thiz)->extractTagFeatures(tagFilePath);
        env->ReleaseStringUTFChars(tagPath, tagFilePath);
    }

    JNIEXPORT jbyteArray JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_droidJPEGCalibrate(JNIEnv* env, jclass, jlong thiz, jbyteArray jpegdata, jint front1orback0, jint orientCase) {
        jbyte* picjData = env->GetByteArrayElements(jpegdata, 0);
        uchar* buf = (uchar*) picjData;
        size_t len = env->GetArrayLength(jpegdata);
        std::vector<uchar> cdata(buf, buf+len);
        cv::Mat m = cv::imdecode(cdata, CV_LOAD_IMAGE_COLOR);

        // do calibration: rotate + flip
        ((FaceDetector*)thiz)->fromDroidCamToCV(m, front1orback0, orientCase);

        LOGD("picture size after calibrated: %d X %d", m.rows, m.cols);
        std::vector<int> params;
        params.push_back(CV_IMWRITE_JPEG_QUALITY);
        params.push_back(100);
        std::vector<uchar> cdataEnc;
        cv::imencode(".jpg", m, cdataEnc, params);
        jbyteArray jpegCalibrated = env->NewByteArray(cdataEnc.size());
        env->SetByteArrayRegion(jpegCalibrated, 0, cdataEnc.size(), (jbyte*)&cdataEnc[0]);

        env->ReleaseByteArrayElements(jpegdata, picjData, JNI_ABORT);
        return jpegCalibrated;
    }

    JNIEXPORT jbyteArray JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_boxesProcess(JNIEnv* env, jclass, jbyteArray jpegdata, jobjectArray bbxposArr, jbooleanArray bbxprocArr) {
        jbyte* imgjData = env->GetByteArrayElements(jpegdata, 0);
        uchar* buf = (uchar*) imgjData;
        size_t len = env->GetArrayLength(jpegdata);
        std::vector<uchar> cdata(buf, buf+len);
        cv::Mat img = cv::imdecode(cdata, CV_LOAD_IMAGE_COLOR);

        int bbxnum = env->GetArrayLength(bbxposArr);
        // LOGD("boxes number: %d", bbxnum);
        jboolean* bbxproc = env->GetBooleanArrayElements(bbxprocArr, 0);

        for (int i=0; i < bbxnum; i++) {
            jintArray bbxposJ = (jintArray) env->GetObjectArrayElement(bbxposArr, i);
            jint *bbxpos = env->GetIntArrayElements(bbxposJ, 0);

            // LOGD("box position: (%d, %d) - (%d, %d), text: %s, do blur: %s, blur type: %d", bbxpos[0], bbxpos[1], bbxpos[2], bbxpos[3], bbxtxt, bbxproc[i]?"yes":"no", bbxproctype[i]);

            cv::Rect roi = cv::Rect(cv::Point(bbxpos[0], bbxpos[1]), cv::Point(bbxpos[2], bbxpos[3]));

            if (bbxproc[i])
                cv::medianBlur(img(roi), img(roi), 77);

            env->ReleaseIntArrayElements(bbxposJ, bbxpos, JNI_ABORT);
        }
        env->ReleaseBooleanArrayElements(bbxprocArr, bbxproc, JNI_ABORT);

        std::vector<int> params;
        params.push_back(CV_IMWRITE_JPEG_QUALITY);
        params.push_back(100);
        std::vector<uchar> cdataEnc;
        cv::imencode(".jpg", img, cdataEnc, params);
        jbyteArray jpegBoxsProcessed = env->NewByteArray(cdataEnc.size());
        env->SetByteArrayRegion(jpegBoxsProcessed, 0, cdataEnc.size(), (jbyte*)&cdataEnc[0]);

        env->ReleaseByteArrayElements(jpegdata, imgjData, JNI_ABORT);
        return jpegBoxsProcessed;
    }

    JNIEXPORT void JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_detectFaceTagFromJPEG(JNIEnv* env, jclass, jlong thiz, jbyteArray jpegdata) {
        jbyte* imgjData = env->GetByteArrayElements(jpegdata, 0);
        uchar* buf = (uchar*) imgjData;
        size_t len = env->GetArrayLength(jpegdata);
        std::vector<uchar> cdata(buf, buf+len);
        cv::Mat img = cv::imdecode(cdata, CV_LOAD_IMAGE_COLOR);
        cv::Mat imgDet = img;
        ((FaceDetector*)thiz)->detectFace(imgDet);
        ((FaceDetector*)thiz)->detectTag(imgDet);

        env->ReleaseByteArrayElements(jpegdata, imgjData, JNI_ABORT);
    }

    JNIEXPORT void JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_detectFaceTagFromRaw(JNIEnv* env, jclass, jlong thiz, jint width, jint height, jbyteArray frmdata, jint front1orback0, jint orientCase) {
        // LOGD("native detect() called.");
        jbyte* frmjData = env->GetByteArrayElements(frmdata, 0);
        // call some image processing function
        // LOGD("frame size: %d X %d, data length: %d", width, height, env->GetArrayLength(frmdata));
        ((FaceDetector*)thiz)->detectFaceTag((int) width, (int) height, (unsigned char*) frmjData, (int) front1orback0, (int) orientCase);

        env->ReleaseByteArrayElements(frmdata, frmjData, JNI_ABORT);
    }


    JNIEXPORT jintArray JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_getBBXPos(JNIEnv* env, jclass, jlong thiz, jint face0tag1) {
        jintArray posArr;
        if (!face0tag1) {
            std::vector<cv::Rect> pos;
            pos = ((FaceDetector*)thiz)->bbsFiltered;
            std::vector<int> tagBelow;
            tagBelow = ((FaceDetector*)thiz)->bbsTagBelow;
            posArr = env->NewIntArray(pos.size() * 5);
            jint posBuf[5];
            int p = 0;
            int ith=0;
            for(std::vector<cv::Rect>::const_iterator r = pos.begin(); r != pos.end(); r++) {
                posBuf[0] = r->x;
                posBuf[1] = r->y;
                posBuf[2] = r->x + r->width;
                posBuf[3] = r->y + r->height;
                posBuf[4] = tagBelow[ith];
                env->SetIntArrayRegion(posArr, p, 5, posBuf);
                p += 5;
                ith++;
            }
        } else {
            std::vector<cv::Point> pos;
            pos = ((FaceDetector*)thiz)->bbsTags;
            posArr = env->NewIntArray(pos.size() * 2);
            jint posBuf[2];
            int p = 0;
            for(std::vector<cv::Point>::const_iterator r = pos.begin(); r != pos.end(); r++) {
                posBuf[0] = r->x;
                posBuf[1] = r->y;
                env->SetIntArrayRegion(posArr, p, 2, posBuf);
                p += 2;
            }
        }

        LOGD("bbx length: %d", env->GetArrayLength(posArr));
        return posArr;
    }


    JNIEXPORT jbyteArray JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_drawFaces(JNIEnv* env, jclass, jbyteArray jpegdata, jintArray faceposArr) {
        jbyte* imgjData = env->GetByteArrayElements(jpegdata, 0);
        uchar* buf = (uchar*) imgjData;
        size_t len = env->GetArrayLength(jpegdata);
        std::vector<uchar> cdata(buf, buf+len);
        cv::Mat img = cv::imdecode(cdata, CV_LOAD_IMAGE_COLOR);

        size_t facenum = env->GetArrayLength(faceposArr) / 5;
        jint* facejData = env->GetIntArrayElements(faceposArr, 0);
        for (size_t i=0; i<facenum; i++)
            cv::rectangle(img, cv::Point(facejData[5*i], facejData[5*i+1]), cv::Point(facejData[5*i+2], facejData[5*i+3]), cv::Scalar(0,0,255), 2);

        std::vector<int> params;
        params.push_back(CV_IMWRITE_JPEG_QUALITY);
        params.push_back(100);
        std::vector<uchar> cdataEnc;
        cv::imencode(".jpg", img, cdataEnc, params);
        jbyteArray jpegBoxsProcessed = env->NewByteArray(cdataEnc.size());
        env->SetByteArrayRegion(jpegBoxsProcessed, 0, cdataEnc.size(), (jbyte*)&cdataEnc[0]);

        env->ReleaseByteArrayElements(jpegdata, imgjData, JNI_ABORT);
        env->ReleaseIntArrayElements(faceposArr, facejData, JNI_ABORT);
        return jpegBoxsProcessed;
    }

    JNIEXPORT jbyteArray JNICALL Java_com_zerry_mobisys16demo_FaceTagDet_drawTags(JNIEnv* env, jclass, jbyteArray jpegdata, jintArray tagposArr) {
        jbyte* imgjData = env->GetByteArrayElements(jpegdata, 0);
        uchar* buf = (uchar*) imgjData;
        size_t len = env->GetArrayLength(jpegdata);
        std::vector<uchar> cdata(buf, buf+len);
        cv::Mat img = cv::imdecode(cdata, CV_LOAD_IMAGE_COLOR);

        size_t tagnum = env->GetArrayLength(tagposArr) / 8;
        jint* tagjData = env->GetIntArrayElements(tagposArr, 0);
        for (size_t i=0; i<tagnum; i++) {
            cv::line(img, cv::Point(tagjData[8*i], tagjData[8*i+1]), cv::Point(tagjData[8*i+2], tagjData[8*i+3]), cv::Scalar(0,255,0), 2);
            cv::line(img, cv::Point(tagjData[8*i+2], tagjData[8*i+3]), cv::Point(tagjData[8*i+4], tagjData[8*i+5]), cv::Scalar(0,255,0), 2);
            cv::line(img, cv::Point(tagjData[8*i+4], tagjData[8*i+5]), cv::Point(tagjData[8*i+6], tagjData[8*i+7]), cv::Scalar(0,255,0), 2);
            cv::line(img, cv::Point(tagjData[8*i+6], tagjData[8*i+7]), cv::Point(tagjData[8*i], tagjData[8*i+1]), cv::Scalar(0,255,0), 2);
        }

        std::vector<int> params;
        params.push_back(CV_IMWRITE_JPEG_QUALITY);
        params.push_back(100);
        std::vector<uchar> cdataEnc;
        cv::imencode(".jpg", img, cdataEnc, params);
        jbyteArray jpegBoxsProcessed = env->NewByteArray(cdataEnc.size());
        env->SetByteArrayRegion(jpegBoxsProcessed, 0, cdataEnc.size(), (jbyte*)&cdataEnc[0]);

        env->ReleaseByteArrayElements(jpegdata, imgjData, JNI_ABORT);
        env->ReleaseIntArrayElements(tagposArr, tagjData, JNI_ABORT);
        return jpegBoxsProcessed;
    }



#ifdef __cplusplus
}
#endif
