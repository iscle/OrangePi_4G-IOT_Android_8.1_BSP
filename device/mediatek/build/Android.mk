LOCAL_PATH := $(call my-dir)

ifneq ($(TARGET_DEVICE), gobo)

ifeq ($(is_sdk_build),true)
$(shell if [ ! -e $(HOST_OUT)/sdk/$(TARGET_PRODUCT)/sdk_deps.mk ]; then mkdir -p $(HOST_OUT)/sdk/$(TARGET_PRODUCT) && echo "-include device/mediatek/build/core/sdk_deps.mk" > $(HOST_OUT)/sdk/$(TARGET_PRODUCT)/sdk_deps.mk; fi)
endif

ifdef TARGET_2ND_ARCH

define mtk-add-vndk-lib
include $$(CLEAR_VARS)
LOCAL_MODULE := $(1)$(2)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PREBUILT_MODULE_FILE := $$(TARGET_OUT_INTERMEDIATE_LIBRARIES)/$(1).so
LOCAL_MULTILIB := 64
LOCAL_MODULE_TAGS := optional
LOCAL_INSTALLED_MODULE_STEM := $(1).so
LOCAL_MODULE_SUFFIX := .so
LOCAL_VENDOR_MODULE := $(3)
LOCAL_MODULE_RELATIVE_PATH := $(4)
include $$(BUILD_PREBUILT)

include $$(CLEAR_VARS)
LOCAL_MODULE := $(1)$(2)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PREBUILT_MODULE_FILE := $$($$(TARGET_2ND_ARCH_VAR_PREFIX)TARGET_OUT_INTERMEDIATE_LIBRARIES)/$(1).so
LOCAL_MULTILIB := 32
LOCAL_MODULE_TAGS := optional
LOCAL_INSTALLED_MODULE_STEM := $(1).so
LOCAL_MODULE_SUFFIX := .so
LOCAL_VENDOR_MODULE := $(3)
LOCAL_MODULE_RELATIVE_PATH := $(4)
include $$(BUILD_PREBUILT)

ALL_MODULES.$(1).REQUIRED := $$(strip $$(ALL_MODULES.$(1).REQUIRED) $(1)$(2))
ALL_MODULES.$(1)$$(TARGET_2ND_ARCH_MODULE_SUFFIX).REQUIRED := $$(strip $$(ALL_MODULES.$(1)$$(TARGET_2ND_ARCH_MODULE_SUFFIX).REQUIRED) $(1)$(2))
TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES := $$(filter-out $(1)$(2):%,$$(TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES))
2ND_TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES := $$(filter-out $(1)$(2)$$(TARGET_2ND_ARCH_MODULE_SUFFIX):%,$$(2ND_TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES))
endef

else

define mtk-add-vndk-lib
include $$(CLEAR_VARS)
LOCAL_MODULE := $(1)$(2)
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PREBUILT_MODULE_FILE := $$(TARGET_OUT_INTERMEDIATE_LIBRARIES)/$(1).so
LOCAL_MULTILIB := 32
LOCAL_MODULE_TAGS := optional
LOCAL_INSTALLED_MODULE_STEM := $(1).so
LOCAL_MODULE_SUFFIX := .so
LOCAL_VENDOR_MODULE := $(3)
LOCAL_MODULE_RELATIVE_PATH := $(4)
include $$(BUILD_PREBUILT)

ALL_MODULES.$(1).REQUIRED := $$(strip $$(ALL_MODULES.$(1).REQUIRED) $(1)$(2))
TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES := $$(filter-out $(1)$(2):%,$$(TARGET_DEPENDENCIES_ON_SHARED_LIBRARIES))
endef

endif

# clone from system/lib to vendor/lib
AOSP_VNDK_EXT_LIBRARIES :=

# clone from system/lib to system/lib/vndk-sp

ifneq ($(BUILD_QEMU_IMAGES),true)

ifndef BOARD_VNDK_VERSION
AOSP_VNDK_SP_LIBRARIES := \
    android.hardware.graphics.allocator@2.0 \
    android.hardware.graphics.mapper@2.0 \
    android.hardware.graphics.common@1.0 \
    android.hardware.renderscript@1.0 \
    android.hidl.base@1.0 \
    android.hidl.memory@1.0 \
    libRSCpuRef \
    libRSDriver \
    libRS_internal \
    libbacktrace \
    libbase \
    libbcinfo \
    libblas \
    libc++ \
    libcompiler_rt \
    libcutils \
    libft2 \
    libhardware \
    libhidlbase \
    libhidlmemory \
    libhidltransport \
    libhwbinder \
    libion \
    liblzma \
    libpng \
    libunwind \
    libutils \

endif

else

AOSP_VNDK_SP_LIBRARIES ?=

endif


# clone from vendor/lib to system/lib
MTK_VNDK_EXT_LIBRARIES := \


#
$(foreach lib,$(AOSP_VNDK_EXT_LIBRARIES),$(eval $(call mtk-add-vndk-lib,$(lib),_vnd,true)))
$(foreach lib,$(AOSP_VNDK_SP_LIBRARIES),$(eval $(call mtk-add-vndk-lib,$(lib),.vndk-sp,,vndk-sp)))
$(foreach lib,$(MTK_VNDK_EXT_LIBRARIES),$(eval $(call mtk-add-vndk-lib,$(lib),_fwk)))
endif
