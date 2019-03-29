ifeq ($(TARGET_BUILD_PDK),true)
ifeq ($(TARGET_BOARD_PLATFORM),msm8996)
#----------------------------------------------------------------------
# Fixup libandroid dependency
#----------------------------------------------------------------------
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := libandroid
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES_arm := ../../../../$(PRODUCT_OUT)/obj/PACKAGING/pdk_fusion_intermediates/system/lib/libandroid.so
LOCAL_SRC_FILES_arm64 := ../../../../$(PRODUCT_OUT)/obj/PACKAGING/pdk_fusion_intermediates/system/lib64/libandroid.so
LOCAL_MULTILIB := both
include $(BUILD_PREBUILT)

$(LOCAL_PATH)/../../../../out/target/product/msm8996/obj/PACKAGING/pdk_fusion_intermediates/system/lib/libandroid.so : $(PRODUCT_OUT)/obj/PACKAGING/pdk_fusion_intermediates/pdk_fusion.stamp
$(LOCAL_PATH)/../../../../out/target/product/msm8996/obj/PACKAGING/pdk_fusion_intermediates/system/lib64/libandroid.so : $(PRODUCT_OUT)/obj/PACKAGING/pdk_fusion_intermediates/pdk_fusion.stamp
endif
endif
