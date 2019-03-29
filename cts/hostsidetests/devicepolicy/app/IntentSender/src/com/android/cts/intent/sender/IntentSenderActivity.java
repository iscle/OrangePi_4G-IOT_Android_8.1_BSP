/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.intent.sender;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipboardManager.OnPrimaryClipChangedListener;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class IntentSenderActivity extends Activity {

    private static String TAG = "IntentSenderActivity";

    private final SynchronousQueue<Result> mResult = new SynchronousQueue<>();

    private ClipboardManager mClipboardManager;

    public static class Result {
        public final int resultCode;
        public final Intent data;

        public Result(int resultCode, Intent data) {
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            try {
                mResult.offer(new Result(resultCode, data), 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Intent getResult(Intent intent) throws Exception {
        Log.d(TAG, "Sending intent " + intent);
        startActivityForResult(intent, 42);
        final Result result = mResult.poll(30, TimeUnit.SECONDS);
        if (result != null) {
            Log.d(TAG, "Result intent: " + result.data);
        }
        return (result != null) ? result.data : null;
    }

    /**
     * This method will send an intent accross profiles to IntentReceiverActivity, and return the
     * result intent set by IntentReceiverActivity.
     */
    public Intent getCrossProfileResult(Intent intent)
            throws Exception {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> ris = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        //  There should be two matches:
        //  - com.android.cts.intent.receiver (on the current profile).
        //  - One that will send the intent to the other profile.
        //  It's the second one we want to send the intent to.

        for (ResolveInfo ri : ris) {
            if (!ri.activityInfo.packageName.equals("com.android.cts.intent.receiver")) {
                intent.setComponent(new ComponentName(ri.activityInfo.packageName,
                        ri.activityInfo.name));
                return getResult(intent);
            }
        }
        Log.e(TAG, "The intent " + intent + " cannot be sent accross profiles");
        return null;
    }

    public void addPrimaryClipChangedListener(OnPrimaryClipChangedListener listener) {
        mClipboardManager.addPrimaryClipChangedListener(listener);
    }

    public void removePrimaryClipChangedListener(
            OnPrimaryClipChangedListener listener) {
        mClipboardManager.removePrimaryClipChangedListener(listener);
    }

}
