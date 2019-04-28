/*
 * Copyright (c) 2014 The Android Open Source Project
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

#define LOG_TAG "BluetoothHeadsetClientServiceJni"
#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_hf_client.h"
#include "utils/Log.h"

namespace android {

static bthf_client_interface_t* sBluetoothHfpClientInterface = NULL;
static jobject mCallbacksObj = NULL;

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onVrStateChanged;
static jmethodID method_onNetworkState;
static jmethodID method_onNetworkRoaming;
static jmethodID method_onNetworkSignal;
static jmethodID method_onBatteryLevel;
static jmethodID method_onCurrentOperator;
static jmethodID method_onCall;
static jmethodID method_onCallSetup;
static jmethodID method_onCallHeld;
static jmethodID method_onRespAndHold;
static jmethodID method_onClip;
static jmethodID method_onCallWaiting;
static jmethodID method_onCurrentCalls;
static jmethodID method_onVolumeChange;
static jmethodID method_onCmdResult;
static jmethodID method_onSubscriberInfo;
static jmethodID method_onInBandRing;
static jmethodID method_onLastVoiceTagNumber;
static jmethodID method_onRingIndication;

static jbyteArray marshall_bda(const RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return NULL;

  jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(RawAddress));
  if (!addr) {
    ALOGE("Fail to new jbyteArray bd addr");
    return NULL;
  }
  sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  return addr;
}

static void connection_state_cb(const RawAddress* bd_addr,
                                bthf_client_connection_state_t state,
                                unsigned int peer_feat,
                                unsigned int chld_feat) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ALOGD("%s: state %d peer_feat %d chld_feat %d", __func__, state, peer_feat, chld_feat);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                               (jint)state, (jint)peer_feat, (jint)chld_feat,
                               addr.get());
}

static void audio_state_cb(const RawAddress* bd_addr,
                           bthf_client_audio_state_t state) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                               (jint)state, addr.get());
}

static void vr_cmd_cb(const RawAddress* bd_addr, bthf_client_vr_state_t state) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVrStateChanged,
                               (jint)state);
}

static void network_state_cb(const RawAddress* bd_addr,
                             bthf_client_network_state_t state) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkState,
                               (jint)state, addr.get());
}

static void network_roaming_cb(const RawAddress* bd_addr,
                               bthf_client_service_type_t type) {
  CallbackEnv sCallbackEnv(__func__);

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkRoaming,
                               (jint)type, addr.get());
}

static void network_signal_cb(const RawAddress* bd_addr, int signal) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNetworkSignal,
                               (jint)signal, addr.get());
}

static void battery_level_cb(const RawAddress* bd_addr, int level) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onBatteryLevel,
                               (jint)level, addr.get());
}

static void current_operator_cb(const RawAddress* bd_addr, const char* name) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ScopedLocalRef<jstring> js_name(sCallbackEnv.get(),
                                  sCallbackEnv->NewStringUTF(name));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCurrentOperator,
                               js_name.get(), addr.get());
}

static void call_cb(const RawAddress* bd_addr, bthf_client_call_t call) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCall, (jint)call,
                               addr.get());
}

static void callsetup_cb(const RawAddress* bd_addr,
                         bthf_client_callsetup_t callsetup) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ALOGD("callsetup_cb bdaddr %02x:%02x:%02x:%02x:%02x:%02x",
        bd_addr->address[0], bd_addr->address[1], bd_addr->address[2],
        bd_addr->address[3], bd_addr->address[4], bd_addr->address[5]);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallSetup,
                               (jint)callsetup, addr.get());
}

static void callheld_cb(const RawAddress* bd_addr,
                        bthf_client_callheld_t callheld) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallHeld, (jint)callheld,
                               addr.get());
}

static void resp_and_hold_cb(const RawAddress* bd_addr,
                             bthf_client_resp_and_hold_t resp_and_hold) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRespAndHold,
                               (jint)resp_and_hold, addr.get());
}

static void clip_cb(const RawAddress* bd_addr, const char* number) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClip, js_number.get(),
                               addr.get());
}

static void call_waiting_cb(const RawAddress* bd_addr, const char* number) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallWaiting,
                               js_number.get(), addr.get());
}

static void current_calls_cb(const RawAddress* bd_addr, int index,
                             bthf_client_call_direction_t dir,
                             bthf_client_call_state_t state,
                             bthf_client_call_mpty_type_t mpty,
                             const char* number) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCurrentCalls, index, dir,
                               state, mpty, js_number.get(), addr.get());
}

static void volume_change_cb(const RawAddress* bd_addr,
                             bthf_client_volume_type_t type, int volume) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVolumeChange, (jint)type,
                               (jint)volume, addr.get());
}

static void cmd_complete_cb(const RawAddress* bd_addr,
                            bthf_client_cmd_complete_t type, int cme) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCmdResult, (jint)type,
                               (jint)cme, addr.get());
}

static void subscriber_info_cb(const RawAddress* bd_addr, const char* name,
                               bthf_client_subscriber_service_type_t type) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  ScopedLocalRef<jstring> js_name(sCallbackEnv.get(),
                                  sCallbackEnv->NewStringUTF(name));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSubscriberInfo,
                               js_name.get(), (jint)type, addr.get());
}

static void in_band_ring_cb(const RawAddress* bd_addr,
                            bthf_client_in_band_ring_state_t in_band) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onInBandRing,
                               (jint)in_band, addr.get());
}

static void last_voice_tag_number_cb(const RawAddress* bd_addr,
                                     const char* number) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onLastVoiceTagNumber,
                               js_number.get(), addr.get());
}

static void ring_indication_cb(const RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRingIndication,
                               addr.get());
}

static bthf_client_callbacks_t sBluetoothHfpClientCallbacks = {
    sizeof(sBluetoothHfpClientCallbacks),
    connection_state_cb,
    audio_state_cb,
    vr_cmd_cb,
    network_state_cb,
    network_roaming_cb,
    network_signal_cb,
    battery_level_cb,
    current_operator_cb,
    call_cb,
    callsetup_cb,
    callheld_cb,
    resp_and_hold_cb,
    clip_cb,
    call_waiting_cb,
    current_calls_cb,
    volume_change_cb,
    cmd_complete_cb,
    subscriber_info_cb,
    in_band_ring_cb,
    last_voice_tag_number_cb,
    ring_indication_cb,
};

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_onConnectionStateChanged =
      env->GetMethodID(clazz, "onConnectionStateChanged", "(III[B)V");
  method_onAudioStateChanged =
      env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");
  method_onVrStateChanged = env->GetMethodID(clazz, "onVrStateChanged", "(I)V");
  method_onNetworkState = env->GetMethodID(clazz, "onNetworkState", "(I[B)V");
  method_onNetworkRoaming = env->GetMethodID(clazz, "onNetworkRoaming", "(I[B)V");
  method_onNetworkSignal = env->GetMethodID(clazz, "onNetworkSignal", "(I[B)V");
  method_onBatteryLevel = env->GetMethodID(clazz, "onBatteryLevel", "(I[B)V");
  method_onCurrentOperator =
      env->GetMethodID(clazz, "onCurrentOperator", "(Ljava/lang/String;[B)V");
  method_onCall = env->GetMethodID(clazz, "onCall", "(I[B)V");
  method_onCallSetup = env->GetMethodID(clazz, "onCallSetup", "(I[B)V");
  method_onCallHeld = env->GetMethodID(clazz, "onCallHeld", "(I[B)V");
  method_onRespAndHold = env->GetMethodID(clazz, "onRespAndHold", "(I[B)V");
  method_onClip = env->GetMethodID(clazz, "onClip", "(Ljava/lang/String;[B)V");
  method_onCallWaiting =
      env->GetMethodID(clazz, "onCallWaiting", "(Ljava/lang/String;[B)V");
  method_onCurrentCalls =
      env->GetMethodID(clazz, "onCurrentCalls", "(IIIILjava/lang/String;[B)V");
  method_onVolumeChange = env->GetMethodID(clazz, "onVolumeChange", "(II[B)V");
  method_onCmdResult = env->GetMethodID(clazz, "onCmdResult", "(II[B)V");
  method_onSubscriberInfo =
      env->GetMethodID(clazz, "onSubscriberInfo", "(Ljava/lang/String;I[B)V");
  method_onInBandRing = env->GetMethodID(clazz, "onInBandRing", "(I[B)V");
  method_onLastVoiceTagNumber =
      env->GetMethodID(clazz, "onLastVoiceTagNumber", "(Ljava/lang/String;[B)V");
  method_onRingIndication = env->GetMethodID(clazz, "onRingIndication", "([B)V");

  ALOGI("%s succeeds", __func__);
}

static void initializeNative(JNIEnv* env, jobject object) {
  ALOGD("%s: HfpClient", __func__);
  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpClientInterface != NULL) {
    ALOGW("Cleaning up Bluetooth HFP Client Interface before initializing");
    sBluetoothHfpClientInterface->cleanup();
    sBluetoothHfpClientInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth HFP Client callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothHfpClientInterface =
      (bthf_client_interface_t*)btInf->get_profile_interface(
          BT_PROFILE_HANDSFREE_CLIENT_ID);
  if (sBluetoothHfpClientInterface == NULL) {
    ALOGE("Failed to get Bluetooth HFP Client Interface");
    return;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->init(&sBluetoothHfpClientCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth HFP Client, status: %d", status);
    sBluetoothHfpClientInterface = NULL;
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

  if (sBluetoothHfpClientInterface != NULL) {
    ALOGW("Cleaning up Bluetooth HFP Client Interface...");
    sBluetoothHfpClientInterface->cleanup();
    sBluetoothHfpClientInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth HFP Client callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectNative(JNIEnv* env, jobject object, jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->connect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed AG connection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectNative(JNIEnv* env, jobject object,
                                 jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->disconnect((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed AG disconnection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectAudioNative(JNIEnv* env, jobject object,
                                   jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->connect_audio((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed AG audio connection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectAudioNative(JNIEnv* env, jobject object,
                                      jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->disconnect_audio((const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed AG audio disconnection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean startVoiceRecognitionNative(JNIEnv* env, jobject object,
                                            jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->start_voice_recognition(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to start voice recognition, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean stopVoiceRecognitionNative(JNIEnv* env, jobject object,
                                           jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->stop_voice_recognition(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to stop voice recognition, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setVolumeNative(JNIEnv* env, jobject object, jbyteArray address,
                                jint volume_type, jint volume) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->volume_control(
      (const RawAddress*)addr, (bthf_client_volume_type_t)volume_type, volume);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("FAILED to control volume, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean dialNative(JNIEnv* env, jobject object, jbyteArray address,
                           jstring number_str) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* number = NULL;
  if (number_str != NULL) {
    number = env->GetStringUTFChars(number_str, NULL);
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->dial((const RawAddress*)addr, number);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to dial, status: %d", status);
  }
  if (number != NULL) {
    env->ReleaseStringUTFChars(number_str, number);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean dialMemoryNative(JNIEnv* env, jobject object,
                                 jbyteArray address, jint location) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->dial_memory(
      (const RawAddress*)addr, (int)location);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to dial from memory, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean handleCallActionNative(JNIEnv* env, jobject object,
                                       jbyteArray address, jint action,
                                       jint index) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->handle_call_action(
      (const RawAddress*)addr, (bthf_client_call_action_t)action, (int)index);

  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to enter private mode, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean queryCurrentCallsNative(JNIEnv* env, jobject object,
                                        jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->query_current_calls(
      (const RawAddress*)addr);

  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to query current calls, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean queryCurrentOperatorNameNative(JNIEnv* env, jobject object,
                                               jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->query_current_operator_name(
          (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to query current operator name, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean retrieveSubscriberInfoNative(JNIEnv* env, jobject object,
                                             jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->retrieve_subscriber_info(
      (const RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to retrieve subscriber info, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendDtmfNative(JNIEnv* env, jobject object, jbyteArray address,
                               jbyte code) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpClientInterface->send_dtmf(
      (const RawAddress*)addr, (char)code);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to send DTMF, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean requestLastVoiceTagNumberNative(JNIEnv* env, jobject object,
                                                jbyteArray address) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpClientInterface->request_last_voice_tag_number(
          (const RawAddress*)addr);

  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to request last Voice Tag number, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendATCmdNative(JNIEnv* env, jobject object, jbyteArray address,
                                jint cmd, jint val1, jint val2,
                                jstring arg_str) {
  if (!sBluetoothHfpClientInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* arg = NULL;
  if (arg_str != NULL) {
    arg = env->GetStringUTFChars(arg_str, NULL);
  }

  bt_status_t status = sBluetoothHfpClientInterface->send_at_cmd(
      (const RawAddress*)addr, cmd, val1, val2, arg);

  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to send cmd, status: %d", status);
  }

  if (arg != NULL) {
    env->ReleaseStringUTFChars(arg_str, arg);
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "()V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectNative", "([B)Z", (void*)connectNative},
    {"disconnectNative", "([B)Z", (void*)disconnectNative},
    {"connectAudioNative", "([B)Z", (void*)connectAudioNative},
    {"disconnectAudioNative", "([B)Z", (void*)disconnectAudioNative},
    {"startVoiceRecognitionNative", "([B)Z",
     (void*)startVoiceRecognitionNative},
    {"stopVoiceRecognitionNative", "([B)Z", (void*)stopVoiceRecognitionNative},
    {"setVolumeNative", "([BII)Z", (void*)setVolumeNative},
    {"dialNative", "([BLjava/lang/String;)Z", (void*)dialNative},
    {"dialMemoryNative", "([BI)Z", (void*)dialMemoryNative},
    {"handleCallActionNative", "([BII)Z", (void*)handleCallActionNative},
    {"queryCurrentCallsNative", "([B)Z", (void*)queryCurrentCallsNative},
    {"queryCurrentOperatorNameNative", "([B)Z",
     (void*)queryCurrentOperatorNameNative},
    {"retrieveSubscriberInfoNative", "([B)Z",
     (void*)retrieveSubscriberInfoNative},
    {"sendDtmfNative", "([BB)Z", (void*)sendDtmfNative},
    {"requestLastVoiceTagNumberNative", "([B)Z",
     (void*)requestLastVoiceTagNumberNative},
    {"sendATCmdNative", "([BIIILjava/lang/String;)Z", (void*)sendATCmdNative},
};

int register_com_android_bluetooth_hfpclient(JNIEnv* env) {
  return jniRegisterNativeMethods(
      env, "com/android/bluetooth/hfpclient/NativeInterface",
      sMethods, NELEM(sMethods));
}

} /* namespace android */
