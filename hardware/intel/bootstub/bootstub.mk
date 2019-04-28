CMDLINE_SIZE ?= 0x400
BOOTSTUB_SIZE ?= 8192

BOOTSTUB_SRC_FILES := bootstub.c sfi.c ssp-uart.c imr_toc.c spi-uart.c
BOOTSTUB_SRC_FILES_x86 := head.S e820_bios.S

ifeq ($(TARGET_IS_64_BIT),true)
BOOTSTUB_2ND_ARCH_VAR_PREFIX := $(TARGET_2ND_ARCH_VAR_PREFIX)
else
BOOTSTUB_2ND_ARCH_VAR_PREFIX :=
endif

LOCAL_SRC_FILES := $(BOOTSTUB_SRC_FILES)
LOCAL_SRC_FILES_x86 := $(BOOTSTUB_SRC_FILES_x86)
ANDROID_TOOLCHAIN_FLAGS := -m32 -ffreestanding
LOCAL_CFLAGS := $(ANDROID_TOOLCHAIN_FLAGS) -Wall -O1 -DCMDLINE_SIZE=${CMDLINE_SIZE} -DAOSP_HEADER_ADDRESS=$(BOOTSTUB_AOSP_HEADER_ADDRESS) $(BOOTSTUB_CFLAGS)
LOCAL_ASFLAGS := -DSTACK_OFFSET=$(BOOTSTUB_STACK_OFFSET)
LOCAL_C_INCLUDES = system/core/mkbootimg
LOCAL_MODULE := $(BOOTSTUB_BINARY).bin
LOCAL_MODULE_TAGS := optional
LOCAL_MULTILIB := 32
LOCAL_MODULE_PATH := $(PRODUCT_OUT)
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_2ND_ARCH_VAR_PREFIX := $(BOOTSTUB_2ND_ARCH_VAR_PREFIX)

include $(BUILD_SYSTEM)/binary.mk

$(LOCAL_INTERMEDIATE_TARGETS): PRIVATE_TARGET_GLOBAL_CFLAGS := $(LOCAL_CFLAGS)
$(LOCAL_BUILT_MODULE) : PRIVATE_ELF_FILE := $(intermediates)/$(PRIVATE_MODULE).elf
$(LOCAL_BUILT_MODULE) : PRIVATE_LINK_SCRIPT := $(LOCAL_PATH)/2ndbootloader.lds
$(LOCAL_BUILT_MODULE) : BOOTSTUB_OBJS := $(patsubst %.c, %.o , $(LOCAL_SRC_FILES))
$(LOCAL_BUILT_MODULE) : BOOTSTUB_OBJS += $(patsubst %.S, %.o , $(LOCAL_SRC_FILES_x86))
$(LOCAL_BUILT_MODULE) : BOOTSTUB_OBJS := $(addprefix $(intermediates)/, $(BOOTSTUB_OBJS))
$(LOCAL_BUILT_MODULE) : BOOTSTUB_ENTRY := $(BOOTSTUB_ENTRY)

$(LOCAL_BUILT_MODULE): $(all_objects)
	$(hide) mkdir -p $(dir $@)
	@echo "Generating bootstub.bin: $@"
	$(hide) $(TARGET_LD) \
		-m elf_i386 \
		-T $(PRIVATE_LINK_SCRIPT) --defsym=BOOTSTUB_ENTRY=$(BOOTSTUB_ENTRY) \
		$(BOOTSTUB_OBJS) \
		-o $(PRIVATE_ELF_FILE)
	$(hide) $(TARGET_OBJCOPY) -O binary -R .note -R .comment -S $(PRIVATE_ELF_FILE) $@

$(LOCAL_BUILT_MODULE).size_check: $(LOCAL_BUILT_MODULE)
	$(hide) ACTUAL_SIZE=`$(call get-file-size,$<)`; \
	if [ "$$ACTUAL_SIZE" -gt "$(BOOTSTUB_SIZE)" ]; then \
		echo "$<: $$ACTUAL_SIZE exceeds size limit of $(BOOTSTUB_SIZE) bytes, aborting."; \
		exit 1; \
	fi
	$(hide) touch $@

# Then assemble the final bootstub file
bootstub_full := $(PRODUCT_OUT)/$(BOOTSTUB_BINARY)
$(bootstub_full) : $(LOCAL_BUILT_MODULE) $(LOCAL_BUILT_MODULE).size_check
	@echo "Generating bootstub: $@"
	$(hide) cat $< /dev/zero | dd bs=$(BOOTSTUB_SIZE) count=1 > $@

