/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "BluetoothA2dpServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_av.h"
#include "utils/Log.h"

#include <string.h>

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onCodecConfigChanged;

static struct {
  jclass clazz;
  jmethodID constructor;
  jmethodID getCodecType;
  jmethodID getCodecPriority;
  jmethodID getSampleRate;
  jmethodID getBitsPerSample;
  jmethodID getChannelMode;
  jmethodID getCodecSpecific1;
  jmethodID getCodecSpecific2;
  jmethodID getCodecSpecific3;
  jmethodID getCodecSpecific4;
} android_bluetooth_BluetoothCodecConfig;

static const btav_source_interface_t* sBluetoothA2dpInterface = NULL;
static jobject mCallbacksObj = NULL;

static void bta2dp_connection_state_callback(btav_connection_state_t state,
                                             RawAddress* bd_addr) {
  ALOGI("%s", __func__);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for connection state");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                               (jint)state, addr.get());
}

static void bta2dp_audio_state_callback(btav_audio_state_t state,
                                        RawAddress* bd_addr) {
  ALOGI("%s", __func__);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for connection state");
    return;
  }

  ///M: check mCallbacksObj nullness @{
  if (mCallbacksObj != NULL) {
    sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                 (jbyte*)bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                                 (jint)state, addr.get());
  } else {
    ALOGE("mCallbacksObj is null, cannot callback to onAudioStateChanged");
  }
  /// @}
}

static void bta2dp_audio_config_callback(
    btav_a2dp_codec_config_t codec_config,
    std::vector<btav_a2dp_codec_config_t> codecs_local_capabilities,
    std::vector<btav_a2dp_codec_config_t> codecs_selectable_capabilities) {
  ALOGI("%s", __func__);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  jobject codecConfigObj = sCallbackEnv->NewObject(
      android_bluetooth_BluetoothCodecConfig.clazz,
      android_bluetooth_BluetoothCodecConfig.constructor,
      (jint)codec_config.codec_type, (jint)codec_config.codec_priority,
      (jint)codec_config.sample_rate, (jint)codec_config.bits_per_sample,
      (jint)codec_config.channel_mode, (jlong)codec_config.codec_specific_1,
      (jlong)codec_config.codec_specific_2,
      (jlong)codec_config.codec_specific_3,
      (jlong)codec_config.codec_specific_4);

  jsize i = 0;
  jobjectArray local_capabilities_array = sCallbackEnv->NewObjectArray(
      (jsize)codecs_local_capabilities.size(),
      android_bluetooth_BluetoothCodecConfig.clazz, NULL);
  for (auto const& cap : codecs_local_capabilities) {
    jobject capObj = sCallbackEnv->NewObject(
        android_bluetooth_BluetoothCodecConfig.clazz,
        android_bluetooth_BluetoothCodecConfig.constructor,
        (jint)cap.codec_type, (jint)cap.codec_priority, (jint)cap.sample_rate,
        (jint)cap.bits_per_sample, (jint)cap.channel_mode,
        (jlong)cap.codec_specific_1, (jlong)cap.codec_specific_2,
        (jlong)cap.codec_specific_3, (jlong)cap.codec_specific_4);
    sCallbackEnv->SetObjectArrayElement(local_capabilities_array, i++, capObj);
    sCallbackEnv->DeleteLocalRef(capObj);
  }

  i = 0;
  jobjectArray selectable_capabilities_array = sCallbackEnv->NewObjectArray(
      (jsize)codecs_selectable_capabilities.size(),
      android_bluetooth_BluetoothCodecConfig.clazz, NULL);
  for (auto const& cap : codecs_selectable_capabilities) {
    jobject capObj = sCallbackEnv->NewObject(
        android_bluetooth_BluetoothCodecConfig.clazz,
        android_bluetooth_BluetoothCodecConfig.constructor,
        (jint)cap.codec_type, (jint)cap.codec_priority, (jint)cap.sample_rate,
        (jint)cap.bits_per_sample, (jint)cap.channel_mode,
        (jlong)cap.codec_specific_1, (jlong)cap.codec_specific_2,
        (jlong)cap.codec_specific_3, (jlong)cap.codec_specific_4);
    sCallbackEnv->SetObjectArrayElement(selectable_capabilities_array, i++,
                                        capObj);
    sCallbackEnv->DeleteLocalRef(capObj);
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCodecConfigChanged,
                               codecConfigObj, local_capabilities_array,
                               selectable_capabilities_array);
}

static btav_source_callbacks_t sBluetoothA2dpCallbacks = {
    sizeof(sBluetoothA2dpCallbacks), bta2dp_connection_state_callback,
    bta2dp_audio_state_callback, bta2dp_audio_config_callback,
};

static void classInitNative(JNIEnv* env, jclass clazz) {
  jclass jniBluetoothCodecConfigClass =
      env->FindClass("android/bluetooth/BluetoothCodecConfig");
  android_bluetooth_BluetoothCodecConfig.constructor =
      env->GetMethodID(jniBluetoothCodecConfigClass, "<init>", "(IIIIIJJJJ)V");
  android_bluetooth_BluetoothCodecConfig.getCodecType =
      env->GetMethodID(jniBluetoothCodecConfigClass, "getCodecType", "()I");
  android_bluetooth_BluetoothCodecConfig.getCodecPriority =
      env->GetMethodID(jniBluetoothCodecConfigClass, "getCodecPriority", "()I");
  android_bluetooth_BluetoothCodecConfig.getSampleRate =
      env->GetMethodID(jniBluetoothCodecConfigClass, "getSampleRate", "()I");
  android_bluetooth_BluetoothCodecConfig.getBitsPerSample =
      env->GetMethodID(jniBluetoothCodecConfigClass, "getBitsPerSample", "()I");
  android_bluetooth_BluetoothCodecConfig.getChannelMode =
      env->GetMethodID(jniBluetoothCodecConfigClass, "getChannelMode", "()I");
  android_bluetooth_BluetoothCodecConfig.getCodecSpecific1 = env->GetMethodID(
      jniBluetoothCodecConfigClass, "getCodecSpecific1", "()J");
  android_bluetooth_BluetoothCodecConfig.getCodecSpecific2 = env->GetMethodID(
      jniBluetoothCodecConfigClass, "getCodecSpecific2", "()J");
  android_bluetooth_BluetoothCodecConfig.getCodecSpecific3 = env->GetMethodID(
      jniBluetoothCodecConfigClass, "getCodecSpecific3", "()J");
  android_bluetooth_BluetoothCodecConfig.getCodecSpecific4 = env->GetMethodID(
      jniBluetoothCodecConfigClass, "getCodecSpecific4", "()J");

  method_onConnectionStateChanged =
      env->GetMethodID(clazz, "onConnectionStateChanged", "(I[B)V");

  method_onAudioStateChanged =
      env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");

  method_onCodecConfigChanged = env->GetMethodID(
      clazz, "onCodecConfigChanged",
      "(Landroid/bluetooth/BluetoothCodecConfig;[Landroid/bluetooth/"
      "BluetoothCodecConfig;[Landroid/bluetooth/BluetoothCodecConfig;)V");

  ALOGI("%s: succeeds", __func__);
}

static std::vector<btav_a2dp_codec_config_t> prepareCodecPreferences(
    JNIEnv* env, jobject object, jobjectArray codecConfigArray) {
  std::vector<btav_a2dp_codec_config_t> codec_preferences;

  int numConfigs = env->GetArrayLength(codecConfigArray);
  for (int i = 0; i < numConfigs; i++) {
    jobject jcodecConfig = env->GetObjectArrayElement(codecConfigArray, i);
    if (jcodecConfig == nullptr) continue;
    if (!env->IsInstanceOf(jcodecConfig,
                           android_bluetooth_BluetoothCodecConfig.clazz)) {
      ALOGE("Invalid BluetoothCodecConfig instance");
      continue;
    }
    jint codecType = env->CallIntMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecType);
    jint codecPriority = env->CallIntMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecPriority);
    jint sampleRate = env->CallIntMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getSampleRate);
    jint bitsPerSample = env->CallIntMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getBitsPerSample);
    jint channelMode = env->CallIntMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getChannelMode);
    jlong codecSpecific1 = env->CallLongMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecSpecific1);
    jlong codecSpecific2 = env->CallLongMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecSpecific2);
    jlong codecSpecific3 = env->CallLongMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecSpecific3);
    jlong codecSpecific4 = env->CallLongMethod(
        jcodecConfig, android_bluetooth_BluetoothCodecConfig.getCodecSpecific4);

    btav_a2dp_codec_config_t codec_config = {
        .codec_type = static_cast<btav_a2dp_codec_index_t>(codecType),
        .codec_priority =
            static_cast<btav_a2dp_codec_priority_t>(codecPriority),
        .sample_rate = static_cast<btav_a2dp_codec_sample_rate_t>(sampleRate),
        .bits_per_sample =
            static_cast<btav_a2dp_codec_bits_per_sample_t>(bitsPerSample),
        .channel_mode =
            static_cast<btav_a2dp_codec_channel_mode_t>(channelMode),
        .codec_specific_1 = codecSpecific1,
        .codec_specific_2 = codecSpecific2,
        .codec_specific_3 = codecSpecific3,
        .codec_specific_4 = codecSpecific4};

    codec_preferences.push_back(codec_config);
  }
  return codec_preferences;
}

static void initNative(JNIEnv* env, jobject object,
                       jobjectArray codecConfigArray) {
  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothA2dpInterface != NULL) {
    ALOGW("Cleaning up A2DP Interface before initializing...");
    sBluetoothA2dpInterface->cleanup();
    sBluetoothA2dpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up A2DP callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  if ((mCallbacksObj = env->NewGlobalRef(object)) == NULL) {
    ALOGE("Failed to allocate Global Ref for A2DP Callbacks");
    return;
  }

  android_bluetooth_BluetoothCodecConfig.clazz = (jclass)env->NewGlobalRef(
      env->FindClass("android/bluetooth/BluetoothCodecConfig"));
  if (android_bluetooth_BluetoothCodecConfig.clazz == nullptr) {
    ALOGE("Failed to allocate Global Ref for BluetoothCodecConfig class");
    return;
  }

  sBluetoothA2dpInterface =
      (btav_source_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_ADVANCED_AUDIO_ID);
  if (sBluetoothA2dpInterface == NULL) {
    ALOGE("Failed to get Bluetooth A2DP Interface");
    return;
  }

  std::vector<btav_a2dp_codec_config_t> codec_priorities =
      prepareCodecPreferences(env, object, codecConfigArray);

  bt_status_t status =
      sBluetoothA2dpInterface->init(&sBluetoothA2dpCallbacks, codec_priorities);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth A2DP, status: %d", status);
    sBluetoothA2dpInterface = NULL;
    return;
  }
}

static void cleanupNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothA2dpInterface != NULL) {
    sBluetoothA2dpInterface->cleanup();
    sBluetoothA2dpInterface = NULL;
  }

  env->DeleteGlobalRef(android_bluetooth_BluetoothCodecConfig.clazz);
  android_bluetooth_BluetoothCodecConfig.clazz = nullptr;

  if (mCallbacksObj != NULL) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectA2dpNative(JNIEnv* env, jobject object,
                                  jbyteArray address) {
  ALOGI("%s: sBluetoothA2dpInterface: %p", __func__, sBluetoothA2dpInterface);
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothA2dpInterface->connect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF connection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectA2dpNative(JNIEnv* env, jobject object,
                                     jbyteArray address) {
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothA2dpInterface->disconnect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF disconnection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setCodecConfigPreferenceNative(JNIEnv* env, jobject object,
                                               jobjectArray codecConfigArray) {
  if (!sBluetoothA2dpInterface) return JNI_FALSE;

  std::vector<btav_a2dp_codec_config_t> codec_preferences =
      prepareCodecPreferences(env, object, codecConfigArray);

  bt_status_t status = sBluetoothA2dpInterface->config_codec(codec_preferences);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed codec configuration, status: %d", status);
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initNative", "([Landroid/bluetooth/BluetoothCodecConfig;)V",
     (void*)initNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectA2dpNative", "([B)Z", (void*)connectA2dpNative},
    {"disconnectA2dpNative", "([B)Z", (void*)disconnectA2dpNative},
    {"setCodecConfigPreferenceNative",
     "([Landroid/bluetooth/BluetoothCodecConfig;)Z",
     (void*)setCodecConfigPreferenceNative},
};

int register_com_android_bluetooth_a2dp(JNIEnv* env) {
  return jniRegisterNativeMethods(env,
                                  "com/android/bluetooth/a2dp/A2dpStateMachine",
                                  sMethods, NELEM(sMethods));
}
}
