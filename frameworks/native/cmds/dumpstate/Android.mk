LOCAL_PATH:= $(call my-dir)

# =======================#
# dumpstate_test_fixture #
# =======================#
include $(CLEAR_VARS)

LOCAL_MODULE := dumpstate_test_fixture
LOCAL_COMPATIBILITY_SUITE := device-tests
LOCAL_MODULE_TAGS := tests

LOCAL_CFLAGS := \
       -Wall -Werror -Wno-missing-field-initializers -Wno-unused-variable -Wunused-parameter

LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk

LOCAL_SRC_FILES := \
        tests/dumpstate_test_fixture.cpp

LOCAL_TEST_DATA := $(call find-test-data-in-subdirs, $(LOCAL_PATH), *, tests/testdata)

include $(BUILD_NATIVE_TEST)
