
# Linux driver folder
ccflags-y += -I$(srctree)/drivers/input/touchscreen/mediatek/
ccflags-y += -I$(srctree)/drivers/input/touchscreen/mediatek/$(CONFIG_TOUCHSCREEN_MTK_FTS_DIRECTORY)
ccflags-y += -I$(srctree)/drivers/misc/mediatek/include/mt-plat/
ccflags-y += -I$(srctree)/drivers/misc/mediatek/include/mt-plat/$(MTK_PLATFORM)/include/
ccflags-y += -I$(srctree)/drivers/base
ccflags-y += -I$(srctree)/drivers/misc/mediatek/sensors-1.0/hwmon/include

obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_ex_fun.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_flash.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_gesture.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_core.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_esdcheck.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_ex_mode.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_proximity.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_i2c.o
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH)    +=  focaltech_point_report_check.o

obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH) +=     focaltech_test/
obj-$(CONFIG_TOUCHSCREEN_MTK_FOCALTECH) +=     focaltech_flash/
