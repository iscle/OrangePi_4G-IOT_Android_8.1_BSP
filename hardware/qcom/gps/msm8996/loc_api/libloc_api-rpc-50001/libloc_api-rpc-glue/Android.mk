LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

RPC_INC:=rpc_inc

source_files:= \
    src/loc_api_rpc_glue.c \
    src/loc_api_sync_call.c \
    src/loc_apicb_appinit.c \
    src/loc_api_fixup.c \
    src/loc_api_log.c \
    src/LocApiRpc.cpp

LOCAL_SRC_FILES:= $(source_files)

LOCAL_CFLAGS:=-fno-short-enums
LOCAL_CFLAGS+=-DDEBUG -DUSE_QCOM_AUTO_RPC -DUSE_QCOM_AUTO_RPC
LOCAL_CFLAGS+=$(GPS_FEATURES)

# for loc_api_fixup.c
LOCAL_CFLAGS+=-DADD_XDR_FLOAT -DADD_XDR_BOOL

LOCAL_SHARED_LIBRARIES:= \
    librpc \
    libutils \
    libcutils \
    libcommondefs \
    libgps.utils \
    libloc_core

LOCAL_STATIC_LIBRARIES := \
    libloc_api_rpcgen

LOCAL_PRELINK_MODULE:= false

LOCAL_C_INCLUDES:= \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/rpc_inc \
    $(TARGET_OUT_HEADERS)/gps.utils \
    $(TARGET_OUT_HEADERS)/libloc_core \
    $(TARGET_OUT_HEADERS)/loc_api/rpcgen/inc \
    $(TARGET_OUT_HEADERS)/libcommondefs/rpcgen/inc \
    $(TARGET_OUT_HEADERS)/librpc \
    $(TARGET_OUT_HEADERS)/libloc-rpc/rpc_inc \
    $(TOP)/hardware/msm7k/librpc


LOCAL_MODULE:= libloc_api-rpc-qc
LOCAL_MODULE_OWNER := qcom

LOCAL_MODULE_TAGS := optional
LOCAL_PROPRIETARY_MODULE := true

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libloc_api-rpc-qc_headers
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH/$(RPC_INC))
include $(BUILD_HEADER_LIBRARY)
