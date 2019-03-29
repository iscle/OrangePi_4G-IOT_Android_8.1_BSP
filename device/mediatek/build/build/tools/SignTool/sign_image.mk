MTK_PROJECT_NAME := $(subst full_,,$(TARGET_PRODUCT))
MTK_PROJECT_FOLDER := $(shell find device/* -maxdepth 1 -name $(MTK_PROJECT_NAME))
PRJ_MF := $(MTK_PROJECT_FOLDER)/ProjectConfig.mk
include $(PRJ_MF)

export MTK_NAND_PAGE_SIZE
export MTK_EMMC_SUPPORT
export MTK_NAND_UBIFS_SUPPORT
export FULL_PROJECT
export MTK_COMBO_NAND_SUPPORT
export TRUSTONIC_TEE_SUPPORT
export MTK_SEC_SECRO_AC_SUPPORT
export MTK_NAND_PAGE_SIZE

SHOWTIMECMD   =  date "+%Y/%m/%d %H:%M:%S"
SHOWTIME      =  $(shell $(SHOWTIMECMD))
LOG_DIR =  out/target/product/
SECFL_PL = device/mediatek/build/build/tools/SignTool/sign_sec_file_list.pl

ifeq ($(strip $(TRUSTONIC_TEE_SUPPORT)),yes)
  DEAL_STDOUT_SIGN_IMAGE := 2>&1 | tee -a $(LOG_DIR)$(MTK_PROJECT_NAME)_sign-image.log
else
  DEAL_STDOUT_SIGN_IMAGE := > $(LOG_DIR)$(MTK_PROJECT_NAME)_sign-image.log 2>&1
endif

SIGN_TOOL := device/mediatek/build/build/tools/SignTool/SignTool.pl

sign-image:
ifeq ($(MTK_DEPENDENCY_AUTO_CHECK), true)
	-@echo [Update] $@: $?
endif
	$(hide) echo $(SHOWTIME) $@ ing ...
	$(hide) echo -e \\t\\t\\t\\b\\b\\b\\bLOG: $(LOG_DIR)$(MTK_PROJECT_NAME)_$@.log
	$(hide) rm -f $(LOG_DIR)$(MTK_PROJECT_NAME)_$@.log $(LOG_DIR)$(MTK_PROJECT_NAME)_$@.log_err
	$(hide) perl $(SECFL_PL)
	$(hide) make -j24 snod
	$(hide) perl $(SIGN_TOOL) $(MTK_PROJECT_NAME) $(MTK_PROJECT_NAME) $(MTK_SEC_SECRO_AC_SUPPORT) $(MTK_NAND_PAGE_SIZE) $(DEAL_STDOUT_SIGN_IMAGE) 



