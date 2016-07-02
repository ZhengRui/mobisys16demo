[demo video](https://www.youtube.com/watch?v=jszzgnOLXko)

#### setup

+ modify `opencv2` path, `android-cmake` path, `ndk` path, `dlib` path in files `facetaglib/build.sh`, `facetaglib/CMakeLists.txt`, `facetaglib/cv2nonfree/CMakeLists.txt`

+ build the face-tag detection library: `cd facetaglib; ./build.sh`

+ modify `py-faster-rcnn` path in `server/_init_paths.py`

+ modify gesture recognition model path in `server/server.py`, you can find [here](tbc) about the training of this model

+ start the server: `cd server; python server.py`
