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

package android.alarmclock.service;

import android.alarmclock.common.Utils;
import android.alarmclock.common.Utils.TestcaseType;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainInteractionSession extends VoiceInteractionSession {
    static final String TAG = "MainInteractionSession";

    private List<MyTask> mUsedTasks = new ArrayList<MyTask>();
    private Context mContext;
    private TestcaseType mTestType;
    private String mTestResult = "";

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
        String testCaseType = args.getString(Utils.TESTCASE_TYPE);
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
            case DISMISS_ALARM:
                intent = new Intent(AlarmClock.ACTION_DISMISS_ALARM);
                intent.putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                        AlarmClock.ALARM_SEARCH_MODE_NEXT);
                break;

            case SET_ALARM_FOR_DISMISSAL:
            case SET_ALARM:
                intent = new Intent(AlarmClock.ACTION_SET_ALARM);
                intent.putExtra(AlarmClock.EXTRA_HOUR, 14);
                break;

            case SNOOZE_ALARM:
                intent = new Intent(AlarmClock.ACTION_SNOOZE_ALARM);
                break;

            default:
                Log.e(TAG, "Unexpected value");
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
                intent.toString() + Utils.toBundleString(extras));
        Intent broadcastIntent = new Intent(Utils.BROADCAST_INTENT + mTestType.toString());
        broadcastIntent.putExtra(Utils.TEST_RESULT, mTestResult);
        broadcastIntent.putExtra(Utils.TESTCASE_TYPE, mTestType.toString());
        Log.i(TAG, "sending_broadcast for testcase+type = " +
                broadcastIntent.getStringExtra(Utils.TESTCASE_TYPE) +
                ", test_result = " + broadcastIntent.getStringExtra(Utils.TEST_RESULT));
        mContext.sendBroadcast(broadcastIntent);
    }

    synchronized MyTask newTask() {
        MyTask t = new MyTask();
        mUsedTasks.add(t);
        return t;
    }

    @Override
    public void onRequestCompleteVoice(CompleteVoiceRequest request) {
        mTestResult = Utils.COMPLETION_RESULT;
        CharSequence prompt = request.getVoicePrompt().getVoicePromptAt(0);
        Log.i(TAG, "in Session testcasetype = " + mTestType +
                ", onRequestCompleteVoice recvd. message = " + prompt);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setCompleteReq(true);
        newTask().execute(asyncTaskArg);
    }

    @Override
    public void onRequestAbortVoice(AbortVoiceRequest request) {
        mTestResult = Utils.ABORT_RESULT;
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setCompleteReq(false);
        Log.i(TAG, "in Session sending sendAbortResult for testcasetype = " + mTestType);
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
