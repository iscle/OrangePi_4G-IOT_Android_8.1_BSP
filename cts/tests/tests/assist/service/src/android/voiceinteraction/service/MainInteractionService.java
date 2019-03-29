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

package android.assist.service;

import static android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST;
import static android.service.voice.VoiceInteractionSession.SHOW_WITH_SCREENSHOT;

import android.assist.common.Utils;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import java.lang.Exception;
import java.lang.Override;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainInteractionService extends VoiceInteractionService {
    static final String TAG = "MainInteractionService";
    private Intent mIntent;
    private boolean mReady = false;
    private BroadcastReceiver mBroadcastReceiver, mResumeReceiver;
    private CountDownLatch mResumeLatch;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate received");
    }

    @Override
    public void onReady() {
        super.onReady();
        Log.i(TAG, "onReady received");
        mReady = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received - intent: " + intent);
        mIntent = intent;
        maybeStart();
        return START_NOT_STICKY;
    }

    private void maybeStart() {
        if (mIntent == null || !mReady) {
            Log.wtf(TAG, "Can't start session because either intent is null or onReady() "
                    + "has not been called yet. mIntent = " + mIntent + ", mReady = " + mReady);
        } else {
            if (isActiveService(this, new ComponentName(this, getClass()))) {
                if (mIntent.getBooleanExtra(Utils.EXTRA_REGISTER_RECEIVER, false)) {
                    mResumeLatch = new CountDownLatch(1);
                    if (mBroadcastReceiver == null) {
                        mBroadcastReceiver = new MainInteractionServiceBroadcastReceiver();
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(Utils.BROADCAST_INTENT_START_ASSIST);
                        registerReceiver(mBroadcastReceiver, filter);
                        Log.i(TAG, "Registered receiver to start session later");

                        IntentFilter resumeFilter = new IntentFilter(Utils.APP_3P_HASRESUMED);
                        mResumeReceiver = new MainServiceAppResumeReceiver();
                        registerReceiver(mResumeReceiver, resumeFilter);
                        Log.i(TAG, "Registered receiver for resuming activity");
                    }
                    sendBroadcast(new Intent(Utils.ASSIST_RECEIVER_REGISTERED));
              } else {
                  Log.i(TAG, "Yay! about to start session");
                  Bundle bundle = new Bundle();
                  bundle.putString(Utils.TESTCASE_TYPE,
                          mIntent.getStringExtra(Utils.TESTCASE_TYPE));
                  showSession(bundle, VoiceInteractionSession.SHOW_WITH_ASSIST |
                      VoiceInteractionSession.SHOW_WITH_SCREENSHOT);
              }
            } else {
                Log.wtf(TAG, "**** Not starting MainInteractionService because" +
                        " it is not set as the current voice interaction service");
            }
        }
    }

    private class MainInteractionServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(MainInteractionService.TAG, "Recieved broadcast to start session now.");
            if (intent.getAction().equals(Utils.BROADCAST_INTENT_START_ASSIST)) {
                String testCaseName = intent.getStringExtra(Utils.TESTCASE_TYPE);
                Log.i(MainInteractionService.TAG, "trying to start 3p activity: " + testCaseName);
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    extras = new Bundle();
                }
                if (testCaseName.equals(Utils.SCREENSHOT)) {
                    try {
                        // extra info to pass along to 3p activity.

                        Intent intent3p = new Intent();
                        Log.i(TAG, "starting the 3p app again");
                        intent3p.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent3p.setAction("android.intent.action.TEST_APP_" + testCaseName);
                        intent3p.setComponent(Utils.getTestAppComponent(testCaseName));
                        intent3p.putExtras(extras);
                        startActivity(intent3p);
                        if (!MainInteractionService.this.mResumeLatch
                                .await(Utils.ACTIVITY_ONRESUME_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            Log.i(TAG, "waited for 3p activity to resume");
                        }
                    } catch (Exception e) {
                        Log.i(TAG, "failed so reload 3p app: " + e.toString());
                    }
                }
                extras.putString(Utils.TESTCASE_TYPE, mIntent.getStringExtra(Utils.TESTCASE_TYPE));
                MainInteractionService.this.showSession(
                        extras, SHOW_WITH_ASSIST | SHOW_WITH_SCREENSHOT);
            }
        }
    }

    private class MainServiceAppResumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Utils.APP_3P_HASRESUMED)) {
                Log.i(MainInteractionService.TAG,
                    "3p activity has resumed in this new receiver");
                if (MainInteractionService.this.mResumeLatch != null) {
                    MainInteractionService.this.mResumeLatch.countDown();
                } else {
                    Log.i(TAG, "mResumeLatch was null");
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }
    }
}