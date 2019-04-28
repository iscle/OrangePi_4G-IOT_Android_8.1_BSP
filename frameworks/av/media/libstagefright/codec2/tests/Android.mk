# Build the unit tests.
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_MODULE := codec2_test

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := \
	vndk/C2UtilTest.cpp \
	C2_test.cpp \
	C2Param_test.cpp \

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libstagefright_codec2 \
	liblog

LOCAL_C_INCLUDES := \
	frameworks/av/media/libstagefright/codec2/include \
	frameworks/av/media/libstagefright/codec2/vndk/include \
	$(TOP)/frameworks/native/include/media/openmax \

LOCAL_CFLAGS += -Werror -Wall -std=c++14
LOCAL_CLANG := true

include $(BUILD_NATIVE_TEST)

# Include subdirectory makefiles
# ============================================================

# If we're building with ONE_SHOT_MAKEFILE (mm, mmm), then what the framework
# team really wants is to build the stuff defined by this makefile.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call first-makefiles-under,$(LOCAL_PATH))
endif
