# device/<odm>/<platform>/odm/odm.mk
LOCAL_PATH := $(call my-dir)

CUSTOM_IMAGE_MOUNT_POINT := odm
CUSTOM_IMAGE_PARTITION_SIZE := $(BOARD_ODMIMAGE_PARTITION_SIZE)
CUSTOM_IMAGE_FILE_SYSTEM_TYPE := ext4
CUSTOM_IMAGE_DICT_FILE := $(LOCAL_PATH)/odm.dict
CUSTOM_IMAGE_SELINUX := true

CUSTOM_IMAGE_COPY_FILES := \
    $(LOCAL_PATH)/odm.prop:odm.prop \
    $(LOCAL_PATH)/init.odm.rc:etc/init/init.odm.rc

-include $(MTK_TARGET_PROJECT_FOLDER)/odm/odm.mk

###############################################################################
# To install kernel modules built from kernel source:
# * Register the built kernel module paths in MTK_KERNEL_MODULES
# * Add insmod instructions in $(LOCAL_PATH)/init.odm.rc
###############################################################################
ifneq (true,$(strip $(TARGET_NO_KERNEL)))

ifneq (true,$(MTK_KERNEL_MODULES_MK_PARSED))
    -include device/mediatek/$(MTK_PLATFORM_DIR)/kernel_modules.mk
    MTK_KERNEL_MODULES_MK_PARSED := true
endif

ifneq (,$(MTK_KERNEL_MODULES))

CUSTOM_IMAGE_COPY_FILES += \
    $(foreach m,$(MTK_KERNEL_MODULES),$(m):libs/modules/$(notdir $(m)))

endif # MTK_KERNEL_MODULES is not empty
endif # TARGET_NO_KERNEL == true
