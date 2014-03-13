LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
OPENCV_INSTALL_MODULES:=on


include C:\Users\szymonri\DevTools\OpenCV-2.4.8-android-sdk\OpenCV-2.4.8-android-sdk\sdk\native\jni\OpenCV.mk

LOCAL_MODULE    := processing
LOCAL_SRC_FILES := jni_part.cpp
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_LDLIBS +=  -llog -ldl

include $(BUILD_SHARED_LIBRARY)
