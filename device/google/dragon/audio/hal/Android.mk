LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm

LOCAL_SRC_FILES := \
	audio_hw.c \
        dsp/biquad.c \
        dsp/crossover.c \
        dsp/crossover2.c \
        dsp/drc.c \
        dsp/drc_kernel.c \
        dsp/drc_math.c \
        dsp/dsp_util.c \
        dsp/eq2.c \
        dsp/eq.c \
	cras_dsp.c \
	cras_dsp_ini.c \
	cras_dsp_mod_builtin.c \
	cras_dsp_pipeline.c \
	cras_expr.c \
	iniparser.c \
	dictionary.c

LOCAL_SHARED_LIBRARIES := \
	liblog \
	libcutils \
	libaudioutils \
	libtinyalsa \
	libtinycompress \
	libaudioroute \
	libdl


LOCAL_C_INCLUDES += \
	device/google/dragon/audio/hal/dsp \
	external/tinyalsa/include \
	external/tinycompress/include \
	$(call include-path-for, audio-utils) \
	$(call include-path-for, audio-route) \
	$(call include-path-for, audio-effects)

LOCAL_MODULE := audio.primary.dragon

LOCAL_MODULE_RELATIVE_PATH := hw

LOCAL_MODULE_TAGS := optional

# b/26236653, dsp_util.c inline assembly code does not compile with llvm yet.
LOCAL_CLANG_CFLAGS += -no-integrated-as

include $(BUILD_SHARED_LIBRARY)
