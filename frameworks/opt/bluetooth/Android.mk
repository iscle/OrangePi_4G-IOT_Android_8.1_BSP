LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

src_dirs:= src/android/bluetooth/client/map

LOCAL_SRC_FILES := \
        $(call all-java-files-under, $(src_dirs))

LOCAL_MODULE:= android.bluetooth.client.map
LOCAL_JAVA_LIBRARIES := javax.obex
LOCAL_STATIC_JAVA_LIBRARIES := com.android.vcard

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_STATIC_JAVA_LIBRARY)


include $(call all-makefiles-under,$(LOCAL_PATH))
