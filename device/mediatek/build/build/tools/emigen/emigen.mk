CUSTOM_MEMORY_HDR = bootable/bootloader/preloader/custom/$(PROJECT)/inc/custom_MemoryDevice.h
MEMORY_DEVICE_XLS = device/mediatek/build/build/tools/emigen/$(PLATFORM)/MemoryDeviceList_$(PLATFORM).xls

all:
	@perl device/mediatek/build/build/tools/emigen/$(PLATFORM)/emigen.pl $(CUSTOM_MEMORY_HDR) \
                     $(MEMORY_DEVICE_XLS) $(PLATFORM) $(PROJECT)
