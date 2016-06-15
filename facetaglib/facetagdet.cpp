
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
    featDetector.hessianThreshold = 400;
}

void FaceDetector::extractTagFeatures(string tagPath) {
    Mat img_marker = imread(tagPath, CV_LOAD_IMAGE_GRAYSCALE);

    int maxL = max(img_marker.rows, img_marker.cols);
    if(maxL > 400) {
        int scale = maxL / 300;
        resize(img_marker, img_marker, Size(img_marker.cols/scale, img_marker.rows/scale), INTER_AREA);
    }
    std::vector<cv::KeyPoint> keypoints_marker;
    Mat descriptors_marker;
    featDetector.detect(img_marker, keypoints_marker);
    descExtractor.compute(img_marker, keypoints_marker, descriptors_marker);

    Point s = Point(img_marker.cols, img_marker.rows);
    size_marker_s.push_back(s);
    keypoints_marker_s.push_back(keypoints_marker);
    descriptors_marker_s.push_back(descriptors_marker);
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

void FaceDetector::detectFace(Mat &BGRMat) {
    Mat BGRMatDlib = BGRMat;
    Mat BGRMatBak = BGRMat;
    int width = BGRMat.cols;
    int height = BGRMat.rows;
    float scale = max(width, height) / 480.;
    resize(BGRMat, BGRMat, Size(round(BGRMat.cols / scale), round(BGRMat.rows / scale)), INTER_AREA);

    // LOGD("after transformation: %d x %d", BGRMat.cols, BGRMat.rows);

   // face detection
    cvtColor(BGRMat, GRAYMat, CV_BGR2GRAY);
    equalizeHist(GRAYMat, GRAYMat);
    facecascade.detectMultiScale(GRAYMat, bbs, 1.1, 3, CV_HAAR_SCALE_IMAGE, cvSize(40, 40));
    LOGD("1stStage: Detect %d faces", (int) bbs.size());

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
    LOGD("2ndStage: Detect %d faces", (int) bbsFiltered.size());

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
    LOGD("3rdStage: Detect %d faces", (int) bbsFiltered.size());

    bbsTagBelow.clear();
    for(size_t i=0; i<bbsFiltered.size(); i++)
        bbsTagBelow.push_back(0);

    BGRMat = BGRMatBak;
}

void FaceDetector::detectTag(Mat &BGRMat) {
    bbsTags.clear();
    vector<Rect> mkrpps;
    int H = BGRMat.rows;
    int W = BGRMat.cols;
    LOGD("H, W: %d, %d", H, W);
    for(vector<Rect>::iterator r = bbsFiltered.begin(); r != bbsFiltered.end(); r++)
        mkrpps.push_back(Rect(Point(max(r->tl().x-25, 0), min(r->br().y+20, H-10)), Point(min(r->br().x+25, W), H-10)));

    mkrpps.push_back(Rect(0,0,W,H));
    Mat prop_gray, descriptors_scene, descriptors_marker, inliers, homo;
    vector<KeyPoint> keypoints_scene, keypoints_marker;
    FlannBasedMatcher matcher;
    vector<DMatch> matches, good_matches;
    vector<Point2f> obj, scene, obj_corners(4), scene_corners(4);

    int ith = 0;
    for(vector<Rect>::iterator r = mkrpps.begin(); r != mkrpps.end()-1; r++) { // for each proposal
        LOGD("proposal position tl - br: (%d, %d) - (%d, %d)", r->tl().x, r->tl().y, r->br().x, r->br().y);
        cvtColor(BGRMat(*r), prop_gray, CV_BGR2GRAY);
        featDetector.detect(prop_gray, keypoints_scene);
        descExtractor.compute(prop_gray, keypoints_scene, descriptors_scene);

        for (int m=0; m < (int) descriptors_marker_s.size(); m++) { // for each marker
            descriptors_marker = descriptors_marker_s[m];
            keypoints_marker = keypoints_marker_s[m];
            matches.clear();
            matcher.match(descriptors_marker, descriptors_scene, matches);
            double max_dist = 0, min_dist = 100;

            for(int i=0; i < descriptors_marker.rows; i++) {
                double dist = matches[i].distance;
                if(dist < min_dist) min_dist = dist;
                if(dist > max_dist) max_dist = dist;
            }

            good_matches.clear();
            for(int i=0; i < descriptors_marker.rows; i++) {
                if(matches[i].distance < 3*min_dist) {
                    good_matches.push_back(matches[i]);
                }
            }
            if (good_matches.size() < 4) continue;
            // LOGD("good match size : %d", (int) good_matches.size());

            obj.clear(); scene.clear();
            for(int i=0; i < (int) good_matches.size(); i++) {
                obj.push_back(keypoints_marker[good_matches[i].queryIdx].pt);
                scene.push_back(keypoints_scene[good_matches[i].trainIdx].pt);
            }

            homo = findHomography(obj, scene, CV_RANSAC, 3, inliers);

            int xw = size_marker_s[m].x;
            int yh = size_marker_s[m].y;
            obj_corners[0] = cvPoint(0,0);
            obj_corners[1] = cvPoint(xw, 0);
            obj_corners[2] = cvPoint(xw, yh);
            obj_corners[3] = cvPoint(0, yh);
            perspectiveTransform(obj_corners, scene_corners, homo);

            if(contourArea(scene_corners) < 5000 || !isContourConvex(scene_corners)) continue;
            Point tagP0 = Point((*r).tl()+Point(scene_corners[0].x, scene_corners[0].y));
            bbsTags.push_back(tagP0);
            Point tagP1 = Point((*r).tl()+Point(scene_corners[1].x, scene_corners[1].y));
            bbsTags.push_back(tagP1);
            Point tagP2 = Point((*r).tl()+Point(scene_corners[2].x, scene_corners[2].y));
            bbsTags.push_back(tagP2);
            Point tagP3 = Point((*r).tl()+Point(scene_corners[3].x, scene_corners[3].y));
            bbsTags.push_back(tagP3);

            bbsTagBelow[ith] = m+1;
            LOGD("find tag type: %d, position : (%d, %d) - (%d, %d) - (%d, %d) - (%d, %d)", m+1, tagP0.x, tagP0.y, tagP1.x, tagP1.y, tagP2.x, tagP2.y, tagP3.x, tagP3.y);
        }
        ith++;
    }
}


void FaceDetector::detectFaceTag(int width, int height, unsigned char *frmCData, int front1orback0, int orientCase) {
    Mat YUVMat(height + height / 2, width, CV_8UC1, frmCData);
    cvtColor(YUVMat, BGRMat, CV_YUV420sp2BGR);
    fromDroidCamToCV(BGRMat, front1orback0, orientCase);
    detectFace(BGRMat);
    detectTag(BGRMat);
}

