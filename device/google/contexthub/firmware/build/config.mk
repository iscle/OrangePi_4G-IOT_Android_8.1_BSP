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

NANO_BUILD := $(NANOHUB_OS_PATH)/build

NANO_OS := nanohub
NANO_ALL_CLASSES := phone watch
NANO_ALL_TARGETS := APP BL OS

NANO_ALL_ALL := $(AUX_ALL_VARIANTS) $(AUX_ALL_OSES) $(AUX_ALL_ARCHS) $(AUX_ALL_SUBARCHS) $(AUX_ALL_CPUS) $(NANO_ALL_CLASSES) $(NANO_ALL_TARGETS)

GLOBAL_NANO_OBJCOPY_FLAGS_cortexm4 := -I elf32-littlearm -O binary

CLEAR_NANO_VARS := $(NANO_BUILD)/clear_vars.mk
NANOHUB_BL_CONFIG := $(NANO_BUILD)/bl_config.mk
NANOHUB_OS_CONFIG := $(NANO_BUILD)/os_config.mk
NANOHUB_APP_CONFIG := $(NANO_BUILD)/app_config.mk

BUILD_NANOHUB_BL_STATIC_LIBRARY := $(NANO_BUILD)/bl_static_library.mk
BUILD_NANOHUB_OS_STATIC_LIBRARY := $(NANO_BUILD)/os_static_library.mk
BUILD_NANOHUB_APP_STATIC_LIBRARY := $(NANO_BUILD)/app_static_library.mk
BUILD_NANOHUB_EXECUTABLE := $(NANO_BUILD)/nanohub_executable.mk
BUILD_NANOHUB_BL_EXECUTABLE := $(NANO_BUILD)/bl_executable.mk
BUILD_NANOHUB_OS_EXECUTABLE := $(NANO_BUILD)/os_executable.mk
BUILD_NANOHUB_OS_IMAGE := $(NANO_BUILD)/os_image.mk
BUILD_NANOHUB_APP_EXECUTABLE := $(NANO_BUILD)/app_executable.mk
BUILD_NANOHUB_APP_CHRE_EXECUTABLE := $(NANO_BUILD)/app_chre_executable.mk

NANOAPP_POSTPROCESS := $(HOST_OUT_EXECUTABLES)/nanoapp_postprocess
NANOAPP_SIGN := $(HOST_OUT_EXECUTABLES)/nanoapp_sign

# $(1) - optional value to assign to
#        pass empty to clear for repeated includes
#        pass some definitive invalid value otherwise (after last repeat)
define nano-reset-built-env
$(eval OVERRIDE_BUILT_MODULE_PATH:=$(1)) \
$(eval LOCAL_BUILT_MODULE:=$(1)) \
$(eval LOCAL_INSTALLED_MODULE:=$(1)) \
$(eval LOCAL_INTERMEDIATE_TARGETS:=$(1))
endef

# variant may declare it's class; default is phone
define nano-variant-class
$(if $(filter $(NANO_ALL_CLASSES),$(NANO_CLASS_$(AUX_OS_VARIANT))),$(NANO_CLASS_$(AUX_OS_VARIANT)),phone)
endef

# $(1) - target file
# $(2) - list of dependencies
define nano-gen-linker-script-from-list-body
    mkdir -p $(dir $(1)) && \
    rm -f $(1).tmp && \
    touch $(1).tmp && \
    $(foreach file,$(2),echo "INCLUDE $(file)"  >> $(1).tmp &&) \
    mv -f $(1).tmp $(1)
endef

# $(1) - target file
# $(2) - list of dependencies
define nano-gen-linker-script-from-list
$(1): $(2)
	$(call nano-gen-linker-script-from-list-body,$(1),$(2))
endef

# create linker script rule
#
# $(1) - target file
# $(2) - { os | bl }
# $(3) - platform name
# $(4) - platform class
# $(5) - platform dir
define nano-gen-linker-script
$(eval $(call nano-gen-linker-script-from-list,$(1),$(patsubst %,$(NANOHUB_OS_PATH)/os/platform/$(5)/lkr/%.lkr,$(3).map $(4).$(2) $(4).common)))
endef

# create linker script rule
#
# $(1) - target file
# $(2) - bl|os
# $(3) - platform name
# $(4) - platform class
# $(5) - platform dir
#
# NOTE: ($(2), $(3) - unused; keep for argument compatibility with nano-gen-linker-script
define nano-gen-linker-script-native
$(eval $(call nano-gen-linker-script-from-list,$(1),$(NANOHUB_OS_PATH)/os/platform/$(5)/lkr/$(4).extra.lkr))
endef

# variables that Android.mk or our config files may define per-cpu, per-arch etc;
# must include all LOCAL* variables we modify in any place within the scope of for-each-variant.
#
# workflow is as follows:
# before starting iterations:
#   all the LOCAL_<var> and LOCAL_<var>_<cpu,arch,subarch,variant,os,class> from NANO_VAR_LIST
#   are copied to LOCAL_NANO_*; original LOCAL_* are erased to avoid conflicts fith underlaying build which also does suffix handling.
#   this part is performed by nano-user-vars-save-all
# on every iteration, before includeing file:
#   all the LOCAL_NANO_<var>_<cpu,arch,subarch,variant,os,class> vars are loaded for the current variant,
#   and then concatenated all toghether and also with
#   NANO_VARIANT_<target>_<var>_<variant> (where target is OS or BL for system builds, or APP for nanoapp builds) and
#   NANO_VARIANT_<var>_<variant>; result is stored in LOCAL_<var>
#   this var is used by underlaying build infrastructure as usual
#   this part is performed by nano-user-vars-load-all
# on every iteration, after includeing file:
#   reset "BUILT" variables in order to let next iteration going
# after all iterations done:
#   erase all volatile AUX* enviraonment, cleanup all the LOCAL* and LOCAL_NANO* vars
NANO_VAR_LIST :=                \
    C_INCLUDES                  \
    CFLAGS                      \
    ASFLAGS                     \
    CPPFLAGS                    \
    SRC_FILES                   \
    STATIC_LIBRARIES            \
    WHOLE_STATIC_LIBRARIES      \
    LDFLAGS                     \
    OBJCOPY_SECT                \

# collect anything that user might define per-cpu, per-arch etc
# $(1) - one of $(NANO_VAR_LIST)
# $(2) - optional: one of { APP,BL,OS }
define nano-user-var-load
$(eval LOCAL_$(1) :=                                                    \
    $(LOCAL_NANO_$(1))                                                  \
    $(NANO_VARIANT_$(1)_$(AUX_OS_VARIANT))                              \
    $(NANO_VARIANT_$(2)_$(1)_$(AUX_OS_VARIANT))                         \
    $(LOCAL_NANO_$(1)_$(AUX_OS_VARIANT))                                \
    $(LOCAL_NANO_$(1)_$(AUX_CPU))                                       \
    $(LOCAL_NANO_$(1)_$(AUX_ARCH))                                      \
    $(LOCAL_NANO_$(1)_$(AUX_SUBARCH))                                   \
    $(LOCAL_NANO_$(1)_$(AUX_OS))                                        \
    $(LOCAL_NANO_$(1)_$(call nano-variant-class,$(AUX_OS_VARIANT)))     \
    $(LOCAL_NANO_$(1)_$(LOCAL_NANO_MODULE_TYPE))                        \
)
endef

define nano-user-var-reset-final
$(eval LOCAL_$(1):=) \
$(eval LOCAL_NANO_$(1):=) \
$(foreach v,$(NANO_ALL_ALL),\
    $(eval LOCAL_NANO_$(1)_$(v):=) \
    $(eval LOCAL_$(1)_$(v):=) \
)
endef

define nano-user-vars-reset-final
$(foreach _nuvrf_var,$(NANO_VAR_LIST),$(call nano-user-var-reset-final,$(_nuvrf_var)))
endef

# $(1) - optional: one of APP,BL,OS
define nano-user-vars-load-all
$(foreach _nuvla_var,$(NANO_VAR_LIST),$(call nano-user-var-load,$(_nuvla_var),$(1)))
endef

define nano-user-vars-save-all
$(foreach _nuvsa_var,$(NANO_VAR_LIST),\
    $(eval LOCAL_NANO_$(_nuvsa_var) := $(LOCAL_$(_nuvsa_var))) \
    $(eval LOCAL_$(_nuvsa_var):=) \
    $(foreach v,$(NANO_ALL_ALL),\
        $(eval LOCAL_NANO_$(_nuvsa_var)_$(v):=$(LOCAL_$(_nuvsa_var)_$(v))) \
        $(eval LOCAL_$(_nuvsa_var)_$(v):=) \
    ) \
)
endef

# $(1) - variant list
# $(2) - optional: one of APP,BL,OS
# $(3) - path to makefile which has to be included for each variant
define for-each-variant-unchecked
    $(eval AUX_RECURSIVE_VARIANT_LIST:=$(1)) \
    $(call nano-user-vars-save-all) \
    $(foreach _fev_variant,$(1),\
        $(call aux-variant-load-env,$(_fev_variant)) \
        $(call nano-user-vars-load-all,$(2)) \
        $(info $(LOCAL_PATH): $(LOCAL_MODULE): OS=$(AUX_OS) ARCH=$(AUX_ARCH) SUBARCH=$(AUX_SUBARCH) CPU=$(AUX_CPU)) \
        $(eval include $(3)) \
        $(call nano-reset-built-env,) \
    ) \
    $(eval AUX_RECURSIVE_VARIANT_LIST:=) \
    $(call aux-variant-load-env,) \
    $(call nano-reset-built-env,$(LOCAL_MODULE)-module-is-poisoned) \
    $(call nano-user-vars-reset-final) \

endef

# $(1),$(2) - two sets to compare for equality
# returns true, if sets have the same items (not necessarily in the same order)
# returns empty string on mismatch
define equal-sets
$(if $(strip $(filterout $(1),$(2))$(filterout $(2),$(1))),,true)
endef

# this call would include a given makefile in the loop,
# and would iterate through available variants from $(1)
# $(1) - variant list
# $(2) - optional: one of APP,BL,OS
# $(3) - path to makefile to include for each variant
define for-each-variant
$(if $(AUX_RECURSIVE_VARIANT_LIST),$(if \
        $(call equal-sets,$(AUX_RECURSIVE_VARIANT_LIST),$(1)),,\
        $(error $(LOCAL_PATH): Recursive variant list mismatch: "$(AUX_RECURSIVE_VARIANT_LIST)" and "$(1))),\
    $(call for-each-variant-unchecked,$(1),$(2),$(3)))
endef
