# Workaround: Let Android.mk support source & prebuilt existing at same time

ifneq ($(LOCAL_PREFER_SOURCE_PATH),)
LOCAL_PREFER_SOURCE_PATH := $(wildcard $(LOCAL_PREFER_SOURCE_PATH))
endif
ifeq ($(LOCAL_PREFER_SOURCE_PATH),)
# use prebuilt
include $(BUILD_PREBUILT)

else#LOCAL_PREFER_SOURCE_PATH
# use source

ifdef LOCAL_IS_HOST_MODULE
  my_prefix := HOST_
else
  my_prefix := TARGET_
endif

include $(BUILD_SYSTEM)/multilib.mk

my_skip_non_preferred_arch :=

# check if first arch is supported
LOCAL_2ND_ARCH_VAR_PREFIX :=
include $(BUILD_SYSTEM)/module_arch_supported.mk
ifeq ($(my_module_arch_supported),true)
# first arch is supported
-include vendor/mediatek/build/core/base_rules.mk
ifneq ($(my_module_multilib),both)
my_skip_non_preferred_arch := true
endif # $(my_module_multilib)
# For apps, we don't want to set up the prebuilt apk rule twice even if "LOCAL_MULTILIB := both".
ifeq (APPS,$(LOCAL_MODULE_CLASS))
my_skip_non_preferred_arch := true
endif
endif # $(my_module_arch_supported)

ifndef my_skip_non_preferred_arch
ifneq (,$($(my_prefix)2ND_ARCH))
# check if secondary arch is supported
LOCAL_2ND_ARCH_VAR_PREFIX := $($(my_prefix)2ND_ARCH_VAR_PREFIX)
include $(BUILD_SYSTEM)/module_arch_supported.mk
ifeq ($(my_module_arch_supported),true)
# secondary arch is supported
-include vendor/mediatek/build/core/base_rules.mk
endif # $(my_module_arch_supported)
endif # $($(my_prefix)2ND_ARCH)
endif # $(my_skip_non_preferred_arch) not true

LOCAL_2ND_ARCH_VAR_PREFIX :=

my_module_arch_supported :=

endif#LOCAL_PREFER_SOURCE_PATH
LOCAL_PREFER_SOURCE_PATH :=
