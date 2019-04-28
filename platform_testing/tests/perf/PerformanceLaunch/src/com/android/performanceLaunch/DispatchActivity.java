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

package com.android.performanceLaunch;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.lang.ProcessBuilder;

public class DispatchActivity extends Activity {

    private static final String TAG = "DispatchActivity";
    private Context mContext;
    private Bundle mExtras;
    private Intent mIntent;
    private String mActivityName;
    private String mSimpleperfBin;
    private String mSimpleperfEvt;
    private String mSimpleperfDir;
    private String mPackageName;
    private int mPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExtras = getIntent().getExtras();
        if (mExtras != null) {
            mActivityName = mExtras.getString("ACTIVITY_NAME");
            mSimpleperfBin = mExtras.getString("SIMPLEPERF_BIN");
            mSimpleperfEvt = mExtras.getString("SIMPLEPERF_EVT");
            mSimpleperfDir = mExtras.getString("SIMPLEPERF_DIR");
            if (mSimpleperfBin == null || mSimpleperfBin.isEmpty()) {
                mSimpleperfBin = "/system/xbin/simpleperf";
            }
            if (mSimpleperfEvt == null || mSimpleperfEvt.isEmpty()) {
                mSimpleperfEvt = "cpu-cycles,instructions:k,instructions:u";
            }
            if (mSimpleperfDir == null || mSimpleperfDir.isEmpty()) {
                mSimpleperfDir = "/sdcard/perf_simpleperf";
            }
            mPackageName = getApplicationContext().getPackageName();
            mContext = getApplicationContext();

            ComponentName cn = new ComponentName(mPackageName, mActivityName);
            mIntent = new Intent(Intent.ACTION_MAIN);
            mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mIntent.setComponent(cn);
        }

        try {
            mPid = android.os.Process.myPid();
            ProcessBuilder simpleperf =
                    new ProcessBuilder(mSimpleperfBin, "stat", "--group",
                            mSimpleperfEvt, "-p", String.valueOf(mPid));

            simpleperf.redirectOutput(new File(String.format("%s/%s.%s",
                    mSimpleperfDir, "perf.data", String.valueOf(mPid))));
            simpleperf.start();

        } catch (Exception e) {
            Log.v(TAG, "simpleperf throw exception");
            e.printStackTrace();
        }

        startActivity(mIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Runtime.getRuntime().exec("pkill -l SIGINT simpleperf").waitFor();
        } catch (Exception e) {
            Log.v(TAG, "Failed to stop simpleperf");
            e.printStackTrace();
        }
    }
}
