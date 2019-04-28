LOCAL_MODULE_TAGS := tests

LOCAL_CFLAGS += -std=c++11 -Werror -Wall -Wextra
LOCAL_LDFLAGS +=  -llog

intermediates := $(call intermediates-dir-for,STATIC_LIBRARIES,libRS,TARGET,)
LOCAL_C_INCLUDES += $(intermediates)
