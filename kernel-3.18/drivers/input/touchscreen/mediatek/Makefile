subdir-ccflags-y += -Werror
ccflags-y += -I$(srctree)/drivers/misc/mediatek/include/mt-plat/
ccflags-y += -I$(srctree)/drivers/input/touchscreen/mediatek
# In case the platform does NOT support this type of sensors

ifneq ($(CONFIG_TOUCHSCREEN_MTK),)
obj-y   +=  mtk_tpd.o tpd_button.o tpd_calibrate.o tpd_debug.o \
		tpd_default.o tpd_init.o tpd_misc.o tpd_setting.o
endif

obj-$(CONFIG_TOUCHSCREEN_MTK)   += tpd_common_probe.o
obj-$(CONFIG_TOUCHSCREEN_MTK_GT910)	+=  GT910/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT1151)	+=  GT1151/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT1151TB)	+=  GT1151TB/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT9XXTB_HN_NEW)	+=  gt9xxtb_hn_new/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT9XX_HOTKNOT)	+=  GT9XX_hotknot/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT9XX_HOTKNOT_PHONE)	+=  GT9XX_hotknot_phone/
obj-$(CONFIG_TOUCHSCREEN_MTK_SYNAPTICS_I2C_RMI4)	+=  synaptics_i2c_rmi4/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT928)	+=  GT928/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT9XX_HOTKNOT_SCP)	+=  GT9XX_hotknot_scp/
obj-$(CONFIG_TOUCHSCREEN_MTK_FT5X0X)	+=  ft5x0x/
obj-$(CONFIG_TOUCHSCREEN_MTK_SYNAPTICS_3320_50) +=  synaptics_3320_50/
obj-$(CONFIG_TOUCHSCREEN_UNIFIED_DRIVER_4)	+=  unified_driver_4/
obj-$(CONFIG_TOUCHSCREEN_MTK_MSG2238A)  +=  msg2238a/
obj-$(CONFIG_TOUCHSCREEN_MTK_FT6336S)   +=  ft6336s/
#android 8.0 debug tp list
obj-$(CONFIG_TOUCHSCREEN_MTK_GT1XX_GS)	+=  GT1XX_GS/
obj-$(CONFIG_TOUCHSCREEN_MTK_GT9XX_GS)	+=  GT9XX_GS/
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)	+=  focaltech/
obj-$(CONFIG_TOUCHSCREEN_MTK_MSTAR)	+=  mstar/
obj-$(CONFIG_TOUCHSCREEN_MTK_GSLX68X)	+=  gslx68x/
