
#ifndef FACETAGDET_HPP
#define FACETAGDET_HPP

#include <android/log.h>

#define TAG "fdtagLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__)

#include <string>
#include <cstdint>
#include <opencv2/opencv.hpp>
#include <opencv2/nonfree/nonfree.hpp>
#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing.h>
#include <dlib/opencv.h>

class FaceDetector {
public:
    FaceDetector(std::string cascadePath);
    ~FaceDetector();
    void extractTagFeatures(std::string tagPath);
    void fromDroidCamToCV(cv::Mat &m, int front1orback0, int orientCase);
    void detectFace(cv::Mat &BGRMat);
    void detectTag(cv::Mat &BGRMat);
    void detectFaceTag(int width, int height, unsigned char* frmCData, int front1orback0, int orientCase);
    std::vector<cv::Rect> bbsFiltered;
    std::vector<int> bbsTagBelow;
    std::vector<cv::Point> bbsTags;

private:
    std::vector<cv::Rect> bbs;
    std::vector<cv::Point> size_marker_s;
    std::vector<cv::Mat> descriptors_marker_s;
    cv::CascadeClassifier facecascade;
    cv::Mat kernel;
    cv::Mat BGRMat, GRAYMat, bbHSV, skinMask;
    dlib::frontal_face_detector dlibDetector;
    cv::SurfFeatureDetector featDetector;
    cv::SurfDescriptorExtractor descExtractor;
    std::vector<std::vector<cv::KeyPoint> > keypoints_marker_s;
};

#endif
