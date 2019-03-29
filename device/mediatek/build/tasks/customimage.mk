# device/mediatek/common/BoardConfig.mk
ifeq ($(strip $(MTK_CIP_SUPPORT)),yes)
  TARGET_OUT_MTK_CIP := $(MTK_PTGEN_PRODUCT_OUT)/custom
else
  TARGET_OUT_MTK_CIP := $(MTK_PTGEN_PRODUCT_OUT)/system
endif


ifeq ($(strip $(MTK_CIP_SUPPORT)),yes)

ifeq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
MTK_CIP_OP_TARGET_NAMES := op03 op06 op07 op08 op11 op17 op18 op105 op15 op16 op120 op126 op112 op12
MTK_CIP_RP_TARGET_NAMES :=
else
MTK_CIP_OP_TARGET_NAMES :=
MTK_CIP_RP_TARGET_NAMES := la na eu ind jpn mea au rus wwop
endif#MTK_CARRIEREXPRESS_PACK

#
INTERNAL_CUSTOMIMAGE_FILES := $(filter $(TARGET_OUT_MTK_CIP)/%,$(ALL_PREBUILT) $(ALL_COPIED_HEADERS) $(ALL_GENERATED_SOURCES) $(ALL_DEFAULT_INSTALLED_MODULES))


ifneq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
include vendor/mediatek/proprietary/operator/legacy/common/build/UspOperatorConfig.mk
$(foreach x,$(USP_OPERATOR_FEATURES),$(eval $(x)))
endif
include vendor/mediatek/proprietary/operator/legacy/common/build/CIP-Properties.mk
CIP_PROPERTY_OVERRIDES := $(CIP_PROPERTY_OVERRIDES)

CIP_PROPERTIES_BUILD_RPOP :=
CIP_PROPERTIES_BUILD_RPOP += ro.cip.build.date=`date`
ifdef OPTR_SPEC_SEG_DEF
ifneq ($(strip $(OPTR_SPEC_SEG_DEF)),NONE)
ifneq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
CIP_PROPERTIES_BUILD_RPOP += persist.mtk_usp_md_sbp_code=$(subst OP,,$(OPTR))
endif
CIP_PROPERTIES_BUILD_RPOP += persist.operator.optr=$(OPTR)
CIP_PROPERTIES_BUILD_RPOP += persist.operator.spec=$(SPEC)
CIP_PROPERTIES_BUILD_RPOP += persist.operator.seg=$(SEG)
CIP_PROPERTIES_BUILD_RPOP += ro.mtk_md_sbp_custom_value=$(patsubst OP%,%,$(OPTR))
endif
endif#OPTR_SPEC_SEG_DEF
ifneq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
CIP_PROPERTIES_BUILD_RPOP += ro.mtk_carrierexpress_pack=$(strip $(MTK_CARRIEREXPRESS_PACK))
endif
CIP_PROPERTIES_BUILD_RPOP += $(CIP_PROPERTY_OVERRIDES)

INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/cip-build.prop
$(TARGET_OUT_MTK_CIP)/cip-build.prop: PRIVATE_ITEMS := $(CIP_PROPERTIES_BUILD_RPOP)
$(TARGET_OUT_MTK_CIP)/cip-build.prop:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)


ifneq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
include device/mediatek/build/tasks/tools/build_cip_usp_info.mk
endif#MTK_CARRIEREXPRESS_PACK


INSTALLED_CUSTOMIMAGE_TARGET := $(TARGET_OUT_MTK_CIP).img

define build-customimage-target
    mkdir -p $(TARGET_OUT_MTK_CIP)
    mkdir -p $(TARGET_OUT_MTK_CIP)/lib
    mkdir -p $(TARGET_OUT_MTK_CIP)/lib64
    mkdir -p $(TARGET_OUT_MTK_CIP)/app
    mkdir -p $(TARGET_OUT_MTK_CIP)/framework
    mkdir -p $(TARGET_OUT_MTK_CIP)/plugin
    mkdir -p $(TARGET_OUT_MTK_CIP)/media
    mkdir -p $(TARGET_OUT_MTK_CIP)/etc
    mkdir -p $(TARGET_OUT_MTK_CIP)/usp
    mkuserimg.sh -s $(TARGET_OUT_MTK_CIP) $(INSTALLED_CUSTOMIMAGE_TARGET) ext4 custom $(BOARD_CUSTOMIMAGE_PARTITION_SIZE) $(PRODUCT_OUT)/obj/ETC/file_contexts.bin_intermediates/file_contexts.bin
endef

$(INSTALLED_CUSTOMIMAGE_TARGET): $(INTERNAL_USERIMAGES_DEPS) $(INTERNAL_CUSTOMIMAGE_FILES)
	$(build-customimage-target)

.PHONY: customimage
customimage: $(INSTALLED_CUSTOMIMAGE_TARGET)

droidcore: $(INSTALLED_CUSTOMIMAGE_TARGET)


ALL_CUSTOMIMAGE_CLEAN_FILES := \
        $(INSTALLED_CUSTOMIMAGE_TARGET) \
        $(TARGET_OUT_MTK_CIP) \
        $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/mediatek-op_intermediates \
        $(TARGET_OUT_INTERMEDIATES)/ETC/DmApnInfo.xml_intermediates \
        $(TARGET_OUT_INTERMEDIATES)/ETC/smsSelfRegConfig.xml_intermediates \
        $(TARGET_OUT_INTERMEDIATES)/ETC/CIP_MD_SBP_intermediates \
        $(TARGET_OUT_COMMON_INTERMEDIATES)/APPS/FwkPlugin_intermediates

.PHONY: clean-customimage
clean-customimage:

clean-customimage: PRIVATE_CLEAN_FILES := $(ALL_CUSTOMIMAGE_CLEAN_FILES)
clean-customimage:
	$(hide) rm -rf $(PRIVATE_CLEAN_FILES)

ifneq ($(filter customimage,$(MAKECMDGOALS)),)
$(info rm -rf $(ALL_CUSTOMIMAGE_CLEAN_FILES))
$(shell rm -rf $(ALL_CUSTOMIMAGE_CLEAN_FILES))
endif

all_customimage:
	@echo build all customimage for $(MTK_TARGET_PROJECT)
	perl vendor/mediatek/proprietary/operator/legacy/common/build/CIPbuild.pl -ini=vendor/mediatek/proprietary/operator/legacy/common/build/$(MTK_TARGET_PROJECT).ini -p=$(MTK_TARGET_PROJECT) -pf=$(TARGET_BOARD_PLATFORM) -cxp=$(MTK_CARRIEREXPRESS_PACK) -out=$(OUT_DIR)

# PARSE_TIME_MAKE_GOALS=%_customimage
# $(1): OP
# $(2): RP
# $(3): MTK_TARGET_PROJECT
# $(4): TARGET_BOARD_PLATFORM
# $(5): OUT_DIR
define build_cip_target
.PHONY: $(if $(1),$(1),$(2))_customimage
$(if $(1),$(1),$(2))_customimage:
	@echo build $(if $(1),$(1),$(2)) customimage for $(3)
	perl vendor/mediatek/proprietary/operator/legacy/common/build/CIPbuild.pl -ini=vendor/mediatek/proprietary/operator/legacy/common/build/$(3).ini -p=$(3) -pf=$(4) -out=$(5) $(if $(1),-op=$(patsubst op%,OP%,$(1)),-rp=$(2) -cxp=$(MTK_CARRIEREXPRESS_PACK))

endef

$(foreach op,$(MTK_CIP_OP_TARGET_NAMES),\
  $(eval $(call build_cip_target,$(op),,$(MTK_TARGET_PROJECT),$(TARGET_BOARD_PLATFORM),$(OUT_DIR)))\
)
$(foreach rp,$(MTK_CIP_RP_TARGET_NAMES),\
  $(eval $(call build_cip_target,,$(rp),$(MTK_TARGET_PROJECT),$(TARGET_BOARD_PLATFORM),$(OUT_DIR)))\
)

else# MTK_CARRIEREXPRESS_PACK without CIP

INTERNAL_CUSTOMIMAGE_FILES :=
ifneq ($(filter-out no,$(strip $(MTK_CARRIEREXPRESS_PACK))),)
include device/mediatek/build/tasks/tools/build_cip_usp_info.mk
$(INTERNAL_SYSTEMIMAGE_FILES): $(INTERNAL_CUSTOMIMAGE_FILES)
endif#MTK_CARRIEREXPRESS_PACK

endif#MTK_CIP_SUPPORT
