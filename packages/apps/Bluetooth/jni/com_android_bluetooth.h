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

#ifndef COM_ANDROID_BLUETOOTH_H
#define COM_ANDROID_BLUETOOTH_H

#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"
#include "android_runtime/Log.h"
#include "hardware/bluetooth.h"
#include "hardware/hardware.h"
#include "jni.h"
#include "nativehelper/ScopedLocalRef.h"
#include "utils/Log.h"

namespace android {

JNIEnv* getCallbackEnv();

class CallbackEnv {
public:
    CallbackEnv(const char *methodName) : mName(methodName) {
        mCallbackEnv = getCallbackEnv();
    }

    ~CallbackEnv() {
      if (mCallbackEnv && mCallbackEnv->ExceptionCheck()) {
          ALOGE("An exception was thrown by callback '%s'.", mName);
          LOGE_EX(mCallbackEnv);
          mCallbackEnv->ExceptionClear();
      }
    }

    bool valid() const {
      JNIEnv *env = AndroidRuntime::getJNIEnv();
      if (!mCallbackEnv || (mCallbackEnv != env)) {
          ALOGE("%s: Callback env fail: env: %p, callback: %p", mName, env, mCallbackEnv);
          return false;
      }
      return true;
    }

    JNIEnv *operator-> () const {
        return mCallbackEnv;
    }

    JNIEnv *get() const {
        return mCallbackEnv;
    }

private:
    JNIEnv *mCallbackEnv;
    const char *mName;

    DISALLOW_COPY_AND_ASSIGN(CallbackEnv);
};

const bt_interface_t* getBluetoothInterface();

int register_com_android_bluetooth_hfp(JNIEnv* env);

int register_com_android_bluetooth_hfpclient(JNIEnv* env);

int register_com_android_bluetooth_a2dp(JNIEnv* env);

int register_com_android_bluetooth_a2dp_sink(JNIEnv* env);

int register_com_android_bluetooth_avrcp(JNIEnv* env);

int register_com_android_bluetooth_avrcp_controller(JNIEnv* env);

int register_com_android_bluetooth_hid(JNIEnv* env);

int register_com_android_bluetooth_hidd(JNIEnv* env);

int register_com_android_bluetooth_hdp(JNIEnv* env);

int register_com_android_bluetooth_pan(JNIEnv* env);

int register_com_android_bluetooth_gatt (JNIEnv* env);

int register_com_android_bluetooth_sdp (JNIEnv* env);

}

#endif /* COM_ANDROID_BLUETOOTH_H */
