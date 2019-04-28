LOCAL_PATH := $(call my-dir)

# service library
include $(CLEAR_VARS)
LOCAL_SRC_FILES := MediaExtractorService.cpp
LOCAL_SHARED_LIBRARIES := libmedia libstagefright libbinder libutils liblog
LOCAL_MODULE:= libmediaextractorservice
include $(BUILD_SHARED_LIBRARY)


# service executable
include $(CLEAR_VARS)
# seccomp filters are defined for the following architectures:
LOCAL_REQUIRED_MODULES_arm := mediaextractor.policy
LOCAL_REQUIRED_MODULES_arm64 := mediaextractor.policy
LOCAL_REQUIRED_MODULES_x86 := mediaextractor.policy
LOCAL_SRC_FILES := main_extractorservice.cpp
LOCAL_SHARED_LIBRARIES := libmedia libmediaextractorservice libbinder libutils \
    liblog libbase libicuuc libavservices_minijail
LOCAL_STATIC_LIBRARIES := libicuandroid_utils
LOCAL_MODULE:= mediaextractor
LOCAL_INIT_RC := mediaextractor.rc
LOCAL_C_INCLUDES := frameworks/av/media/libmedia
include $(BUILD_EXECUTABLE)

# service seccomp filter
ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm arm64 x86))
include $(CLEAR_VARS)
LOCAL_MODULE := mediaextractor.policy
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT)/etc/seccomp_policy
LOCAL_SRC_FILES := seccomp_policy/mediaextractor-$(TARGET_ARCH).policy
include $(BUILD_PREBUILT)
endif
