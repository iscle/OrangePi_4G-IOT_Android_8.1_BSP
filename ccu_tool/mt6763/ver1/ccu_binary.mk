my_ccu_module := $(LOCAL_MODULE)
my_ccu_module_path := $(LOCAL_MODULE_PATH)
my_ccu_module_class := $(LOCAL_MODULE_CLASS)


MD32_VER := MS15E30-GNU

MD32_ROOT := $(LOCAL_PATH)/md32ccu/
LOAD_MD32_TOOLCHAIN := source $(MD32_ROOT)envsetup.sh;

#$(info my_ccu_module = $(my_ccu_module))
ifneq ($(strip $(my_ccu_module)),)

LOCAL_MODULE := $(my_ccu_module)

LOCAL_CFLAGS += -Os -proc $(MD32_VER) -cmode -I$$MD32_NEWLIB_HOME/$(MD32_VER)/include $(CCU_INCLUDE_PATH) -D__PLATFORM__
LOCAL_CFLAGS += -ffunction-sections -fdata-sections
#LOCAL_CFLAGS += -g
LOCAL_ASFLAGS += -mcpu=$(MD32_VER)
#LOCAL_ASFLAGS += -g
#LOCAL_LDFLAGS += -T $(LOCAL_PATH)/md32-split.sc
LOCAL_LDFLAGS += -T $(MTK_PLATFORM_SW_VER_DIR)/sensor.sc
LOCAL_LDFLAGS += -L$$MD32_NEWLIB_HOME/$(MD32_VER)/lib -L$$MS15E30_GNU_LIB $$MD32_NEWLIB_OPT --no-check-sections -lmd32_gnu -L$(MD32_ROOT)/Md32/nml/s15r30_md32_v3.0/lib/Release
#LOCAL_LDFLAGS += --gc-section

LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes
LOCAL_CFLAGS += $(addprefix -I,$(my_ccu_src_path))

LOCAL_IS_HOST_MODULE :=
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_MULTILIB := 32
ifdef TARGET_2ND_ARCH
LOCAL_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
LOCAL_NO_2ND_ARCH_MODULE_SUFFIX := true
endif
include $(BUILD_SYSTEM)/base_rules.mk
LOCAL_2ND_ARCH_VAR_PREFIX :=
LOCAL_NO_2ND_ARCH_MODULE_SUFFIX :=

c_normal_sources := $(filter %.c,$(LOCAL_SRC_FILES))
c_normal_objects := $(addprefix $(intermediates)/,$(c_normal_sources:.c=.o))
ifneq ($(strip $(c_normal_objects)),)
$(c_normal_objects): PRIVATE_CC := $(MD32_ROOT)ccmd32
$(c_normal_objects): PRIVATE_CFLAGS := $(LOCAL_CFLAGS)
$(c_normal_objects): $(intermediates)/%.o: $(TOPDIR)$(LOCAL_PATH)/%.c \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(LOAD_MD32_TOOLCHAIN) $(PRIVATE_CC) $(PRIVATE_CFLAGS) -c -o $@ $< -MD -Mo $(patsubst %.o,%.d,$@) -Mt $@
	@cp -f $(patsubst %.o,%.d,$@) $(patsubst %.o,%.P,$@).tmp
	@sed -nr 's#[^:]+:[[:space:]]*(.+)#\1:#gp' $(1) >> $(patsubst %.o,%.P,$@).tmp
	@sed -r 's#"##g' $(patsubst %.o,%.P,$@).tmp >> $(patsubst %.o,%.P,$@)
	@rm -f $(patsubst %.o,%.d,$@) $(patsubst %.o,%.P,$@).tmp

-include $(c_normal_objects:%.o=%.P)
endif


gen_c_sources := $(filter %.c,$(LOCAL_GENERATED_SOURCES))
gen_c_objects := $(gen_c_sources:%.c=%.o)

#$(info gen_c_objects = $(gen_c_objects))

ifneq ($(strip $(gen_c_objects)),)
$(gen_c_objects): PRIVATE_CC := $(MD32_ROOT)ccmd32
$(gen_c_objects): PRIVATE_CFLAGS := $(LOCAL_CFLAGS)
$(gen_c_objects): $(intermediates)/%.o: $(intermediates)/%.c \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(LOAD_MD32_TOOLCHAIN) $(PRIVATE_CC) $(PRIVATE_CFLAGS) -c -o $@ $< -MD -Mo $(patsubst %.o,%.d,$@) -Mt $@
	@cp -f $(patsubst %.o,%.d,$@) $(patsubst %.o,%.P,$@).tmp
	@sed -nr 's#[^:]+:[[:space:]]*(.+)#\1:#gp' $(1) >> $(patsubst %.o,%.P,$@).tmp
	@sed -r 's#"##g' $(patsubst %.o,%.P,$@).tmp >> $(patsubst %.o,%.P,$@)
	@rm -f $(patsubst %.o,%.d,$@) $(patsubst %.o,%.P,$@).tmp

-include $(c_normal_objects:%.o=%.P)
endif

asm_sources_s := $(filter %.s,$(LOCAL_SRC_FILES))
asm_objects_s := $(addprefix $(intermediates)/,$(asm_sources_s:.s=.o))
ifneq ($(strip $(asm_objects_s)),)
$(asm_objects_s): PRIVATE_AS := md32-elf-as
$(asm_objects_s): PRIVATE_ASFLAGS := $(LOCAL_ASFLAGS)
$(asm_objects_s): $(intermediates)/%.o: $(TOPDIR)$(LOCAL_PATH)/%.s \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(LOAD_MD32_TOOLCHAIN) $(PRIVATE_AS) $(PRIVATE_ASFLAGS) -o $@ $<

endif


all_objects := $(c_normal_objects) $(asm_objects_s) $(gen_c_objects) $(addprefix $(TOPDIR)$(LOCAL_PATH)/,$(LOCAL_PREBUILT_OBJ_FILES))
ALL_C_CPP_ETC_OBJECTS += $(all_objects)

built_static_libraries := \
    $(foreach lib,$(LOCAL_STATIC_LIBRARIES), \
      $(call intermediates-dir-for, \
        STATIC_LIBRARIES,$(lib),$(LOCAL_IS_HOST_MODULE),,$(LOCAL_2ND_ARCH_VAR_PREFIX))/$(lib).a)

linked_module := $(intermediates)/LINKED/$(notdir $(LOCAL_BUILT_MODULE))
$(linked_module): PRIVATE_LD := md32-elf-ld
$(linked_module): PRIVATE_SIZE := md32-elf-size
$(linked_module): PRIVATE_MAX := $(my_ccu_max_code)
$(linked_module): PRIVATE_LDFLAGS := $(LOCAL_LDFLAGS)
$(linked_module): PRIVATE_ALL_OBJECTS := $(all_objects)
$(linked_module): PRIVATE_ALL_STATIC_LIBRARIES := $(built_static_libraries)
$(linked_module): $(all_objects) $(built_static_libraries)
	@mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE)"
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -le $(PRIVATE_MAX) ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_LD) -o $@ $(PRIVATE_ALL_OBJECTS) $(if $(strip $(PRIVATE_ALL_STATIC_LIBRARIES)),--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group) $(PRIVATE_LDFLAGS); else touch $@; fi


$(LOCAL_BUILT_MODULE): PRIVATE_STRIP := md32-elf-strip
$(LOCAL_BUILT_MODULE): PRIVATE_SPLIT := md32-elf-split
$(LOCAL_BUILT_MODULE): PRIVATE_SIZE := md32-elf-size
$(LOCAL_BUILT_MODULE): $(linked_module)
	@echo "target Strip: $(PRIVATE_MODULE) ($@)"
	@mkdir -p $(dir $@)
	@rm -f $@.pm $@.dm
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_STRIP) --strip-all $< -o $@; fi
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SPLIT) -bin $@; else touch $@.pm $@.dm; fi


my_ccu_stripped_module := $(LOCAL_BUILT_MODULE)

include $(CLEAR_VARS)
LOCAL_MODULE := $(my_ccu_module).dm
LOCAL_MODULE_PATH := $(my_ccu_module_path)
LOCAL_MODULE_CLASS := $(my_ccu_module_class)
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_OWNER := mtk
LOCAL_MULTILIB := 32
LOCAL_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
include $(BUILD_SYSTEM)/base_rules.mk
LOCAL_2ND_ARCH_VAR_PREFIX :=
$(LOCAL_BUILT_MODULE): $(my_ccu_stripped_module)
	$(hide) mkdir -p $(dir $@)
	$(hide) cp -f $(dir $<)$(notdir $@) $@


ALL_DEFAULT_INSTALLED_MODULES += $(LOCAL_INSTALLED_MODULE)

include $(CLEAR_VARS)
LOCAL_MODULE := $(my_ccu_module).pm
LOCAL_MODULE_PATH := $(my_ccu_module_path)
LOCAL_MODULE_CLASS := $(my_ccu_module_class)
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_OWNER := mtk
LOCAL_MULTILIB := 32
LOCAL_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
include $(BUILD_SYSTEM)/base_rules.mk
LOCAL_2ND_ARCH_VAR_PREFIX :=
$(LOCAL_BUILT_MODULE): $(my_ccu_stripped_module)
	$(hide) mkdir -p $(dir $@)
	$(hide) cp -f $(dir $<)$(notdir $@) $@

ALL_DEFAULT_INSTALLED_MODULES += $(LOCAL_INSTALLED_MODULE)

endif #ifneq ($(strip $(my_ccu_module)),)