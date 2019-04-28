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

#define LOG_TAG "BluetoothAvrcpServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_rc.h"
#include "utils/Log.h"

#include <string.h>

namespace android {
static jmethodID method_getRcFeatures;
static jmethodID method_getPlayStatus;
static jmethodID method_getElementAttr;
static jmethodID method_registerNotification;
static jmethodID method_volumeChangeCallback;
static jmethodID method_handlePassthroughCmd;
static jmethodID method_getFolderItemsCallback;
static jmethodID method_setAddressedPlayerCallback;

static jmethodID method_setBrowsedPlayerCallback;
static jmethodID method_changePathCallback;
static jmethodID method_searchCallback;
static jmethodID method_playItemCallback;
static jmethodID method_getItemAttrCallback;
static jmethodID method_addToPlayListCallback;
static jmethodID method_getTotalNumOfItemsCallback;

static const btrc_interface_t* sBluetoothAvrcpInterface = NULL;
static jobject mCallbacksObj = NULL;

/* Function declarations */
static bool copy_item_attributes(JNIEnv* env, jobject object,
                                 btrc_folder_items_t* pitem,
                                 jint* p_attributesIds,
                                 jobjectArray attributesArray, int item_idx,
                                 int attribCopiedIndex);

static bool copy_jstring(uint8_t* str, int maxBytes, jstring jstr, JNIEnv* env);

static void cleanup_items(btrc_folder_items_t* p_items, int numItems);

static void btavrcp_remote_features_callback(RawAddress* bd_addr,
                                             btrc_remote_features_t features) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Unable to allocate byte array for bd_addr");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getRcFeatures, addr.get(),
                               (jint)features);
}

/** Callback for play status request */
static void btavrcp_get_play_status_callback(RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get_play_status command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getPlayStatus, addr.get());
}

static void btavrcp_get_element_attr_callback(uint8_t num_attr,
                                              btrc_media_attr_t* p_attrs,
                                              RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get_element_attr command");
    return;
  }

  ScopedLocalRef<jintArray> attrs(
      sCallbackEnv.get(), (jintArray)sCallbackEnv->NewIntArray(num_attr));
  if (!attrs.get()) {
    ALOGE("Fail to new jintArray for attrs");
    return;
  }

  sCallbackEnv->SetIntArrayRegion(attrs.get(), 0, num_attr, (jint*)p_attrs);

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getElementAttr, addr.get(),
                               (jbyte)num_attr, attrs.get());
}

static void btavrcp_register_notification_callback(btrc_event_id_t event_id,
                                                   uint32_t param,
                                                   RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for register_notification command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_registerNotification,
                               addr.get(), (jint)event_id, (jint)param);
}

static void btavrcp_volume_change_callback(uint8_t volume, uint8_t ctype,
                                           RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for volume_change command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_volumeChangeCallback,
                               addr.get(), (jint)volume, (jint)ctype);
}

static void btavrcp_passthrough_command_callback(int id, int pressed,
                                                 RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for passthrough_command command");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handlePassthroughCmd,
                               addr.get(), (jint)id, (jint)pressed);
}

static void btavrcp_set_addressed_player_callback(uint16_t player_id,
                                                  RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for set_addressed_player command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_setAddressedPlayerCallback,
                               addr.get(), (jint)player_id);
}

static void btavrcp_set_browsed_player_callback(uint16_t player_id,
                                                RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for set_browsed_player command");
    return;
  }
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_setBrowsedPlayerCallback,
                               addr.get(), (jint)player_id);
}

static void btavrcp_get_folder_items_callback(
    uint8_t scope, uint32_t start_item, uint32_t end_item, uint8_t num_attr,
    uint32_t* p_attr_ids, RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get_folder_items command");
    return;
  }

  uint32_t* puiAttr = (uint32_t*)p_attr_ids;
  ScopedLocalRef<jintArray> attr_ids(sCallbackEnv.get(), NULL);
  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);

  /* check number of attributes requested by remote device */
  if ((num_attr != BTRC_NUM_ATTR_ALL) && (num_attr != BTRC_NUM_ATTR_NONE)) {
    /* allocate memory for attr_ids only if some attributes passed from below
     * layer */
    attr_ids.reset((jintArray)sCallbackEnv->NewIntArray(num_attr));
    if (!attr_ids.get()) {
      ALOGE("Fail to allocate new jintArray for attrs");
      return;
    }
    sCallbackEnv->SetIntArrayRegion(attr_ids.get(), 0, num_attr,
                                    (jint*)puiAttr);
  }

  sCallbackEnv->CallVoidMethod(
      mCallbacksObj, method_getFolderItemsCallback, addr.get(), (jbyte)scope,
      (jlong)start_item, (jlong)end_item, (jbyte)num_attr, attr_ids.get());
}

static void btavrcp_change_path_callback(uint8_t direction, uint8_t* folder_uid,
                                         RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> attrs(sCallbackEnv.get(),
                                   sCallbackEnv->NewByteArray(BTRC_UID_SIZE));
  if (!attrs.get()) {
    ALOGE("Fail to new jintArray for attrs");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for change_path command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetByteArrayRegion(
      attrs.get(), 0, sizeof(uint8_t) * BTRC_UID_SIZE, (jbyte*)folder_uid);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_changePathCallback,
                               addr.get(), (jbyte)direction, attrs.get());
}

static void btavrcp_get_item_attr_callback(uint8_t scope, uint8_t* uid,
                                           uint16_t uid_counter,
                                           uint8_t num_attr,
                                           btrc_media_attr_t* p_attrs,
                                           RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;

  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> attr_uid(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(BTRC_UID_SIZE));
  if (!attr_uid.get()) {
    ALOGE("Fail to new jintArray for attr_uid");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get_item_attr command");
    return;
  }

  ScopedLocalRef<jintArray> attrs(
      sCallbackEnv.get(), (jintArray)sCallbackEnv->NewIntArray(num_attr));
  if (!attrs.get()) {
    ALOGE("Fail to new jintArray for attrs");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetIntArrayRegion(attrs.get(), 0, num_attr, (jint*)p_attrs);
  sCallbackEnv->SetByteArrayRegion(
      attr_uid.get(), 0, sizeof(uint8_t) * BTRC_UID_SIZE, (jbyte*)uid);

  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getItemAttrCallback,
                               addr.get(), (jbyte)scope, attr_uid.get(),
                               (jint)uid_counter, (jbyte)num_attr, attrs.get());
}

static void btavrcp_play_item_callback(uint8_t scope, uint16_t uid_counter,
                                       uint8_t* uid, RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> attrs(sCallbackEnv.get(),
                                   sCallbackEnv->NewByteArray(BTRC_UID_SIZE));
  if (!attrs.get()) {
    ALOGE("%s: Fail to new jByteArray attrs for play_item command", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for play_item command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetByteArrayRegion(
      attrs.get(), 0, sizeof(uint8_t) * BTRC_UID_SIZE, (jbyte*)uid);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_playItemCallback,
                               addr.get(), (jbyte)scope, (jint)uid_counter,
                               attrs.get());
}

static void btavrcp_get_total_num_items_callback(uint8_t scope,
                                                 RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for get_total_num_items command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getTotalNumOfItemsCallback,
                               addr.get(), (jbyte)scope);
}

static void btavrcp_search_callback(uint16_t charset_id, uint16_t str_len,
                                    uint8_t* p_str, RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> attrs(sCallbackEnv.get(),
                                   sCallbackEnv->NewByteArray(str_len));
  if (!attrs.get()) {
    ALOGE("Fail to new jintArray for attrs");
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for search command");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetByteArrayRegion(attrs.get(), 0, str_len * sizeof(uint8_t),
                                   (jbyte*)p_str);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_searchCallback, addr.get(),
                               (jint)charset_id, attrs.get());
}

static void btavrcp_add_to_play_list_callback(uint8_t scope, uint8_t* uid,
                                              uint16_t uid_counter,
                                              RawAddress* bd_addr) {
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  if (!mCallbacksObj) {
    ALOGE("%s: mCallbacksObj is null", __func__);
    return;
  }

  ScopedLocalRef<jbyteArray> addr(
      sCallbackEnv.get(), sCallbackEnv->NewByteArray(sizeof(RawAddress)));
  if (!addr.get()) {
    ALOGE("Fail to new jbyteArray bd addr for add_to_play_list command");
    return;
  }

  ScopedLocalRef<jbyteArray> attrs(sCallbackEnv.get(),
                                   sCallbackEnv->NewByteArray(BTRC_UID_SIZE));
  if (!attrs.get()) {
    ALOGE("Fail to new jByteArray for attrs");
    return;
  }

  sCallbackEnv->SetByteArrayRegion(addr.get(), 0, sizeof(RawAddress),
                                   (jbyte*)bd_addr);
  sCallbackEnv->SetByteArrayRegion(
      attrs.get(), 0, sizeof(uint8_t) * BTRC_UID_SIZE, (jbyte*)uid);
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_addToPlayListCallback,
                               addr.get(), (jbyte)scope, attrs.get(),
                               (jint)uid_counter);
}

static btrc_callbacks_t sBluetoothAvrcpCallbacks = {
    sizeof(sBluetoothAvrcpCallbacks),
    btavrcp_remote_features_callback,
    btavrcp_get_play_status_callback,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    btavrcp_get_element_attr_callback,
    btavrcp_register_notification_callback,
    btavrcp_volume_change_callback,
    btavrcp_passthrough_command_callback,
    btavrcp_set_addressed_player_callback,
    btavrcp_set_browsed_player_callback,
    btavrcp_get_folder_items_callback,
    btavrcp_change_path_callback,
    btavrcp_get_item_attr_callback,
    btavrcp_play_item_callback,
    btavrcp_get_total_num_items_callback,
    btavrcp_search_callback,
    btavrcp_add_to_play_list_callback,
};

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_getRcFeatures =
      env->GetMethodID(clazz, "getRcFeaturesRequestFromNative", "([BI)V");
  method_getPlayStatus =
      env->GetMethodID(clazz, "getPlayStatusRequestFromNative", "([B)V");

  method_getElementAttr =
      env->GetMethodID(clazz, "getElementAttrRequestFromNative", "([BB[I)V");

  method_registerNotification = env->GetMethodID(
      clazz, "registerNotificationRequestFromNative", "([BII)V");

  method_volumeChangeCallback =
      env->GetMethodID(clazz, "volumeChangeRequestFromNative", "([BII)V");

  method_handlePassthroughCmd = env->GetMethodID(
      clazz, "handlePassthroughCmdRequestFromNative", "([BII)V");

  method_setAddressedPlayerCallback =
      env->GetMethodID(clazz, "setAddressedPlayerRequestFromNative", "([BI)V");

  method_setBrowsedPlayerCallback =
      env->GetMethodID(clazz, "setBrowsedPlayerRequestFromNative", "([BI)V");

  method_getFolderItemsCallback =
      env->GetMethodID(clazz, "getFolderItemsRequestFromNative", "([BBJJB[I)V");

  method_changePathCallback =
      env->GetMethodID(clazz, "changePathRequestFromNative", "([BB[B)V");

  method_getItemAttrCallback =
      env->GetMethodID(clazz, "getItemAttrRequestFromNative", "([BB[BIB[I)V");

  method_playItemCallback =
      env->GetMethodID(clazz, "playItemRequestFromNative", "([BBI[B)V");

  method_getTotalNumOfItemsCallback =
      env->GetMethodID(clazz, "getTotalNumOfItemsRequestFromNative", "([BB)V");

  method_searchCallback =
      env->GetMethodID(clazz, "searchRequestFromNative", "([BI[B)V");

  method_addToPlayListCallback =
      env->GetMethodID(clazz, "addToPlayListRequestFromNative", "([BB[BI)V");

  ALOGI("%s: succeeds", __func__);
}

static void initNative(JNIEnv* env, jobject object) {
  const bt_interface_t* btInf = getBluetoothInterface();
  if (btInf == NULL) {
    ALOGE("Bluetooth module is not loaded");
    return;
  }

  if (sBluetoothAvrcpInterface != NULL) {
    ALOGW("Cleaning up Avrcp Interface before initializing...");
    sBluetoothAvrcpInterface->cleanup();
    sBluetoothAvrcpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Avrcp callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sBluetoothAvrcpInterface =
      (btrc_interface_t*)btInf->get_profile_interface(BT_PROFILE_AV_RC_ID);
  if (sBluetoothAvrcpInterface == NULL) {
    ALOGE("Failed to get Bluetooth Avrcp Interface");
    return;
  }

  bt_status_t status =
      sBluetoothAvrcpInterface->init(&sBluetoothAvrcpCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed to initialize Bluetooth Avrcp, status: %d", status);
    sBluetoothAvrcpInterface = NULL;
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

  if (sBluetoothAvrcpInterface != NULL) {
    sBluetoothAvrcpInterface->cleanup();
    sBluetoothAvrcpInterface = NULL;
  }

  if (mCallbacksObj != NULL) {
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
}

static jboolean getPlayStatusRspNative(JNIEnv* env, jobject object,
                                       jbyteArray address, jint playStatus,
                                       jint songLen, jint songPos) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothAvrcpInterface->get_play_status_rsp(
      (RawAddress*)addr, (btrc_play_status_t)playStatus, songLen, songPos);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get_play_status_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean getElementAttrRspNative(JNIEnv* env, jobject object,
                                        jbyteArray address, jbyte numAttr,
                                        jintArray attrIds,
                                        jobjectArray textArray) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  if (numAttr > BTRC_MAX_ELEM_ATTR_SIZE) {
    ALOGE("get_element_attr_rsp: number of attributes exceed maximum");
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  btrc_element_attr_val_t* pAttrs = new btrc_element_attr_val_t[numAttr];
  if (!pAttrs) {
    ALOGE("get_element_attr_rsp: not have enough memeory");
    env->ReleaseByteArrayElements(address, addr, 0);
    return JNI_FALSE;
  }

  jint* attr = env->GetIntArrayElements(attrIds, NULL);
  if (!attr) {
    delete[] pAttrs;
    jniThrowIOException(env, EINVAL);
    env->ReleaseByteArrayElements(address, addr, 0);
    return JNI_FALSE;
  }

  int attr_cnt;
  for (attr_cnt = 0; attr_cnt < numAttr; ++attr_cnt) {
    pAttrs[attr_cnt].attr_id = attr[attr_cnt];
    ScopedLocalRef<jstring> text(
        env, (jstring)env->GetObjectArrayElement(textArray, attr_cnt));

    if (!copy_jstring(pAttrs[attr_cnt].text, BTRC_MAX_ATTR_STR_LEN, text.get(),
                      env)) {
      break;
    }
  }

  if (attr_cnt < numAttr) {
    delete[] pAttrs;
    env->ReleaseIntArrayElements(attrIds, attr, 0);
    ALOGE("%s: Failed to copy attributes", __func__);
    return JNI_FALSE;
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status =
      sBluetoothAvrcpInterface->get_element_attr_rsp(btAddr, numAttr, pAttrs);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get_element_attr_rsp, status: %d", status);
  }

  delete[] pAttrs;
  env->ReleaseIntArrayElements(attrIds, attr, 0);
  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean getItemAttrRspNative(JNIEnv* env, jobject object,
                                     jbyteArray address, jint rspStatus,
                                     jbyte numAttr, jintArray attrIds,
                                     jobjectArray textArray) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  if (numAttr > BTRC_MAX_ELEM_ATTR_SIZE) {
    ALOGE("get_element_attr_rsp: number of attributes exceed maximum");
    return JNI_FALSE;
  }

  btrc_element_attr_val_t* pAttrs = new btrc_element_attr_val_t[numAttr];
  if (!pAttrs) {
    ALOGE("%s: not have enough memory", __func__);
    env->ReleaseByteArrayElements(address, addr, 0);
    return JNI_FALSE;
  }

  jint* attr = NULL;
  if (attrIds != NULL) {
    attr = env->GetIntArrayElements(attrIds, NULL);
    if (!attr) {
      delete[] pAttrs;
      jniThrowIOException(env, EINVAL);
      env->ReleaseByteArrayElements(address, addr, 0);
      return JNI_FALSE;
    }
  }

  for (int attr_cnt = 0; attr_cnt < numAttr; ++attr_cnt) {
    pAttrs[attr_cnt].attr_id = attr[attr_cnt];
    ScopedLocalRef<jstring> text(
        env, (jstring)env->GetObjectArrayElement(textArray, attr_cnt));

    if (!copy_jstring(pAttrs[attr_cnt].text, BTRC_MAX_ATTR_STR_LEN, text.get(),
                      env)) {
      rspStatus = BTRC_STS_INTERNAL_ERR;
      ALOGE("%s: Failed to copy attributes", __func__);
      break;
    }
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->get_item_attr_rsp(
      btAddr, (btrc_status_t)rspStatus, numAttr, pAttrs);
  if (status != BT_STATUS_SUCCESS)
    ALOGE("Failed get_item_attr_rsp, status: %d", status);

  if (pAttrs) delete[] pAttrs;
  if (attr) env->ReleaseIntArrayElements(attrIds, attr, 0);
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspPlayStatusNative(JNIEnv* env,
                                                        jobject object,
                                                        jint type,
                                                        jint playStatus) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  param.play_status = (btrc_play_status_t)playStatus;

  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_PLAY_STATUS_CHANGED, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp play status, status: %d", status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspTrackChangeNative(JNIEnv* env,
                                                         jobject object,
                                                         jint type,
                                                         jbyteArray track) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* trk = env->GetByteArrayElements(track, NULL);
  if (!trk) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  uint64_t uid = 0;
  for (int uid_idx = 0; uid_idx < BTRC_UID_SIZE; ++uid_idx) {
    param.track[uid_idx] = trk[uid_idx];
    uid = uid + (trk[uid_idx] << (BTRC_UID_SIZE - 1 - uid_idx));
  }

  ALOGV("%s: Sending track change notification: %d -> %llu", __func__, type,
        uid);

  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_TRACK_CHANGE, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp track change, status: %d", status);
  }

  env->ReleaseByteArrayElements(track, trk, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspPlayPosNative(JNIEnv* env,
                                                     jobject object, jint type,
                                                     jint playPos) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  param.song_pos = (uint32_t)playPos;

  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_PLAY_POS_CHANGED, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp play position, status: %d", status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspNowPlayingChangedNative(JNIEnv* env,
                                                               jobject object,
                                                               jint type) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_NOW_PLAYING_CONTENT_CHANGED, (btrc_notification_type_t)type,
      &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp, nowPlaying Content status: %d",
          status);
  }
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspUIDsChangedNative(JNIEnv* env,
                                                         jobject object,
                                                         jint type,
                                                         jint uidCounter) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  param.uids_changed.uid_counter = (uint16_t)uidCounter;

  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_UIDS_CHANGED, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp, uids changed status: %d", status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspAddrPlayerChangedNative(
    JNIEnv* env, jobject object, jint type, jint playerId, jint uidCounter) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  param.addr_player_changed.player_id = (uint16_t)playerId;
  param.addr_player_changed.uid_counter = (uint16_t)uidCounter;

  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_ADDR_PLAYER_CHANGE, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed register_notification_rsp address player changed status: %d",
          status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspAvalPlayerChangedNative(JNIEnv* env,
                                                               jobject object,
                                                               jint type) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  btrc_register_notification_t param;
  bt_status_t status = sBluetoothAvrcpInterface->register_notification_rsp(
      BTRC_EVT_AVAL_PLAYER_CHANGE, (btrc_notification_type_t)type, &param);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE(
        "Failed register_notification_rsp available player changed status, "
        "status: %d",
        status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setVolumeNative(JNIEnv* env, jobject object, jint volume) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  bt_status_t status = sBluetoothAvrcpInterface->set_volume((uint8_t)volume);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed set_volume, status: %d", status);
  }

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

/* native response for scope as Media player */
static jboolean mediaPlayerListRspNative(
    JNIEnv* env, jobject object, jbyteArray address, jint rspStatus,
    jint uidCounter, jbyte itemType, jint numItems, jintArray playerIds,
    jbyteArray playerTypes, jintArray playerSubtypes,
    jbyteArray playStatusValues, jshortArray featureBitmask,
    jobjectArray textArray) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  jbyte *p_playerTypes = NULL, *p_PlayStatusValues = NULL;
  jshort* p_FeatBitMaskValues = NULL;
  jint *p_playerIds = NULL, *p_playerSubTypes = NULL;
  btrc_folder_items_t* p_items = NULL;
  if (rspStatus == BTRC_STS_NO_ERROR) {
    /* allocate memory */
    p_playerIds = env->GetIntArrayElements(playerIds, NULL);
    p_playerTypes = env->GetByteArrayElements(playerTypes, NULL);
    p_playerSubTypes = env->GetIntArrayElements(playerSubtypes, NULL);
    p_PlayStatusValues = env->GetByteArrayElements(playStatusValues, NULL);
    p_FeatBitMaskValues = env->GetShortArrayElements(featureBitmask, NULL);
    p_items = new btrc_folder_items_t[numItems];
    /* deallocate memory and return if allocation failed */
    if (!p_playerIds || !p_playerTypes || !p_playerSubTypes ||
        !p_PlayStatusValues || !p_FeatBitMaskValues || !p_items) {
      if (p_playerIds) env->ReleaseIntArrayElements(playerIds, p_playerIds, 0);
      if (p_playerTypes)
        env->ReleaseByteArrayElements(playerTypes, p_playerTypes, 0);
      if (p_playerSubTypes)
        env->ReleaseIntArrayElements(playerSubtypes, p_playerSubTypes, 0);
      if (p_PlayStatusValues)
        env->ReleaseByteArrayElements(playStatusValues, p_PlayStatusValues, 0);
      if (p_FeatBitMaskValues)
        env->ReleaseShortArrayElements(featureBitmask, p_FeatBitMaskValues, 0);
      if (p_items) delete[] p_items;

      jniThrowIOException(env, EINVAL);
      ALOGE("%s: not have enough memory", __func__);
      return JNI_FALSE;
    }

    p_items->item_type = (uint8_t)itemType;

    /* copy list of media players along with other parameters */
    int itemIdx;
    for (itemIdx = 0; itemIdx < numItems; ++itemIdx) {
      p_items[itemIdx].player.player_id = p_playerIds[itemIdx];
      p_items[itemIdx].player.major_type = p_playerTypes[itemIdx];
      p_items[itemIdx].player.sub_type = p_playerSubTypes[itemIdx];
      p_items[itemIdx].player.play_status = p_PlayStatusValues[itemIdx];
      p_items[itemIdx].player.charset_id = BTRC_CHARSET_ID_UTF8;

      ScopedLocalRef<jstring> text(
          env, (jstring)env->GetObjectArrayElement(textArray, itemIdx));
      /* copy player name */
      if (!copy_jstring(p_items[itemIdx].player.name, BTRC_MAX_ATTR_STR_LEN,
                        text.get(), env))
        break;

      /* Feature bit mask is 128-bit value each */
      for (int InnCnt = 0; InnCnt < 16; InnCnt++) {
        p_items[itemIdx].player.features[InnCnt] =
            (uint8_t)p_FeatBitMaskValues[(itemIdx * 16) + InnCnt];
      }
    }

    /* failed to copy list of media players */
    if (itemIdx < numItems) {
      rspStatus = BTRC_STS_INTERNAL_ERR;
      ALOGE("%s: Failed to copy Media player attributes", __func__);
    }
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->get_folder_items_list_rsp(
      btAddr, (btrc_status_t)rspStatus, uidCounter, numItems, p_items);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get_folder_items_list_rsp, status: %d", status);
  }

  /* release allocated memory */
  if (p_items) delete[] p_items;
  if (p_playerTypes)
    env->ReleaseByteArrayElements(playerTypes, p_playerTypes, 0);
  if (p_playerSubTypes)
    env->ReleaseIntArrayElements(playerSubtypes, p_playerSubTypes, 0);
  if (p_PlayStatusValues)
    env->ReleaseByteArrayElements(playStatusValues, p_PlayStatusValues, 0);
  if (p_FeatBitMaskValues) {
    env->ReleaseShortArrayElements(featureBitmask, p_FeatBitMaskValues, 0);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean getFolderItemsRspNative(
    JNIEnv* env, jobject object, jbyteArray address, jint rspStatus,
    jshort uidCounter, jbyte scope, jint numItems, jbyteArray folderType,
    jbyteArray playable, jbyteArray itemType, jbyteArray itemUidArray,
    jobjectArray displayNameArray, jintArray numAttrs, jintArray attributesIds,
    jobjectArray attributesArray) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  jbyte *p_playable = NULL, *p_item_uid = NULL;
  jbyte* p_item_types = NULL; /* Folder or Media Item */
  jint* p_attributesIds = NULL;
  jbyte* p_folder_types =
      NULL; /* Folder properties like Album/Genre/Artists etc */
  jint* p_num_attrs = NULL;
  btrc_folder_items_t* p_items = NULL;
  /* none of the parameters should be null when no error */
  if (rspStatus == BTRC_STS_NO_ERROR) {
    /* allocate memory to each rsp item */
    if (folderType != NULL)
      p_folder_types = env->GetByteArrayElements(folderType, NULL);
    if (playable != NULL)
      p_playable = env->GetByteArrayElements(playable, NULL);
    if (itemType != NULL)
      p_item_types = env->GetByteArrayElements(itemType, NULL);
    if (NULL != numAttrs)
      p_num_attrs = env->GetIntArrayElements(numAttrs, NULL);
    if (NULL != attributesIds)
      p_attributesIds = env->GetIntArrayElements(attributesIds, NULL);
    if (itemUidArray != NULL)
      p_item_uid = (jbyte*)env->GetByteArrayElements(itemUidArray, NULL);

    p_items = new btrc_folder_items_t[numItems];

    /* if memory alloc failed, release memory */
    if (p_items && p_folder_types && p_playable && p_item_types && p_item_uid &&
        /* attributes can be null if remote requests 0 attributes */
        ((numAttrs != NULL && p_num_attrs) || (!numAttrs && !p_num_attrs)) &&
        ((attributesIds != NULL && p_attributesIds) ||
         (!attributesIds && !p_attributesIds))) {
      memset(p_items, 0, sizeof(btrc_folder_items_t) * numItems);
      if (scope == BTRC_SCOPE_FILE_SYSTEM || scope == BTRC_SCOPE_SEARCH ||
          scope == BTRC_SCOPE_NOW_PLAYING) {
        int attribCopiedIndex = 0;
        for (int item_idx = 0; item_idx < numItems; item_idx++) {
          if (BTRC_ITEM_FOLDER == p_item_types[item_idx]) {
            btrc_folder_items_t* pitem = &p_items[item_idx];

            memcpy(pitem->folder.uid, p_item_uid + item_idx * BTRC_UID_SIZE,
                   BTRC_UID_SIZE);
            pitem->item_type = (uint8_t)BTRC_ITEM_FOLDER;
            pitem->folder.charset_id = BTRC_CHARSET_ID_UTF8;
            pitem->folder.type = p_folder_types[item_idx];
            pitem->folder.playable = p_playable[item_idx];

            ScopedLocalRef<jstring> text(
                env, (jstring)env->GetObjectArrayElement(displayNameArray,
                                                         item_idx));
            if (!copy_jstring(pitem->folder.name, BTRC_MAX_ATTR_STR_LEN,
                              text.get(), env)) {
              rspStatus = BTRC_STS_INTERNAL_ERR;
              ALOGE("%s: failed to copy display name of folder item", __func__);
              break;
            }
          } else if (BTRC_ITEM_MEDIA == p_item_types[item_idx]) {
            btrc_folder_items_t* pitem = &p_items[item_idx];
            memcpy(pitem->media.uid, p_item_uid + item_idx * BTRC_UID_SIZE,
                   BTRC_UID_SIZE);

            pitem->item_type = (uint8_t)BTRC_ITEM_MEDIA;
            pitem->media.charset_id = BTRC_CHARSET_ID_UTF8;
            pitem->media.type = BTRC_MEDIA_TYPE_AUDIO;
            pitem->media.num_attrs =
                (p_num_attrs != NULL) ? p_num_attrs[item_idx] : 0;

            ScopedLocalRef<jstring> text(
                env, (jstring)env->GetObjectArrayElement(displayNameArray,
                                                         item_idx));
            if (!copy_jstring(pitem->media.name, BTRC_MAX_ATTR_STR_LEN,
                              text.get(), env)) {
              rspStatus = BTRC_STS_INTERNAL_ERR;
              ALOGE("%s: failed to copy display name of media item", __func__);
              break;
            }

            /* copy item attributes */
            if (!copy_item_attributes(env, object, pitem, p_attributesIds,
                                      attributesArray, item_idx,
                                      attribCopiedIndex)) {
              ALOGE("%s: error in copying attributes of item = %s", __func__,
                    pitem->media.name);
              rspStatus = BTRC_STS_INTERNAL_ERR;
              break;
            }
            attribCopiedIndex += pitem->media.num_attrs;
          }
        }
      }
    } else {
      rspStatus = BTRC_STS_INTERNAL_ERR;
      ALOGE("%s: unable to allocate memory", __func__);
    }
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->get_folder_items_list_rsp(
      btAddr, (btrc_status_t)rspStatus, uidCounter, numItems, p_items);
  if (status != BT_STATUS_SUCCESS)
    ALOGE("Failed get_folder_items_list_rsp, status: %d", status);

  /* Release allocated memory for all attributes in each media item */
  if (p_items) cleanup_items(p_items, numItems);

  /* Release allocated memory  */
  if (p_folder_types)
    env->ReleaseByteArrayElements(folderType, p_folder_types, 0);
  if (p_playable) env->ReleaseByteArrayElements(playable, p_playable, 0);
  if (p_item_types) env->ReleaseByteArrayElements(itemType, p_item_types, 0);
  if (p_num_attrs) env->ReleaseIntArrayElements(numAttrs, p_num_attrs, 0);
  if (p_attributesIds)
    env->ReleaseIntArrayElements(attributesIds, p_attributesIds, 0);
  if (p_item_uid) env->ReleaseByteArrayElements(itemUidArray, p_item_uid, 0);
  if (p_items) delete[] p_items;
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setAddressedPlayerRspNative(JNIEnv* env, jobject object,
                                            jbyteArray address,
                                            jint rspStatus) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->set_addressed_player_rsp(
      btAddr, (btrc_status_t)rspStatus);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed set_addressed_player_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setBrowsedPlayerRspNative(JNIEnv* env, jobject object,
                                          jbyteArray address, jint rspStatus,
                                          jbyte depth, jint numItems,
                                          jobjectArray textArray) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  btrc_br_folder_name_t* p_folders = NULL;
  if (rspStatus == BTRC_STS_NO_ERROR) {
    if (depth > 0) {
      p_folders = new btrc_br_folder_name_t[depth];
    }

    for (int folder_idx = 0; folder_idx < depth; folder_idx++) {
      /* copy folder names */
      ScopedLocalRef<jstring> text(
          env, (jstring)env->GetObjectArrayElement(textArray, folder_idx));

      if (!copy_jstring(p_folders[folder_idx].p_str, BTRC_MAX_ATTR_STR_LEN,
                        text.get(), env)) {
        rspStatus = BTRC_STS_INTERNAL_ERR;
        delete[] p_folders;
        env->ReleaseByteArrayElements(address, addr, 0);
        ALOGE("%s: Failed to copy folder name", __func__);
        return JNI_FALSE;
      }

      p_folders[folder_idx].str_len =
          strlen((char*)p_folders[folder_idx].p_str);
    }
  }

  uint8_t folder_depth =
      depth; /* folder_depth is 0 if current folder is root */
  uint16_t charset_id = BTRC_CHARSET_ID_UTF8;
  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->set_browsed_player_rsp(
      btAddr, (btrc_status_t)rspStatus, numItems, charset_id, folder_depth,
      p_folders);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("%s: Failed set_browsed_player_rsp, status: %d", __func__, status);
  }

  if (depth > 0) {
    delete[] p_folders;
  }

  env->ReleaseByteArrayElements(address, addr, 0);
  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean changePathRspNative(JNIEnv* env, jobject object,
                                    jbyteArray address, jint rspStatus,
                                    jint numItems) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  uint32_t nItems = (uint32_t)numItems;
  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->change_path_rsp(
      btAddr, (btrc_status_t)rspStatus, (uint32_t)nItems);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed change_path_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean searchRspNative(JNIEnv* env, jobject object, jbyteArray address,
                                jint rspStatus, jint uidCounter,
                                jint numItems) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  uint32_t nItems = (uint32_t)numItems;
  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->search_rsp(
      btAddr, (btrc_status_t)rspStatus, (uint32_t)uidCounter, (uint32_t)nItems);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed search_rsp, status: %d", status);
  }

  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean playItemRspNative(JNIEnv* env, jobject object,
                                  jbyteArray address, jint rspStatus) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status =
      sBluetoothAvrcpInterface->play_item_rsp(btAddr, (btrc_status_t)rspStatus);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed play_item_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean getTotalNumOfItemsRspNative(JNIEnv* env, jobject object,
                                            jbyteArray address, jint rspStatus,
                                            jint uidCounter, jint numItems) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  uint32_t nItems = (uint32_t)numItems;
  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->get_total_num_of_items_rsp(
      btAddr, (btrc_status_t)rspStatus, (uint32_t)uidCounter, (uint32_t)nItems);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed get_total_num_of_items_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean addToNowPlayingRspNative(JNIEnv* env, jobject object,
                                         jbyteArray address, jint rspStatus) {
  if (!sBluetoothAvrcpInterface) {
    ALOGE("%s: sBluetoothAvrcpInterface is null", __func__);
    return JNI_FALSE;
  }

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    jniThrowIOException(env, EINVAL);
    return JNI_FALSE;
  }

  RawAddress* btAddr = (RawAddress*)addr;
  bt_status_t status = sBluetoothAvrcpInterface->add_to_now_playing_rsp(
      btAddr, (btrc_status_t)rspStatus);
  if (status != BT_STATUS_SUCCESS) {
    ALOGE("Failed add_to_now_playing_rsp, status: %d", status);
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initNative", "()V", (void*)initNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"getPlayStatusRspNative", "([BIII)Z", (void*)getPlayStatusRspNative},
    {"getElementAttrRspNative", "([BB[I[Ljava/lang/String;)Z",
     (void*)getElementAttrRspNative},
    {"registerNotificationRspPlayStatusNative", "(II)Z",
     (void*)registerNotificationRspPlayStatusNative},
    {"registerNotificationRspTrackChangeNative", "(I[B)Z",
     (void*)registerNotificationRspTrackChangeNative},
    {"registerNotificationRspPlayPosNative", "(II)Z",
     (void*)registerNotificationRspPlayPosNative},
    {"setVolumeNative", "(I)Z", (void*)setVolumeNative},

    {"setAddressedPlayerRspNative", "([BI)Z",
     (void*)setAddressedPlayerRspNative},

    {"setBrowsedPlayerRspNative", "([BIBI[Ljava/lang/String;)Z",
     (void*)setBrowsedPlayerRspNative},

    {"mediaPlayerListRspNative", "([BIIBI[I[B[I[B[S[Ljava/lang/String;)Z",
     (void*)mediaPlayerListRspNative},

    {"getFolderItemsRspNative",
     "([BISBI[B[B[B[B[Ljava/lang/String;[I[I[Ljava/lang/String;)Z",
     (void*)getFolderItemsRspNative},

    {"changePathRspNative", "([BII)Z", (void*)changePathRspNative},

    {"getItemAttrRspNative", "([BIB[I[Ljava/lang/String;)Z",
     (void*)getItemAttrRspNative},

    {"playItemRspNative", "([BI)Z", (void*)playItemRspNative},

    {"getTotalNumOfItemsRspNative", "([BIII)Z",
     (void*)getTotalNumOfItemsRspNative},

    {"searchRspNative", "([BIII)Z", (void*)searchRspNative},

    {"addToNowPlayingRspNative", "([BI)Z", (void*)addToNowPlayingRspNative},

    {"registerNotificationRspAddrPlayerChangedNative", "(III)Z",
     (void*)registerNotificationRspAddrPlayerChangedNative},

    {"registerNotificationRspAvalPlayerChangedNative", "(I)Z",
     (void*)registerNotificationRspAvalPlayerChangedNative},

    {"registerNotificationRspUIDsChangedNative", "(II)Z",
     (void*)registerNotificationRspUIDsChangedNative},

    {"registerNotificationRspNowPlayingChangedNative", "(I)Z",
     (void*)registerNotificationRspNowPlayingChangedNative}};

int register_com_android_bluetooth_avrcp(JNIEnv* env) {
  return jniRegisterNativeMethods(env, "com/android/bluetooth/avrcp/Avrcp",
                                  sMethods, NELEM(sMethods));
}

/* Helper function to copy attributes of item.
 * Assumes that all items in response have same number of attributes
 *
 * returns true on succes, false otherwise.
*/
static bool copy_item_attributes(JNIEnv* env, jobject object,
                                 btrc_folder_items_t* pitem,
                                 jint* p_attributesIds,
                                 jobjectArray attributesArray, int item_idx,
                                 int attribCopiedIndex) {
  bool success = true;

  /* copy attributes of the item */
  if (0 < pitem->media.num_attrs) {
    int num_attrs = pitem->media.num_attrs;
    ALOGI("%s num_attr = %d", __func__, num_attrs);
    pitem->media.p_attrs = new btrc_element_attr_val_t[num_attrs];
    if (!pitem->media.p_attrs) {
      return false;
    }

    for (int tempAtrCount = 0; tempAtrCount < pitem->media.num_attrs;
         ++tempAtrCount) {
      pitem->media.p_attrs[tempAtrCount].attr_id =
          p_attributesIds[attribCopiedIndex + tempAtrCount];

      ScopedLocalRef<jstring> text(
          env, (jstring)env->GetObjectArrayElement(
                   attributesArray, attribCopiedIndex + tempAtrCount));

      if (!copy_jstring(pitem->media.p_attrs[tempAtrCount].text,
                        BTRC_MAX_ATTR_STR_LEN, text.get(), env)) {
        success = false;
        ALOGE("%s: failed to copy attributes", __func__);
        break;
      }
    }
  }
  return success;
}

/* Helper function to copy String data from java to native
 *
 * returns true on succes, false otherwise
 */
static bool copy_jstring(uint8_t* str, int maxBytes, jstring jstr,
                         JNIEnv* env) {
  if (str == NULL || jstr == NULL || env == NULL) return false;

  memset(str, 0, maxBytes);
  const char* p_str = env->GetStringUTFChars(jstr, NULL);
  size_t len = strnlen(p_str, maxBytes - 1);
  memcpy(str, p_str, len);

  env->ReleaseStringUTFChars(jstr, p_str);
  return true;
}

/* Helper function to cleanup items */
static void cleanup_items(btrc_folder_items_t* p_items, int numItems) {
  for (int item_idx = 0; item_idx < numItems; item_idx++) {
    /* release memory for attributes in case item is media item */
    if ((BTRC_ITEM_MEDIA == p_items[item_idx].item_type) &&
        p_items[item_idx].media.p_attrs != NULL)
      delete[] p_items[item_idx].media.p_attrs;
  }
}
}
