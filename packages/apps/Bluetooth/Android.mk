LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, lib/mapapi)

LOCAL_MODULE := bluetooth.mapsapi
include $(BUILD_STATIC_JAVA_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src) \
        $(call all-proto-files-under, src)

LOCAL_PACKAGE_NAME := Bluetooth
LOCAL_CERTIFICATE := platform
LOCAL_USE_AAPT2 := true

LOCAL_JNI_SHARED_LIBRARIES := libbluetooth_jni
LOCAL_JAVA_LIBRARIES := javax.obex telephony-common libprotobuf-java-micro services.net
LOCAL_STATIC_JAVA_LIBRARIES := com.android.vcard bluetooth.mapsapi sap-api-java-static services.net
LOCAL_STATIC_ANDROID_LIBRARIES := \
        android-support-v4
LOCAL_PROTOC_OPTIMIZE_TYPE := micro

LOCAL_REQUIRED_MODULES := bluetooth.default

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
