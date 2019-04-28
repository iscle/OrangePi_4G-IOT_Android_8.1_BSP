# Build the unit tests
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := MediaResourceManager_test

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := \
  MediaResourceManager_test.cpp \

LOCAL_SHARED_LIBRARIES := \
  liblog \
  libmrm_arbitrator \
  libutils \

LOCAL_C_INCLUDES := \
  $(LOCAL_PATH)/../arbitrator \

#LOCAL_32_BIT_ONLY := true

LOCAL_CFLAGS += -Werror

include $(BUILD_NATIVE_TEST)

