OTA_SCATTER_GENERATOR = device/mediatek/build/build/tools/ptgen/ota_scatter.pl
OTA_SCATTER_FILE = out/target/product/$(Project)/ota_scatter.txt
SCATTER_FILE_NOEMMC := out/target/product/$(Project)/$(PLATFORM)_Android_scatter.txt
SCATTER_FILE_EMMC := out/target/product/$(Project)/$(PLATFORM)_Android_scatter_emmc.txt
ifneq ($(wildcard $(SCATTER_FILE_NOEMMC)),)
  SCATTER_FILE := $(SCATTER_FILE_NOEMMC)
else
  ifneq ($(wildcard $(SCATTER_FILE_EMMC)),)
    SCATTER_FILE := $(SCATTER_FILE_EMMC)
  else
    $(error No scatter file exists under out/target/product/$(Project)/!!)
  endif
endif

all:
	echo perl $(OTA_SCATTER_GENERATOR) $(SCATTER_FILE) $(OTA_SCATTER_FILE)
	perl $(OTA_SCATTER_GENERATOR) $(SCATTER_FILE) $(OTA_SCATTER_FILE)
