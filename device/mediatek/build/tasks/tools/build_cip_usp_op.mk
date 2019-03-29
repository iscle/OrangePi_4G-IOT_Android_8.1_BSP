ifndef OP_SPEC
  $(error OP_SPEC is not defined)
endif

OPTR_SPEC_SEG_DEF := $(OP_SPEC)
OPTR := $(word 1, $(subst _,$(space),$(OP_SPEC)))
SPEC := $(word 2, $(subst _,$(space),$(OP_SPEC)))
SEG := $(word 3, $(subst _,$(space),$(OP_SPEC)))

-include vendor/mediatek/proprietary/operator/common/build/UspOperatorConfig.mk
-include vendor/mediatek/proprietary/operator/legacy/common/build/UspOperatorConfig.mk

$(foreach x,$(USP_OPERATOR_FEATURES),$(eval $(x)))

-include vendor/mediatek/proprietary/operator/common/build/CIP-Properties.mk
-include vendor/mediatek/proprietary/operator/legacy/common/build/CIP-Properties.mk

CIP_PROPERTIES_USP_PACKAGES_ALL += $(USP_OPERATOR_PACKAGES)

CIP_PROPERTIES_USP_APK_PATH_ALL += OPTR:$(OPTR)
CIP_PROPERTIES_USP_APK_PATH_ALL += $(USP_OPERATOR_APK_PATH)

CIP_PROPERTIES_USP_CONTENT_$(OPTR) := [Package-start]
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += $(USP_OPERATOR_PACKAGES)
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += [Package-end]
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += [Property-start]

ifneq ("$(wildcard vendor/mediatek/proprietary/operator/SPEC/$(OPTR)/$(SPEC)/$(SEG)/optr_package_config.mk)","")
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.optr=$(OPTR)
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.spec=$(SPEC)
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.seg=$(SEG)
else
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.optr=
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.spec=
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += persist.operator.seg=
endif

CIP_PROPERTIES_USP_CONTENT_$(OPTR) += $(CIP_PROPERTY_OVERRIDES)
CIP_PROPERTIES_USP_CONTENT_$(OPTR) += [Property-end]

CIP_PROPERTIES_USP_APKS_PATH_$(OPTR) := $(USP_OPERATOR_APK_PATH)


INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-content-$(OPTR).txt
$(TARGET_OUT_MTK_CIP)/usp/usp-content-$(OPTR).txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_CONTENT_$(OPTR))
$(TARGET_OUT_MTK_CIP)/usp/usp-content-$(OPTR).txt:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)


INTERNAL_CUSTOMIMAGE_FILES += $(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-$(OPTR).txt
$(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-$(OPTR).txt: PRIVATE_ITEMS := $(CIP_PROPERTIES_USP_APKS_PATH_$(OPTR))
$(TARGET_OUT_MTK_CIP)/usp/usp-apks-path-$(OPTR).txt:
	mkdir -p $(dir $@)
	rm -f $@
	$(foreach item,$(PRIVATE_ITEMS),echo "$(item)" >>$@;)
