/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "BluetoothHidDevServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_hd.h"
#include "utils/Log.h"

#include <string.h>

namespace android {

static jmethodID method_onApplicationStateChanged;
static jmethodID method_onConnectStateChanged;
static jmethodID method_onGetReport;
static jmethodID method_onSetReport;
static jmethodID method_onSetProtocol;
static jmethodID method_onIntrData;
static jmethodID method_onVirtualCableUnplug;

static const bthd_interface_t* sHiddIf = NULL;
static jobject mCallbacksObj = NULL;

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

static void application_state_callback(RawAddress* bd_addr,
                                       bthd_application_state_t state) {
  jboolean registered = JNI_FALSE;

  CallbackEnv sCallbackEnv(__func__);

  if (state == BTHD_APP_STATE_REGISTERED) {
    registered = JNI_TRUE;
  }

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), NULL);

  if (bd_addr) {
    addr.reset(marshall_bda(bd_addr));
    if (!addr.get()) {
      ALOGE("%s: failed to allocate storage for bt_addr", __FUNCTION__);
      return;
    }
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onApplicationStateChanged,
                               addr.get(), registered);
}

static void connection_state_callback(RawAddress* bd_addr,
                                      bthd_connection_state_t state) {
  CallbackEnv sCallbackEnv(__func__);

  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    ALOGE("%s: failed to allocate storage for bt_addr", __FUNCTION__);
    return;
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged,
                               addr.get(), (jint)state);
}

static void get_report_callback(uint8_t type, uint8_t id,
                                uint16_t buffer_size) {
  CallbackEnv sCallbackEnv(__func__);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetReport, type, id,
                               buffer_size);
}

static void set_report_callback(uint8_t type, uint8_t id, uint16_t len,
                                uint8_t* p_data) {
  CallbackEnv sCallbackEnv(__func__);

  ScopedLocalRef<jbyteArray> data(sCallbackEnv.get(),
                                  sCallbackEnv->NewByteArray(len));
  if (!data.get()) {
    ALOGE("%s: failed to allocate storage for report data", __FUNCTION__);
    return;
  }
  sCallbackEnv->SetByteArrayRegion(data.get(), 0, len, (jbyte*)p_data);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetReport, (jbyte)type,
                               (jbyte)id, data.get());
}

static void set_protocol_callback(uint8_t protocol) {
  CallbackEnv sCallbackEnv(__func__);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetProtocol, protocol);
}

static void intr_data_callback(uint8_t report_id, uint16_t len,
                               uint8_t* p_data) {
  CallbackEnv sCallbackEnv(__func__);

  ScopedLocalRef<jbyteArray> data(sCallbackEnv.get(),
                                  sCallbackEnv->NewByteArray(len));
  if (!data.get()) {
    ALOGE("%s: failed to allocate storage for report data", __FUNCTION__);
    return;
  }
  sCallbackEnv->SetByteArrayRegion(data.get(), 0, len, (jbyte*)p_data);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onIntrData,
                               (jbyte)report_id, data.get());
}

static void vc_unplug_callback(void) {
  CallbackEnv sCallbackEnv(__func__);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVirtualCableUnplug);
}

static bthd_callbacks_t sHiddCb = {
    sizeof(sHiddCb),

    application_state_callback,
    connection_state_callback,
    get_report_callback,
    set_report_callback,
    set_protocol_callback,
    intr_data_callback,
    vc_unplug_callback,
};

static void classInitNative(JNIEnv* env, jclass clazz) {
  ALOGV("%s: done", __FUNCTION__);

  method_onApplicationStateChanged =
      env->GetMethodID(clazz, "onApplicationStateChanged", "([BZ)V");
  method_onConnectStateChanged =
      env->GetMethodID(clazz, "onConnectStateChanged", "([BI)V");
  method_onGetReport = env->GetMethodID(clazz, "onGetReport", "(BBS)V");
  method_onSetReport = env->GetMethodID(clazz, "onSetReport", "(BB[B)V");
  method_onSetProtocol = env->GetMethodID(clazz, "onSetProtocol", "(B)V");
  method_onIntrData = env->GetMethodID(clazz, "onIntrData", "(B[B)V");
  method_onVirtualCableUnplug =
      env->GetMethodID(clazz, "onVirtualCableUnplug", "()V");
}

static void initNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btif;
  bt_status_t status;

  ALOGV("%s enter", __FUNCTION__);

  if ((btif = getBluetoothInterface()) == NULL) {
    ALOGE("Cannot obtain BT interface");
    return;
  }

  if (sHiddIf != NULL) {
    ALOGW("Cleaning up interface");
    sHiddIf->cleanup();
    sHiddIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  if ((sHiddIf = (bthd_interface_t*)btif->get_profile_interface(
           BT_PROFILE_HIDDEV_ID)) == NULL) {
    ALOGE("Cannot obtain interface");
    return;
  }

  if ((status = sHiddIf->init(&sHiddCb)) != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize interface (%d)", status);
    sHiddIf = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);

  ALOGV("%s done", __FUNCTION__);
}

static void cleanupNative(JNIEnv* env, jobject object) {
  ALOGV("%s enter", __FUNCTION__);

  if (sHiddIf != NULL) {
    ALOGI("Cleaning up interface");
    sHiddIf->cleanup();
    sHiddIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGI("Cleaning up callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  ALOGV("%s done", __FUNCTION__);
}

static void fill_qos(JNIEnv* env, jintArray in, bthd_qos_param_t* out) {
  // set default values
  out->service_type = 0x01;  // best effort
  out->token_rate = out->token_bucket_size = out->peak_bandwidth =
      0;                                                    // don't care
  out->access_latency = out->delay_variation = 0xffffffff;  // don't care

  if (in == NULL) return;

  jsize len = env->GetArrayLength(in);

  if (len != 6) return;

  uint32_t* buf = (uint32_t*)calloc(len, sizeof(uint32_t));

  if (buf == NULL) return;

  env->GetIntArrayRegion(in, 0, len, (jint*)buf);

  out->service_type = (uint8_t)buf[0];
  out->token_rate = buf[1];
  out->token_bucket_size = buf[2];
  out->peak_bandwidth = buf[3];
  out->access_latency = buf[4];
  out->delay_variation = buf[5];

  free(buf);
}

static jboolean registerAppNative(JNIEnv* env, jobject thiz, jstring name,
                                  jstring description, jstring provider,
                                  jbyte subclass, jbyteArray descriptors,
                                  jintArray p_in_qos, jintArray p_out_qos) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;
  bthd_app_param_t app_param;
  bthd_qos_param_t in_qos;
  bthd_qos_param_t out_qos;
  jsize size;
  uint8_t* data;

  size = env->GetArrayLength(descriptors);
  data = (uint8_t*)malloc(size);

  if (data != NULL) {
    env->GetByteArrayRegion(descriptors, 0, size, (jbyte*)data);

    app_param.name = env->GetStringUTFChars(name, NULL);
    app_param.description = env->GetStringUTFChars(description, NULL);
    app_param.provider = env->GetStringUTFChars(provider, NULL);
    app_param.subclass = subclass;
    app_param.desc_list = data;
    app_param.desc_list_len = size;

    fill_qos(env, p_in_qos, &in_qos);
    fill_qos(env, p_out_qos, &out_qos);

    bt_status_t ret = sHiddIf->register_app(&app_param, &in_qos, &out_qos);

    ALOGV("%s: register_app() returned %d", __FUNCTION__, ret);

    if (ret == BT_STATUS_SUCCESS) {
      result = JNI_TRUE;
    }

    env->ReleaseStringUTFChars(name, app_param.name);
    env->ReleaseStringUTFChars(description, app_param.description);
    env->ReleaseStringUTFChars(provider, app_param.provider);

    free(data);
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean unregisterAppNative(JNIEnv* env, jobject thiz) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;

  bt_status_t ret = sHiddIf->unregister_app();

  ALOGV("%s: unregister_app() returned %d", __FUNCTION__, ret);

  if (ret == BT_STATUS_SUCCESS) {
    result = JNI_TRUE;
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean sendReportNative(JNIEnv* env, jobject thiz, jint id,
                                 jbyteArray data) {
  jboolean result = JNI_FALSE;
  jsize size;
  uint8_t* buf;

  size = env->GetArrayLength(data);
  buf = (uint8_t*)malloc(size);

  if (buf != NULL) {
    env->GetByteArrayRegion(data, 0, size, (jbyte*)buf);

    bt_status_t ret =
        sHiddIf->send_report(BTHD_REPORT_TYPE_INTRDATA, id, size, buf);

    if (ret == BT_STATUS_SUCCESS) {
      result = JNI_TRUE;
    }

    free(buf);
  }

  return result;
}

static jboolean replyReportNative(JNIEnv* env, jobject thiz, jbyte type,
                                  jbyte id, jbyteArray data) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;
  jsize size;
  uint8_t* buf;

  size = env->GetArrayLength(data);
  buf = (uint8_t*)malloc(size);

  if (buf != NULL) {
    int report_type = (type & 0x03);
    env->GetByteArrayRegion(data, 0, size, (jbyte*)buf);

    bt_status_t ret =
        sHiddIf->send_report((bthd_report_type_t)report_type, id, size, buf);

    ALOGV("%s: send_report() returned %d", __FUNCTION__, ret);

    if (ret == BT_STATUS_SUCCESS) {
      result = JNI_TRUE;
    }

    free(buf);
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean reportErrorNative(JNIEnv* env, jobject thiz, jbyte error) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;

  bt_status_t ret = sHiddIf->report_error(error);

  ALOGV("%s: report_error() returned %d", __FUNCTION__, ret);

  if (ret == BT_STATUS_SUCCESS) {
    result = JNI_TRUE;
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean unplugNative(JNIEnv* env, jobject thiz) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;

  bt_status_t ret = sHiddIf->virtual_cable_unplug();

  ALOGV("%s: virtual_cable_unplug() returned %d", __FUNCTION__, ret);

  if (ret == BT_STATUS_SUCCESS) {
    result = JNI_TRUE;
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean connectNative(JNIEnv* env, jobject thiz, jbyteArray address) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    ALOGE("Bluetooth device address null");
    return JNI_FALSE;
  }

  bt_status_t ret = sHiddIf->connect((RawAddress*)addr);

  ALOGV("%s: connect() returned %d", __FUNCTION__, ret);

  if (ret == BT_STATUS_SUCCESS) {
    result = JNI_TRUE;
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static jboolean disconnectNative(JNIEnv* env, jobject thiz) {
  ALOGV("%s enter", __FUNCTION__);

  jboolean result = JNI_FALSE;

  bt_status_t ret = sHiddIf->disconnect();

  ALOGV("%s: disconnect() returned %d", __FUNCTION__, ret);

  if (ret == BT_STATUS_SUCCESS) {
    result = JNI_TRUE;
  }

  ALOGV("%s done (%d)", __FUNCTION__, result);

  return result;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initNative", "()V", (void*)initNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"registerAppNative",
     "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;B[B[I[I)Z",
     (void*)registerAppNative},
    {"unregisterAppNative", "()Z", (void*)unregisterAppNative},
    {"sendReportNative", "(I[B)Z", (void*)sendReportNative},
    {"replyReportNative", "(BB[B)Z", (void*)replyReportNative},
    {"reportErrorNative", "(B)Z", (void*)reportErrorNative},
    {"unplugNative", "()Z", (void*)unplugNative},
    {"connectNative", "([B)Z", (void*)connectNative},
    {"disconnectNative", "()Z", (void*)disconnectNative},
};

int register_com_android_bluetooth_hidd(JNIEnv* env) {
  return jniRegisterNativeMethods(env,
                                  "com/android/bluetooth/hid/HidDevService",
                                  sMethods, NELEM(sMethods));
}
}
