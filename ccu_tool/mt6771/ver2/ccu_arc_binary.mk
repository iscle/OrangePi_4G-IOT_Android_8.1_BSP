my_ccu_module := $(LOCAL_MODULE)
my_ccu_module_path := $(LOCAL_MODULE_PATH)
my_ccu_module_class := $(LOCAL_MODULE_CLASS)


ifneq ($(strip $(my_ccu_module)),)

LOCAL_MODULE := $(my_ccu_module)

CCU_ARC_TOOL_PREFIX := prebuilts/gcc/linux-x86/arc/arc_gnu_2016.09_prebuilt_elf32_le_linux/bin/arc-elf32-

LOCAL_CC := $(CCU_ARC_TOOL_PREFIX)gcc
LOCAL_AS := $(CCU_ARC_TOOL_PREFIX)as
LOCAL_LD := $(CCU_ARC_TOOL_PREFIX)ld
LOCAL_AR := $(CCU_ARC_TOOL_PREFIX)ar
LOCAL_STRIP := $(CCU_ARC_TOOL_PREFIX)strip
LOCAL_SPLIT :=
LOCAL_SIZE := $(CCU_ARC_TOOL_PREFIX)size
LOCAL_OBJCOPY := $(CCU_ARC_TOOL_PREFIX)objcopy

LOCAL_CFLAGS += -std=gnu99 -mlong-calls \
-Os -I$(CCU_INCLUDE_PATH) -D__PLATFORM__\
-g \
-mcpu=em4_fpus -mno-sdata -mlittle-endian -mdiv-rem -mbarrel-shifter -mnorm -mswap -mfpu=fpus_fma -mno-volatile-cache -mno-millicode \
$(ADT_COPT) \
-D_HAVE_LIBGLOSS_ -DCPU_ARCEM -D__GNU__ -DTOOLCHAIN=ARC_GNU \
-fstrict-volatile-bitfields

LOCAL_ASFLAGS += $(COMPILE_OPT) -mcpu=em4_fpus -x assembler-with-cpp $(ADT_AOPT)

LOCAL_LDFLAGS += -mcpu=em4_fpus -mno-sdata -mlittle-endian -mdiv-rem -mbarrel-shifter -mnorm -mswap -mfpu=fpus_fma -mno-volatile-cache -mno-millicode -nostartfiles -e_start \
$(ADT_LOPT)
#-Wl,-M,-Map=$(MTK_PATH_SOURCE)/hardware/libcamera_3a/libccu/$(TARGET_BOARD_PLATFORM)/ver1/make/make_ccu/make_scripts/arc_test_mw.map
LOCAL_LDFLAGS += -lm

LOCAL_LDFLAGS1 += -Wl,--script=$(MTK_PLATFORM_SW_VER_DIR)/sensor1.ld
LOCAL_LDFLAGS2 += -Wl,--script=$(MTK_PLATFORM_SW_VER_DIR)/sensor2.ld

LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes
LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes/imgsensor
LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes/ccu_main
LOCAL_CFLAGS += -I$(MTK_PLATFORM_SW_VER_DIR)/includes/ccu_ext_interface
LOCAL_CFLAGS += $(addprefix -I,$(my_ccu_src_path))

LOCAL_IS_HOST_MODULE :=
LOCAL_MODULE_CLASS := $(my_ccu_module_class)
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_MULTILIB := 32
ifdef TARGET_2ND_ARCH
LOCAL_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
LOCAL_NO_2ND_ARCH_MODULE_SUFFIX := true
endif
include $(BUILD_SYSTEM)/base_rules.mk


c_normal_sources := $(filter %.c,$(LOCAL_SRC_FILES))
c_normal_objects := $(addprefix $(intermediates)/,$(c_normal_sources:.c=.o))
ifneq ($(strip $(c_normal_objects)),)
$(c_normal_objects): PRIVATE_CC := $(LOCAL_CC)
$(c_normal_objects): PRIVATE_CFLAGS := $(LOCAL_CFLAGS)
$(c_normal_objects): $(intermediates)/%.o: $(TOPDIR)$(LOCAL_PATH)/%.c \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(hide) $(PRIVATE_CC) $(PRIVATE_CFLAGS) -c -o $@ $< -MMD -MT '$@' -MF $(patsubst %.o,%.P,$@)

-include $(c_normal_objects:%.o=%.P)
endif

gen_c_sources := $(filter %.c,$(LOCAL_GENERATED_SOURCES))
gen_c_objects := $(gen_c_sources:%.c=%.o)
ifneq ($(strip $(gen_c_objects)),)
$(gen_c_objects): PRIVATE_CC := $(LOCAL_CC)
$(gen_c_objects): PRIVATE_CFLAGS := $(LOCAL_CFLAGS)
$(gen_c_objects): $(intermediates)/%.o: $(intermediates)/%.c \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(hide) $(PRIVATE_CC) $(PRIVATE_CFLAGS) -c -o $@ $< -MD -MP -MF $(patsubst %.o,%.P,$@)

-include $(gen_c_objects:%.o=%.P)
endif

asm_sources_s := $(filter %.s,$(LOCAL_SRC_FILES))
asm_objects_s := $(addprefix $(intermediates)/,$(asm_sources_s:.s=.o))
ifneq ($(strip $(asm_objects_s)),)
$(asm_objects_s): PRIVATE_AS := $(LOCAL_AS)
$(asm_objects_s): PRIVATE_ASFLAGS := $(LOCAL_ASFLAGS)
$(asm_objects_s): $(intermediates)/%.o: $(TOPDIR)$(LOCAL_PATH)/%.s \
    $(my_additional_dependencies)
	@mkdir -p $(dir $@)
	$(hide) $(PRIVATE_AS) $(PRIVATE_ASFLAGS) -c -o $@ $< -MD -MP -MF $(patsubst %.o,%.P,$@)

-include $(asm_objects_s:%.o=%.P)
endif


all_objects := $(c_normal_objects) $(asm_objects_s) $(gen_c_objects) $(addprefix $(TOPDIR)$(LOCAL_PATH)/,$(LOCAL_PREBUILT_OBJ_FILES))
ALL_C_CPP_ETC_OBJECTS += $(all_objects)

built_static_libraries := \
    $(foreach lib,$(LOCAL_STATIC_LIBRARIES), \
      $(call intermediates-dir-for, \
        STATIC_LIBRARIES,$(lib),$(LOCAL_IS_HOST_MODULE),,$(LOCAL_2ND_ARCH_VAR_PREFIX))/$(lib).a)


linked_module1 := $(intermediates)/LINKED/$(notdir $(LOCAL_BUILT_MODULE))1
$(linked_module1): PRIVATE_LD := $(LOCAL_CC)
$(linked_module1): PRIVATE_SIZE := $(LOCAL_SIZE)
$(linked_module1): PRIVATE_MAX := $(MAX_SIZE_PM)
$(linked_module1): PRIVATE_LDFLAGS := $(LOCAL_LDFLAGS) $(LOCAL_LDFLAGS1)
$(linked_module1): PRIVATE_ALL_OBJECTS := $(all_objects)
$(linked_module1): PRIVATE_ALL_STATIC_LIBRARIES := $(built_static_libraries)
$(linked_module1): $(all_objects) $(built_static_libraries)
	@mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE) 1 ($@)"
	@CODESIZE=$$($(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -le $(PRIVATE_MAX) ]; then $(PRIVATE_LD) -o $@ $(PRIVATE_ALL_OBJECTS) $(if $(strip $(PRIVATE_ALL_STATIC_LIBRARIES)),--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group) $(PRIVATE_LDFLAGS); else touch $@; fi

linked_module2 := $(intermediates)/LINKED/$(notdir $(LOCAL_BUILT_MODULE))2
$(linked_module2): PRIVATE_LD := $(LOCAL_CC)
$(linked_module2): PRIVATE_SIZE := $(LOCAL_SIZE)
$(linked_module2): PRIVATE_MAX := $(MAX_SIZE_PM)
$(linked_module2): PRIVATE_LDFLAGS := $(LOCAL_LDFLAGS) $(LOCAL_LDFLAGS2)
$(linked_module2): PRIVATE_ALL_OBJECTS := $(all_objects)
$(linked_module2): PRIVATE_ALL_STATIC_LIBRARIES := $(built_static_libraries)
$(linked_module2): $(all_objects) $(built_static_libraries)
	@mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE) 2 ($@)"
	@CODESIZE=$$($(PRIVATE_SIZE) --common $< | grep -v text | awk '{printf $$1}'); if [ $${CODESIZE} -le $(PRIVATE_MAX) ]; then $(PRIVATE_LD) -o $@ $(PRIVATE_ALL_OBJECTS) $(if $(strip $(PRIVATE_ALL_STATIC_LIBRARIES)),--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group) $(PRIVATE_LDFLAGS); else touch $@; fi

my_ccu_stripped_module11 := $(LOCAL_BUILT_MODULE)1.dm
$(my_ccu_stripped_module11): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module11): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module11): PRIVATE_LINK_MODULE := $(linked_module1)
$(my_ccu_stripped_module11): $(linked_module1)
	@echo "target Strip: $(PRIVATE_MODULE) 1 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.data -j.sdata -j.sbss -j.bss -j.rodata $< $@

my_ccu_stripped_module12 := $(LOCAL_BUILT_MODULE)1.pm
$(my_ccu_stripped_module12): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module12): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module12): PRIVATE_LINK_MODULE := $(linked_module1)
$(my_ccu_stripped_module12): $(linked_module1)
	@echo "target Strip: $(PRIVATE_MODULE) 1 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.text $< $@	

my_ccu_stripped_module21 := $(LOCAL_BUILT_MODULE)2.dm
$(my_ccu_stripped_module21): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module21): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module21): PRIVATE_LINK_MODULE := $(linked_module2)
$(my_ccu_stripped_module21): $(linked_module2)
	@echo "target Strip: $(PRIVATE_MODULE) 2 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.data -j.sdata -j.sbss -j.bss -j.rodata $< $@

my_ccu_stripped_module22 := $(LOCAL_BUILT_MODULE)2.pm
$(my_ccu_stripped_module22): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module22): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module22): PRIVATE_LINK_MODULE := $(linked_module2)
$(my_ccu_stripped_module22): $(linked_module2)
	@echo "target Strip: $(PRIVATE_MODULE) 2 ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.pm $<.dm $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.text $< $@

my_ccu_stripped_module_ddr1 := $(LOCAL_BUILT_MODULE)1.ddr
$(my_ccu_stripped_module_ddr1): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module_ddr1): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module_ddr1): PRIVATE_LINK_MODULE := $(linked_module1)
$(my_ccu_stripped_module_ddr1): $(linked_module1)
	@echo "target Strip: $(PRIVATE_MODULE) ddr ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.ddr $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.data -j.bss -j.text -j.rodata $< $@

my_ccu_stripped_module_ddr2 := $(LOCAL_BUILT_MODULE)2.ddr
$(my_ccu_stripped_module_ddr2): PRIVATE_OBJCOPY := $(LOCAL_OBJCOPY)
$(my_ccu_stripped_module_ddr2): PRIVATE_SIZE := $(LOCAL_SIZE)
$(my_ccu_stripped_module_ddr2): PRIVATE_LINK_MODULE := $(linked_module2)
$(my_ccu_stripped_module_ddr2): $(linked_module2)
	@echo "target Strip: $(PRIVATE_MODULE) ddr ($@)"
	@mkdir -p $(dir $@)
	@rm -f $<.ddr $@
	$(hide) $(PRIVATE_OBJCOPY) -O binary -j.data -j.bss -j.text -j.rodata $< $@

my_ccu_expend_dm1 := $(my_ccu_stripped_module11).exp
$(my_ccu_expend_dm1): $(my_ccu_stripped_module11)
	@DMSIZE=`cat $< | wc -c`; echo "DM1 $$DMSIZE";\
	RMSIZE=`expr $(MAX_SIZE_DM) - $$DMSIZE`; echo "DM1 $$RMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$RMSIZE count=1;
	@cat $< $<.empty > $<.exp

my_ccu_expend_pm1 := $(my_ccu_stripped_module12).exp
$(my_ccu_expend_pm1): $(my_ccu_stripped_module12)
	@PMSIZE=`cat $< | wc -c`; echo "PM1 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_PM) - $$PMSIZE`; echo "PM1 $$PRMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$PRMSIZE count=1;
	@cat $< $<.empty > $<.exp

my_ccu_expend_dm2 := $(my_ccu_stripped_module21).exp
$(my_ccu_expend_dm2): $(my_ccu_stripped_module21)
	@DMSIZE=`cat $< | wc -c`; echo "DM2 $$DMSIZE";\
	RMSIZE=`expr $(MAX_SIZE_DM) - $$DMSIZE`; echo "DM2 $$RMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$RMSIZE count=1;
	@cat $< $<.empty > $<.exp

my_ccu_expend_pm2 := $(my_ccu_stripped_module22).exp
$(my_ccu_expend_pm2): $(my_ccu_stripped_module22)
	@PMSIZE=`cat $< | wc -c`; echo "PM2 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_PM) - $$PMSIZE`; echo "PM2 $$PRMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$PRMSIZE count=1;
	@cat $< $<.empty > $<.exp

my_ccu_expend_all1 := $(my_ccu_stripped_module_ddr1).exp
$(my_ccu_expend_all1): $(my_ccu_stripped_module_ddr1)
	@PMSIZE=`cat $< | wc -c`; echo "PM2 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_ALL) - $$PMSIZE`; echo "PM2 $$PRMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$PRMSIZE count=1;
	@cat $< $<.empty > $<.exp

my_ccu_expend_all2 := $(my_ccu_stripped_module_ddr2).exp
$(my_ccu_expend_all2): $(my_ccu_stripped_module_ddr2)
	@PMSIZE=`cat $< | wc -c`; echo "PM2 $$PMSIZE";\
	PRMSIZE=`expr $(MAX_SIZE_ALL) - $$PMSIZE`; echo "PM2 $$PRMSIZE";\
	dd if=/dev/zero of=$<.empty bs=$$PRMSIZE count=1;
	@cat $< $<.empty > $<.exp

#$(LOCAL_BUILT_MODULE) : $(my_ccu_expend_dm1) $(my_ccu_expend_pm1) $(my_ccu_expend_dm2) $(my_ccu_expend_pm2)
$(LOCAL_BUILT_MODULE) : $(my_ccu_expend_all1) $(my_ccu_expend_all2)

LOCAL_LDFLAGS1 =
LOCAL_LDFLAGS2 =



include $(CLEAR_VARS)
LOCAL_MODULE := $(my_ccu_module).ddr
LOCAL_MODULE_PATH := $(my_ccu_module_path)
LOCAL_MODULE_CLASS := $(my_ccu_module_class)
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_OWNER := mtk
LOCAL_MULTILIB := 32
LOCAL_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
include $(BUILD_SYSTEM)/base_rules.mk
LOCAL_2ND_ARCH_VAR_PREFIX :=
$(LOCAL_BUILT_MODULE): PRIVATE_PM1 := $(my_ccu_expend_all1)
$(LOCAL_BUILT_MODULE): PRIVATE_PM2 := $(my_ccu_expend_all2)
$(LOCAL_BUILT_MODULE): $(my_ccu_expend_all1) $(my_ccu_expend_all2)
	$(hide) mkdir -p $(dir $@)
	@touch $@
	@cat $(PRIVATE_PM1) $(PRIVATE_PM2) > $@

ALL_DEFAULT_INSTALLED_MODULES += $(LOCAL_INSTALLED_MODULE)

my_ccu_generated_sources += $(my_ccu_module).ddr

endif #ifneq ($(strip $(my_ccu_module)),)