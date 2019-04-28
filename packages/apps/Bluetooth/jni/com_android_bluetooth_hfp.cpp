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

#define LOG_TAG "BluetoothHeadsetServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_hf.h"
#include "utils/Log.h"

#include <string.h>
#include <mutex>
#include <shared_mutex>

namespace android {

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onVrStateChanged;
static jmethodID method_onAnswerCall;
static jmethodID method_onHangupCall;
static jmethodID method_onVolumeChanged;
static jmethodID method_onDialCall;
static jmethodID method_onSendDtmf;
static jmethodID method_onNoiceReductionEnable;
static jmethodID method_onWBS;
static jmethodID method_onAtChld;
static jmethodID method_onAtCnum;
static jmethodID method_onAtCind;
static jmethodID method_onAtCops;
static jmethodID method_onAtClcc;
static jmethodID method_onUnknownAt;
static jmethodID method_onKeyPressed;
static jmethodID method_onAtBind;
static jmethodID method_onAtBiev;

static const bthf_interface_t* sBluetoothHfpInterface = NULL;
static std::shared_timed_mutex interface_mutex;

static jobject mCallbacksObj = NULL;
static std::shared_timed_mutex callbacks_mutex;

static jbyteArray marshall_bda(RawAddress* bd_addr) {
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

static void connection_state_callback(bthf_connection_state_t state,
                                      RawAddress* bd_addr) {
  ALOGI("%s", __func__);

  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  ///M: check mCallbacksObj nullness @{
  if (mCallbacksObj != NULL) {
      sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                   (jint)state, addr.get());
  } else {
      ALOGE("mCallbacksObj is null, cannot callback to onConnectionStateChanged");
  }
  /// @}
}

static void audio_state_callback(bthf_audio_state_t state,
                                 RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged,
                               (jint)state, addr.get());
}

static void voice_recognition_callback(bthf_vr_state_t state,
                                       RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVrStateChanged,
                               (jint)state, addr.get());
}

static void answer_call_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAnswerCall, addr.get());
}

static void hangup_call_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onHangupCall, addr.get());
}

static void volume_control_callback(bthf_volume_type_t type, int volume,
                                    RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVolumeChanged,
                               (jint)type, (jint)volume, addr.get());
}

static void dial_call_callback(char* number, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  ScopedLocalRef<jstring> js_number(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(number));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDialCall,
                               js_number.get(), addr.get());
}

static void dtmf_cmd_callback(char dtmf, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  // TBD dtmf has changed from int to char
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSendDtmf, dtmf,
                               addr.get());
}

static void noice_reduction_callback(bthf_nrec_t nrec, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNoiceReductionEnable,
                               nrec == BTHF_NREC_START, addr.get());
}

static void wbs_callback(bthf_wbs_config_t wbs_config, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (addr.get() == NULL) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onWBS, wbs_config,
                               addr.get());
}

static void at_chld_callback(bthf_chld_type_t chld, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtChld, chld,
                               addr.get());
}

static void at_cnum_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCnum, addr.get());
}

static void at_cind_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCind, addr.get());
}

static void at_cops_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtCops, addr.get());
}

static void at_clcc_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtClcc, addr.get());
}

static void unknown_at_callback(char* at_string, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  ScopedLocalRef<jstring> js_at_string(sCallbackEnv.get(),
                                       sCallbackEnv->NewStringUTF(at_string));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onUnknownAt,
                               js_at_string.get(), addr.get());
}

static void key_pressed_callback(RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for audio state");
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onKeyPressed, addr.get());
}

static void at_bind_callback(char* at_string, RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (addr.get() == NULL) return;

  ScopedLocalRef<jstring> js_at_string(sCallbackEnv.get(),
                                       sCallbackEnv->NewStringUTF(at_string));

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtBind,
                               js_at_string.get(), addr.get());
}

static void at_biev_callback(bthf_hf_ind_type_t ind_id, int ind_value,
                             RawAddress* bd_addr) {
  std::shared_lock<std::shared_timed_mutex> lock(callbacks_mutex);
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid() || mCallbacksObj == NULL) return;

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (addr.get() == NULL) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAtBiev, ind_id,
                               (jint)ind_value, addr.get());
}

static bthf_callbacks_t sBluetoothHfpCallbacks = {
    sizeof(sBluetoothHfpCallbacks),
    connection_state_callback,
    audio_state_callback,
    voice_recognition_callback,
    answer_call_callback,
    hangup_call_callback,
    volume_control_callback,
    dial_call_callback,
    dtmf_cmd_callback,
    noice_reduction_callback,
    wbs_callback,
    at_chld_callback,
    at_cnum_callback,
    at_cind_callback,
    at_cops_callback,
    at_clcc_callback,
    unknown_at_callback,
    at_bind_callback,
    at_biev_callback,
    key_pressed_callback};

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_onConnectionStateChanged =
      env->GetMethodID(clazz, "onConnectionStateChanged", "(I[B)V");
  method_onAudioStateChanged =
      env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");
  method_onVrStateChanged =
      env->GetMethodID(clazz, "onVrStateChanged", "(I[B)V");
  method_onAnswerCall = env->GetMethodID(clazz, "onAnswerCall", "([B)V");
  method_onHangupCall = env->GetMethodID(clazz, "onHangupCall", "([B)V");
  method_onVolumeChanged =
      env->GetMethodID(clazz, "onVolumeChanged", "(II[B)V");
  method_onDialCall =
      env->GetMethodID(clazz, "onDialCall", "(Ljava/lang/String;[B)V");
  method_onSendDtmf = env->GetMethodID(clazz, "onSendDtmf", "(I[B)V");
  method_onNoiceReductionEnable =
      env->GetMethodID(clazz, "onNoiceReductionEnable", "(Z[B)V");
  method_onWBS = env->GetMethodID(clazz, "onWBS", "(I[B)V");
  method_onAtChld = env->GetMethodID(clazz, "onAtChld", "(I[B)V");
  method_onAtCnum = env->GetMethodID(clazz, "onAtCnum", "([B)V");
  method_onAtCind = env->GetMethodID(clazz, "onAtCind", "([B)V");
  method_onAtCops = env->GetMethodID(clazz, "onAtCops", "([B)V");
  method_onAtClcc = env->GetMethodID(clazz, "onAtClcc", "([B)V");
  method_onUnknownAt =
      env->GetMethodID(clazz, "onUnknownAt", "(Ljava/lang/String;[B)V");
  method_onKeyPressed = env->GetMethodID(clazz, "onKeyPressed", "([B)V");
  method_onAtBind =
      env->GetMethodID(clazz, "onATBind", "(Ljava/lang/String;[B)V");
  method_onAtBiev = env->GetMethodID(clazz, "onATBiev", "(II[B)V");

  ALOGI("%s: succeeds", __func__);
}

static void initializeNative(JNIEnv* env, jobject object, jint max_hf_clients,
                             jboolean inband_ringing_support) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpInterface != NULL) {
    ALOGW("Cleaning up Bluetooth Handsfree Interface before initializing...");
    sBluetoothHfpInterface->cleanup();
    sBluetoothHfpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth Handsfree callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothHfpInterface =
      (bthf_interface_t*)btInf->get_profile_interface(BT_PROFILE_HANDSFREE_ID);
  if (sBluetoothHfpInterface == NULL) {
    ALOGE("Failed to get Bluetooth Handsfree Interface");
    return;
  }

  bt_status_t status = sBluetoothHfpInterface->init(
      &sBluetoothHfpCallbacks, max_hf_clients, inband_ringing_support);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth HFP, status: %d", status);
    sBluetoothHfpInterface = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject object) {
  std::unique_lock<std::shared_timed_mutex> interface_lock(interface_mutex);
  std::unique_lock<std::shared_timed_mutex> callbacks_lock(callbacks_mutex);

  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothHfpInterface != NULL) {
    ALOGW("Cleaning up Bluetooth Handsfree Interface...");
    sBluetoothHfpInterface->cleanup();
    sBluetoothHfpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth Handsfree callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean connectHfpNative(JNIEnv* env, jobject object,
                                 jbyteArray address) {
  ALOGI("%s: sBluetoothHfpInterface: %p", __func__, sBluetoothHfpInterface);
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->connect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF connection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectHfpNative(JNIEnv* env, jobject object,
                                    jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->disconnect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF disconnection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectAudioNative(JNIEnv* env, jobject object,
                                   jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->connect_audio((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF audio connection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectAudioNative(JNIEnv* env, jobject object,
                                      jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpInterface->disconnect_audio((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF audio disconnection, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean startVoiceRecognitionNative(JNIEnv* env, jobject object,
                                            jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpInterface->start_voice_recognition((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to start voice recognition, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean stopVoiceRecognitionNative(JNIEnv* env, jobject object,
                                           jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status =
      sBluetoothHfpInterface->stop_voice_recognition((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to stop voice recognition, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setVolumeNative(JNIEnv* env, jobject object, jint volume_type,
                                jint volume, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->volume_control(
      (bthf_volume_type_t)volume_type, volume, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("FAILED to control volume, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean notifyDeviceStatusNative(JNIEnv* env, jobject object,
                                         jint network_state, jint service_type,
                                         jint signal, jint battery_charge) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  bt_status_t status = sBluetoothHfpInterface->device_status_notification(
      (bthf_network_state_t)network_state, (bthf_service_type_t)service_type,
      signal, battery_charge);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("FAILED to notify device status, status: %d", status);
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean copsResponseNative(JNIEnv* env, jobject object,
                                   jstring operator_str, jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* operator_name = env->GetStringUTFChars(operator_str, NULL);

  bt_status_t status =
      sBluetoothHfpInterface->cops_response(operator_name, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed sending cops response, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  env->ReleaseStringUTFChars(operator_str, operator_name);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean cindResponseNative(JNIEnv* env, jobject object, jint service,
                                   jint num_active, jint num_held,
                                   jint call_state, jint signal, jint roam,
                                   jint battery_charge, jbyteArray address) {
  ALOGI("%s: sBluetoothHfpInterface: %p", __func__, sBluetoothHfpInterface);

  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->cind_response(
      service, num_active, num_held, (bthf_call_state_t)call_state, signal,
      roam, battery_charge, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed cind_response, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean bindResponseNative(JNIEnv* env, jobject object, jint ind_id,
                                   jboolean ind_status, jbyteArray address) {
  ALOGI("%s: sBluetoothHfpInterface: %p", __func__, sBluetoothHfpInterface);

  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->bind_response(
      (bthf_hf_ind_type_t)ind_id,
      ind_status ? BTHF_HF_IND_ENABLED : BTHF_HF_IND_DISABLED,
      (RawAddress*)addr);

  if (status != BT_STATUS_SUCCESS)
    ALOGE("%s: Failed bind_response, status: %d", __func__, status);

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS ? JNI_TRUE : JNI_FALSE);
}

static jboolean atResponseStringNative(JNIEnv* env, jobject object,
                                       jstring response_str,
                                       jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* response = env->GetStringUTFChars(response_str, NULL);

  bt_status_t status = sBluetoothHfpInterface->formatted_at_response(
      response, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed formatted AT response, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  env->ReleaseStringUTFChars(response_str, response);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean atResponseCodeNative(JNIEnv* env, jobject object,
                                     jint response_code, jint cmee_code,
                                     jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->at_response(
      (bthf_at_response_t)response_code, cmee_code, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed AT response, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean clccResponseNative(JNIEnv* env, jobject object, jint index,
                                   jint dir, jint callStatus, jint mode,
                                   jboolean mpty, jstring number_str, jint type,
                                   jbyteArray address) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  const char* number = NULL;
  if (number_str) number = env->GetStringUTFChars(number_str, NULL);

  bt_status_t status = sBluetoothHfpInterface->clcc_response(
      index, (bthf_call_direction_t)dir, (bthf_call_state_t)callStatus,
      (bthf_call_mode_t)mode,
      mpty ? BTHF_CALL_MPTY_TYPE_MULTI : BTHF_CALL_MPTY_TYPE_SINGLE, number,
      (bthf_call_addrtype_t)type, (RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed sending CLCC response, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  if (number) env->ReleaseStringUTFChars(number_str, number);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean phoneStateChangeNative(JNIEnv* env, jobject object,
                                       jint num_active, jint num_held,
                                       jint call_state, jstring number_str,
                                       jint type) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  const char* number = env->GetStringUTFChars(number_str, NULL);

  bt_status_t status = sBluetoothHfpInterface->phone_state_change(
      num_active, num_held, (bthf_call_state_t)call_state, number,
      (bthf_call_addrtype_t)type);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed report phone state change, status: %d", status);
  }
  env->ReleaseStringUTFChars(number_str, number);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean configureWBSNative(JNIEnv* env, jobject object,
                                   jbyteArray address, jint codec_config) {
  std::shared_lock<std::shared_timed_mutex> lock(interface_mutex);
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothHfpInterface->configure_wbs(
      (RawAddress*)addr, (bthf_wbs_config_t)codec_config);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF WBS codec config, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setScoAllowedNative(JNIEnv* env, jobject object,
                                    jboolean value) {
  if (!sBluetoothHfpInterface) return JNI_FALSE;

  bt_status_t status =
      sBluetoothHfpInterface->set_sco_allowed(value == JNI_TRUE);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed HF set sco allowed, status: %d", status);
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "(IZ)V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectHfpNative", "([B)Z", (void*)connectHfpNative},
    {"disconnectHfpNative", "([B)Z", (void*)disconnectHfpNative},
    {"connectAudioNative", "([B)Z", (void*)connectAudioNative},
    {"disconnectAudioNative", "([B)Z", (void*)disconnectAudioNative},
    {"startVoiceRecognitionNative", "([B)Z",
     (void*)startVoiceRecognitionNative},
    {"stopVoiceRecognitionNative", "([B)Z", (void*)stopVoiceRecognitionNative},
    {"setVolumeNative", "(II[B)Z", (void*)setVolumeNative},
    {"notifyDeviceStatusNative", "(IIII)Z", (void*)notifyDeviceStatusNative},
    {"copsResponseNative", "(Ljava/lang/String;[B)Z",
     (void*)copsResponseNative},
    {"cindResponseNative", "(IIIIIII[B)Z", (void*)cindResponseNative},
    {"bindResponseNative", "(IZ[B)Z", (void*)bindResponseNative},
    {"atResponseStringNative", "(Ljava/lang/String;[B)Z",
     (void*)atResponseStringNative},
    {"atResponseCodeNative", "(II[B)Z", (void*)atResponseCodeNative},
    {"clccResponseNative", "(IIIIZLjava/lang/String;I[B)Z",
     (void*)clccResponseNative},
    {"phoneStateChangeNative", "(IIILjava/lang/String;I)Z",
     (void*)phoneStateChangeNative},
    {"configureWBSNative", "([BI)Z", (void*)configureWBSNative},
    {"setScoAllowedNative", "(Z)Z", (void*)setScoAllowedNative},
};

int register_com_android_bluetooth_hfp(JNIEnv* env) {
  return jniRegisterNativeMethods(
      env, "com/android/bluetooth/hfp/HeadsetStateMachine", sMethods,
      NELEM(sMethods));
}

} /* namespace android */
