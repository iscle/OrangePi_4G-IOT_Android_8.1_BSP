# A workaround for the build hang issue when non-MTK lunch is selected.
# e.g., aosp_arm-eng.
#
# Many Android.mk files under vendor/mediatek will recursively include
# themselves if MTK_PLATFORM isn't defined. e.g.,
#   include $(LOCAL_PATH)/$(shell echo $(MTK_PLATFORM) | tr A-Z a-z )/Android.mk
#
# Only include Android.mk files under vendor/mediatek/ when
# MTK_PROJECT_NAME is defined. This file should be renamed to
# vendor/mediatek/Android.mk, either manually or through <copyfile> setting
# in repo manifest.

LOCAL_PATH := $(call my-dir)
mtk_subdir_makefiles :=
ifneq ($(strip $(MTK_PROJECT_NAME)),)
mtk_subdir_makefiles := $(call first-makefiles-under, $(LOCAL_PATH))
else ifeq ($(is_sdk_build),true)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/libs/anrappmanager/Android.mk)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/libs/mplugin/Android.mk)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/proprietary/frameworks/base/res/Android.mk)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/proprietary/frameworks/common/Android.mk)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/proprietary/frameworks/opt/anr/anrappmanager/Android.mk)
mtk_subdir_makefiles += $(wildcard $(LOCAL_PATH)/proprietary/frameworks/opt/mplugin/Android.mk)
endif
$(foreach mk,$(mtk_subdir_makefiles),$(info including $(mk) ...)$(eval include $(mk)))
