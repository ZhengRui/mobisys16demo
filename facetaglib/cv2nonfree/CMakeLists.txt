cmake_minimum_required(VERSION 2.8)
project(opencv2Nonfree)


set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(source_files
    nonfree_init.cpp
    sift.cpp
    surf.cpp)

# opencv
# find_package(OpenCV REQUIRED)
# message(STATUS "OpenCV version:  (${OpenCV_VERSION})")
# message(STATUS "    libraries: (${OpenCV_LIBS})")
# message(STATUS "    include path: (${OpenCV_INCLUDE_DIRS})")

include_directories(../../../../Libs/opencv-2.4.11/modules/nonfree/include)
# set(LIBS ${LIBS} ${OpenCV_LIBS})
message(STATUS "LIBS in cv2nonfree: (${LIBS})")

add_library(opencv2Nonfree SHARED ${source_files})
target_link_libraries(opencv2Nonfree ${LIBS})
