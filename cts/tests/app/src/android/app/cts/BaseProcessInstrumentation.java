/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BaseProcessInstrumentation extends Instrumentation {
    final String mMainProc;
    final String mReceiverClass;

    public BaseProcessInstrumentation(String mainProc, String receiverClass) {
        mMainProc = mainProc;
        mReceiverClass = receiverClass;
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        final String proc = getProcessName();
        //Log.i("xxx", "Instrumentation starting in " + proc);
        final Bundle result = new Bundle();
        result.putBoolean(proc, true);
        if (proc.equals(mMainProc)) {
            // We are running in the main instr process...  start a service that will launch
            // a secondary proc.
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(ActivityManagerTest.SIMPLE_PACKAGE_NAME, mReceiverClass);
            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            //Log.i("xxx", "Instrumentation sending broadcast: " + intent);
            getContext().sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    //Log.i("xxx", "Instrumentation finishing in " + proc);
                    finish(Activity.RESULT_OK, result);
                }
            }, null, 0, null, null);
        } else {
            // We are running in a secondary proc, just report it.
            //Log.i("xxx", "Instrumentation adding result in " + proc);
            addResults(result);
        }
    }
}
