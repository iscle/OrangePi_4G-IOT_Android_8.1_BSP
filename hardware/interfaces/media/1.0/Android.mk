LOCAL_PATH := $(call my-dir)

#######################################
# media_profiles_V1_0.dtd

include $(CLEAR_VARS)

LOCAL_MODULE := media_profiles_V1_0.dtd
LOCAL_SRC_FILES := media_profiles.dtd
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)

include $(BUILD_PREBUILT)

