my_ccu_sensor := $(strip $(my_ccu_sensor))

ifeq ($(my_ccu_sensor),)
  $(error my_ccu_sensor is not defined)
endif

define is_ccu_supported
$(shell $(MTK_PLATFORM_SW_VER_DIR)/extractor $1>/dev/null && echo $1)
endef

i := $(sort $(wildcard $(my_ccu_src_path)/$(my_ccu_sensor)/*.c))
i := $(foreach x,$(i),$(call is_ccu_supported,$(x)))
$(info $(my_ccu_sensor): is_ccu_supported = $(i))

m := $(call UpperCase,$(my_ccu_sensor))
ifeq ($(MY_CCU_MULTIPLE_BIN),yes)
  # reset LOCAL_MODULE for each sensor
  LOCAL_MODULE := $(MY_CCU_DRVNAME_$(m))
endif

LOCAL_MODULE_CLASS := EXECUTABLES
intermediates := $(call intermediates-dir-for,$(LOCAL_MODULE_CLASS),$(LOCAL_MODULE),,,$(if $(TARGET_2ND_ARCH),$(TARGET_2ND_ARCH_VAR_PREFIX)))
ifeq ($(strip $(intermediates)),)
  intermediates := out/target/product/$(MTK_TARGET_PROJECT)/obj_arm/$(LOCAL_MODULE_CLASS)/$(LOCAL_MODULE)_intermediates
endif
ifeq ($(strip $(i)),)
  #$(error No *.c in $(my_ccu_src_path)/$(my_ccu_sensor))
  GEN :=
  LOCAL_MODULE :=
else
  GEN := $(intermediates)/$(MY_CCU_DRVNAME_$(m)).c
endif

LOCAL_GENERATED_SOURCES += $(GEN)
my_ccu_generated_sources := $(my_ccu_generated_sources) $(GEN)

$(GEN): PRIVATE_INPUT := $(i)
$(GEN): $(i) $(MTK_PLATFORM_SW_VER_DIR)/extractor
	@echo Extract $@:
	$(hide) mkdir -p $(dir $@)
	$(hide) rm -f $@
	true $(foreach f,$(PRIVATE_INPUT),&& $(MTK_PLATFORM_SW_VER_DIR)/extractor $(f) $@)
