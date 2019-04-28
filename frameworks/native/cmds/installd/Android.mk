LOCAL_PATH := $(call my-dir)

#
# OTA Executable
#

include $(CLEAR_VARS)
LOCAL_MODULE := otapreopt
LOCAL_CFLAGS := -Wall -Werror

# Base & ASLR boundaries for boot image creation.
ifndef LIBART_IMG_HOST_MIN_BASE_ADDRESS_DELTA
  LOCAL_LIBART_IMG_HOST_MIN_BASE_ADDRESS_DELTA := -0x1000000
else
  LOCAL_LIBART_IMG_HOST_MIN_BASE_ADDRESS_DELTA := $(LIBART_IMG_HOST_MIN_BASE_ADDRESS_DELTA)
endif
ifndef LIBART_IMG_HOST_MAX_BASE_ADDRESS_DELTA
  LOCAL_LIBART_IMG_HOST_MAX_BASE_ADDRESS_DELTA := 0x1000000
else
  LOCAL_LIBART_IMG_HOST_MAX_BASE_ADDRESS_DELTA := $(LIBART_IMG_HOST_MAX_BASE_ADDRESS_DELTA)
endif
LOCAL_CFLAGS += -DART_BASE_ADDRESS=$(LIBART_IMG_HOST_BASE_ADDRESS)
LOCAL_CFLAGS += -DART_BASE_ADDRESS_MIN_DELTA=$(LOCAL_LIBART_IMG_HOST_MIN_BASE_ADDRESS_DELTA)
LOCAL_CFLAGS += -DART_BASE_ADDRESS_MAX_DELTA=$(LOCAL_LIBART_IMG_HOST_MAX_BASE_ADDRESS_DELTA)

LOCAL_SRC_FILES := otapreopt.cpp globals.cpp utils.cpp dexopt.cpp
LOCAL_HEADER_LIBRARIES := dex2oat_headers
LOCAL_SHARED_LIBRARIES := \
    libbase \
    libcutils \
    liblog \
    liblogwrap \
    libselinux \
    libutils \

LOCAL_STATIC_LIBRARIES := libdiskusage
LOCAL_CLANG := true
include $(BUILD_EXECUTABLE)

# OTA slot script

include $(CLEAR_VARS)
LOCAL_MODULE:= otapreopt_slot
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := otapreopt_slot.sh
LOCAL_INIT_RC := otapreopt.rc

include $(BUILD_PREBUILT)

# OTA postinstall script

include $(CLEAR_VARS)
LOCAL_MODULE:= otapreopt_script
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := otapreopt_script.sh

# Let this depend on otapreopt, the chroot tool and the slot script, so we just have to mention one
# in a configuration.
LOCAL_REQUIRED_MODULES := otapreopt otapreopt_chroot otapreopt_slot

include $(BUILD_PREBUILT)
