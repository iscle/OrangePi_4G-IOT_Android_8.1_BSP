/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define LOG_TAG "BtGatt.JNI"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_gatt.h"
#include "utils/Log.h"

#include <base/bind.h>
#include <string.h>
#include <memory>

#include <cutils/log.h>
#define info(fmt, ...) ALOGI("%s(L%d): " fmt, __func__, __LINE__, ##__VA_ARGS__)
#define debug(fmt, ...) \
  ALOGD("%s(L%d): " fmt, __func__, __LINE__, ##__VA_ARGS__)
#define warn(fmt, ...) \
  ALOGW("WARNING: %s(L%d): " fmt "##", __func__, __LINE__, ##__VA_ARGS__)
#define error(fmt, ...) \
  ALOGE("ERROR: %s(L%d): " fmt "##", __func__, __LINE__, ##__VA_ARGS__)
#define asrt(s) \
  if (!(s)) ALOGE("%s(L%d): ASSERT %s failed! ##", __func__, __LINE__, #s)

#define BD_ADDR_LEN 6

#define UUID_PARAMS(uuid) uuid_lsb(uuid), uuid_msb(uuid)

static void set_uuid(uint8_t* uuid, jlong uuid_msb, jlong uuid_lsb) {
  for (int i = 0; i != 8; ++i) {
    uuid[i] = (uuid_lsb >> (8 * i)) & 0xFF;
    uuid[i + 8] = (uuid_msb >> (8 * i)) & 0xFF;
  }
}

static uint64_t uuid_lsb(const bt_uuid_t& uuid) {
  uint64_t lsb = 0;

  for (int i = 7; i >= 0; i--) {
    lsb <<= 8;
    lsb |= uuid.uu[i];
  }

  return lsb;
}

static uint64_t uuid_msb(const bt_uuid_t& uuid) {
  uint64_t msb = 0;

  for (int i = 15; i >= 8; i--) {
    msb <<= 8;
    msb |= uuid.uu[i];
  }

  return msb;
}

static RawAddress str2addr(JNIEnv* env, jstring address) {
  RawAddress bd_addr;
  const char* c_address = env->GetStringUTFChars(address, NULL);
  if (!c_address) return bd_addr;

  RawAddress::FromString(std::string(c_address), bd_addr);
  env->ReleaseStringUTFChars(address, c_address);

  return bd_addr;
}

static jstring bdaddr2newjstr(JNIEnv* env, const RawAddress* bda) {
  char c_address[32];
  snprintf(c_address, sizeof(c_address), "%02X:%02X:%02X:%02X:%02X:%02X",
           bda->address[0], bda->address[1], bda->address[2], bda->address[3],
           bda->address[4], bda->address[5]);

  return env->NewStringUTF(c_address);
}

static std::vector<uint8_t> toVector(JNIEnv* env, jbyteArray ba) {
  jbyte* data_data = env->GetByteArrayElements(ba, NULL);
  uint16_t data_len = (uint16_t)env->GetArrayLength(ba);
  std::vector<uint8_t> data_vec(data_data, data_data + data_len);
  env->ReleaseByteArrayElements(ba, data_data, JNI_ABORT);
  return data_vec;
}

namespace android {

/**
 * Client callback methods
 */

static jmethodID method_onClientRegistered;
static jmethodID method_onScannerRegistered;
static jmethodID method_onScanResult;
static jmethodID method_onConnected;
static jmethodID method_onDisconnected;
static jmethodID method_onReadCharacteristic;
static jmethodID method_onWriteCharacteristic;
static jmethodID method_onExecuteCompleted;
static jmethodID method_onSearchCompleted;
static jmethodID method_onReadDescriptor;
static jmethodID method_onWriteDescriptor;
static jmethodID method_onNotify;
static jmethodID method_onRegisterForNotifications;
static jmethodID method_onReadRemoteRssi;
static jmethodID method_onConfigureMTU;
static jmethodID method_onScanFilterConfig;
static jmethodID method_onScanFilterParamsConfigured;
static jmethodID method_onScanFilterEnableDisabled;
static jmethodID method_onClientCongestion;
static jmethodID method_onBatchScanStorageConfigured;
static jmethodID method_onBatchScanStartStopped;
static jmethodID method_onBatchScanReports;
static jmethodID method_onBatchScanThresholdCrossed;

static jmethodID method_CreateonTrackAdvFoundLostObject;
static jmethodID method_onTrackAdvFoundLost;
static jmethodID method_onScanParamSetupCompleted;
static jmethodID method_getSampleGattDbElement;
static jmethodID method_onGetGattDb;
static jmethodID method_onClientPhyUpdate;
static jmethodID method_onClientPhyRead;
static jmethodID method_onClientConnUpdate;

/**
 * Server callback methods
 */
static jmethodID method_onServerRegistered;
static jmethodID method_onClientConnected;
static jmethodID method_onServiceAdded;
static jmethodID method_onServiceStopped;
static jmethodID method_onServiceDeleted;
static jmethodID method_onResponseSendCompleted;
static jmethodID method_onServerReadCharacteristic;
static jmethodID method_onServerReadDescriptor;
static jmethodID method_onServerWriteCharacteristic;
static jmethodID method_onServerWriteDescriptor;
static jmethodID method_onExecuteWrite;
static jmethodID method_onNotificationSent;
static jmethodID method_onServerCongestion;
static jmethodID method_onServerMtuChanged;
static jmethodID method_onServerPhyUpdate;
static jmethodID method_onServerPhyRead;
static jmethodID method_onServerConnUpdate;

/**
 * Advertiser callback methods
 */
static jmethodID method_onAdvertisingSetStarted;
static jmethodID method_onOwnAddressRead;
static jmethodID method_onAdvertisingEnabled;
static jmethodID method_onAdvertisingDataSet;
static jmethodID method_onScanResponseDataSet;
static jmethodID method_onAdvertisingParametersUpdated;
static jmethodID method_onPeriodicAdvertisingParametersUpdated;
static jmethodID method_onPeriodicAdvertisingDataSet;
static jmethodID method_onPeriodicAdvertisingEnabled;

/**
 * Periodic scanner callback methods
 */
static jmethodID method_onSyncLost;
static jmethodID method_onSyncReport;
static jmethodID method_onSyncStarted;

/**
 * Static variables
 */

static const btgatt_interface_t* sGattIf = NULL;
static jobject mCallbacksObj = NULL;
static jobject mAdvertiseCallbacksObj = NULL;
static jobject mPeriodicScanCallbacksObj = NULL;

/**
 * BTA client callbacks
 */

void btgattc_register_app_cb(int status, int clientIf,
                             const bt_uuid_t& app_uuid) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientRegistered, status,
                               clientIf, UUID_PARAMS(app_uuid));
}

void btgattc_scan_result_cb(uint16_t event_type, uint8_t addr_type,
                            RawAddress* bda, uint8_t primary_phy,
                            uint8_t secondary_phy, uint8_t advertising_sid,
                            int8_t tx_power, int8_t rssi,
                            uint16_t periodic_adv_int,
                            std::vector<uint8_t> adv_data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), bda));
  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(),
                                sCallbackEnv->NewByteArray(adv_data.size()));
  sCallbackEnv->SetByteArrayRegion(jb.get(), 0, adv_data.size(),
                                   (jbyte*)adv_data.data());

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onScanResult, event_type,
                               addr_type, address.get(), primary_phy,
                               secondary_phy, advertising_sid, tx_power, rssi,
                               periodic_adv_int, jb.get());
}

void btgattc_open_cb(int conn_id, int status, int clientIf,
                     const RawAddress& bda) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnected, clientIf,
                               conn_id, status, address.get());
}

void btgattc_close_cb(int conn_id, int status, int clientIf,
                      const RawAddress& bda) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDisconnected, clientIf,
                               conn_id, status, address.get());
}

void btgattc_search_complete_cb(int conn_id, int status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSearchCompleted, conn_id,
                               status);
}

void btgattc_register_for_notification_cb(int conn_id, int registered,
                                          int status, uint16_t handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRegisterForNotifications,
                               conn_id, status, registered, handle);
}

void btgattc_notify_cb(int conn_id, const btgatt_notify_params_t& p_data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(
      sCallbackEnv.get(), bdaddr2newjstr(sCallbackEnv.get(), &p_data.bda));
  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(),
                                sCallbackEnv->NewByteArray(p_data.len));
  sCallbackEnv->SetByteArrayRegion(jb.get(), 0, p_data.len,
                                   (jbyte*)p_data.value);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotify, conn_id,
                               address.get(), p_data.handle, p_data.is_notify,
                               jb.get());
}

void btgattc_read_characteristic_cb(int conn_id, int status,
                                    btgatt_read_params_t* p_data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(), NULL);
  if (status == 0) {  // Success
    jb.reset(sCallbackEnv->NewByteArray(p_data->value.len));
    sCallbackEnv->SetByteArrayRegion(jb.get(), 0, p_data->value.len,
                                     (jbyte*)p_data->value.value);
  } else {
    uint8_t value = 0;
    jb.reset(sCallbackEnv->NewByteArray(1));
    sCallbackEnv->SetByteArrayRegion(jb.get(), 0, 1, (jbyte*)&value);
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onReadCharacteristic,
                               conn_id, status, p_data->handle, jb.get());
}

void btgattc_write_characteristic_cb(int conn_id, int status, uint16_t handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onWriteCharacteristic,
                               conn_id, status, handle);
}

void btgattc_execute_write_cb(int conn_id, int status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onExecuteCompleted,
                               conn_id, status);
}

void btgattc_read_descriptor_cb(int conn_id, int status,
                                const btgatt_read_params_t& p_data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(), NULL);
  if (p_data.value.len != 0) {
    jb.reset(sCallbackEnv->NewByteArray(p_data.value.len));
    sCallbackEnv->SetByteArrayRegion(jb.get(), 0, p_data.value.len,
                                     (jbyte*)p_data.value.value);
  } else {
    jb.reset(sCallbackEnv->NewByteArray(1));
  }

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onReadDescriptor, conn_id,
                               status, p_data.handle, jb.get());
}

void btgattc_write_descriptor_cb(int conn_id, int status, uint16_t handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onWriteDescriptor, conn_id,
                               status, handle);
}

void btgattc_remote_rssi_cb(int client_if, const RawAddress& bda, int rssi,
                            int status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onReadRemoteRssi,
                               client_if, address.get(), rssi, status);
}

void btgattc_configure_mtu_cb(int conn_id, int status, int mtu) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConfigureMTU, conn_id,
                               status, mtu);
}

void btgattc_congestion_cb(int conn_id, bool congested) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientCongestion,
                               conn_id, congested);
}

void btgattc_batchscan_reports_cb(int client_if, int status, int report_format,
                                  int num_records, std::vector<uint8_t> data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(),
                                sCallbackEnv->NewByteArray(data.size()));
  sCallbackEnv->SetByteArrayRegion(jb.get(), 0, data.size(),
                                   (jbyte*)data.data());

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onBatchScanReports, status,
                               client_if, report_format, num_records, jb.get());
}

void btgattc_batchscan_threshold_cb(int client_if) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj,
                               method_onBatchScanThresholdCrossed, client_if);
}

void btgattc_track_adv_event_cb(btgatt_track_adv_info_t* p_adv_track_info) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(
      sCallbackEnv.get(),
      bdaddr2newjstr(sCallbackEnv.get(), &p_adv_track_info->bd_addr));

  ScopedLocalRef<jbyteArray> jb_adv_pkt(
      sCallbackEnv.get(),
      sCallbackEnv->NewByteArray(p_adv_track_info->adv_pkt_len));
  ScopedLocalRef<jbyteArray> jb_scan_rsp(
      sCallbackEnv.get(),
      sCallbackEnv->NewByteArray(p_adv_track_info->scan_rsp_len));

  sCallbackEnv->SetByteArrayRegion(jb_adv_pkt.get(), 0,
                                   p_adv_track_info->adv_pkt_len,
                                   (jbyte*)p_adv_track_info->p_adv_pkt_data);

  sCallbackEnv->SetByteArrayRegion(jb_scan_rsp.get(), 0,
                                   p_adv_track_info->scan_rsp_len,
                                   (jbyte*)p_adv_track_info->p_scan_rsp_data);

  ScopedLocalRef<jobject> trackadv_obj(
      sCallbackEnv.get(),
      sCallbackEnv->CallObjectMethod(
          mCallbacksObj, method_CreateonTrackAdvFoundLostObject,
          p_adv_track_info->client_if, p_adv_track_info->adv_pkt_len,
          jb_adv_pkt.get(), p_adv_track_info->scan_rsp_len, jb_scan_rsp.get(),
          p_adv_track_info->filt_index, p_adv_track_info->advertiser_state,
          p_adv_track_info->advertiser_info_present, address.get(),
          p_adv_track_info->addr_type, p_adv_track_info->tx_power,
          p_adv_track_info->rssi_value, p_adv_track_info->time_stamp));

  if (NULL != trackadv_obj.get()) {
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onTrackAdvFoundLost,
                                 trackadv_obj.get());
  }
}

void fillGattDbElementArray(JNIEnv* env, jobject* array,
                            const btgatt_db_element_t* db, int count) {
  // Because JNI uses a different class loader in the callback context, we
  // cannot simply get the class.
  // As a workaround, we have to make sure we obtain an object of the class
  // first, as this will cause
  // class loader to load it.
  ScopedLocalRef<jobject> objectForClass(
      env, env->CallObjectMethod(mCallbacksObj, method_getSampleGattDbElement));
  ScopedLocalRef<jclass> gattDbElementClazz(
      env, env->GetObjectClass(objectForClass.get()));

  jmethodID gattDbElementConstructor =
      env->GetMethodID(gattDbElementClazz.get(), "<init>", "()V");

  ScopedLocalRef<jclass> arrayListclazz(env,
                                        env->FindClass("java/util/ArrayList"));
  jmethodID arrayAdd =
      env->GetMethodID(arrayListclazz.get(), "add", "(Ljava/lang/Object;)Z");

  ScopedLocalRef<jclass> uuidClazz(env, env->FindClass("java/util/UUID"));
  jmethodID uuidConstructor =
      env->GetMethodID(uuidClazz.get(), "<init>", "(JJ)V");

  for (int i = 0; i < count; i++) {
    const btgatt_db_element_t& curr = db[i];

    ScopedLocalRef<jobject> element(
        env,
        env->NewObject(gattDbElementClazz.get(), gattDbElementConstructor));

    jfieldID fid = env->GetFieldID(gattDbElementClazz.get(), "id", "I");
    env->SetIntField(element.get(), fid, curr.id);

    fid = env->GetFieldID(gattDbElementClazz.get(), "attributeHandle", "I");
    env->SetIntField(element.get(), fid, curr.attribute_handle);

    ScopedLocalRef<jobject> uuid(
        env, env->NewObject(uuidClazz.get(), uuidConstructor,
                            uuid_msb(curr.uuid), uuid_lsb(curr.uuid)));
    fid = env->GetFieldID(gattDbElementClazz.get(), "uuid", "Ljava/util/UUID;");
    env->SetObjectField(element.get(), fid, uuid.get());

    fid = env->GetFieldID(gattDbElementClazz.get(), "type", "I");
    env->SetIntField(element.get(), fid, curr.type);

    fid = env->GetFieldID(gattDbElementClazz.get(), "attributeHandle", "I");
    env->SetIntField(element.get(), fid, curr.attribute_handle);

    fid = env->GetFieldID(gattDbElementClazz.get(), "startHandle", "I");
    env->SetIntField(element.get(), fid, curr.start_handle);

    fid = env->GetFieldID(gattDbElementClazz.get(), "endHandle", "I");
    env->SetIntField(element.get(), fid, curr.end_handle);

    fid = env->GetFieldID(gattDbElementClazz.get(), "properties", "I");
    env->SetIntField(element.get(), fid, curr.properties);

    env->CallBooleanMethod(*array, arrayAdd, element.get());
  }
}

void btgattc_get_gatt_db_cb(int conn_id, const btgatt_db_element_t* db,
                            int count) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  jclass arrayListclazz = sCallbackEnv->FindClass("java/util/ArrayList");
  ScopedLocalRef<jobject> array(
      sCallbackEnv.get(),
      sCallbackEnv->NewObject(
          arrayListclazz,
          sCallbackEnv->GetMethodID(arrayListclazz, "<init>", "()V")));

  jobject arrayPtr = array.get();
  fillGattDbElementArray(sCallbackEnv.get(), &arrayPtr, db, count);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetGattDb, conn_id,
                               array.get());
}

void btgattc_phy_updated_cb(int conn_id, uint8_t tx_phy, uint8_t rx_phy,
                            uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientPhyUpdate, conn_id,
                               tx_phy, rx_phy, status);
}

void btgattc_conn_updated_cb(int conn_id, uint16_t interval, uint16_t latency,
                             uint16_t timeout, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientConnUpdate,
                               conn_id, interval, latency, timeout, status);
}

static const btgatt_scanner_callbacks_t sGattScannerCallbacks = {
    btgattc_scan_result_cb,
    btgattc_batchscan_reports_cb,
    btgattc_batchscan_threshold_cb,
    btgattc_track_adv_event_cb,
};

static const btgatt_client_callbacks_t sGattClientCallbacks = {
    btgattc_register_app_cb,
    btgattc_open_cb,
    btgattc_close_cb,
    btgattc_search_complete_cb,
    btgattc_register_for_notification_cb,
    btgattc_notify_cb,
    btgattc_read_characteristic_cb,
    btgattc_write_characteristic_cb,
    btgattc_read_descriptor_cb,
    btgattc_write_descriptor_cb,
    btgattc_execute_write_cb,
    btgattc_remote_rssi_cb,
    btgattc_configure_mtu_cb,
    btgattc_congestion_cb,
    btgattc_get_gatt_db_cb,
    NULL, /* services_removed_cb */
    NULL, /* services_added_cb */
    btgattc_phy_updated_cb,
    btgattc_conn_updated_cb};

/**
 * BTA server callbacks
 */

void btgatts_register_app_cb(int status, int server_if, const bt_uuid_t& uuid) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerRegistered, status,
                               server_if, UUID_PARAMS(uuid));
}

void btgatts_connection_cb(int conn_id, int server_if, int connected,
                           const RawAddress& bda) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientConnected,
                               address.get(), connected, conn_id, server_if);
}

void btgatts_service_added_cb(int status, int server_if,
                              std::vector<btgatt_db_element_t> service) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  jclass arrayListclazz = sCallbackEnv->FindClass("java/util/ArrayList");
  ScopedLocalRef<jobject> array(
      sCallbackEnv.get(),
      sCallbackEnv->NewObject(
          arrayListclazz,
          sCallbackEnv->GetMethodID(arrayListclazz, "<init>", "()V")));
  jobject arrayPtr = array.get();
  fillGattDbElementArray(sCallbackEnv.get(), &arrayPtr, service.data(),
                         service.size());

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServiceAdded, status,
                               server_if, array.get());
}

void btgatts_service_stopped_cb(int status, int server_if, int srvc_handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServiceStopped, status,
                               server_if, srvc_handle);
}

void btgatts_service_deleted_cb(int status, int server_if, int srvc_handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServiceDeleted, status,
                               server_if, srvc_handle);
}

void btgatts_request_read_characteristic_cb(int conn_id, int trans_id,
                                            const RawAddress& bda,
                                            int attr_handle, int offset,
                                            bool is_long) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerReadCharacteristic,
                               address.get(), conn_id, trans_id, attr_handle,
                               offset, is_long);
}

void btgatts_request_read_descriptor_cb(int conn_id, int trans_id,
                                        const RawAddress& bda, int attr_handle,
                                        int offset, bool is_long) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerReadDescriptor,
                               address.get(), conn_id, trans_id, attr_handle,
                               offset, is_long);
}

void btgatts_request_write_characteristic_cb(int conn_id, int trans_id,
                                             const RawAddress& bda,
                                             int attr_handle, int offset,
                                             bool need_rsp, bool is_prep,
                                             std::vector<uint8_t> value) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  ScopedLocalRef<jbyteArray> val(sCallbackEnv.get(),
                                 sCallbackEnv->NewByteArray(value.size()));
  if (val.get())
    sCallbackEnv->SetByteArrayRegion(val.get(), 0, value.size(),
                                     (jbyte*)value.data());
  sCallbackEnv->CallVoidMethod(
      mCallbacksObj, method_onServerWriteCharacteristic, address.get(), conn_id,
      trans_id, attr_handle, offset, value.size(), need_rsp, is_prep,
      val.get());
}

void btgatts_request_write_descriptor_cb(int conn_id, int trans_id,
                                         const RawAddress& bda, int attr_handle,
                                         int offset, bool need_rsp,
                                         bool is_prep,
                                         std::vector<uint8_t> value) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  ScopedLocalRef<jbyteArray> val(sCallbackEnv.get(),
                                 sCallbackEnv->NewByteArray(value.size()));
  if (val.get())
    sCallbackEnv->SetByteArrayRegion(val.get(), 0, value.size(),
                                     (jbyte*)value.data());
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerWriteDescriptor,
                               address.get(), conn_id, trans_id, attr_handle,
                               offset, value.size(), need_rsp, is_prep,
                               val.get());
}

void btgatts_request_exec_write_cb(int conn_id, int trans_id,
                                   const RawAddress& bda, int exec_write) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onExecuteWrite,
                               address.get(), conn_id, trans_id, exec_write);
}

void btgatts_response_confirmation_cb(int status, int handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onResponseSendCompleted,
                               status, handle);
}

void btgatts_indication_sent_cb(int conn_id, int status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotificationSent,
                               conn_id, status);
}

void btgatts_congestion_cb(int conn_id, bool congested) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerCongestion,
                               conn_id, congested);
}

void btgatts_mtu_changed_cb(int conn_id, int mtu) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerMtuChanged,
                               conn_id, mtu);
}

void btgatts_phy_updated_cb(int conn_id, uint8_t tx_phy, uint8_t rx_phy,
                            uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerPhyUpdate, conn_id,
                               tx_phy, rx_phy, status);
}

void btgatts_conn_updated_cb(int conn_id, uint16_t interval, uint16_t latency,
                             uint16_t timeout, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerConnUpdate,
                               conn_id, interval, latency, timeout, status);
}

static const btgatt_server_callbacks_t sGattServerCallbacks = {
    btgatts_register_app_cb,
    btgatts_connection_cb,
    btgatts_service_added_cb,
    btgatts_service_stopped_cb,
    btgatts_service_deleted_cb,
    btgatts_request_read_characteristic_cb,
    btgatts_request_read_descriptor_cb,
    btgatts_request_write_characteristic_cb,
    btgatts_request_write_descriptor_cb,
    btgatts_request_exec_write_cb,
    btgatts_response_confirmation_cb,
    btgatts_indication_sent_cb,
    btgatts_congestion_cb,
    btgatts_mtu_changed_cb,
    btgatts_phy_updated_cb,
    btgatts_conn_updated_cb};

/**
 * GATT callbacks
 */

static const btgatt_callbacks_t sGattCallbacks = {
    sizeof(btgatt_callbacks_t), &sGattClientCallbacks, &sGattServerCallbacks,
    &sGattScannerCallbacks,
};

/**
 * Native function definitions
 */
static void classInitNative(JNIEnv* env, jclass clazz) {
  // Client callbacks

  method_onClientRegistered =
      env->GetMethodID(clazz, "onClientRegistered", "(IIJJ)V");
  method_onScannerRegistered =
      env->GetMethodID(clazz, "onScannerRegistered", "(IIJJ)V");
  method_onScanResult = env->GetMethodID(clazz, "onScanResult",
                                         "(IILjava/lang/String;IIIIII[B)V");
  method_onConnected =
      env->GetMethodID(clazz, "onConnected", "(IIILjava/lang/String;)V");
  method_onDisconnected =
      env->GetMethodID(clazz, "onDisconnected", "(IIILjava/lang/String;)V");
  method_onReadCharacteristic =
      env->GetMethodID(clazz, "onReadCharacteristic", "(III[B)V");
  method_onWriteCharacteristic =
      env->GetMethodID(clazz, "onWriteCharacteristic", "(III)V");
  method_onExecuteCompleted =
      env->GetMethodID(clazz, "onExecuteCompleted", "(II)V");
  method_onSearchCompleted =
      env->GetMethodID(clazz, "onSearchCompleted", "(II)V");
  method_onReadDescriptor =
      env->GetMethodID(clazz, "onReadDescriptor", "(III[B)V");
  method_onWriteDescriptor =
      env->GetMethodID(clazz, "onWriteDescriptor", "(III)V");
  method_onNotify =
      env->GetMethodID(clazz, "onNotify", "(ILjava/lang/String;IZ[B)V");
  method_onRegisterForNotifications =
      env->GetMethodID(clazz, "onRegisterForNotifications", "(IIII)V");
  method_onReadRemoteRssi =
      env->GetMethodID(clazz, "onReadRemoteRssi", "(ILjava/lang/String;II)V");
  method_onConfigureMTU = env->GetMethodID(clazz, "onConfigureMTU", "(III)V");
  method_onScanFilterConfig =
      env->GetMethodID(clazz, "onScanFilterConfig", "(IIIII)V");
  method_onScanFilterParamsConfigured =
      env->GetMethodID(clazz, "onScanFilterParamsConfigured", "(IIII)V");
  method_onScanFilterEnableDisabled =
      env->GetMethodID(clazz, "onScanFilterEnableDisabled", "(III)V");
  method_onClientCongestion =
      env->GetMethodID(clazz, "onClientCongestion", "(IZ)V");
  method_onBatchScanStorageConfigured =
      env->GetMethodID(clazz, "onBatchScanStorageConfigured", "(II)V");
  method_onBatchScanStartStopped =
      env->GetMethodID(clazz, "onBatchScanStartStopped", "(III)V");
  method_onBatchScanReports =
      env->GetMethodID(clazz, "onBatchScanReports", "(IIII[B)V");
  method_onBatchScanThresholdCrossed =
      env->GetMethodID(clazz, "onBatchScanThresholdCrossed", "(I)V");
  method_CreateonTrackAdvFoundLostObject =
      env->GetMethodID(clazz, "CreateonTrackAdvFoundLostObject",
                       "(II[BI[BIIILjava/lang/String;IIII)Lcom/android/"
                       "bluetooth/gatt/AdvtFilterOnFoundOnLostInfo;");
  method_onTrackAdvFoundLost = env->GetMethodID(
      clazz, "onTrackAdvFoundLost",
      "(Lcom/android/bluetooth/gatt/AdvtFilterOnFoundOnLostInfo;)V");
  method_onScanParamSetupCompleted =
      env->GetMethodID(clazz, "onScanParamSetupCompleted", "(II)V");
  method_getSampleGattDbElement =
      env->GetMethodID(clazz, "GetSampleGattDbElement",
                       "()Lcom/android/bluetooth/gatt/GattDbElement;");
  method_onGetGattDb =
      env->GetMethodID(clazz, "onGetGattDb", "(ILjava/util/ArrayList;)V");
  method_onClientPhyRead =
      env->GetMethodID(clazz, "onClientPhyRead", "(ILjava/lang/String;III)V");
  method_onClientPhyUpdate =
      env->GetMethodID(clazz, "onClientPhyUpdate", "(IIII)V");
  method_onClientConnUpdate =
      env->GetMethodID(clazz, "onClientConnUpdate", "(IIIII)V");

  // Server callbacks

  method_onServerRegistered =
      env->GetMethodID(clazz, "onServerRegistered", "(IIJJ)V");
  method_onClientConnected =
      env->GetMethodID(clazz, "onClientConnected", "(Ljava/lang/String;ZII)V");
  method_onServiceAdded =
      env->GetMethodID(clazz, "onServiceAdded", "(IILjava/util/List;)V");
  method_onServiceStopped =
      env->GetMethodID(clazz, "onServiceStopped", "(III)V");
  method_onServiceDeleted =
      env->GetMethodID(clazz, "onServiceDeleted", "(III)V");
  method_onResponseSendCompleted =
      env->GetMethodID(clazz, "onResponseSendCompleted", "(II)V");
  method_onServerReadCharacteristic = env->GetMethodID(
      clazz, "onServerReadCharacteristic", "(Ljava/lang/String;IIIIZ)V");
  method_onServerReadDescriptor = env->GetMethodID(
      clazz, "onServerReadDescriptor", "(Ljava/lang/String;IIIIZ)V");
  method_onServerWriteCharacteristic = env->GetMethodID(
      clazz, "onServerWriteCharacteristic", "(Ljava/lang/String;IIIIIZZ[B)V");
  method_onServerWriteDescriptor = env->GetMethodID(
      clazz, "onServerWriteDescriptor", "(Ljava/lang/String;IIIIIZZ[B)V");
  method_onExecuteWrite =
      env->GetMethodID(clazz, "onExecuteWrite", "(Ljava/lang/String;III)V");
  method_onNotificationSent =
      env->GetMethodID(clazz, "onNotificationSent", "(II)V");
  method_onServerCongestion =
      env->GetMethodID(clazz, "onServerCongestion", "(IZ)V");
  method_onServerMtuChanged = env->GetMethodID(clazz, "onMtuChanged", "(II)V");
  method_onServerPhyRead =
      env->GetMethodID(clazz, "onServerPhyRead", "(ILjava/lang/String;III)V");
  method_onServerPhyUpdate =
      env->GetMethodID(clazz, "onServerPhyUpdate", "(IIII)V");
  method_onServerConnUpdate =
      env->GetMethodID(clazz, "onServerConnUpdate", "(IIIII)V");

  info("classInitNative: Success!");
}

static const bt_interface_t* btIf;

static void initializeNative(JNIEnv* env, jobject object) {
  if (btIf) return;

  btIf = getBluetoothInterface();
  if (btIf == NULL) {
    error("Bluetooth module is not loaded");
    return;
  }

  if (sGattIf != NULL) {
    ALOGW("Cleaning up Bluetooth GATT Interface before initializing...");
    sGattIf->cleanup();
    sGattIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth GATT callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sGattIf =
      (btgatt_interface_t*)btIf->get_profile_interface(BT_PROFILE_GATT_ID);
  if (sGattIf == NULL) {
    error("Failed to get Bluetooth GATT Interface");
    return;
  }

  bt_status_t status = sGattIf->init(&sGattCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    error("Failed to initialize Bluetooth GATT, status: %d", status);
    sGattIf = NULL;
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv* env, jobject object) {
  if (!btIf) return;

  if (sGattIf != NULL) {
    sGattIf->cleanup();
    sGattIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
  btIf = NULL;
}

/**
 * Native Client functions
 */

static int gattClientGetDeviceTypeNative(JNIEnv* env, jobject object,
                                         jstring address) {
  if (!sGattIf) return 0;
  return sGattIf->client->get_device_type(str2addr(env, address));
}

static void gattClientRegisterAppNative(JNIEnv* env, jobject object,
                                        jlong app_uuid_lsb,
                                        jlong app_uuid_msb) {
  bt_uuid_t uuid;

  if (!sGattIf) return;
  set_uuid(uuid.uu, app_uuid_msb, app_uuid_lsb);
  sGattIf->client->register_client(uuid);
}

static void gattClientUnregisterAppNative(JNIEnv* env, jobject object,
                                          jint clientIf) {
  if (!sGattIf) return;
  sGattIf->client->unregister_client(clientIf);
}

void btgattc_register_scanner_cb(bt_uuid_t app_uuid, uint8_t scannerId,
                                 uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onScannerRegistered,
                               status, scannerId, UUID_PARAMS(app_uuid));
}

static void registerScannerNative(JNIEnv* env, jobject object,
                                  jlong app_uuid_lsb, jlong app_uuid_msb) {
  if (!sGattIf) return;

  bt_uuid_t uuid;
  set_uuid(uuid.uu, app_uuid_msb, app_uuid_lsb);
  sGattIf->scanner->RegisterScanner(
      base::Bind(&btgattc_register_scanner_cb, uuid));
}

static void unregisterScannerNative(JNIEnv* env, jobject object,
                                    jint scanner_id) {
  if (!sGattIf) return;

  sGattIf->scanner->Unregister(scanner_id);
}

static void gattClientScanNative(JNIEnv* env, jobject object, jboolean start) {
  if (!sGattIf) return;
  sGattIf->scanner->Scan(start);
}

static void gattClientConnectNative(JNIEnv* env, jobject object, jint clientif,
                                    jstring address, jboolean isDirect,
                                    jint transport, jboolean opportunistic,
                                    jint initiating_phys) {
  if (!sGattIf) return;

  sGattIf->client->connect(clientif, str2addr(env, address), isDirect,
                           transport, opportunistic, initiating_phys);
}

static void gattClientDisconnectNative(JNIEnv* env, jobject object,
                                       jint clientIf, jstring address,
                                       jint conn_id) {
  if (!sGattIf) return;
  sGattIf->client->disconnect(clientIf, str2addr(env, address), conn_id);
}

static void gattClientSetPreferredPhyNative(JNIEnv* env, jobject object,
                                            jint clientIf, jstring address,
                                            jint tx_phy, jint rx_phy,
                                            jint phy_options) {
  if (!sGattIf) return;
  sGattIf->client->set_preferred_phy(str2addr(env, address), tx_phy, rx_phy,
                                     phy_options);
}

static void readClientPhyCb(uint8_t clientIf, RawAddress bda, uint8_t tx_phy,
                            uint8_t rx_phy, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onClientPhyRead, clientIf,
                               address.get(), tx_phy, rx_phy, status);
}

static void gattClientReadPhyNative(JNIEnv* env, jobject object, jint clientIf,
                                    jstring address) {
  if (!sGattIf) return;

  RawAddress bda = str2addr(env, address);
  sGattIf->client->read_phy(bda, base::Bind(&readClientPhyCb, clientIf, bda));
}

static void gattClientRefreshNative(JNIEnv* env, jobject object, jint clientIf,
                                    jstring address) {
  if (!sGattIf) return;

  sGattIf->client->refresh(clientIf, str2addr(env, address));
}

static void gattClientSearchServiceNative(JNIEnv* env, jobject object,
                                          jint conn_id, jboolean search_all,
                                          jlong service_uuid_lsb,
                                          jlong service_uuid_msb) {
  if (!sGattIf) return;

  bt_uuid_t uuid;
  set_uuid(uuid.uu, service_uuid_msb, service_uuid_lsb);
  sGattIf->client->search_service(conn_id, search_all ? 0 : &uuid);
}

static void gattClientDiscoverServiceByUuidNative(JNIEnv* env, jobject object,
                                                  jint conn_id,
                                                  jlong service_uuid_lsb,
                                                  jlong service_uuid_msb) {
  if (!sGattIf) return;

  bt_uuid_t uuid;
  set_uuid(uuid.uu, service_uuid_msb, service_uuid_lsb);
  sGattIf->client->btif_gattc_discover_service_by_uuid(conn_id, uuid);
}

static void gattClientGetGattDbNative(JNIEnv* env, jobject object,
                                      jint conn_id) {
  if (!sGattIf) return;

  sGattIf->client->get_gatt_db(conn_id);
}

static void gattClientReadCharacteristicNative(JNIEnv* env, jobject object,
                                               jint conn_id, jint handle,
                                               jint authReq) {
  if (!sGattIf) return;

  sGattIf->client->read_characteristic(conn_id, handle, authReq);
}

static void gattClientReadUsingCharacteristicUuidNative(
    JNIEnv* env, jobject object, jint conn_id, jlong uuid_lsb, jlong uuid_msb,
    jint s_handle, jint e_handle, jint authReq) {
  if (!sGattIf) return;

  bt_uuid_t uuid;
  set_uuid(uuid.uu, uuid_msb, uuid_lsb);
  sGattIf->client->read_using_characteristic_uuid(conn_id, uuid, s_handle,
                                                  e_handle, authReq);
}

static void gattClientReadDescriptorNative(JNIEnv* env, jobject object,
                                           jint conn_id, jint handle,
                                           jint authReq) {
  if (!sGattIf) return;

  sGattIf->client->read_descriptor(conn_id, handle, authReq);
}

static void gattClientWriteCharacteristicNative(JNIEnv* env, jobject object,
                                                jint conn_id, jint handle,
                                                jint write_type, jint auth_req,
                                                jbyteArray value) {
  if (!sGattIf) return;

  if (value == NULL) {
    warn("gattClientWriteCharacteristicNative() ignoring NULL array");
    return;
  }

  uint16_t len = (uint16_t)env->GetArrayLength(value);
  jbyte* p_value = env->GetByteArrayElements(value, NULL);
  if (p_value == NULL) return;

  std::vector<uint8_t> vect_val(p_value, p_value + len);
  env->ReleaseByteArrayElements(value, p_value, 0);

  sGattIf->client->write_characteristic(conn_id, handle, write_type, auth_req,
                                        std::move(vect_val));
}

static void gattClientExecuteWriteNative(JNIEnv* env, jobject object,
                                         jint conn_id, jboolean execute) {
  if (!sGattIf) return;
  sGattIf->client->execute_write(conn_id, execute ? 1 : 0);
}

static void gattClientWriteDescriptorNative(JNIEnv* env, jobject object,
                                            jint conn_id, jint handle,
                                            jint auth_req, jbyteArray value) {
  if (!sGattIf) return;

  if (value == NULL) {
    warn("gattClientWriteDescriptorNative() ignoring NULL array");
    return;
  }

  uint16_t len = (uint16_t)env->GetArrayLength(value);
  jbyte* p_value = env->GetByteArrayElements(value, NULL);
  if (p_value == NULL) return;

  std::vector<uint8_t> vect_val(p_value, p_value + len);
  env->ReleaseByteArrayElements(value, p_value, 0);

  sGattIf->client->write_descriptor(conn_id, handle, auth_req,
                                    std::move(vect_val));
}

static void gattClientRegisterForNotificationsNative(
    JNIEnv* env, jobject object, jint clientIf, jstring address, jint handle,
    jboolean enable) {
  if (!sGattIf) return;

  RawAddress bd_addr = str2addr(env, address);
  if (enable)
    sGattIf->client->register_for_notification(clientIf, bd_addr, handle);
  else
    sGattIf->client->deregister_for_notification(clientIf, bd_addr, handle);
}

static void gattClientReadRemoteRssiNative(JNIEnv* env, jobject object,
                                           jint clientif, jstring address) {
  if (!sGattIf) return;

  sGattIf->client->read_remote_rssi(clientif, str2addr(env, address));
}

void set_scan_params_cmpl_cb(int client_if, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onScanParamSetupCompleted,
                               status, client_if);
}

static void gattSetScanParametersNative(JNIEnv* env, jobject object,
                                        jint client_if, jint scan_interval_unit,
                                        jint scan_window_unit) {
  if (!sGattIf) return;
  sGattIf->scanner->SetScanParameters(
      scan_interval_unit, scan_window_unit,
      base::Bind(&set_scan_params_cmpl_cb, client_if));
}

void scan_filter_param_cb(uint8_t client_if, uint8_t avbl_space, uint8_t action,
                          uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj,
                               method_onScanFilterParamsConfigured, action,
                               status, client_if, avbl_space);
}

static void gattClientScanFilterParamAddNative(JNIEnv* env, jobject object,
                                               jobject params) {
  if (!sGattIf) return;
  const int add_scan_filter_params_action = 0;
  auto filt_params = std::make_unique<btgatt_filt_param_setup_t>();

  jmethodID methodId = 0;
  ScopedLocalRef<jclass> filtparam(env, env->GetObjectClass(params));

  methodId = env->GetMethodID(filtparam.get(), "getClientIf", "()I");
  uint8_t client_if = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getFiltIndex", "()I");
  uint8_t filt_index = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getFeatSeln", "()I");
  filt_params->feat_seln = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getListLogicType", "()I");
  filt_params->list_logic_type = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getFiltLogicType", "()I");
  filt_params->filt_logic_type = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getDelyMode", "()I");
  filt_params->dely_mode = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getFoundTimeout", "()I");
  filt_params->found_timeout = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getLostTimeout", "()I");
  filt_params->lost_timeout = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getFoundTimeOutCnt", "()I");
  filt_params->found_timeout_cnt = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getNumOfTrackEntries", "()I");
  filt_params->num_of_tracking_entries = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getRSSIHighValue", "()I");
  filt_params->rssi_high_thres = env->CallIntMethod(params, methodId);

  methodId = env->GetMethodID(filtparam.get(), "getRSSILowValue", "()I");
  filt_params->rssi_low_thres = env->CallIntMethod(params, methodId);

  sGattIf->scanner->ScanFilterParamSetup(
      client_if, add_scan_filter_params_action, filt_index,
      std::move(filt_params), base::Bind(&scan_filter_param_cb, client_if));
}

static void gattClientScanFilterParamDeleteNative(JNIEnv* env, jobject object,
                                                  jint client_if,
                                                  jint filt_index) {
  if (!sGattIf) return;
  const int delete_scan_filter_params_action = 1;
  sGattIf->scanner->ScanFilterParamSetup(
      client_if, delete_scan_filter_params_action, filt_index, nullptr,
      base::Bind(&scan_filter_param_cb, client_if));
}

static void gattClientScanFilterParamClearAllNative(JNIEnv* env, jobject object,
                                                    jint client_if) {
  if (!sGattIf) return;
  const int clear_scan_filter_params_action = 2;
  sGattIf->scanner->ScanFilterParamSetup(
      client_if, clear_scan_filter_params_action, 0 /* index, unused */,
      nullptr, base::Bind(&scan_filter_param_cb, client_if));
}

static void scan_filter_cfg_cb(uint8_t client_if, uint8_t filt_type,
                               uint8_t avbl_space, uint8_t action,
                               uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onScanFilterConfig, action,
                               status, client_if, filt_type, avbl_space);
}

static void gattClientScanFilterAddRemoveNative(
    JNIEnv* env, jobject object, jint client_if, jint action, jint filt_type,
    jint filt_index, jint company_id, jint company_id_mask, jlong uuid_lsb,
    jlong uuid_msb, jlong uuid_mask_lsb, jlong uuid_mask_msb, jstring name,
    jstring address, jbyte addr_type, jbyteArray data, jbyteArray mask) {
  switch (filt_type) {
    case 0:  // BTM_BLE_PF_ADDR_FILTER
    {
      RawAddress bda = str2addr(env, address);
      sGattIf->scanner->ScanFilterAddRemove(
          action, filt_type, filt_index, 0, 0, NULL, NULL, &bda, addr_type, {},
          {}, base::Bind(&scan_filter_cfg_cb, client_if));
      break;
    }

    case 1:  // BTM_BLE_PF_SRVC_DATA
    {
      jbyte* data_array = env->GetByteArrayElements(data, 0);
      int data_len = env->GetArrayLength(data);
      std::vector<uint8_t> vec_data(data_array, data_array + data_len);
      env->ReleaseByteArrayElements(data, data_array, JNI_ABORT);

      jbyte* mask_array = env->GetByteArrayElements(mask, NULL);
      uint16_t mask_len = (uint16_t)env->GetArrayLength(mask);
      std::vector<uint8_t> vec_mask(mask_array, mask_array + mask_len);
      env->ReleaseByteArrayElements(mask, mask_array, JNI_ABORT);

      sGattIf->scanner->ScanFilterAddRemove(
          action, filt_type, filt_index, 0, 0, NULL, NULL, NULL, 0,
          std::move(vec_data), std::move(vec_mask),
          base::Bind(&scan_filter_cfg_cb, client_if));
      break;
    }

    case 2:  // BTM_BLE_PF_SRVC_UUID
    case 3:  // BTM_BLE_PF_SRVC_SOL_UUID
    {
      bt_uuid_t uuid, uuid_mask;
      set_uuid(uuid.uu, uuid_msb, uuid_lsb);
      set_uuid(uuid_mask.uu, uuid_mask_msb, uuid_mask_lsb);
      if (uuid_mask_lsb != 0 && uuid_mask_msb != 0)
        sGattIf->scanner->ScanFilterAddRemove(
            action, filt_type, filt_index, 0, 0, &uuid, &uuid_mask, NULL, 0, {},
            {}, base::Bind(&scan_filter_cfg_cb, client_if));
      else
        sGattIf->scanner->ScanFilterAddRemove(
            action, filt_type, filt_index, 0, 0, &uuid, NULL, NULL, 0, {}, {},
            base::Bind(&scan_filter_cfg_cb, client_if));
      break;
    }

    case 4:  // BTM_BLE_PF_LOCAL_NAME
    {
      const char* c_name = env->GetStringUTFChars(name, NULL);
      if (c_name != NULL && strlen(c_name) != 0) {
        std::vector<uint8_t> vec_name(c_name, c_name + strlen(c_name));
        env->ReleaseStringUTFChars(name, c_name);
        sGattIf->scanner->ScanFilterAddRemove(
            action, filt_type, filt_index, 0, 0, NULL, NULL, NULL, 0,
            std::move(vec_name), {},
            base::Bind(&scan_filter_cfg_cb, client_if));
      }
      break;
    }

    case 5:  // BTM_BLE_PF_MANU_DATA
    case 6:  // BTM_BLE_PF_SRVC_DATA_PATTERN
    {
      jbyte* data_array = env->GetByteArrayElements(data, 0);
      int data_len = env->GetArrayLength(data);
      std::vector<uint8_t> vec_data(data_array, data_array + data_len);
      env->ReleaseByteArrayElements(data, data_array, JNI_ABORT);

      jbyte* mask_array = env->GetByteArrayElements(mask, NULL);
      uint16_t mask_len = (uint16_t)env->GetArrayLength(mask);
      std::vector<uint8_t> vec_mask(mask_array, mask_array + mask_len);
      env->ReleaseByteArrayElements(mask, mask_array, JNI_ABORT);

      sGattIf->scanner->ScanFilterAddRemove(
          action, filt_type, filt_index, company_id, company_id_mask, NULL,
          NULL, NULL, 0, std::move(vec_data), std::move(vec_mask),
          base::Bind(&scan_filter_cfg_cb, client_if));
      break;
    }

    default:
      break;
  }
}

static void gattClientScanFilterAddNative(
    JNIEnv* env, jobject object, jint client_if, jint filt_type,
    jint filt_index, jint company_id, jint company_id_mask, jlong uuid_lsb,
    jlong uuid_msb, jlong uuid_mask_lsb, jlong uuid_mask_msb, jstring name,
    jstring address, jbyte addr_type, jbyteArray data, jbyteArray mask) {
  if (!sGattIf) return;
  int action = 0;
  gattClientScanFilterAddRemoveNative(
      env, object, client_if, action, filt_type, filt_index, company_id,
      company_id_mask, uuid_lsb, uuid_msb, uuid_mask_lsb, uuid_mask_msb, name,
      address, addr_type, data, mask);
}

static void gattClientScanFilterDeleteNative(
    JNIEnv* env, jobject object, jint client_if, jint filt_type,
    jint filt_index, jint company_id, jint company_id_mask, jlong uuid_lsb,
    jlong uuid_msb, jlong uuid_mask_lsb, jlong uuid_mask_msb, jstring name,
    jstring address, jbyte addr_type, jbyteArray data, jbyteArray mask) {
  if (!sGattIf) return;
  int action = 1;
  gattClientScanFilterAddRemoveNative(
      env, object, client_if, action, filt_type, filt_index, company_id,
      company_id_mask, uuid_lsb, uuid_msb, uuid_mask_lsb, uuid_mask_msb, name,
      address, addr_type, data, mask);
}

static void gattClientScanFilterClearNative(JNIEnv* env, jobject object,
                                            jint client_if, jint filt_index) {
  if (!sGattIf) return;
  sGattIf->scanner->ScanFilterClear(filt_index,
                                    base::Bind(&scan_filter_cfg_cb, client_if));
}

void scan_enable_cb(uint8_t client_if, uint8_t action, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onScanFilterEnableDisabled,
                               action, status, client_if);
}

static void gattClientScanFilterEnableNative(JNIEnv* env, jobject object,
                                             jint client_if, jboolean enable) {
  if (!sGattIf) return;
  sGattIf->scanner->ScanFilterEnable(enable,
                                     base::Bind(&scan_enable_cb, client_if));
}

static void gattClientConfigureMTUNative(JNIEnv* env, jobject object,
                                         jint conn_id, jint mtu) {
  if (!sGattIf) return;
  sGattIf->client->configure_mtu(conn_id, mtu);
}

static void gattConnectionParameterUpdateNative(JNIEnv* env, jobject object,
                                                jint client_if, jstring address,
                                                jint min_interval,
                                                jint max_interval, jint latency,
                                                jint timeout) {
  if (!sGattIf) return;
  sGattIf->client->conn_parameter_update(str2addr(env, address), min_interval,
                                         max_interval, latency, timeout);
}

void batchscan_cfg_storage_cb(uint8_t client_if, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(
      mCallbacksObj, method_onBatchScanStorageConfigured, status, client_if);
}

static void gattClientConfigBatchScanStorageNative(
    JNIEnv* env, jobject object, jint client_if, jint max_full_reports_percent,
    jint max_trunc_reports_percent, jint notify_threshold_level_percent) {
  if (!sGattIf) return;
  sGattIf->scanner->BatchscanConfigStorage(
      client_if, max_full_reports_percent, max_trunc_reports_percent,
      notify_threshold_level_percent,
      base::Bind(&batchscan_cfg_storage_cb, client_if));
}

void batchscan_enable_cb(uint8_t client_if, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onBatchScanStartStopped,
                               0 /* unused */, status, client_if);
}

static void gattClientStartBatchScanNative(JNIEnv* env, jobject object,
                                           jint client_if, jint scan_mode,
                                           jint scan_interval_unit,
                                           jint scan_window_unit,
                                           jint addr_type, jint discard_rule) {
  if (!sGattIf) return;
  sGattIf->scanner->BatchscanEnable(
      scan_mode, scan_interval_unit, scan_window_unit, addr_type, discard_rule,
      base::Bind(&batchscan_enable_cb, client_if));
}

static void gattClientStopBatchScanNative(JNIEnv* env, jobject object,
                                          jint client_if) {
  if (!sGattIf) return;
  sGattIf->scanner->BatchscanDisable(
      base::Bind(&batchscan_enable_cb, client_if));
}

static void gattClientReadScanReportsNative(JNIEnv* env, jobject object,
                                            jint client_if, jint scan_type) {
  if (!sGattIf) return;
  sGattIf->scanner->BatchscanReadReports(client_if, scan_type);
}

/**
 * Native server functions
 */
static void gattServerRegisterAppNative(JNIEnv* env, jobject object,
                                        jlong app_uuid_lsb,
                                        jlong app_uuid_msb) {
  bt_uuid_t uuid;
  if (!sGattIf) return;
  set_uuid(uuid.uu, app_uuid_msb, app_uuid_lsb);
  sGattIf->server->register_server(uuid);
}

static void gattServerUnregisterAppNative(JNIEnv* env, jobject object,
                                          jint serverIf) {
  if (!sGattIf) return;
  sGattIf->server->unregister_server(serverIf);
}

static void gattServerConnectNative(JNIEnv* env, jobject object, jint server_if,
                                    jstring address, jboolean is_direct,
                                    jint transport) {
  if (!sGattIf) return;

  RawAddress bd_addr = str2addr(env, address);
  sGattIf->server->connect(server_if, bd_addr, is_direct, transport);
}

static void gattServerDisconnectNative(JNIEnv* env, jobject object,
                                       jint serverIf, jstring address,
                                       jint conn_id) {
  if (!sGattIf) return;
  sGattIf->server->disconnect(serverIf, str2addr(env, address), conn_id);
}

static void gattServerSetPreferredPhyNative(JNIEnv* env, jobject object,
                                            jint serverIf, jstring address,
                                            jint tx_phy, jint rx_phy,
                                            jint phy_options) {
  if (!sGattIf) return;
  RawAddress bda = str2addr(env, address);
  sGattIf->server->set_preferred_phy(bda, tx_phy, rx_phy, phy_options);
}

static void readServerPhyCb(uint8_t serverIf, RawAddress bda, uint8_t tx_phy,
                            uint8_t rx_phy, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> address(sCallbackEnv.get(),
                                  bdaddr2newjstr(sCallbackEnv.get(), &bda));

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onServerPhyRead, serverIf,
                               address.get(), tx_phy, rx_phy, status);
}

static void gattServerReadPhyNative(JNIEnv* env, jobject object, jint serverIf,
                                    jstring address) {
  if (!sGattIf) return;

  RawAddress bda = str2addr(env, address);
  sGattIf->server->read_phy(bda, base::Bind(&readServerPhyCb, serverIf, bda));
}

static void gattServerAddServiceNative(JNIEnv* env, jobject object,
                                       jint server_if,
                                       jobject gatt_db_elements) {
  if (!sGattIf) return;

  jclass arrayListclazz = env->FindClass("java/util/List");
  jmethodID arrayGet =
      env->GetMethodID(arrayListclazz, "get", "(I)Ljava/lang/Object;");
  jmethodID arraySize = env->GetMethodID(arrayListclazz, "size", "()I");

  int count = env->CallIntMethod(gatt_db_elements, arraySize);
  std::vector<btgatt_db_element_t> db;

  jclass uuidClazz = env->FindClass("java/util/UUID");
  jmethodID uuidGetMsb =
      env->GetMethodID(uuidClazz, "getMostSignificantBits", "()J");
  jmethodID uuidGetLsb =
      env->GetMethodID(uuidClazz, "getLeastSignificantBits", "()J");

  jobject objectForClass =
      env->CallObjectMethod(mCallbacksObj, method_getSampleGattDbElement);
  jclass gattDbElementClazz = env->GetObjectClass(objectForClass);

  for (int i = 0; i < count; i++) {
    btgatt_db_element_t curr;

    jint index = i;
    ScopedLocalRef<jobject> element(
        env, env->CallObjectMethod(gatt_db_elements, arrayGet, index));

    jfieldID fid;

    fid = env->GetFieldID(gattDbElementClazz, "id", "I");
    curr.id = env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "uuid", "Ljava/util/UUID;");
    ScopedLocalRef<jobject> uuid(env, env->GetObjectField(element.get(), fid));
    if (uuid.get() != NULL) {
      jlong uuid_msb = env->CallLongMethod(uuid.get(), uuidGetMsb);
      jlong uuid_lsb = env->CallLongMethod(uuid.get(), uuidGetLsb);
      set_uuid(curr.uuid.uu, uuid_msb, uuid_lsb);
    }

    fid = env->GetFieldID(gattDbElementClazz, "type", "I");
    curr.type =
        (bt_gatt_db_attribute_type_t)env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "attributeHandle", "I");
    curr.attribute_handle = env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "startHandle", "I");
    curr.start_handle = env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "endHandle", "I");
    curr.end_handle = env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "properties", "I");
    curr.properties = env->GetIntField(element.get(), fid);

    fid = env->GetFieldID(gattDbElementClazz, "permissions", "I");
    curr.permissions = env->GetIntField(element.get(), fid);

    db.push_back(curr);
  }

  sGattIf->server->add_service(server_if, std::move(db));
}

static void gattServerStopServiceNative(JNIEnv* env, jobject object,
                                        jint server_if, jint svc_handle) {
  if (!sGattIf) return;
  sGattIf->server->stop_service(server_if, svc_handle);
}

static void gattServerDeleteServiceNative(JNIEnv* env, jobject object,
                                          jint server_if, jint svc_handle) {
  if (!sGattIf) return;
  sGattIf->server->delete_service(server_if, svc_handle);
}

static void gattServerSendIndicationNative(JNIEnv* env, jobject object,
                                           jint server_if, jint attr_handle,
                                           jint conn_id, jbyteArray val) {
  if (!sGattIf) return;

  jbyte* array = env->GetByteArrayElements(val, 0);
  int val_len = env->GetArrayLength(val);

  std::vector<uint8_t> vect_val((uint8_t*)array, (uint8_t*)array + val_len);
  env->ReleaseByteArrayElements(val, array, JNI_ABORT);

  sGattIf->server->send_indication(server_if, attr_handle, conn_id,
                                   /*confirm*/ 1, std::move(vect_val));
}

static void gattServerSendNotificationNative(JNIEnv* env, jobject object,
                                             jint server_if, jint attr_handle,
                                             jint conn_id, jbyteArray val) {
  if (!sGattIf) return;

  jbyte* array = env->GetByteArrayElements(val, 0);
  int val_len = env->GetArrayLength(val);

  std::vector<uint8_t> vect_val((uint8_t*)array, (uint8_t*)array + val_len);
  env->ReleaseByteArrayElements(val, array, JNI_ABORT);

  sGattIf->server->send_indication(server_if, attr_handle, conn_id,
                                   /*confirm*/ 0, std::move(vect_val));
}

static void gattServerSendResponseNative(JNIEnv* env, jobject object,
                                         jint server_if, jint conn_id,
                                         jint trans_id, jint status,
                                         jint handle, jint offset,
                                         jbyteArray val, jint auth_req) {
  if (!sGattIf) return;

  btgatt_response_t response;

  response.attr_value.handle = handle;
  response.attr_value.auth_req = auth_req;
  response.attr_value.offset = offset;
  response.attr_value.len = 0;

  if (val != NULL) {
    response.attr_value.len = (uint16_t)env->GetArrayLength(val);
    jbyte* array = env->GetByteArrayElements(val, 0);

    for (int i = 0; i != response.attr_value.len; ++i)
      response.attr_value.value[i] = (uint8_t)array[i];
    env->ReleaseByteArrayElements(val, array, JNI_ABORT);
  }

  sGattIf->server->send_response(conn_id, trans_id, status, response);
}

static void advertiseClassInitNative(JNIEnv* env, jclass clazz) {
  method_onAdvertisingSetStarted =
      env->GetMethodID(clazz, "onAdvertisingSetStarted", "(IIII)V");
  method_onOwnAddressRead =
      env->GetMethodID(clazz, "onOwnAddressRead", "(IILjava/lang/String;)V");
  method_onAdvertisingEnabled =
      env->GetMethodID(clazz, "onAdvertisingEnabled", "(IZI)V");
  method_onAdvertisingDataSet =
      env->GetMethodID(clazz, "onAdvertisingDataSet", "(II)V");
  method_onScanResponseDataSet =
      env->GetMethodID(clazz, "onScanResponseDataSet", "(II)V");
  method_onAdvertisingParametersUpdated =
      env->GetMethodID(clazz, "onAdvertisingParametersUpdated", "(III)V");
  method_onPeriodicAdvertisingParametersUpdated = env->GetMethodID(
      clazz, "onPeriodicAdvertisingParametersUpdated", "(II)V");
  method_onPeriodicAdvertisingDataSet =
      env->GetMethodID(clazz, "onPeriodicAdvertisingDataSet", "(II)V");
  method_onPeriodicAdvertisingEnabled =
      env->GetMethodID(clazz, "onPeriodicAdvertisingEnabled", "(IZI)V");
}

static void advertiseInitializeNative(JNIEnv* env, jobject object) {
  if (mAdvertiseCallbacksObj != NULL) {
    ALOGW("Cleaning up Advertise callback object");
    env->DeleteGlobalRef(mAdvertiseCallbacksObj);
    mAdvertiseCallbacksObj = NULL;
  }

  mAdvertiseCallbacksObj = env->NewGlobalRef(object);
}

static void advertiseCleanupNative(JNIEnv* env, jobject object) {
  if (mAdvertiseCallbacksObj != NULL) {
    env->DeleteGlobalRef(mAdvertiseCallbacksObj);
    mAdvertiseCallbacksObj = NULL;
  }
}

static uint32_t INTERVAL_MAX = 0xFFFFFF;
// Always give controller 31.25ms difference between min and max
static uint32_t INTERVAL_DELTA = 50;

static AdvertiseParameters parseParams(JNIEnv* env, jobject i) {
  AdvertiseParameters p;

  jclass clazz = env->GetObjectClass(i);
  jmethodID methodId;

  methodId = env->GetMethodID(clazz, "isConnectable", "()Z");
  jboolean isConnectable = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "isScannable", "()Z");
  jboolean isScannable = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "isLegacy", "()Z");
  jboolean isLegacy = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "isAnonymous", "()Z");
  jboolean isAnonymous = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "includeTxPower", "()Z");
  jboolean includeTxPower = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "getPrimaryPhy", "()I");
  uint8_t primaryPhy = env->CallIntMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "getSecondaryPhy", "()I");
  uint8_t secondaryPhy = env->CallIntMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "getInterval", "()I");
  uint32_t interval = env->CallIntMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "getTxPowerLevel", "()I");
  int8_t txPowerLevel = env->CallIntMethod(i, methodId);

  uint16_t props = 0;
  if (isConnectable) props |= 0x01;
  if (isScannable) props |= 0x02;
  if (isLegacy) props |= 0x10;
  if (isAnonymous) props |= 0x20;
  if (includeTxPower) props |= 0x40;

  if (interval > INTERVAL_MAX - INTERVAL_DELTA) {
    interval = INTERVAL_MAX - INTERVAL_DELTA;
  }

  p.advertising_event_properties = props;
  p.min_interval = interval;
  p.max_interval = interval + INTERVAL_DELTA;
  p.channel_map = 0x07; /* all channels */
  p.tx_power = txPowerLevel;
  p.primary_advertising_phy = primaryPhy;
  p.secondary_advertising_phy = secondaryPhy;
  p.scan_request_notification_enable = false;
  return p;
}

static PeriodicAdvertisingParameters parsePeriodicParams(JNIEnv* env,
                                                         jobject i) {
  PeriodicAdvertisingParameters p;

  if (i == NULL) {
    p.enable = false;
    return p;
  }

  jclass clazz = env->GetObjectClass(i);
  jmethodID methodId;

  methodId = env->GetMethodID(clazz, "getIncludeTxPower", "()Z");
  jboolean includeTxPower = env->CallBooleanMethod(i, methodId);
  methodId = env->GetMethodID(clazz, "getInterval", "()I");
  uint16_t interval = env->CallIntMethod(i, methodId);

  p.enable = true;
  p.min_interval = interval;
  p.max_interval = interval + 16; /* 20ms difference betwen min and max */
  uint16_t props = 0;
  if (includeTxPower) props |= 0x40;
  p.periodic_advertising_properties = props;
  return p;
}

static void ble_advertising_set_started_cb(int reg_id, uint8_t advertiser_id,
                                           int8_t tx_power, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj,
                               method_onAdvertisingSetStarted, reg_id,
                               advertiser_id, tx_power, status);
}

static void ble_advertising_set_timeout_cb(uint8_t advertiser_id,
                                           uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj,
                               method_onAdvertisingEnabled, advertiser_id,
                               false, status);
}

static void startAdvertisingSetNative(JNIEnv* env, jobject object,
                                      jobject parameters, jbyteArray adv_data,
                                      jbyteArray scan_resp,
                                      jobject periodic_parameters,
                                      jbyteArray periodic_data, jint duration,
                                      jint maxExtAdvEvents, jint reg_id) {
  if (!sGattIf) return;

  jbyte* scan_resp_data = env->GetByteArrayElements(scan_resp, NULL);
  uint16_t scan_resp_len = (uint16_t)env->GetArrayLength(scan_resp);
  std::vector<uint8_t> scan_resp_vec(scan_resp_data,
                                     scan_resp_data + scan_resp_len);
  env->ReleaseByteArrayElements(scan_resp, scan_resp_data, JNI_ABORT);

  AdvertiseParameters params = parseParams(env, parameters);
  PeriodicAdvertisingParameters periodicParams =
      parsePeriodicParams(env, periodic_parameters);

  jbyte* adv_data_data = env->GetByteArrayElements(adv_data, NULL);
  uint16_t adv_data_len = (uint16_t)env->GetArrayLength(adv_data);
  std::vector<uint8_t> data_vec(adv_data_data, adv_data_data + adv_data_len);
  env->ReleaseByteArrayElements(adv_data, adv_data_data, JNI_ABORT);

  jbyte* periodic_data_data = env->GetByteArrayElements(periodic_data, NULL);
  uint16_t periodic_data_len = (uint16_t)env->GetArrayLength(periodic_data);
  std::vector<uint8_t> periodic_data_vec(
      periodic_data_data, periodic_data_data + periodic_data_len);
  env->ReleaseByteArrayElements(periodic_data, periodic_data_data, JNI_ABORT);

  sGattIf->advertiser->StartAdvertisingSet(
      base::Bind(&ble_advertising_set_started_cb, reg_id), params, data_vec,
      scan_resp_vec, periodicParams, periodic_data_vec, duration,
      maxExtAdvEvents, base::Bind(ble_advertising_set_timeout_cb));
}

static void stopAdvertisingSetNative(JNIEnv* env, jobject object,
                                     jint advertiser_id) {
  if (!sGattIf) return;

  sGattIf->advertiser->Unregister(advertiser_id);
}

static void getOwnAddressCb(uint8_t advertiser_id, uint8_t address_type,
                            RawAddress address) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jstring> addr(sCallbackEnv.get(),
                               bdaddr2newjstr(sCallbackEnv.get(), &address));
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj, method_onOwnAddressRead,
                               advertiser_id, address_type, addr.get());
}

static void getOwnAddressNative(JNIEnv* env, jobject object,
                                jint advertiser_id) {
  if (!sGattIf) return;
  sGattIf->advertiser->GetOwnAddress(
      advertiser_id, base::Bind(&getOwnAddressCb, advertiser_id));
}

static void callJniCallback(jmethodID method, uint8_t advertiser_id,
                            uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj, method, advertiser_id,
                               status);
}

static void enableSetCb(uint8_t advertiser_id, bool enable, uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj,
                               method_onAdvertisingEnabled, advertiser_id,
                               enable, status);
}

static void enableAdvertisingSetNative(JNIEnv* env, jobject object,
                                       jint advertiser_id, jboolean enable,
                                       jint duration, jint maxExtAdvEvents) {
  if (!sGattIf) return;

  sGattIf->advertiser->Enable(advertiser_id, enable,
                              base::Bind(&enableSetCb, advertiser_id, enable),
                              duration, maxExtAdvEvents,
                              base::Bind(&enableSetCb, advertiser_id, false));
}

static void setAdvertisingDataNative(JNIEnv* env, jobject object,
                                     jint advertiser_id, jbyteArray data) {
  if (!sGattIf) return;

  sGattIf->advertiser->SetData(
      advertiser_id, false, toVector(env, data),
      base::Bind(&callJniCallback, method_onAdvertisingDataSet, advertiser_id));
}

static void setScanResponseDataNative(JNIEnv* env, jobject object,
                                      jint advertiser_id, jbyteArray data) {
  if (!sGattIf) return;

  sGattIf->advertiser->SetData(
      advertiser_id, true, toVector(env, data),
      base::Bind(&callJniCallback, method_onScanResponseDataSet,
                 advertiser_id));
}

static void setAdvertisingParametersNativeCb(uint8_t advertiser_id,
                                             uint8_t status, int8_t tx_power) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj,
                               method_onAdvertisingParametersUpdated,
                               advertiser_id, tx_power, status);
}

static void setAdvertisingParametersNative(JNIEnv* env, jobject object,
                                           jint advertiser_id,
                                           jobject parameters) {
  if (!sGattIf) return;

  AdvertiseParameters params = parseParams(env, parameters);
  sGattIf->advertiser->SetParameters(
      advertiser_id, params,
      base::Bind(&setAdvertisingParametersNativeCb, advertiser_id));
}

static void setPeriodicAdvertisingParametersNative(
    JNIEnv* env, jobject object, jint advertiser_id,
    jobject periodic_parameters) {
  if (!sGattIf) return;

  PeriodicAdvertisingParameters periodicParams =
      parsePeriodicParams(env, periodic_parameters);
  sGattIf->advertiser->SetPeriodicAdvertisingParameters(
      advertiser_id, periodicParams,
      base::Bind(&callJniCallback,
                 method_onPeriodicAdvertisingParametersUpdated, advertiser_id));
}

static void setPeriodicAdvertisingDataNative(JNIEnv* env, jobject object,
                                             jint advertiser_id,
                                             jbyteArray data) {
  if (!sGattIf) return;

  sGattIf->advertiser->SetPeriodicAdvertisingData(
      advertiser_id, toVector(env, data),
      base::Bind(&callJniCallback, method_onPeriodicAdvertisingDataSet,
                 advertiser_id));
}

static void enablePeriodicSetCb(uint8_t advertiser_id, bool enable,
                                uint8_t status) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  sCallbackEnv->CallVoidMethod(mAdvertiseCallbacksObj,
                               method_onPeriodicAdvertisingEnabled,
                               advertiser_id, enable, status);
}

static void setPeriodicAdvertisingEnableNative(JNIEnv* env, jobject object,
                                               jint advertiser_id,
                                               jboolean enable) {
  if (!sGattIf) return;

  sGattIf->advertiser->SetPeriodicAdvertisingEnable(
      advertiser_id, enable,
      base::Bind(&enablePeriodicSetCb, advertiser_id, enable));
}

static void periodicScanClassInitNative(JNIEnv* env, jclass clazz) {
  method_onSyncStarted =
      env->GetMethodID(clazz, "onSyncStarted", "(IIIILjava/lang/String;III)V");
  method_onSyncReport = env->GetMethodID(clazz, "onSyncReport", "(IIII[B)V");
  method_onSyncLost = env->GetMethodID(clazz, "onSyncLost", "(I)V");
}

static void periodicScanInitializeNative(JNIEnv* env, jobject object) {
  if (mPeriodicScanCallbacksObj != NULL) {
    ALOGW("Cleaning up periodic scan callback object");
    env->DeleteGlobalRef(mPeriodicScanCallbacksObj);
    mPeriodicScanCallbacksObj = NULL;
  }

  mPeriodicScanCallbacksObj = env->NewGlobalRef(object);
}

static void periodicScanCleanupNative(JNIEnv* env, jobject object) {
  if (mPeriodicScanCallbacksObj != NULL) {
    env->DeleteGlobalRef(mPeriodicScanCallbacksObj);
    mPeriodicScanCallbacksObj = NULL;
  }
}

static void onSyncStarted(int reg_id, uint8_t status, uint16_t sync_handle,
                          uint8_t sid, uint8_t address_type, RawAddress address,
                          uint8_t phy, uint16_t interval) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mPeriodicScanCallbacksObj, method_onSyncStarted,
                               reg_id, sync_handle, sid, address_type, address,
                               phy, interval, status);
}

static void onSyncReport(uint16_t sync_handle, int8_t tx_power, int8_t rssi,
                         uint8_t data_status, std::vector<uint8_t> data) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  ScopedLocalRef<jbyteArray> jb(sCallbackEnv.get(),
                                sCallbackEnv->NewByteArray(data.size()));
  sCallbackEnv->SetByteArrayRegion(jb.get(), 0, data.size(),
                                   (jbyte*)data.data());

  sCallbackEnv->CallVoidMethod(mPeriodicScanCallbacksObj, method_onSyncReport,
                               sync_handle, tx_power, rssi, data_status,
                               jb.get());
}

static void onSyncLost(uint16_t sync_handle) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  sCallbackEnv->CallVoidMethod(mPeriodicScanCallbacksObj, method_onSyncLost,
                               sync_handle);
}

static void startSyncNative(JNIEnv* env, jobject object, jint sid,
                            jstring address, jint skip, jint timeout,
                            jint reg_id) {
  if (!sGattIf) return;

  sGattIf->scanner->StartSync(sid, str2addr(env, address), skip, timeout,
                              base::Bind(&onSyncStarted, reg_id),
                              base::Bind(&onSyncReport),
                              base::Bind(&onSyncLost));
}

static void stopSyncNative(int sync_handle) {
  if (!sGattIf) return;

  sGattIf->scanner->StopSync(sync_handle);
}

static void gattTestNative(JNIEnv* env, jobject object, jint command,
                           jlong uuid1_lsb, jlong uuid1_msb, jstring bda1,
                           jint p1, jint p2, jint p3, jint p4, jint p5) {
  if (!sGattIf) return;

  RawAddress bt_bda1 = str2addr(env, bda1);

  bt_uuid_t uuid1;
  set_uuid(uuid1.uu, uuid1_msb, uuid1_lsb);

  btgatt_test_params_t params;
  params.bda1 = &bt_bda1;
  params.uuid1 = &uuid1;
  params.u1 = p1;
  params.u2 = p2;
  params.u3 = p3;
  params.u4 = p4;
  params.u5 = p5;
  sGattIf->client->test_command(command, params);
}

/**
 * JNI function definitinos
 */

// JNI functions defined in AdvertiseManager class.
static JNINativeMethod sAdvertiseMethods[] = {
    {"classInitNative", "()V", (void*)advertiseClassInitNative},
    {"initializeNative", "()V", (void*)advertiseInitializeNative},
    {"cleanupNative", "()V", (void*)advertiseCleanupNative},
    {"startAdvertisingSetNative",
     "(Landroid/bluetooth/le/AdvertisingSetParameters;[B[BLandroid/bluetooth/"
     "le/PeriodicAdvertisingParameters;[BIII)V",
     (void*)startAdvertisingSetNative},
    {"getOwnAddressNative", "(I)V", (void*)getOwnAddressNative},
    {"stopAdvertisingSetNative", "(I)V", (void*)stopAdvertisingSetNative},
    {"enableAdvertisingSetNative", "(IZII)V",
     (void*)enableAdvertisingSetNative},
    {"setAdvertisingDataNative", "(I[B)V", (void*)setAdvertisingDataNative},
    {"setScanResponseDataNative", "(I[B)V", (void*)setScanResponseDataNative},
    {"setAdvertisingParametersNative",
     "(ILandroid/bluetooth/le/AdvertisingSetParameters;)V",
     (void*)setAdvertisingParametersNative},
    {"setPeriodicAdvertisingParametersNative",
     "(ILandroid/bluetooth/le/PeriodicAdvertisingParameters;)V",
     (void*)setPeriodicAdvertisingParametersNative},
    {"setPeriodicAdvertisingDataNative", "(I[B)V",
     (void*)setPeriodicAdvertisingDataNative},
    {"setPeriodicAdvertisingEnableNative", "(IZ)V",
     (void*)setPeriodicAdvertisingEnableNative},
};

// JNI functions defined in PeriodicScanManager class.
static JNINativeMethod sPeriodicScanMethods[] = {
    {"classInitNative", "()V", (void*)periodicScanClassInitNative},
    {"initializeNative", "()V", (void*)periodicScanInitializeNative},
    {"cleanupNative", "()V", (void*)periodicScanCleanupNative},
    {"startSyncNative", "(ILjava/lang/String;III)V", (void*)startSyncNative},
    {"stopSyncNative", "(I)V", (void*)stopSyncNative},
};

// JNI functions defined in ScanManager class.
static JNINativeMethod sScanMethods[] = {
    {"registerScannerNative", "(JJ)V", (void*)registerScannerNative},
    {"unregisterScannerNative", "(I)V", (void*)unregisterScannerNative},
    {"gattClientScanNative", "(Z)V", (void*)gattClientScanNative},
    // Batch scan JNI functions.
    {"gattClientConfigBatchScanStorageNative", "(IIII)V",
     (void*)gattClientConfigBatchScanStorageNative},
    {"gattClientStartBatchScanNative", "(IIIIII)V",
     (void*)gattClientStartBatchScanNative},
    {"gattClientStopBatchScanNative", "(I)V",
     (void*)gattClientStopBatchScanNative},
    {"gattClientReadScanReportsNative", "(II)V",
     (void*)gattClientReadScanReportsNative},
    // Scan filter JNI functions.
    {"gattClientScanFilterParamAddNative",
     "(Lcom/android/bluetooth/gatt/FilterParams;)V",
     (void*)gattClientScanFilterParamAddNative},
    {"gattClientScanFilterParamDeleteNative", "(II)V",
     (void*)gattClientScanFilterParamDeleteNative},
    {"gattClientScanFilterParamClearAllNative", "(I)V",
     (void*)gattClientScanFilterParamClearAllNative},
    {"gattClientScanFilterAddNative",
     "(IIIIIJJJJLjava/lang/String;Ljava/lang/String;B[B[B)V",
     (void*)gattClientScanFilterAddNative},
    {"gattClientScanFilterDeleteNative",
     "(IIIIIJJJJLjava/lang/String;Ljava/lang/String;B[B[B)V",
     (void*)gattClientScanFilterDeleteNative},
    {"gattClientScanFilterClearNative", "(II)V",
     (void*)gattClientScanFilterClearNative},
    {"gattClientScanFilterEnableNative", "(IZ)V",
     (void*)gattClientScanFilterEnableNative},
    {"gattSetScanParametersNative", "(III)V",
     (void*)gattSetScanParametersNative},
};

// JNI functions defined in GattService class.
static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "()V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"gattClientGetDeviceTypeNative", "(Ljava/lang/String;)I",
     (void*)gattClientGetDeviceTypeNative},
    {"gattClientRegisterAppNative", "(JJ)V",
     (void*)gattClientRegisterAppNative},
    {"gattClientUnregisterAppNative", "(I)V",
     (void*)gattClientUnregisterAppNative},
    {"gattClientConnectNative", "(ILjava/lang/String;ZIZI)V",
     (void*)gattClientConnectNative},
    {"gattClientDisconnectNative", "(ILjava/lang/String;I)V",
     (void*)gattClientDisconnectNative},
    {"gattClientSetPreferredPhyNative", "(ILjava/lang/String;III)V",
     (void*)gattClientSetPreferredPhyNative},
    {"gattClientReadPhyNative", "(ILjava/lang/String;)V",
     (void*)gattClientReadPhyNative},
    {"gattClientRefreshNative", "(ILjava/lang/String;)V",
     (void*)gattClientRefreshNative},
    {"gattClientSearchServiceNative", "(IZJJ)V",
     (void*)gattClientSearchServiceNative},
    {"gattClientDiscoverServiceByUuidNative", "(IJJ)V",
     (void*)gattClientDiscoverServiceByUuidNative},
    {"gattClientGetGattDbNative", "(I)V", (void*)gattClientGetGattDbNative},
    {"gattClientReadCharacteristicNative", "(III)V",
     (void*)gattClientReadCharacteristicNative},
    {"gattClientReadUsingCharacteristicUuidNative", "(IJJIII)V",
     (void*)gattClientReadUsingCharacteristicUuidNative},
    {"gattClientReadDescriptorNative", "(III)V",
     (void*)gattClientReadDescriptorNative},
    {"gattClientWriteCharacteristicNative", "(IIII[B)V",
     (void*)gattClientWriteCharacteristicNative},
    {"gattClientWriteDescriptorNative", "(III[B)V",
     (void*)gattClientWriteDescriptorNative},
    {"gattClientExecuteWriteNative", "(IZ)V",
     (void*)gattClientExecuteWriteNative},
    {"gattClientRegisterForNotificationsNative", "(ILjava/lang/String;IZ)V",
     (void*)gattClientRegisterForNotificationsNative},
    {"gattClientReadRemoteRssiNative", "(ILjava/lang/String;)V",
     (void*)gattClientReadRemoteRssiNative},
    {"gattClientConfigureMTUNative", "(II)V",
     (void*)gattClientConfigureMTUNative},
    {"gattConnectionParameterUpdateNative", "(ILjava/lang/String;IIII)V",
     (void*)gattConnectionParameterUpdateNative},
    {"gattServerRegisterAppNative", "(JJ)V",
     (void*)gattServerRegisterAppNative},
    {"gattServerUnregisterAppNative", "(I)V",
     (void*)gattServerUnregisterAppNative},
    {"gattServerConnectNative", "(ILjava/lang/String;ZI)V",
     (void*)gattServerConnectNative},
    {"gattServerDisconnectNative", "(ILjava/lang/String;I)V",
     (void*)gattServerDisconnectNative},
    {"gattServerSetPreferredPhyNative", "(ILjava/lang/String;III)V",
     (void*)gattServerSetPreferredPhyNative},
    {"gattServerReadPhyNative", "(ILjava/lang/String;)V",
     (void*)gattServerReadPhyNative},
    {"gattServerAddServiceNative", "(ILjava/util/List;)V",
     (void*)gattServerAddServiceNative},
    {"gattServerStopServiceNative", "(II)V",
     (void*)gattServerStopServiceNative},
    {"gattServerDeleteServiceNative", "(II)V",
     (void*)gattServerDeleteServiceNative},
    {"gattServerSendIndicationNative", "(III[B)V",
     (void*)gattServerSendIndicationNative},
    {"gattServerSendNotificationNative", "(III[B)V",
     (void*)gattServerSendNotificationNative},
    {"gattServerSendResponseNative", "(IIIIII[BI)V",
     (void*)gattServerSendResponseNative},

    {"gattTestNative", "(IJJLjava/lang/String;IIIII)V", (void*)gattTestNative},
};

int register_com_android_bluetooth_gatt(JNIEnv* env) {
  int register_success = jniRegisterNativeMethods(
      env, "com/android/bluetooth/gatt/ScanManager$ScanNative", sScanMethods,
      NELEM(sScanMethods));
  register_success &= jniRegisterNativeMethods(
      env, "com/android/bluetooth/gatt/AdvertiseManager", sAdvertiseMethods,
      NELEM(sAdvertiseMethods));
  register_success &= jniRegisterNativeMethods(
      env, "com/android/bluetooth/gatt/PeriodicScanManager",
      sPeriodicScanMethods, NELEM(sPeriodicScanMethods));
  return register_success &
         jniRegisterNativeMethods(env, "com/android/bluetooth/gatt/GattService",
                                  sMethods, NELEM(sMethods));
}
}
