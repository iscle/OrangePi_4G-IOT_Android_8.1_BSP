CIP_PROPERTIES_USP_INFO :=
CIP_PROPERTIES_USP_PACKAGES_ALL :=
CIP_PROPERTIES_USP_APK_PATH_ALL :=
CIP_PROPERTIES_USP_CONTENT_OM :=


$(foreach OP_SPEC,$(MTK_REGIONAL_OP_PACK),\
	$(eval include device/mediatek/build/tasks/tools/build_cip_usp_op.mk)\
)

# compile for OM - start
usp_om := $(OPTR_SPEC_SEG_DEF)
OPTR_SPEC_SEG_DEF := NONE

-include vendor/mediatek/proprietary/operator/common/build/UspOperatorConfig.mk
-include vendor/mediatek/proprietary/operator/legacy/common/build/UspOperatorConfig.mk

$(foreach x,$(USP_OPERATOR_FEATURES),$(eval $(x)))

-include vendor/mediatek/proprietary/operator/common/build/CIP-Properties.mk
-include vendor/mediatek/proprietary/operator/legacy/common/build/CIP-Properties.mk

CIP_PROPERTIES_USP_CONTENT_OM += [Package-start]
CIP_PROPERTIES_USP_CONTENT_OM += $(USP_OPERATOR_PACKAGES)
CIP_PROPERTIES_USP_CONTENT_OM += [Package-end]
CIP_PROPERTIES_USP_PACKAGES_ALL += $(USP_OPERATOR_PACKAGES)

CIP_PROPERTIES_USP_CONTENT_OM += [Property-start]
CIP_PROPERTIES_USP_CONTENT_OM += persist.operator.optr=
CIP_PROPERTIES_USP_CONTENT_OM += persist.operator.spec=
CIP_PROPERTIES_USP_CONTENT_OM += persist.operator.seg=
CIP_PROPERTIES_USP_CONTENT_OM += $(CIP_PROPERTY_OVERRIDES)
CIP_PROPERTIES_USP_CONTENT_OM += [Property-end]

OPTR_SPEC_SEG_DEF := $(usp_om)
# compile for OM - end


INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-info.txt
$(TARGET_OUT_MTK_CIP)/usp/usp-info.txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_INFO)
$(TARGET_OUT_MTK_CIP)/usp/usp-info.txt: PRIVATE_MTK_REGIONAL_OP_PACK := $(MTK_REGIONAL_OP_PACK)
$(TARGET_OUT_MTK_CIP)/usp/usp-info.txt:
	mkdir -p $(dir $@)
	rm -f $@
	echo "MTK_REGIONAL_OP_PACK=$(PRIVATE_MTK_REGIONAL_OP_PACK)" >$@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)


INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-packages-all.txt
$(TARGET_OUT_MTK_CIP)/usp/usp-packages-all.txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_PACKAGES_ALL)
$(TARGET_OUT_MTK_CIP)/usp/usp-packages-all.txt:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)

INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-all.txt
$(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-all.txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_APK_PATH_ALL)
$(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-all.txt:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)

ifneq ($(strip $(CIP_PROPERTIES_USP_CONTENT_OM)),)
INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-content-OP00.txt
endif
$(TARGET_OUT_MTK_CIP)/usp/usp-content-OP00.txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_CONTENT_OM)
$(TARGET_OUT_MTK_CIP)/usp/usp-content-OP00.txt:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)
