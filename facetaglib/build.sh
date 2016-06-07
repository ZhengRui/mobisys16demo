#!/usr/bin/env sh

mkdir -p build
cd build

ACMAKEFD="${HOME}/Work/Libs/caffe-android-lib/android-cmake"
NDK_ROOT="${HOME}/Work/Tools/android-ndk-r10e"
OpenCV_DIR="${HOME}/Work/Libs/opencv-3.1.0/platforms/build_android_arm/install/sdk/native/jni"

cmake -DCMAKE_TOOLCHAIN_FILE=${ACMAKEFD}/android.toolchain.cmake \
      -DANDROID_NDK=${NDK_ROOT} \
      -DCMAKE_BUILD_TYPE=Release \
      -DANDROID_ABI="armeabi-v7a with NEON" \
      -DANDROID_STL=gnustl_static \
      -DANDROID_NATIVE_API_LEVEL=21 \
      -DOpenCV_DIR=${OpenCV_DIR} \
      ..

make VERBOSE=1

cp libfacetagdet.so ${HOME}/Work/Projects/AndroidProjects/visionapps/mobisys16demo/src/main/jniLibs/armeabi-v7a/



