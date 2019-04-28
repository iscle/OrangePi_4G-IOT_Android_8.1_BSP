#
# Include this make file to build your application against this module.
#
# Make sure to include it after you've set all your desired LOCAL variables.
# Note that you must explicitly set your LOCAL_RESOURCE_DIR before including this file.
#
# For example:
#
#   LOCAL_RESOURCE_DIR := \
#        $(LOCAL_PATH)/res
#
#   include frameworks/opt/setupwizard/library/common-gingerbread.mk
#

# Path to directory of setup wizard lib (e.g. frameworks/opt/setupwizard/library)
suwlib_dir := $(dir $(lastword $(MAKEFILE_LIST)))

ifneq ($(LOCAL_USE_AAPT2),true)

# Check that LOCAL_RESOURCE_DIR is defined
ifeq (,$(LOCAL_RESOURCE_DIR))
$(error LOCAL_RESOURCE_DIR must be defined)
endif

# Add --auto-add-overlay flag if not present
ifeq (,$(findstring --auto-add-overlay, $(LOCAL_AAPT_FLAGS)))
LOCAL_AAPT_FLAGS += --auto-add-overlay
endif

# Include setup wizard library, if not already included
ifeq (,$(findstring setup-wizard-lib-gingerbread-compat,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += \
    $(suwlib_dir)/main/res \
    $(suwlib_dir)/gingerbread/res \
    $(suwlib_dir)/recyclerview/res
LOCAL_AAPT_FLAGS += --extra-packages com.android.setupwizardlib
LOCAL_STATIC_JAVA_LIBRARIES += setup-wizard-lib-gingerbread-compat
endif

## Include transitive dependencies below

# Include support-v7-appcompat, if not already included
ifeq (,$(findstring android-support-v7-appcompat,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/appcompat/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
endif

# Include support-v7-recyclerview, if not already included
ifeq (,$(findstring android-support-v7-recyclerview,$(LOCAL_STATIC_JAVA_LIBRARIES)))
LOCAL_RESOURCE_DIR += frameworks/support/v7/recyclerview/res
LOCAL_AAPT_FLAGS += --extra-packages android.support.v7.recyclerview
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-recyclerview
endif

else # LOCAL_USE_AAPT2 := true

ifeq (,$(findstring setup-wizard-lib-gingerbread-compat,$(LOCAL_STATIC_ANDROID_LIBRARIES)))
  LOCAL_STATIC_ANDROID_LIBRARIES += setup-wizard-lib-gingerbread-compat
endif

ifeq (,$(findstring android-support-v7-appcompat,$(LOCAL_STATIC_ANDROID_LIBRARIES)))
  LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v7-appcompat
endif

ifeq (,$(findstring android-support-v7-recyclerview,$(LOCAL_STATIC_ANDROID_LIBRARIES)))
  LOCAL_STATIC_ANDROID_LIBRARIES += android-support-v7-recyclerview
endif

endif # LOCAL_USE_AAPT2
