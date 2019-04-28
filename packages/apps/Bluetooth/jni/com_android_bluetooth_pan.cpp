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

#define LOG_TAG "BluetoothPanServiceJni"

#define LOG_NDEBUG 0

#include "android_runtime/AndroidRuntime.h"
#include "com_android_bluetooth.h"
#include "hardware/bt_pan.h"
#include "utils/Log.h"

#include <string.h>

#include <cutils/log.h>
#define info(fmt, ...) ALOGI("%s(L%d): " fmt, __func__, __LINE__, ##__VA_ARGS__)
#define debug(fmt, ...) \
  ALOGD("%s(L%d): " fmt, __func__, __LINE__, ##__VA_ARGS__)
#define warn(fmt, ...) \
  ALOGW("## WARNING : %s(L%d): " fmt "##", __func__, __LINE__, ##__VA_ARGS__)
#define error(fmt, ...) \
  ALOGE("## ERROR : %s(L%d): " fmt "##", __func__, __LINE__, ##__VA_ARGS__)
#define asrt(s) \
  if (!(s)) ALOGE("## %s(L%d): ASSERT %s failed! ##", __func__, __LINE__, #s)

namespace android {

static jmethodID method_onConnectStateChanged;
static jmethodID method_onControlStateChanged;

static const btpan_interface_t* sPanIf = NULL;
static jobject mCallbacksObj = NULL;

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

static void control_state_callback(btpan_control_state_t state, int local_role,
                                   bt_status_t error, const char* ifname) {
  debug("state:%d, local_role:%d, ifname:%s", state, local_role, ifname);
  if (mCallbacksObj == NULL) {
    error("Callbacks Obj is NULL: '%s", __func__);
    return;
  }
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  ScopedLocalRef<jstring> js_ifname(sCallbackEnv.get(),
                                    sCallbackEnv->NewStringUTF(ifname));
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onControlStateChanged,
                               (jint)local_role, (jint)state, (jint)error,
                               js_ifname.get());
}

static void connection_state_callback(btpan_connection_state_t state,
                                      bt_status_t error,
                                      const RawAddress* bd_addr, int local_role,
                                      int remote_role) {
  debug("state:%d, local_role:%d, remote_role:%d", state, local_role,
        remote_role);
  if (mCallbacksObj == NULL) {
    error("Callbacks Obj is NULL: '%s", __func__);
    return;
  }
  CallbackEnv sCallbackEnv(__func__);
  if (!sCallbackEnv.valid()) return;
  ScopedLocalRef<jbyteArray> addr(sCallbackEnv.get(), marshall_bda(bd_addr));
  if (!addr.get()) {
    error("Fail to new jbyteArray bd addr for PAN channel state");
    return;
  }
  sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged,
                               addr.get(), (jint)state, (jint)error,
                               (jint)local_role, (jint)remote_role);
}

static btpan_callbacks_t sBluetoothPanCallbacks = {
    sizeof(sBluetoothPanCallbacks), control_state_callback,
    connection_state_callback};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
  method_onConnectStateChanged =
      env->GetMethodID(clazz, "onConnectStateChanged", "([BIIII)V");
  method_onControlStateChanged = env->GetMethodID(
      clazz, "onControlStateChanged", "(IIILjava/lang/String;)V");

  info("succeeds");
}
static const bt_interface_t* btIf;

static void initializeNative(JNIEnv* env, jobject object) {
  debug("pan");
  if (btIf) return;

  btIf = getBluetoothInterface();
  if (btIf == NULL) {
    error("Bluetooth module is not loaded");
    return;
  }

  if (sPanIf != NULL) {
    ALOGW("Cleaning up Bluetooth PAN Interface before initializing...");
    sPanIf->cleanup();
    sPanIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth PAN callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }

  sPanIf = (btpan_interface_t*)btIf->get_profile_interface(BT_PROFILE_PAN_ID);
  if (sPanIf == NULL) {
    error("Failed to get Bluetooth PAN Interface");
    return;
  }

  mCallbacksObj = env->NewGlobalRef(object);

  bt_status_t status = sPanIf->init(&sBluetoothPanCallbacks);
  if (status != BT_STATUS_SUCCESS) {
    error("Failed to initialize Bluetooth PAN, status: %d", status);
    sPanIf = NULL;
    if (mCallbacksObj != NULL) {
      ALOGW("initialization failed: Cleaning up Bluetooth PAN callback object");
      env->DeleteGlobalRef(mCallbacksObj);
      mCallbacksObj = NULL;
    }
    return;
  }
}

static void cleanupNative(JNIEnv* env, jobject object) {
  if (!btIf) return;

  if (sPanIf != NULL) {
    ALOGW("Cleaning up Bluetooth PAN Interface...");
    sPanIf->cleanup();
    sPanIf = NULL;
  }

  if (mCallbacksObj != NULL) {
    ALOGW("Cleaning up Bluetooth PAN callback object");
    env->DeleteGlobalRef(mCallbacksObj);
    mCallbacksObj = NULL;
  }
  btIf = NULL;
}

static jboolean enablePanNative(JNIEnv* env, jobject object, jint local_role) {
  bt_status_t status = BT_STATUS_FAIL;
  debug("in");
  if (sPanIf) status = sPanIf->enable(local_role);
  debug("out");
  return status == BT_STATUS_SUCCESS ? JNI_TRUE : JNI_FALSE;
}
static jint getPanLocalRoleNative(JNIEnv* env, jobject object) {
  debug("in");
  int local_role = 0;
  if (sPanIf) local_role = sPanIf->get_local_role();
  debug("out");
  return (jint)local_role;
}

static jboolean connectPanNative(JNIEnv* env, jobject object,
                                 jbyteArray address, jint src_role,
                                 jint dest_role) {
  debug("in");
  if (!sPanIf) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    error("Bluetooth device address null");
    return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  bt_status_t status = sPanIf->connect((RawAddress*)addr, src_role, dest_role);
  if (status != BT_STATUS_SUCCESS) {
    error("Failed PAN channel connection, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static jboolean disconnectPanNative(JNIEnv* env, jobject object,
                                    jbyteArray address) {
  if (!sPanIf) return JNI_FALSE;

  jbyte* addr = env->GetByteArrayElements(address, NULL);
  if (!addr) {
    error("Bluetooth device address null");
    return JNI_FALSE;
  }

  jboolean ret = JNI_TRUE;
  bt_status_t status = sPanIf->disconnect((RawAddress*)addr);
  if (status != BT_STATUS_SUCCESS) {
    error("Failed disconnect pan channel, status: %d", status);
    ret = JNI_FALSE;
  }
  env->ReleaseByteArrayElements(address, addr, 0);

  return ret;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void*)classInitNative},
    {"initializeNative", "()V", (void*)initializeNative},
    {"cleanupNative", "()V", (void*)cleanupNative},
    {"connectPanNative", "([BII)Z", (void*)connectPanNative},
    {"enablePanNative", "(I)Z", (void*)enablePanNative},
    {"getPanLocalRoleNative", "()I", (void*)getPanLocalRoleNative},
    {"disconnectPanNative", "([B)Z", (void*)disconnectPanNative},
    // TBD cleanup
};

int register_com_android_bluetooth_pan(JNIEnv* env) {
  return jniRegisterNativeMethods(env, "com/android/bluetooth/pan/PanService",
                                  sMethods, NELEM(sMethods));
}
}
