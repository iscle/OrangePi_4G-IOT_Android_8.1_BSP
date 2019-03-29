my_ccu_module := $(LOCAL_MODULE)
my_ccu_module_path := $(LOCAL_MODULE_PATH)
my_ccu_module_class := $(LOCAL_MODULE_CLASS)


MD32_VER := MS15E30-GNU

MD32_ROOT := $(LOCAL_PATH)/md32ccu/
LOAD_MD32_TOOLCHAIN := source $(MD32_ROOT)envsetup.sh;
my_split = ccu_tool/md32ccu/ToolChain/md32/bin/ccu-elf-split

#$(info my_ccu_module = $(my_ccu_module))
ifneq ($(strip $(my_ccu_module)),)

LOCAL_MODULE := $(my_ccu_module)

LOCAL_CFLAGS += -Os -proc $(MD32_VER) -cmode -I$$MD32_NEWLIB_HOME/$(MD32_VER)/include $(CCU_INCLUDE_PATH) -D__PLATFORM__
LOCAL_CFLAGS += -ffunction-sections -fdata-sections
LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes
LOCAL_CFLAGS += $(addprefix -I,$(my_ccu_src_path))

LOCAL_ASFLAGS += -mcpu=$(MD32_VER)

LOCAL_LDFLAGS1 += -T $(MTK_PLATFORM_SW_VER_DIR)/sensor1.sc
LOCAL_LDFLAGS2 += -T $(MTK_PLATFORM_SW_VER_DIR)/sensor2.sc
LOCAL_LDFLAGS += -L$$MD32_NEWLIB_HOME/$(MD32_VER)/lib -L$$MS15E30_GNU_LIB $$MD32_NEWLIB_OPT --no-check-sections -lmd32_gnu -L$(MD32_ROOT)/Md32/nml/s15r30_md32_v3.0/lib/Release

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

endif

all_objects := $(c_normal_objects) $(gen_c_objects) $(addprefix $(TOPDIR)$(LOCAL_PATH)/,$(LOCAL_PREBUILT_OBJ_FILES))
ALL_C_CPP_ETC_OBJECTS += $(all_objects)

built_static_libraries := \
    $(foreach lib,$(LOCAL_STATIC_LIBRARIES), \
      $(call intermediates-dir-for, \
        STATIC_LIBRARIES,$(lib),$(LOCAL_IS_HOST_MODULE),,$(LOCAL_2ND_ARCH_VAR_PREFIX))/$(lib).a)

linked_module1 := $(intermediates)/LINKED/$(notdir $(LOCAL_BUILT_MODULE))1
$(linked_module1): PRIVATE_LD := md32-elf-ld
$(linked_module1): PRIVATE_SIZE := md32-elf-size
$(linked_module1): PRIVATE_MAX := $(MAX_SIZE_PM)
$(linked_module1): PRIVATE_LDFLAGS := $(LOCAL_LDFLAGS) $(LOCAL_LDFLAGS1)
$(linked_module1): PRIVATE_ALL_OBJECTS := $(all_objects)
$(linked_module1): PRIVATE_ALL_STATIC_LIBRARIES := $(built_static_libraries)
$(linked_module1): $(all_objects) $(built_static_libraries)
	@mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE) 1 ($@)"
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -le $(PRIVATE_MAX) ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_LD) -o $@ $(PRIVATE_ALL_OBJECTS) $(if $(strip $(PRIVATE_ALL_STATIC_LIBRARIES)),--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group) $(PRIVATE_LDFLAGS); else touch $@; fi

linked_module2 := $(intermediates)/LINKED/$(notdir $(LOCAL_BUILT_MODULE))2
$(linked_module2): PRIVATE_LD := md32-elf-ld
$(linked_module2): PRIVATE_SIZE := md32-elf-size
$(linked_module2): PRIVATE_MAX := $(MAX_SIZE_PM)
$(linked_module2): PRIVATE_LDFLAGS := $(LOCAL_LDFLAGS) $(LOCAL_LDFLAGS2)
$(linked_module2): PRIVATE_ALL_OBJECTS := $(all_objects)
$(linked_module2): PRIVATE_ALL_STATIC_LIBRARIES := $(built_static_libraries)
$(linked_module2): $(all_objects) $(built_static_libraries)
	@mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE) 2 ($@)"
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -le $(PRIVATE_MAX) ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_LD) -o $@ $(PRIVATE_ALL_OBJECTS) $(if $(strip $(PRIVATE_ALL_STATIC_LIBRARIES)),--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group) $(PRIVATE_LDFLAGS); else touch $@; fi

my_ccu_stripped_module1 := $(LOCAL_BUILT_MODULE)1
$(my_ccu_stripped_module1): PRIVATE_STRIP := md32-elf-strip
$(my_ccu_stripped_module1): PRIVATE_SPLIT := $(my_split)
$(my_ccu_stripped_module1): PRIVATE_SIZE := md32-elf-size
$(my_ccu_stripped_module1): PRIVATE_LINK_MODULE := $(linked_module1)
$(my_ccu_stripped_module1): $(linked_module1)
	@echo "target Strip: $(PRIVATE_MODULE) 1 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_STRIP) --strip-all $< -o $@; fi
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $(PRIVATE_LINK_MODULE) | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SPLIT) -bin $@; else touch $@.pm $@.dm; fi

my_ccu_stripped_module2 := $(LOCAL_BUILT_MODULE)2
$(my_ccu_stripped_module2): PRIVATE_STRIP := md32-elf-strip
$(my_ccu_stripped_module2): PRIVATE_SPLIT := $(my_split)
$(my_ccu_stripped_module2): PRIVATE_SIZE := md32-elf-size
$(my_ccu_stripped_module2): PRIVATE_LINK_MODULE := $(linked_module2)
$(my_ccu_stripped_module2): $(linked_module2)
	@echo "target Strip: $(PRIVATE_MODULE) 2 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_STRIP) --strip-all $< -o $@; fi
	@CODESIZE=$$($(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SIZE) --common $(PRIVATE_LINK_MODULE) | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -gt 0 ]; then $(LOAD_MD32_TOOLCHAIN) $(PRIVATE_SPLIT) -bin $@; else touch $@.pm $@.dm; fi

my_ccu_expend_dm1 := $(my_ccu_stripped_module1).dm.exp
$(my_ccu_expend_dm1): $(my_ccu_stripped_module1)
	@DMSIZE=`cat $<.dm | wc -c`; echo "DM1 $$DMSIZE";\
	RMSIZE=`expr $(MAX_SIZE_DM) - $$DMSIZE`; echo "DM1 $$RMSIZE";\
	dd if=/dev/zero of=$<.dm.empty bs=$$RMSIZE count=1;
	@cat $<.dm $<.dm.empty > $<.dm.exp

my_ccu_expend_pm1 := $(my_ccu_stripped_module1).pm.exp
$(my_ccu_expend_pm1): $(my_ccu_stripped_module1)
	@PMSIZE=`cat $<.pm | wc -c`; echo "PM1 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_PM) - $$PMSIZE`; echo "PM1 $$PRMSIZE";\
	dd if=/dev/zero of=$<.pm.empty bs=$$PRMSIZE count=1;
	@cat $<.pm $<.pm.empty > $<.pm.exp

my_ccu_expend_dm2 := $(my_ccu_stripped_module2).dm.exp
$(my_ccu_expend_dm2): $(my_ccu_stripped_module2)
	@DMSIZE=`cat $<.dm | wc -c`; echo "DM2 $$DMSIZE";\
	RMSIZE=`expr $(MAX_SIZE_DM) - $$DMSIZE`; echo "DM2 $$RMSIZE";\
	dd if=/dev/zero of=$<.dm.empty bs=$$RMSIZE count=1;
	@cat $<.dm $<.dm.empty > $<.dm.exp

my_ccu_expend_pm2 := $(my_ccu_stripped_module2).pm.exp
$(my_ccu_expend_pm2): $(my_ccu_stripped_module2)
	@PMSIZE=`cat $<.pm | wc -c`; echo "PM2 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_PM) - $$PMSIZE`; echo "PM2 $$PRMSIZE";\
	dd if=/dev/zero of=$<.pm.empty bs=$$PRMSIZE count=1;
	@cat $<.pm $<.pm.empty > $<.pm.exp

$(LOCAL_BUILT_MODULE) : $(my_ccu_expend_dm1) $(my_ccu_expend_pm1) $(my_ccu_expend_dm2) $(my_ccu_expend_pm2)

LOCAL_LDFLAGS1 =
LOCAL_LDFLAGS2 =

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
$(LOCAL_BUILT_MODULE): PRIVATE_DM1 := $(my_ccu_expend_dm1)
$(LOCAL_BUILT_MODULE): PRIVATE_DM2 := $(my_ccu_expend_dm2)
$(LOCAL_BUILT_MODULE): $(my_ccu_expend_dm1) $(my_ccu_expend_dm2)
	$(hide) mkdir -p $(dir $@)
	@touch $@
	@cat $(PRIVATE_DM1) $(PRIVATE_DM2) > $@

ALL_DEFAULT_INSTALLED_MODULES += $(LOCAL_INSTALLED_MODULE)

my_ccu_generated_sources += $(my_ccu_module).dm

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
$(LOCAL_BUILT_MODULE): PRIVATE_PM1 := $(my_ccu_expend_pm1)
$(LOCAL_BUILT_MODULE): PRIVATE_PM2 := $(my_ccu_expend_pm2)
$(LOCAL_BUILT_MODULE): $(my_ccu_expend_pm1) $(my_ccu_expend_pm2)
	$(hide) mkdir -p $(dir $@)
	@touch $@
	@cat $(PRIVATE_PM1) $(PRIVATE_PM2) > $@

ALL_DEFAULT_INSTALLED_MODULES += $(LOCAL_INSTALLED_MODULE)

my_ccu_generated_sources += $(my_ccu_module).pm

endif #ifneq ($(strip $(my_ccu_module)),)