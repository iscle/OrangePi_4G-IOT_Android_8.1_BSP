LOCAL_PATH := $(call my-dir)

# service library
include $(CLEAR_VARS)
LOCAL_SRC_FILES := MediaCodecService.cpp
LOCAL_SHARED_LIBRARIES := \
    libmedia_omx \
    libbinder \
    libgui \
    libutils \
    liblog \
    libstagefright_omx \
    libstagefright_xmlparser
LOCAL_MODULE:= libmediacodecservice
LOCAL_VENDOR_MODULE := true
LOCAL_32_BIT_ONLY := true
include $(BUILD_SHARED_LIBRARY)

# service executable
include $(CLEAR_VARS)
LOCAL_REQUIRED_MODULES_arm := mediacodec.policy
LOCAL_SRC_FILES := main_codecservice.cpp
LOCAL_SHARED_LIBRARIES := \
    libmedia_omx \
    libmediacodecservice \
    libbinder \
    libutils \
    libgui \
    liblog \
    libbase \
    libavservices_minijail_vendor \
    libcutils \
    libhwbinder \
    libhidltransport \
    libstagefright_omx \
    libstagefright_xmlparser \
    android.hardware.media.omx@1.0 \
    android.hidl.memory@1.0

LOCAL_MODULE := android.hardware.media.omx@1.0-service
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_VENDOR_MODULE := true
LOCAL_32_BIT_ONLY := true
LOCAL_INIT_RC := android.hardware.media.omx@1.0-service.rc
include $(BUILD_EXECUTABLE)

# service seccomp policy
ifeq ($(TARGET_ARCH), $(filter $(TARGET_ARCH), arm arm64))
include $(CLEAR_VARS)
LOCAL_MODULE := mediacodec.policy
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT)/etc/seccomp_policy
# mediacodec runs in 32-bit combatibility mode. For 64 bit architectures,
# use the 32 bit policy
ifdef TARGET_2ND_ARCH
    LOCAL_SRC_FILES := seccomp_policy/mediacodec-$(TARGET_2ND_ARCH).policy
else
    LOCAL_SRC_FILES := seccomp_policy/mediacodec-$(TARGET_ARCH).policy
endif
include $(BUILD_PREBUILT)
endif

include $(call all-makefiles-under, $(LOCAL_PATH))
