LOCAL_PATH := $(call my-dir)

simpleperf_src_path := system/extras/simpleperf

LLVM_ROOT_PATH := external/llvm
include $(LLVM_ROOT_PATH)/llvm.mk

include $(CLEAR_VARS)
LOCAL_MODULE := CtsSimpleperfTestCases
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/nativetest
LOCAL_MULTILIB := both
LOCAL_MODULE_STEM_32 := $(LOCAL_MODULE)32
LOCAL_MODULE_STEM_64 := $(LOCAL_MODULE)64

LOCAL_WHOLE_STATIC_LIBRARIES = \
  libsimpleperf_cts_test \

LOCAL_STATIC_LIBRARIES += \
  libbacktrace_offline \
  libbacktrace \
  libunwind \
  libziparchive \
  libz \
  libgtest \
  libbase \
  libcutils \
  liblog \
  libprocinfo \
  libutils \
  liblzma \
  libLLVMObject \
  libLLVMBitReader \
  libLLVMMC \
  libLLVMMCParser \
  libLLVMCore \
  libLLVMSupport \
  libprotobuf-cpp-lite \
  libevent \
  libc \

LOCAL_POST_LINK_CMD =  \
  TMP_FILE=`mktemp $(OUT_DIR)/simpleperf-post-link-XXXXXXXXXX` && \
  (cd $(simpleperf_src_path)/testdata && zip - -0 -r .) > $$TMP_FILE && \
  $($(LOCAL_2ND_ARCH_VAR_PREFIX)TARGET_OBJCOPY) --add-section .testzipdata=$$TMP_FILE $(linked_module) && \
  rm -f $$TMP_FILE

LOCAL_COMPATIBILITY_SUITE := cts vts general-tests

LOCAL_CTS_TEST_PACKAGE := android.simpleperf
LOCAL_FORCE_STATIC_EXECUTABLE := true
include $(LLVM_DEVICE_BUILD_MK)
include $(BUILD_CTS_EXECUTABLE)

# Build the test APKs using their own makefiles
include $(call all-makefiles-under,$(LOCAL_PATH))
