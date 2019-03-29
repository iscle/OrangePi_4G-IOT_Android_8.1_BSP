MTK_PROJECT_NAME := $(subst full_,,$(TARGET_PRODUCT))
MTK_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(MTK_PROJECT_NAME))
PRJ_MF := $(MTK_PROJECT_FOLDER)/ProjectConfig.mk
include $(PRJ_MF)

export MTK_NAND_PAGE_SIZE
export PLATFORM
export PROJECT
NANDGEN_FILE := device/mediatek/build/build/tools/emigen/$(PLATFORM)/nandgen.pl

all:
	echo perl $(NANDGEN_FILE)
	perl $(NANDGEN_FILE)
