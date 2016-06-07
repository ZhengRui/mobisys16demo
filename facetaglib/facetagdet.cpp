
#include "facetagdet.hpp"

using namespace std;
using namespace cv;

FaceDetector::FaceDetector(string cascadePath) {
    bool suc = facecascade.load(cascadePath);
    if (suc) {
        LOGD("Cascade File loaded.");
    } else {
        LOGD("failed to load Cascade File");
    }
    dlibDetector = dlib::get_frontal_face_detector();
    kernel = getStructuringElement(MORPH_ELLIPSE, Size(3,3));
}


void FaceDetector::fromDroidCamToCV(Mat &m, int front1orback0, int orientCase) {
    // from android camera dataframe coordinate system to natural/viewport coordinate system.
    // this is the coordinate system in which opencv will see what we see.
    switch (orientCase) {
        case 0:
            transpose(m, m);
            flip(m, m, 1-front1orback0);
            break;
        case 1:
            flip(m, m, -1);
            break;
        case 2:
            transpose(m, m);
            flip(m, m, front1orback0);
            break;
        default:
            break;
    }
}

vector<Rect> FaceDetector::detectMat(Mat &BGRMat) {
    Mat BGRMatDlib = BGRMat;
    int width = BGRMat.cols;
    int height = BGRMat.rows;
    float scale = max(width, height) / 480.;
    resize(BGRMat, BGRMat, Size(round(BGRMat.cols / scale), round(BGRMat.rows / scale)), INTER_AREA);

    // LOGD("after transformation: %d x %d", BGRMat.cols, BGRMat.rows);

   // face detection
    cvtColor(BGRMat, GRAYMat, CV_BGR2GRAY);
    equalizeHist(GRAYMat, GRAYMat);
    facecascade.detectMultiScale(GRAYMat, bbs, 1.1, 3, CV_HAAR_SCALE_IMAGE, cvSize(40, 40));
    // LOGD("1stStage: Detect %d faces", (int) bbs.size());

    // filters: skin + dlib
    bbsFiltered.clear();
    for(vector<Rect>::const_iterator r = bbs.begin(); r != bbs.end(); r++) {
        cvtColor(BGRMat(*r), bbHSV, CV_BGR2HSV);
        inRange(bbHSV, Scalar(0, 48, 60), Scalar(30, 255, 255), skinMask);
        dilate(skinMask, skinMask, kernel, Point(-1, -1), 2);
        skinMask = skinMask(Rect(r->width/4, r->height/5, r->width/2, r->height*4/5));
        if (sum(skinMask)[0] / (skinMask.rows * skinMask.cols * 255.0) <= 0.2)
            continue;
        // pass skin filter
        bbsFiltered.push_back(Rect(r->x * scale, r->y * scale, r->width * scale, r->height * scale));
    }
    // LOGD("2ndStage: Detect %d faces", (int) bbsFiltered.size());

    scale = max(width, height) / 960.;
    resize(BGRMatDlib, BGRMatDlib, Size(round(BGRMatDlib.cols / scale), round(BGRMatDlib.rows / scale)), INTER_AREA);

    int maxx = BGRMatDlib.cols;
    int maxy = BGRMatDlib.rows;
    dlib::cv_image<dlib::bgr_pixel> dlibimg(BGRMatDlib);

    for(vector<Rect>::iterator r = bbsFiltered.begin(); r != bbsFiltered.end(); ) {
        int l = r->x * 1.0 / scale;
        int t = r->y * 1.0 / scale;
        int w = r->width * 1.0 / scale;
        int h = r->height * 1.0 / scale;
        Rect patch = Rect(Point(max(l - 15, 0), max(t - 15, 0)), Point(min(l + w + 15, maxx), min(t + h + 15, maxy)));
        // LOGD("%d X %d, (%d, %d, %d, %d)", maxx, maxy, patch.x, patch.y, patch.width, patch.height);
        dlib::cv_image<dlib::bgr_pixel> dlibpatch(BGRMatDlib(patch));
        vector<dlib::rectangle> dfaces = dlibDetector(dlibpatch);

        if (!dfaces.size()) {
            LOGD("CV false positive suspect, remove it.");
            bbsFiltered.erase(r);
        } else {
            r++;
        }
    }

    return bbsFiltered;
}

vector<Rect> FaceDetector::detect(int width, int height, unsigned char *frmCData, int front1orback0, int orientCase) {
    Mat YUVMat(height + height / 2, width, CV_8UC1, frmCData);
    cvtColor(YUVMat, BGRMat, CV_YUV420sp2BGR);
    fromDroidCamToCV(BGRMat, front1orback0, orientCase);
    return detectMat(BGRMat);
}

std::vector<cv::Rect> FaceDetector::getBbsFiltered() {
    return bbsFiltered;
}

