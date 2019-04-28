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

/**
 * @hide
 */

package com.android.bluetooth.btservice;

import android.app.Application;
import android.util.Log;

public class AdapterApp extends Application {
    private static final String TAG = "BluetoothAdapterApp";
    private static final boolean DBG = false;
    //For Debugging only
    private static int sRefCount=0;

    static {
        if (DBG) Log.d(TAG,"Loading JNI Library");
        System.loadLibrary("bluetooth_jni");
    }

    public AdapterApp() {
        super();
        if (DBG) {
            synchronized (AdapterApp.class) {
                sRefCount++;
                Log.d(TAG, "REFCOUNT: Constructed "+ this + " Instance Count = " + sRefCount);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate");
        Config.init(this);
    }

    @Override
    protected void finalize() {
        if (DBG) {
            synchronized (AdapterApp.class) {
                sRefCount--;
                Log.d(TAG, "REFCOUNT: Finalized: " + this +", Instance Count = " + sRefCount);
            }
        }
    }
}
