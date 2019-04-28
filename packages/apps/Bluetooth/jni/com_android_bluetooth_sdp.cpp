/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "BluetoothSdpJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_sdp.h"
#include "utils/Log.h"

#include <string.h>

static const uint8_t UUID_OBEX_OBJECT_PUSH[] = {
    0x00, 0x00, 0x11, 0x05, 0x00, 0x00, 0x10, 0x00,
    0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB};
static const uint8_t UUID_PBAP_PSE[] = {0x00, 0x00, 0x11, 0x2F, 0x00, 0x00,
                                        0x10, 0x00, 0x80, 0x00, 0x00, 0x80,
                                        0x5F, 0x9B, 0x34, 0xFB};
static const uint8_t UUID_MAP_MAS[] = {0x00, 0x00, 0x11, 0x32, 0x00, 0x00,
                                       0x10, 0x00, 0x80, 0x00, 0x00, 0x80,
                                       0x5F, 0x9B, 0x34, 0xFB};
static const uint8_t UUID_MAP_MNS[] = {0x00, 0x00, 0x11, 0x33, 0x00, 0x00,
                                       0x10, 0x00, 0x80, 0x00, 0x00, 0x80,
                                       0x5F, 0x9B, 0x34, 0xFB};
static const uint8_t UUID_SAP[] = {0x00, 0x00, 0x11, 0x2D, 0x00, 0x00,
                                   0x10, 0x00, 0x80, 0x00, 0x00, 0x80,
                                   0x5F, 0x9B, 0x34, 0xFB};
// TODO:
// Both the fact that the UUIDs are declared in multiple places, plus the fact
// that there is a mess of UUID comparison and shortening methods will have to
// be fixed.
// The btcore->uuid module should be used for all instances.

#define UUID_MAX_LENGTH 16
#define IS_UUID(u1, u2) !memcmp(u1, u2, UUID_MAX_LENGTH)

namespace android {
static jmethodID method_sdpRecordFoundCallback;
static jmethodID method_sdpMasRecordFoundCallback;
static jmethodID method_sdpMnsRecordFoundCallback;
static jmethodID method_sdpPseRecordFoundCallback;
static jmethodID method_sdpOppOpsRecordFoundCallback;
static jmethodID method_sdpSapsRecordFoundCallback;

static const btsdp_interface_t* sBluetoothSdpInterface = NULL;

static void sdp_search_callback(bt_status_t status, RawAddress* bd_addr,
                                uint8_t* uuid_in, int record_size,
                                bluetooth_sdp_record* record);

btsdp_callbacks_t sBluetoothSdpCallbacks = {sizeof(sBluetoothSdpCallbacks),
                                            sdp_search_callback};

static jobject sCallbacksObj = NULL;

static void initializeNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();

  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }
  if (sBluetoothSdpInterface != NULL) {
    ALOGW("Cleaning up Bluetooth SDP Interface before initializing...");
    sBluetoothSdpInterface->deinit();
    sBluetoothSdpInterface = NULL;
  }

  sBluetoothSdpInterface = (btsdp_interface_t*)btInf->get_profile_interface(
      BT_PROFILE_SDP_CLIENT_ID);
  if (sBluetoothSdpInterface == NULL) {
    ALOGE("Error getting SDP client interface");
  } else {
    sBluetoothSdpInterface->init(&sBluetoothSdpCallbacks);
  }

  sCallbacksObj = env->NewGlobalRef(object);
}

static void classInitNative(JNIEnv* env, jclass clazz) {
  /* generic SDP record (raw data)*/
  method_sdpRecordFoundCallback =
      env->GetMethodID(clazz, "sdpRecordFoundCallback", "(I[B[BI[B)V");

  /* MAS SDP record*/
  method_sdpMasRecordFoundCallback = env->GetMethodID(
      clazz, "sdpMasRecordFoundCallback", "(I[B[BIIIIIILjava/lang/String;Z)V");
  /* MNS SDP record*/
  method_sdpMnsRecordFoundCallback = env->GetMethodID(
      clazz, "sdpMnsRecordFoundCallback", "(I[B[BIIIILjava/lang/String;Z)V");
  /* PBAP PSE record */
  method_sdpPseRecordFoundCallback = env->GetMethodID(
      clazz, "sdpPseRecordFoundCallback", "(I[B[BIIIIILjava/lang/String;Z)V");
  /* OPP Server record */
  method_sdpOppOpsRecordFoundCallback =
      env->GetMethodID(clazz, "sdpOppOpsRecordFoundCallback",
                       "(I[B[BIIILjava/lang/String;[BZ)V");
  /* SAP Server record */
  method_sdpSapsRecordFoundCallback = env->GetMethodID(
      clazz, "sdpSapsRecordFoundCallback", "(I[B[BIILjava/lang/String;Z)V");
}

static jboolean sdpSearchNative(JNIEnv* env, jobject obj, jbyteArray address,
                                jbyteArray uuidObj) {
  ALOGD("%s", __func__);

  if (!sBluetoothSdpInterface) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (addr == NULL) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  jbyte* uuid = env->GetByteArrayElements(uuidObj, NULL);
  if (!uuid) {
    ALOGE("failed to get uuid");
    env->ReleaseByteArrayElements(address, addr, 0);
    return JNI_FALSE;
  }
  ALOGD("%s UUID %.*s", __func__, 16, (uint8_t*)uuid);

  int ret = sBluetoothSdpInterface->sdp_search((RawAddress*)addr,
                                               (const uint8_t*)uuid);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Search initialization failed: %d", ret);
  }

  if (addr) env->ReleaseByteArrayElements(address, addr, 0);
  if (uuid) env->ReleaseByteArrayElements(uuidObj, uuid, 0);
  return (ret == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static void sdp_search_callback(bt_status_t status, RawAddress* bd_addr,
                                uint8_t* uuid_in, int count,
                                bluetooth_sdp_record* records) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) return;

  ScopedLocalRef<jbyteArray> uuid(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(bt_uuid_t)));
  if (!uuid.get()) return;

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetByteArrayRegion(uuid.get(), 0, sizeof(bt_uuid_t),
                                   (jbyte*)uuid_in);

  ALOGD("%s: Status is: %d, Record count: %d", __func__, status, count);

  // Ensure we run the loop at least once, to also signal errors if they occur
  for (int i = 0; i < count || i == 0; i++) {
    bool more_results = (i < (count - 1)) ? true : false;
    bluetooth_sdp_record* record = &records[i];
    ScopedLocalRef<jstring> service_name(sCallbackEnv.get(), NULL);
    if (record->hdr.service_name_length > 0) {
      ALOGD("%s, ServiceName:  %s", __func__, record->mas.hdr.service_name);
      service_name.reset(
          (jstring)sCallbackEnv->NewStringUTF(record->mas.hdr.service_name));
    }

    /* call the right callback according to the uuid*/
    if (IS_UUID(UUID_MAP_MAS, uuid_in)) {
      sCallbackEnv->CallVoidMethod(
          sCallbacksObj, method_sdpMasRecordFoundCallback, (jint)status,
          addr.get(), uuid.get(), (jint)record->mas.mas_instance_id,
          (jint)record->mas.hdr.l2cap_psm,
          (jint)record->mas.hdr.rfcomm_channel_number,
          (jint)record->mas.hdr.profile_version,
          (jint)record->mas.supported_features,
          (jint)record->mas.supported_message_types, service_name.get(),
          more_results);

    } else if (IS_UUID(UUID_MAP_MNS, uuid_in)) {
      sCallbackEnv->CallVoidMethod(
          sCallbacksObj, method_sdpMnsRecordFoundCallback, (jint)status,
          addr.get(), uuid.get(), (jint)record->mns.hdr.l2cap_psm,
          (jint)record->mns.hdr.rfcomm_channel_number,
          (jint)record->mns.hdr.profile_version,
          (jint)record->mns.supported_features, service_name.get(),
          more_results);

    } else if (IS_UUID(UUID_PBAP_PSE, uuid_in)) {
      sCallbackEnv->CallVoidMethod(
          sCallbacksObj, method_sdpPseRecordFoundCallback, (jint)status,
          addr.get(), uuid.get(), (jint)record->pse.hdr.l2cap_psm,
          (jint)record->pse.hdr.rfcomm_channel_number,
          (jint)record->pse.hdr.profile_version,
          (jint)record->pse.supported_features,
          (jint)record->pse.supported_repositories, service_name.get(),
          more_results);

    } else if (IS_UUID(UUID_OBEX_OBJECT_PUSH, uuid_in)) {
      jint formats_list_size = record->ops.supported_formats_list_len;
      ScopedLocalRef<jbyteArray> formats_list(
          sCallbackEnv.get(), sCallbackEnv->NewByteArray(formats_list_size));
      if (!formats_list.get()) return;
      sCallbackEnv->SetByteArrayRegion(
          formats_list.get(), 0, formats_list_size,
          (jbyte*)record->ops.supported_formats_list);

      sCallbackEnv->CallVoidMethod(
          sCallbacksObj, method_sdpOppOpsRecordFoundCallback, (jint)status,
          addr.get(), uuid.get(), (jint)record->ops.hdr.l2cap_psm,
          (jint)record->ops.hdr.rfcomm_channel_number,
          (jint)record->ops.hdr.profile_version, service_name.get(),
          formats_list.get(), more_results);

    } else if (IS_UUID(UUID_SAP, uuid_in)) {
      sCallbackEnv->CallVoidMethod(
          sCallbacksObj, method_sdpSapsRecordFoundCallback, (jint)status,
          addr.get(), uuid.get(), (jint)record->mas.hdr.rfcomm_channel_number,
          (jint)record->mas.hdr.profile_version, service_name.get(),
          more_results);
    } else {
      // we don't have a wrapper for this uuid, send as raw data
      jint record_data_size = record->hdr.user1_ptr_len;
      ScopedLocalRef<jbyteArray> record_data(
          sCallbackEnv.get(), sCallbackEnv->NewByteArray(record_data_size));
      if (!record_data.get()) return;

      sCallbackEnv->SetByteArrayRegion(record_data.get(), 0, record_data_size,
                                       (jbyte*)record->hdr.user1_ptr);
      sCallbackEnv->CallVoidMethod(sCallbacksObj, method_sdpRecordFoundCallback,
                                   (jint)status, addr.get(), uuid.get(),
                                   record_data_size, record_data.get());
    }
  }  // End of for-loop
}

static jint sdpCreateMapMasRecordNative(JNIEnv* env, jobject obj,
                                        jstring name_str, jint mas_id, jint scn,
                                        jint l2cap_psm, jint version,
                                        jint msg_types, jint features) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return -1;

  bluetooth_sdp_record record = {};  // Must be zero initialized
  record.mas.hdr.type = SDP_TYPE_MAP_MAS;

  const char* service_name = NULL;
  if (name_str != NULL) {
    service_name = env->GetStringUTFChars(name_str, NULL);
    record.mas.hdr.service_name = (char*)service_name;
    record.mas.hdr.service_name_length = strlen(service_name);
  } else {
    record.mas.hdr.service_name = NULL;
    record.mas.hdr.service_name_length = 0;
  }
  record.mas.hdr.rfcomm_channel_number = scn;
  record.mas.hdr.l2cap_psm = l2cap_psm;
  record.mas.hdr.profile_version = version;

  record.mas.mas_instance_id = mas_id;
  record.mas.supported_features = features;
  record.mas.supported_message_types = msg_types;

  int handle = -1;
  int ret = sBluetoothSdpInterface->create_sdp_record(&record, &handle);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Create record failed: %d", ret);
  } else {
    ALOGD("SDP Create record success - handle: %d", handle);
  }

  if (service_name) env->ReleaseStringUTFChars(name_str, service_name);
  return handle;
}

static jint sdpCreateMapMnsRecordNative(JNIEnv* env, jobject obj,
                                        jstring name_str, jint scn,
                                        jint l2cap_psm, jint version,
                                        jint features) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return -1;

  bluetooth_sdp_record record = {};  // Must be zero initialized
  record.mns.hdr.type = SDP_TYPE_MAP_MNS;

  const char* service_name = NULL;
  if (name_str != NULL) {
    service_name = env->GetStringUTFChars(name_str, NULL);
    record.mns.hdr.service_name = (char*)service_name;
    record.mns.hdr.service_name_length = strlen(service_name);
  } else {
    record.mns.hdr.service_name = NULL;
    record.mns.hdr.service_name_length = 0;
  }
  record.mns.hdr.rfcomm_channel_number = scn;
  record.mns.hdr.l2cap_psm = l2cap_psm;
  record.mns.hdr.profile_version = version;

  record.mns.supported_features = features;

  int handle = -1;
  int ret = sBluetoothSdpInterface->create_sdp_record(&record, &handle);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Create record failed: %d", ret);
  } else {
    ALOGD("SDP Create record success - handle: %d", handle);
  }

  if (service_name) env->ReleaseStringUTFChars(name_str, service_name);
  return handle;
}

static jint sdpCreatePbapPseRecordNative(JNIEnv* env, jobject obj,
                                         jstring name_str, jint scn,
                                         jint l2cap_psm, jint version,
                                         jint supported_repositories,
                                         jint features) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return -1;

  bluetooth_sdp_record record = {};  // Must be zero initialized
  record.pse.hdr.type = SDP_TYPE_PBAP_PSE;

  const char* service_name = NULL;
  if (name_str != NULL) {
    service_name = env->GetStringUTFChars(name_str, NULL);
    record.pse.hdr.service_name = (char*)service_name;
    record.pse.hdr.service_name_length = strlen(service_name);
  } else {
    record.pse.hdr.service_name = NULL;
    record.pse.hdr.service_name_length = 0;
  }
  record.pse.hdr.rfcomm_channel_number = scn;
  record.pse.hdr.l2cap_psm = l2cap_psm;
  record.pse.hdr.profile_version = version;

  record.pse.supported_features = features;
  record.pse.supported_repositories = supported_repositories;

  int handle = -1;
  int ret = sBluetoothSdpInterface->create_sdp_record(&record, &handle);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Create record failed: %d", ret);
  } else {
    ALOGD("SDP Create record success - handle: %d", handle);
  }

  if (service_name) env->ReleaseStringUTFChars(name_str, service_name);
  return handle;
}

static jint sdpCreateOppOpsRecordNative(JNIEnv* env, jobject obj,
                                        jstring name_str, jint scn,
                                        jint l2cap_psm, jint version,
                                        jbyteArray supported_formats_list) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return -1;

  bluetooth_sdp_record record = {};  // Must be zero initialized
  record.ops.hdr.type = SDP_TYPE_OPP_SERVER;

  const char* service_name = NULL;
  if (name_str != NULL) {
    service_name = env->GetStringUTFChars(name_str, NULL);
    record.ops.hdr.service_name = (char*)service_name;
    record.ops.hdr.service_name_length = strlen(service_name);
  } else {
    record.ops.hdr.service_name = NULL;
    record.ops.hdr.service_name_length = 0;
  }
  record.ops.hdr.rfcomm_channel_number = scn;
  record.ops.hdr.l2cap_psm = l2cap_psm;
  record.ops.hdr.profile_version = version;

  int formats_list_len = 0;
  jbyte* formats_list = env->GetByteArrayElements(supported_formats_list, NULL);
  if (formats_list != NULL) {
    formats_list_len = env->GetArrayLength(supported_formats_list);
    if (formats_list_len > SDP_OPP_SUPPORTED_FORMATS_MAX_LENGTH) {
      formats_list_len = SDP_OPP_SUPPORTED_FORMATS_MAX_LENGTH;
    }
    memcpy(record.ops.supported_formats_list, formats_list, formats_list_len);
  }

  record.ops.supported_formats_list_len = formats_list_len;

  int handle = -1;
  int ret = sBluetoothSdpInterface->create_sdp_record(&record, &handle);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Create record failed: %d", ret);
  } else {
    ALOGD("SDP Create record success - handle: %d", handle);
  }

  if (service_name) env->ReleaseStringUTFChars(name_str, service_name);
  if (formats_list)
    env->ReleaseByteArrayElements(supported_formats_list, formats_list, 0);
  return handle;
}

static jint sdpCreateSapsRecordNative(JNIEnv* env, jobject obj,
                                      jstring name_str, jint scn,
                                      jint version) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return -1;

  bluetooth_sdp_record record = {};  // Must be zero initialized
  record.sap.hdr.type = SDP_TYPE_SAP_SERVER;

  const char* service_name = NULL;
  if (name_str != NULL) {
    service_name = env->GetStringUTFChars(name_str, NULL);
    record.mas.hdr.service_name = (char*)service_name;
    record.mas.hdr.service_name_length = strlen(service_name);
  } else {
    record.mas.hdr.service_name = NULL;
    record.mas.hdr.service_name_length = 0;
  }
  record.mas.hdr.rfcomm_channel_number = scn;
  record.mas.hdr.profile_version = version;

  int handle = -1;
  int ret = sBluetoothSdpInterface->create_sdp_record(&record, &handle);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Create record failed: %d", ret);
  } else {
    ALOGD("SDP Create record success - handle: %d", handle);
  }

  if (service_name) env->ReleaseStringUTFChars(name_str, service_name);
  return handle;
}

static jboolean sdpRemoveSdpRecordNative(JNIEnv* env, jobject obj,
                                         jint record_id) {
  ALOGD("%s", __func__);
  if (!sBluetoothSdpInterface) return false;

  int ret = sBluetoothSdpInterface->remove_sdp_record(record_id);
  if (ret != BT_STATUS_SUCCESS) {
    ALOGE("SDP Remove record failed: %d", ret);
    return false;
  }

  ALOGD("SDP Remove record success - handle: %d", record_id);
  return true;
}

static void cleanupNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();

  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothSdpInterface != NULL) {
    ALOGW("Cleaning up Bluetooth SDP Interface...");
    sBluetoothSdpInterface->deinit();
    sBluetoothSdpInterface = NULL;
  }

  if (sCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth SDP object");
    env->DeleteGlobalRef(sCallbacksObj);
    sCallbacksObj = NULL;
  }
}

static JNINativeMethod sMethods[] = {
    /* name, signature, funcPtr */
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "()V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"sdpSearchNative", "([B[B)Z", (void*)sdpSearchNative},
    {"sdpCreateMapMasRecordNative", "(Ljava/lang/String;IIIIII)I",
     (void*)sdpCreateMapMasRecordNative},
    {"sdpCreateMapMnsRecordNative", "(Ljava/lang/String;IIII)I",
     (void*)sdpCreateMapMnsRecordNative},
    {"sdpCreatePbapPseRecordNative", "(Ljava/lang/String;IIIII)I",
     (void*)sdpCreatePbapPseRecordNative},
    {"sdpCreateOppOpsRecordNative", "(Ljava/lang/String;III[B)I",
     (void*)sdpCreateOppOpsRecordNative},
    {"sdpCreateSapsRecordNative", "(Ljava/lang/String;II)I",
     (void*)sdpCreateSapsRecordNative},
    {"sdpRemoveSdpRecordNative", "(I)Z", (void*)sdpRemoveSdpRecordNative}};

int register_com_android_bluetooth_sdp(JNIEnv* env) {
  return jniRegisterNativeMethods(env, "com/android/bluetooth/sdp/SdpManager",
                                  sMethods, NELEM(sMethods));
}
}
