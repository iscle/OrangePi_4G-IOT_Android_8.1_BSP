# Use bash for additional echo fancyness
SHELL = /bin/bash

####################################################################################################
## defines

# Build for Jellybean 
#--yd BUILD_ANDROID_JELLYBEAN = $(shell test -d $(ANDROID_ROOT)/frameworks/native && echo 1)

# Build for Lollipop
# ANDROID version check
BUILD_ANDROID_LOLLIPOP = $(shell test -d $(ANDROID_ROOT)/bionic/libc/kernel/uapi && echo 1)
#ANDROID version check END

#--yd PRODUCT = generic_arm64
#--yd TARGET = android

## libraries ##
LIB_PREFIX = lib

STATIC_LIB_EXT = a
SHARED_LIB_EXT = so

# normally, overridden from outside 
# ?= assignment sets it only if not already defined
TARGET ?= android

MLLITE_LIB_NAME     ?= mllite
#--yd MLLITE_LIB_NAME     ?= mllite_64
MPL_LIB_NAME        ?= mplmpu

## applications ##
SHARED_APP_SUFFIX = -shared
STATIC_APP_SUFFIX = -static

####################################################################################################
## compile, includes, and linker

ifeq ($(BUILD_ANDROID_JELLYBEAN),1)
ANDROID_COMPILE = -DANDROID_JELLYBEAN=1
endif

ANDROID_LINK  = -nostdlib
ANDROID_LINK += -fpic
ANDROID_LINK += -Wl,--gc-sections 
ANDROID_LINK += -Wl,--no-whole-archive 
ANDROID_LINK += -L$(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib
ifeq ($(ARCH),arm)
ANDROID_LINK += -L$(ANDROID_ROOT)/out/target/product/$(PRODUCT)/system/lib
endif

ANDROID_LINK_EXECUTABLE  = $(ANDROID_LINK)
ifeq ($(ARCH),arm64)
ANDROID_LINK_EXECUTABLE += -Wl,-dynamic-linker,/system/bin/linker64
else
ANDROID_LINK_EXECUTABLE += -Wl,-dynamic-linker,/system/bin/linker
endif
ifneq ($(BUILD_ANDROID_JELLYBEAN),1)
#--yd ANDROID_LINK_EXECUTABLE += -Wl,-T,$(ANDROID_ROOT)/build/core/armelf.x
#--yd ANDROID_LINK_EXECUTABLE += -Wl,-T,$(ANDROID_ROOT)/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/aarch64-linux-android/lib/ldscripts/armelf.x
ifeq ($(ARCH),arm64)
ANDROID_LINK_EXECUTABLE += -Wl,-T,$(ANDROID_ROOT)/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/aarch64-linux-android/lib/ldscripts/aarch64linux.x
endif
endif
ANDROID_LINK_EXECUTABLE += $(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib/crtbegin_dynamic.o
ANDROID_LINK_EXECUTABLE += $(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib/crtend_android.o

ANDROID_INCLUDES  = -I$(ANDROID_ROOT)/system/core/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/hardware/libhardware/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/hardware/ril/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/dalvik/libnativehelper/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/frameworks/base/include   # ICS
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/frameworks/native/include # Jellybean
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/external/skia/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/out/target/product/generic/obj/include
#--yd ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/arch-arm/include

ifeq ($(BUILD_ANDROID_LOLLIPOP),1)
#for Android L--yd
ANDROID_INCLUDES += -DHAVE_SYS_UIO_H
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/uapi #LP
ifeq ($(ARCH),arm64)
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/uapi/asm-arm64 #LP
else
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/uapi/asm-arm #LP
endif
endif
$(info YD>>>TARGET_ARCH=$(TARGET_ARCH), ARCH=$(ARCH))
#--yd ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/arch-arm64/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/arch-$(ARCH)/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libstdc++/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/common
#--yd ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/arch-arm64
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/arch-$(ARCH)
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/include
#--yd ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/include/arch/arm64
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/include/arch/$(ARCH)
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libthread_db/include
#--yd ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/arm64
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/$(ARCH)
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm

ANDROID_INCLUDES += -I$(ANDROID_ROOT)/out/target/product/generic/obj/SHARED_LIBRARIES/libm_intermediates
#--yd #for Android L--yd
#--yd ANDROID_INCLUDES += -DHAVE_SYS_UIO_H


KERNEL_INCLUDES  = -I$(KERNEL_ROOT)/include

ifeq ($(ARCH),arm)
KERNEL_INCLUDES  += -I$(KERNEL_ROOT)/arch/arm/include -I$(KERNEL_ROOT)/arch/arm/include/generated
endif

#--yd KERNEL_INCLUDES  = -I$(KERNEL_ROOT)/include -I$(KERNEL_ROOT)/include/uapi -I$(KERNEL_ROOT)/arch/arm64/include -I$(KERNEL_ROOT)/arch/arm64/include/generated -I$(KERNEL_ROOT)/arch/arm64/include/uapi

INV_INCLUDES  = -I$(INV_ROOT)/software/core/driver/include
INV_INCLUDES += -I$(MLLITE_DIR)
INV_INCLUDES += -I$(MLLITE_DIR)/linux

INV_DEFINES += -DINV_CACHE_DMP=1

####################################################################################################
## macros

ifndef echo_in_colors
define echo_in_colors
	echo -ne "\e[1;32m"$(1)"\e[0m"
endef 
endif



