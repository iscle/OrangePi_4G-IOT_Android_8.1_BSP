all_modules:
.DELETE_ON_ERROR:
my-dir = $(strip $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST)))))
my_ccu_path := $(call my-dir)
my_ccu_generated_sources :=

ifndef MTK_TARGET_PROJECT
  $(error MTK_TARGET_PROJECT is not defined)
endif
MTK_TARGET_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(MTK_TARGET_PROJECT))
include $(MTK_TARGET_PROJECT_FOLDER)/ProjectConfig.mk

include $(my_ccu_path)/Android.mk

all_modules: $(my_ccu_generated_sources)

