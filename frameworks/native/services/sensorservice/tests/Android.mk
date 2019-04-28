LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	sensorservicetest.cpp

LOCAL_SHARED_LIBRARIES := \
	libutils libsensor libandroid

LOCAL_MODULE:= test-sensorservice

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
