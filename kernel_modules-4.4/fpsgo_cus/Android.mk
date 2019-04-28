LOCAL_PATH := $(call my-dir)

ifneq (true,$(strip $(TARGET_NO_KERNEL)))
ifneq (,$(filter kernel_modules-$(subst kernel-,,$(LINUX_KERNEL_VERSION))/%,$(LOCAL_PATH)))

include $(CLEAR_VARS)
LOCAL_MODULE := fpsgo.ko
LOCAL_STRIP_MODULE := true

LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MULTILIB := first
LOCAL_MODULE_PATH := $(TARGET_ROOT_OUT)/lib/modules
LOCAL_SRC_FILES := $(patsubst $(LOCAL_PATH)/%,%,$(shell find $(LOCAL_PATH) -type f -name '*.[cho]')) Makefile

include $(BUILD_SYSTEM)/base_rules.mk

LOCAL_GENERATED_SOURCES := $(addprefix $(intermediates)/,$(LOCAL_SRC_FILES))

$(LOCAL_GENERATED_SOURCES): $(intermediates)/% : $(LOCAL_PATH)/% | $(ACP)
	@echo "Copy: $@"
	$(copy-file-to-target)

$(KERNEL_OUT)/scripts/sign-file: $(KERNEL_ZIMAGE_OUT);

$(LOCAL_BUILT_MODULE): KOUT := $(KERNEL_OUT)
$(LOCAL_BUILT_MODULE): OPTS := \
  $(KERNEL_MAKE_OPTION) M=$(abspath $(intermediates))
$(LOCAL_BUILT_MODULE): CERT_PATH := $(LINUX_KERNEL_VERSION)/certs
$(LOCAL_BUILT_MODULE): $(wildcard $(LINUX_KERNEL_VERSION)/certs/ko_prvk.pem)
$(LOCAL_BUILT_MODULE): $(wildcard $(LINUX_KERNEL_VERSION)/certs/ko_pubk.x509.der)
$(LOCAL_BUILT_MODULE): $(wildcard vendor/mediatek/proprietary/scripts/kernel_tool/rm_ko_sig.py)
$(LOCAL_BUILT_MODULE): $(LOCAL_GENERATED_SOURCES) $(KERNEL_OUT)/scripts/sign-file
	@echo $@: $^
	$(MAKE) -C $(KOUT) $(OPTS)
ifneq (,$(strip $(LOCAL_STRIP_MODULE)))
	$(hide)$(TARGET_STRIP) --strip-debug $@
endif
	$(hide) $(call sign-kernel-module,$(KOUT)/scripts/sign-file,$(CERT_PATH)/ko_prvk.pem,$(CERT_PATH)/ko_pubk.x509.der)

endif # Kernel version matches current path
endif # TARGET_NO_KERNEL != true
