#subdir-ccflags-y += -Werror
#ccflags-y += -I$(srctree)/drivers/misc/mediatek/imgsensor/inc

#obj-$(CONFIG_RUNYEE_MDOUBLE_IMGSENSOR_GC0310)	+=  main/main_double_gc0310.o
#obj-$(CONFIG_RUNYEE_SDOUBLE_IMGSENSOR_GC0310)	+=  sub/sub_double_gc0310.o

ifeq ($(CONFIG_RUNYEE_DOUBLE_IMGSENSOR), y)
	obj-y += main/
	obj-y += sub/
endif