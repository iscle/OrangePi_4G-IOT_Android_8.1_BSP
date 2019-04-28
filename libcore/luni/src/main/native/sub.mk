# -*- mode: makefile -*-
# This file is included by the top-level libcore Android.mk.
# It's not a normal makefile, so we don't include CLEAR_VARS
# or BUILD_*_LIBRARY.

LOCAL_SRC_FILES := \
    ExecStrings.cpp \
    IcuUtilities.cpp \
    JniException.cpp \
    NetworkUtilities.cpp \
    Register.cpp \
    ZipUtilities.cpp \
    android_system_OsConstants.cpp \
    cbigint.cpp \
    java_lang_StringToReal.cpp \
    java_lang_invoke_MethodHandle.cpp \
    java_math_NativeBN.cpp \
    java_util_regex_Matcher.cpp \
    java_util_regex_Pattern.cpp \
    libcore_icu_ICU.cpp \
    libcore_icu_NativeConverter.cpp \
    libcore_icu_TimeZoneNames.cpp \
    libcore_io_AsynchronousCloseMonitor.cpp \
    libcore_io_Linux.cpp \
    libcore_io_Memory.cpp \
    libcore_util_NativeAllocationRegistry.cpp \
    org_apache_harmony_xml_ExpatParser.cpp \
    sun_misc_Unsafe.cpp \
    valueOf.cpp \

LOCAL_STATIC_LIBRARIES += \
    libbase \
    libfdlibm \

LOCAL_SHARED_LIBRARIES += \
    liblog \
    libnativehelper \
