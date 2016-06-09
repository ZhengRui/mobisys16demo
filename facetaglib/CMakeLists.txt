cmake_minimum_required(VERSION 2.8)
project(FaceTagDetectNative)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(source_files
    facetagdet_jni.cpp
    facetagdet.cpp)

set(LIBS log)

# opencv
find_package(OpenCV REQUIRED)
message(STATUS "OpenCV version:  (${OpenCV_VERSION})")
message(STATUS "    libraries: (${OpenCV_LIBS})")
message(STATUS "    include path: (${OpenCV_INCLUDE_DIRS})")

include_directories(${OpenCV_INCLUDE_DIRS} ../../../Libs/opencv-2.4.11/modules/nonfree/include)
set(LIBS ${LIBS} ${OpenCV_LIBS})

add_subdirectory(cv2nonfree)
set(LIBS ${LIBS} opencv2Nonfree)
message(STATUS "LIBS in root: (${LIBS})")

# dlib
set(source_files ${source_files}
    ../../../Libs/dlib-18.18/dlib/threads/threads_kernel_shared.cpp
    ../../../Libs/dlib-18.18/dlib/entropy_decoder/entropy_decoder_kernel_2.cpp
    ../../../Libs/dlib-18.18/dlib/base64/base64_kernel_1.cpp
    ../../../Libs/dlib-18.18/dlib/threads/threads_kernel_1.cpp
    ../../../Libs/dlib-18.18/dlib/threads/threads_kernel_2.cpp)

include_directories(../../../Libs/dlib-18.18)

add_library(facetagdet SHARED ${source_files})
target_link_libraries(facetagdet ${LIBS})
