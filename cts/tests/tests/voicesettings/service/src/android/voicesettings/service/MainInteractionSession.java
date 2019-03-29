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

package android.voicesettings.service;

import static android.provider.Settings.ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE;
import static android.provider.Settings.EXTRA_DO_NOT_DISTURB_MODE_ENABLED;
import static android.provider.Settings.EXTRA_DO_NOT_DISTURB_MODE_MINUTES;
import static android.provider.Settings.ACTION_VOICE_CONTROL_AIRPLANE_MODE;
import static android.provider.Settings.EXTRA_AIRPLANE_MODE_ENABLED;
import static android.provider.Settings.ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE;
import static android.provider.Settings.EXTRA_BATTERY_SAVER_MODE_ENABLED;

import com.android.compatibility.common.util.BroadcastUtils;
import com.android.compatibility.common.util.BroadcastUtils.TestcaseType;

import android.app.VoiceInteractor;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainInteractionSession extends VoiceInteractionSession {
    static final String TAG = "MainInteractionSession";

    List<MyTask> mUsedTasks = new ArrayList<MyTask>();
    Context mContext;
    TestcaseType mTestType;

    MainInteractionSession(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Canceling the Asynctasks in onDestroy()");
        for (MyTask t : mUsedTasks) {
            t.cancel(true);
        }
        super.onDestroy();
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        String testCaseType = args.getString(BroadcastUtils.TESTCASE_TYPE);
        Log.i(TAG, "received_testcasetype = " + testCaseType);
        try {
            mTestType = TestcaseType.valueOf(testCaseType);
        } catch (IllegalArgumentException e) {
            Log.wtf(TAG, e);
            return;
        } catch (NullPointerException e) {
            Log.wtf(TAG, e);
            return;
        }
        Intent intent;
        switch(mTestType) {
            case ZEN_MODE_ON:
                intent = new Intent(ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE);
                intent.putExtra(EXTRA_DO_NOT_DISTURB_MODE_ENABLED, true);
                intent.putExtra(EXTRA_DO_NOT_DISTURB_MODE_MINUTES,
                                BroadcastUtils.NUM_MINUTES_FOR_ZENMODE);
                break;
            case ZEN_MODE_OFF:
                intent = new Intent(ACTION_VOICE_CONTROL_DO_NOT_DISTURB_MODE);
                intent.putExtra(EXTRA_DO_NOT_DISTURB_MODE_ENABLED, false);
                break;
            case AIRPLANE_MODE_ON:
                intent = new Intent(ACTION_VOICE_CONTROL_AIRPLANE_MODE);
                intent.putExtra(EXTRA_AIRPLANE_MODE_ENABLED, true);
                break;
            case AIRPLANE_MODE_OFF:
                intent = new Intent(ACTION_VOICE_CONTROL_AIRPLANE_MODE);
                intent.putExtra(EXTRA_AIRPLANE_MODE_ENABLED, false);
                break;
            case BATTERYSAVER_MODE_ON:
                intent = new Intent(ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE);
                intent.putExtra(EXTRA_BATTERY_SAVER_MODE_ENABLED, true);
                break;
            case BATTERYSAVER_MODE_OFF:
                intent = new Intent(ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE);
                intent.putExtra(EXTRA_BATTERY_SAVER_MODE_ENABLED, false);
                break;
            default:
                Log.i(TAG, "Not implemented!");
                return;
        }
        Log.i(TAG, "starting_voiceactivity: " + intent.toString());
        startVoiceActivity(intent);
    }

    @Override
    public void onTaskFinished(Intent intent, int taskId) {
        // extras contain the info on what the activity started above did.
        // we probably could verify this also.
        Bundle extras = intent.getExtras();
        Log.i(TAG, "in onTaskFinished: testcasetype = " + mTestType + ", intent: " +
                intent.toString() + BroadcastUtils.toBundleString(extras));
        Intent broadcastIntent = new Intent(BroadcastUtils.BROADCAST_INTENT +
                                            mTestType.toString());
        if (extras == null) {
            extras = new Bundle();
        }
        extras.putString(BroadcastUtils.TESTCASE_TYPE, mTestType.toString());
        broadcastIntent.putExtras(extras);
        Log.i(TAG, "sending_broadcast: Bundle = " + BroadcastUtils.toBundleString(extras) +
                ", intent = " + broadcastIntent.toString());
        mContext.sendBroadcast(broadcastIntent);
    }

    synchronized MyTask newTask() {
        MyTask t = new MyTask();
        mUsedTasks.add(t);
        return t;
    }

    @Override
    public void onRequestCompleteVoice(CompleteVoiceRequest request) {
        VoiceInteractor.Prompt prompt = request.getVoicePrompt();
        CharSequence message = (prompt != null ? prompt.getVoicePromptAt(0) : "(none)");
        Log.i(TAG, "in Session testcasetype = " + mTestType +
                ", onRequestCompleteVoice recvd. message = " + message);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setCompleteReq(true);
        newTask().execute(asyncTaskArg);
    }

    @Override
    public void onRequestAbortVoice(AbortVoiceRequest request) {
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setCompleteReq(false);
        Log.i(TAG, "in Session sending sendAbortResult. ");
        newTask().execute(asyncTaskArg);
    }

    private class AsyncTaskArg {
        CompleteVoiceRequest mCompReq;
        AbortVoiceRequest mAbortReq;
        boolean isCompleteRequest = true;

        AsyncTaskArg setRequest(CompleteVoiceRequest r) {
            mCompReq = r;
            return this;
        }

        AsyncTaskArg setRequest(AbortVoiceRequest r) {
            mAbortReq = r;
            return this;
        }

        AsyncTaskArg setCompleteReq(boolean flag) {
            isCompleteRequest = flag;
            return this;
        }
    }

    private class MyTask extends AsyncTask<AsyncTaskArg, Void, Void> {
        @Override
        protected Void doInBackground(AsyncTaskArg... params) {
            AsyncTaskArg arg = params[0];
            Log.i(TAG, "in MyTask - doInBackground: testType = " +
                    MainInteractionSession.this.mTestType);
            if (arg.isCompleteRequest) {
                arg.mCompReq.sendCompleteResult(new Bundle());
            } else {
                arg.mAbortReq.sendAbortResult(new Bundle());
            }
            return null;
        }
    }
}
