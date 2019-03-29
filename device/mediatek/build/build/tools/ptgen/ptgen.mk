MTK_TARGET_PROJECT := $(subst full_,,$(TARGET_PRODUCT))

include device/mediatek/${MTK_TARGET_PROJECT}/ProjectConfig.mk
-include device/mediatek/${MTK_TARGET_PROJECT}/full_${MTK_TARGET_PROJECT}.mk
include device/mediatek/common/device.mk

include device/mediatek/${MTK_PLATFORM_DIR}/BoardConfig.mk
-include device/mediatek/${MTK_TARGET_PROJECT}/BoardConfig.mk
-include device/mediatek/${MTK_PROJECT}/BoardConfig.mk 

#####  for 6795 TEE  #####
-include alps/vendor/mediatek/proprietary/trustzone/project/${MTK_PLATFORM_DIR}.mk
-include alps/vendor/mediatek/proprietary/trustzone/project/${MTK_TARGET_PROJECT}.mk

ifeq (${OUT_DIR},)
PRODUCT_OUT = out/target/product/${MTK_TARGET_PROJECT}
else
PRODUCT_OUT = ${OUT_DIR}/target/product/${MTK_TARGET_PROJECT}
endif
TARGET_OUT_INTERMEDIATES = ${PRODUCT_OUT}/obj

export PRODUCT_OUT
export TARGET_OUT_INTERMEDIATES

main : ptgen

include vendor/mediatek/proprietary/scripts/ptgen/${TARGET_BOARD_PLATFORM}/Android.mk

