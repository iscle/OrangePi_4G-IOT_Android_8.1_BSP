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

package android.net.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConnectivityReceiver extends BroadcastReceiver {
    static boolean sReceivedConnectivity;
    static boolean sReceivedFinal;
    static CountDownLatch sLatch;

    static void prepare() {
        synchronized (ConnectivityReceiver.class) {
            sReceivedConnectivity = sReceivedFinal = false;
            sLatch = new CountDownLatch(1);
        }
    }

    static boolean waitForBroadcast() {
        try {
            sLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        synchronized (ConnectivityReceiver.class) {
            sLatch = null;
            if (!sReceivedFinal) {
                throw new IllegalStateException("Never received final broadcast");
            }
            return sReceivedConnectivity;
        }
    }

    static final String FINAL_ACTION = "android.net.cts.action.FINAL";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("ConnectivityReceiver", "Received: " + intent.getAction());
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            sReceivedConnectivity = true;
        } else if (FINAL_ACTION.equals(intent.getAction())) {
            sReceivedFinal = true;
            if (sLatch != null) {
                sLatch.countDown();
            }
        }
    }
}
