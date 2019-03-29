#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

include $(BUILD_SYSTEM)/aux_toolchain.mk

ifeq ($(AUX_BUILD_NOT_COMPATIBLE),)

include $(NANO_BUILD)/config_internal.mk

intermediates := $(call intermediates-dir-for,EXECUTABLES,$(LOCAL_MODULE),AUX)

nanohub_linked_map := $(intermediates)/LINKED/$(LOCAL_MODULE).map
nanohub_unchecked_elf := $(intermediates)/UNCHECKED/$(LOCAL_MODULE).elf
nanohub_checked_elf := $(intermediates)/CHECKED/$(LOCAL_MODULE).elf
nanohub_checked_bin := $(intermediates)/CHECKED/$(LOCAL_MODULE).bin

LOCAL_CUSTOM_BUILD_STEP_INPUT := $(nanohub_unchecked_elf)


gen := $(call generated-sources-dir-for,EXECUTABLES,$(LOCAL_MODULE),AUX)

linker_script:=

ifeq ($(LOCAL_NANO_MODULE_TYPE),APP)
linker_script := $(NANOHUB_OS_PATH)/os/platform/$(AUX_ARCH)/lkr/app.lkr
endif

ifeq ($(LOCAL_NANO_MODULE_TYPE),BL)
ifeq ($(AUX_ARCH),stm32)
linker_script := $(gen)/bl.lkr
$(call nano-gen-linker-script,$(linker_script),bl,$(AUX_SUBARCH),stm32f4xx,$(AUX_ARCH))
endif
endif

ifeq ($(LOCAL_NANO_MODULE_TYPE),OS)
ifeq ($(AUX_ARCH),native)
linker_script := $(gen)/os.lkr
$(call nano-gen-linker-script-native,$(linker_script),os,$(AUX_SUBARCH),native,$(AUX_ARCH))
endif
ifeq ($(AUX_ARCH),stm32)
linker_script := $(gen)/os.lkr
$(call nano-gen-linker-script,$(linker_script),os,$(AUX_SUBARCH),stm32f4xx,$(AUX_ARCH))
endif
endif

ifeq ($(linker_script),)
$(error $(LOCAL_PATH): $(LOCAL_MODULE): linker script is not defined for ARCH=$(AUX_ARCH) TYPE=$(LOCAL_NANO_MODULE_TYPE))
endif

LOCAL_ADDITIONAL_DEPENDENCIES += $(linker_script)
LOCAL_LDFLAGS += -T $(linker_script)

ifneq ($(LOCAL_NANO_APP_VERSION),)
LOCAL_NANO_APP_POSTPROCESS_FLAGS += -e $(LOCAL_NANO_APP_VERSION)
endif

$(nanohub_checked_elf): $(nanohub_unchecked_elf)
	$(hide)echo "nanohub Symcheck $@ <= $<"
	$(copy-file-to-target)
nanohub_output := $(nanohub_checked_elf)

# objcopy is per-cpu only
objcopy_params:=

# optional objcopy step
ifneq ($(strip $(LOCAL_OBJCOPY_SECT)),)

objcopy_params := $(GLOBAL_NANO_OBJCOPY_FLAGS) $(GLOBAL_NANO_OBJCOPY_FLAGS_$(AUX_CPU)) $(foreach sect,$(LOCAL_OBJCOPY_SECT), -j $(sect))

$(nanohub_checked_bin): PRIVATE_OBJCOPY_ARGS := $(objcopy_params)
$(nanohub_checked_bin): PRIVATE_MODULE := $(LOCAL_MODULE)
$(nanohub_checked_bin): PRIVATE_OBJCOPY := $(AUX_OBJCOPY)
$(nanohub_checked_bin): $(nanohub_output)
	$(hide)echo "nanohub OBJCOPY $(PRIVATE_MODULE) ($@)"
	$(hide)$(PRIVATE_OBJCOPY) $(PRIVATE_OBJCOPY_ARGS) $< $@
nanohub_output := $(nanohub_checked_bin)

objcopy_params :=
objcopy_sect :=
else
LOCAL_NANO_APP_NO_POSTPROCESS := true
LOCAL_NANO_APP_UNSIGNED := true
endif

ifeq ($(LOCAL_NANO_MODULE_TYPE),APP)

nanohub_napp := $(intermediates)/CHECKED/$(LOCAL_MODULE).napp
nanohub_signed_napp := $(intermediates)/CHECKED/$(LOCAL_MODULE).signed.napp

# postprocess only works on BIN; if it is used, objcopy must be used as well
ifneq ($(LOCAL_NANO_APP_NO_POSTPROCESS),true)
$(if $(LOCAL_OBJCOPY_SECT),,\
    $(error $(LOCAL_PATH): $(LOCAL_MODULE): nanoapp postprocess step requires LOCAL_OBJCOPY_SECT defined))

$(nanohub_napp): PRIVATE_NANO_APP_ID  := $(LOCAL_NANO_APP_ID)
$(nanohub_napp): PRIVATE_NANO_APP_VER := $(LOCAL_NANO_APP_VERSION)
$(nanohub_napp): PRIVATE_NANO_APP_POSTPROCESS_FLAGS := $(LOCAL_NANO_APP_POSTPROCESS_FLAGS)

$(nanohub_napp): $(nanohub_output) $(NANOAPP_POSTPROCESS)
	$(hide)echo "nanoapp POSTPROCESS $@ <= $<"
	$(hide)$(NANOAPP_POSTPROCESS) -a $(PRIVATE_NANO_APP_ID) $(PRIVATE_NANO_APP_POSTPROCESS_FLAGS) $< $@
nanohub_output := $(nanohub_napp)
endif # NO_POSTPROCESS

ifneq ($(LOCAL_NANO_APP_UNSIGNED),true)
$(if $(filter true,$(LOCAL_NANO_APP_NO_POSTPROCESS)),\
    $(error $(LOCAL_PATH): $(LOCAL_MODULE): nanoapp sign step requires nanoapp postprocess))

nanohub_pvt_key := $(NANOHUB_OS_PATH)/os/platform/$(AUX_ARCH)/misc/debug.privkey
nanohub_pub_key := $(NANOHUB_OS_PATH)/os/platform/$(AUX_ARCH)/misc/debug.pubkey

$(nanohub_signed_napp): PRIVATE_PVT_KEY := $(nanohub_pvt_key)
$(nanohub_signed_napp): PRIVATE_PUB_KEY := $(nanohub_pub_key)

$(nanohub_signed_napp): $(nanohub_napp) $(NANOAPP_SIGN)
	$(hide)echo "nanoapp SIGN $@ <= $<"
	$(hide)$(NANOAPP_SIGN) -s -e $(PRIVATE_PVT_KEY) -m $(PRIVATE_PUB_KEY) $< $@

nanohub_output := $(nanohub_signed_napp)
endif # !UNSIGNED

endif # TYPE == APP

LOCAL_CUSTOM_BUILD_STEP_OUTPUT := $(nanohub_output)
LOCAL_LDFLAGS += -Wl,-Map,$(nanohub_linked_map)

###############################
include $(BUILD_AUX_EXECUTABLE)
###############################

LOCAL_CUSTOM_BUILD_STEP_INPUT :=
LOCAL_CUSTOM_BUILD_STEP_OUTPUT :=

endif # AUX_BUILD_NOT_COMPATIBLE
