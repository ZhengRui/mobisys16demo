
#ifndef FACETAGDET_HPP
#define FACETAGDET_HPP

#include <android/log.h>

#define TAG "fdtagLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__)

#include <string>
#include <cstdint>
#include <opencv2/opencv.hpp>
#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing.h>
#include <dlib/opencv.h>

class FaceDetector {
public:
    FaceDetector(std::string cascadePath);
    ~FaceDetector();
    std::vector<cv::Rect> detect(int width, int height, unsigned char* frmCData, int front1orback0, int orientCase);
    std::vector<cv::Rect> detectMat(cv::Mat &BGRMat);
    std::vector<cv::Rect> getBbsFiltered();
    void fromDroidCamToCV(cv::Mat &m, int front1orback0, int orientCase);

private:
    cv::CascadeClassifier facecascade;
    cv::Mat kernel;
    cv::Mat BGRMat, GRAYMat, bbHSV, skinMask;
    std::vector<cv::Rect> bbs, bbsFiltered;
    dlib::frontal_face_detector dlibDetector;
};

#endif
