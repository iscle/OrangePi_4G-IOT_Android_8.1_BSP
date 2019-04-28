#include $(srctree)/drivers/misc/mediatek/Makefile.custom

# Linux driver folder
#ccflags-y += -I$(srctree)/arch/arm/mach-$(MTK_PLATFORM)/$(ARCH_MTK_PROJECT)/touchpanel/Msg28xx/
ccflags-y += -I$(srctree)/drivers/input/touchscreen/mediatek/Msg2238A/
ccflags-y += -I$(srctree)/drivers/input/touchscreen/mediatek/
ccflags-y += -I$(srctree)/drivers/misc/mediatek/include/mt-plat/
ccflags-y += -I$(srctree)/drivers/misc/mediatek/include/mt-plat/$(MTK_PLATFORM)/include/

obj-y	+=  mstar_drv_common.o
obj-y	+=  mstar_drv_hotknot.o
obj-y	+=  mstar_drv_hotknot_queue.o
obj-y	+=  mstar_drv_ic_fw_porting_layer.o
obj-y   +=  mstar_drv_jni_interface.o
obj-y	+=  mstar_drv_main.o
obj-y	+=  mstar_drv_mtk.o
obj-y	+=  mstar_drv_mutual_fw_control.o
obj-y	+=  mstar_drv_platform_interface.o
obj-y	+=  mstar_drv_platform_porting_layer.o
obj-y	+=  mstar_drv_self_fw_control.o
obj-y	+=  mstar_drv_utility_adaption.o
obj-y   +=  mstar_drv_self_mp_test.o
