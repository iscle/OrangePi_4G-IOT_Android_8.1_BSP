LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := \
    libaudioutils \
    libcutils   \
    liblog      \
    libutils    \
    libhardware

LOCAL_SRC_FILES := \
    DeviceHalLocal.cpp          \
    DevicesFactoryHalHybrid.cpp \
    DevicesFactoryHalLocal.cpp  \
    StreamHalLocal.cpp

LOCAL_CFLAGS := -Wall -Werror

ifeq ($(USE_LEGACY_LOCAL_AUDIO_HAL), true)

# Use audiohal directly w/o hwbinder middleware.
# This is for performance comparison and debugging only.

LOCAL_SRC_FILES += \
    EffectBufferHalLocal.cpp    \
    EffectsFactoryHalLocal.cpp  \
    EffectHalLocal.cpp

LOCAL_SHARED_LIBRARIES += \
    libeffects

LOCAL_CFLAGS += -DUSE_LEGACY_LOCAL_AUDIO_HAL

else  # if !USE_LEGACY_LOCAL_AUDIO_HAL

LOCAL_SRC_FILES += \
    ConversionHelperHidl.cpp   \
    HalDeathHandlerHidl.cpp    \
    DeviceHalHidl.cpp          \
    DevicesFactoryHalHidl.cpp  \
    EffectBufferHalHidl.cpp    \
    EffectHalHidl.cpp          \
    EffectsFactoryHalHidl.cpp  \
    StreamHalHidl.cpp

LOCAL_SHARED_LIBRARIES += \
    libbase          \
    libfmq           \
    libhwbinder      \
    libhidlbase      \
    libhidlmemory    \
    libhidltransport \
    android.hardware.audio@2.0             \
    android.hardware.audio.common@2.0      \
    android.hardware.audio.common@2.0-util \
    android.hardware.audio.effect@2.0      \
    android.hidl.allocator@1.0             \
    android.hidl.memory@1.0                \
    libmedia_helper  \
    libmediautils

endif  # USE_LEGACY_LOCAL_AUDIO_HAL

ifeq ($(MTK_AUDIO),yes)
    LOCAL_CFLAGS += -DMTK_AUDIO_FIX_DEFAULT_DEFECT
endif  #MTK_AUDIO

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

LOCAL_MODULE := libaudiohal

include $(BUILD_SHARED_LIBRARY)
