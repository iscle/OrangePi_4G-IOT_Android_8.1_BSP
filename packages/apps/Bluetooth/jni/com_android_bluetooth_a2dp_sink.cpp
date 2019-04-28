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

#define LOG_TAG "BluetoothA2dpSinkServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_av.h"
#include "utils/Log.h"

#include <string.h>

namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onAudioConfigChanged;

static const btav_sink_interface_t* sBluetoothA2dpInterface = NULL;
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

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                               (jint)state, addr.get());
}

static void bta2dp_audio_config_callback(RawAddress* bd_addr,
                                         uint32_t sample_rate,
                                         uint8_t channel_count) {
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
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioConfigChanged,
                               addr.get(), (jint)sample_rate,
                               (jint)channel_count);
}

static btav_sink_callbacks_t sBluetoothA2dpCallbacks = {
    sizeof(sBluetoothA2dpCallbacks), bta2dp_connection_state_callback,
    bta2dp_audio_state_callback, bta2dp_audio_config_callback,
};

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_onConnectionStateChanged =
      env->GetMethodID(clazz, "onConnectionStateChanged", "(I[B)V");

  method_onAudioStateChanged =
      env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");

  method_onAudioConfigChanged =
      env->GetMethodID(clazz, "onAudioConfigChanged", "([BII)V");

  ALOGI("%s: succeeds", __func__);
}

static void initNative(JNIEnv* env, jobject object) {
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

  sBluetoothA2dpInterface =
      (btav_sink_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_ADVANCED_AUDIO_SINK_ID);
  if (sBluetoothA2dpInterface == NULL) {
    ALOGE("Failed to get Bluetooth A2DP Sink Interface");
    return;
  }

  bt_status_t status = sBluetoothA2dpInterface->init(&sBluetoothA2dpCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth A2DP Sink, status: %d", status);
    sBluetoothA2dpInterface = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
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

static void informAudioFocusStateNative(JNIEnv* env, jobject object,
                                        jint focus_state) {
  if (!sBluetoothA2dpInterface) return;
  sBluetoothA2dpInterface->set_audio_focus_state((uint8_t)focus_state);
}

static void informAudioTrackGainNative(JNIEnv* env, jobject object,
                                       jfloat gain) {
  if (!sBluetoothA2dpInterface) return;
  sBluetoothA2dpInterface->set_audio_track_gain((float)gain);
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initNative", "()V", (void*)initNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectA2dpNative", "([B)Z", (void*)connectA2dpNative},
    {"disconnectA2dpNative", "([B)Z", (void*)disconnectA2dpNative},
    {"informAudioFocusStateNative", "(I)V", (void*)informAudioFocusStateNative},
    {"informAudioTrackGainNative", "(F)V", (void*)informAudioTrackGainNative},
};

int register_com_android_bluetooth_a2dp_sink(JNIEnv* env) {
  return jniRegisterNativeMethods(
      env, "com/android/bluetooth/a2dpsink/A2dpSinkStateMachine", sMethods,
      NELEM(sMethods));
}
}
