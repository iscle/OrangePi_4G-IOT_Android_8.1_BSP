cgen:

MTK_CGEN_MAKEFILE := $(lastword $(MAKEFILE_LIST))

ifndef MTK_CGEN_MAKE_ALONE
  ifeq ($(words $(MAKEFILE_LIST)),1)
    # make -f codegen.mk
    MTK_CGEN_MAKE_ALONE := true
  endif
endif
ifeq ($(MTK_CGEN_MAKE_ALONE), true)
else
  $(error make -f codegen.mk)
endif

ifeq ($(TARGET_DEVICE),)
  $(error TARGET_DEVICE is not defined)
endif
ifneq ($(filter full_%,$(TARGET_PRODUCT)),)
  MTK_TARGET_PROJECT ?= $(subst full_,,$(TARGET_PRODUCT))
else
  MTK_TARGET_PROJECT ?= $(TARGET_DEVICE)
endif
MTK_BASE_PROJECT ?= $(TARGET_DEVICE)
MTK_PROJECT := $(MTK_BASE_PROJECT)
MTK_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(MTK_BASE_PROJECT))
MTK_TARGET_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(MTK_TARGET_PROJECT))

include $(MTK_TARGET_PROJECT_FOLDER)/ProjectConfig.mk

# BoardConfig.mk
MTK_INTERNAL_CDEFS := $(foreach t,$(AUTO_ADD_GLOBAL_DEFINE_BY_NAME),$(if $(filter-out no NO none NONE false FALSE,$($(t))),-D$(t)))
MTK_INTERNAL_CDEFS += $(foreach t,$(AUTO_ADD_GLOBAL_DEFINE_BY_VALUE),$(if $(filter-out no NO none NONE false FALSE,$($(t))),$(foreach v,$(shell echo $($(t)) | tr '[a-z]' '[A-Z]'),-D$(v))))
MTK_INTERNAL_CDEFS += $(foreach t,$(AUTO_ADD_GLOBAL_DEFINE_BY_NAME_VALUE),$(if $(filter-out no NO none NONE false FALSE,$($(t))),-D$(t)=\"$(strip $($(t)))\"))

include $(wildcard vendor/mediatek/proprietary/buildinfo/branch.mk)

# build/core/envsetup.mk
OUT_DIR ?= out
TARGET_PRODUCT_OUT_ROOT := $(OUT_DIR)/target/product
ifneq ($(MTK_TARGET_PROJECT),$(MTK_BASE_PROJECT))
PRODUCT_OUT := $(TARGET_PRODUCT_OUT_ROOT)/$(MTK_TARGET_PROJECT)
else
PRODUCT_OUT := $(TARGET_PRODUCT_OUT_ROOT)/$(TARGET_DEVICE)
endif
TARGET_OUT_INTERMEDIATES := $(PRODUCT_OUT)/obj
ifeq ($(strip $(SHOW_COMMANDS)),)
  hide := @
endif

include vendor/mediatek/proprietary/cgen/Android.mk

$(info MTK_CGEN_OUT_DIR = $(MTK_CGEN_OUT_DIR))
