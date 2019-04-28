LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
        C2.cpp    \

LOCAL_C_INCLUDES += \
        $(TOP)/frameworks/av/media/libstagefright/codec2/include \
        $(TOP)/frameworks/native/include/media/hardware \

LOCAL_MODULE:= libstagefright_codec2
LOCAL_CFLAGS += -Werror -Wall
LOCAL_CLANG := true
LOCAL_SANITIZE := unsigned-integer-overflow signed-integer-overflow cfi
LOCAL_SANITIZE_DIAG := cfi

include $(BUILD_SHARED_LIBRARY)

################################################################################

include $(call all-makefiles-under,$(LOCAL_PATH))
