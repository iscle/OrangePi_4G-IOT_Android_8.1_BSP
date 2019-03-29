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

package android.voiceinteraction.service;

import android.app.VoiceInteractor;
import android.app.VoiceInteractor.Prompt;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSession.ConfirmationRequest;
import android.service.voice.VoiceInteractionSession.PickOptionRequest;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.util.ArrayList;
import java.util.List;


public class MainInteractionSession extends VoiceInteractionSession {
    static final String TAG = "MainInteractionSession";

    Intent mStartIntent;
    List<MyTask> mUsedTasks = new ArrayList<MyTask>();

    MainInteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent sessionStarted = new Intent();
        sessionStarted.setClassName("android.voiceinteraction.cts",
                "android.voiceinteraction.cts.VoiceInteractionTestReceiver");
        getContext().sendBroadcast(sessionStarted);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Canceling the Asynctask in onDestroy()");
        for (MyTask t : mUsedTasks) {
            t.cancel(true);
        }
        super.onDestroy();
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        mStartIntent = args.getParcelable("intent");
        if (mStartIntent != null) {
            startVoiceActivity(mStartIntent);
        } else if ((showFlags & SHOW_SOURCE_ACTIVITY) == SHOW_SOURCE_ACTIVITY) {
            // Verify args
            if (args == null
                    || !Utils.PRIVATE_OPTIONS_VALUE.equals(
                            args.getString(Utils.PRIVATE_OPTIONS_KEY))) {
                throw new IllegalArgumentException("Incorrect arguments for SHOW_SOURCE_ACTIVITY");
            }
        }
    }

    void assertPromptFromTestApp(CharSequence prompt, Bundle extras) {
        String str = prompt.toString();
        if (str.equals(Utils.TEST_PROMPT)) {
            Log.i(TAG, "prompt received ok from TestApp in Session");
        } else {
            Utils.addErrorResult(extras, "Invalid prompt received: " + str);
        }
    }

    synchronized MyTask newTask() {
        MyTask t = new MyTask();
        mUsedTasks.add(t);
        return t;
    }

    @Override
    public boolean[] onGetSupportedCommands(String[] commands) {
        boolean[] results = new boolean[commands.length];
        Log.i(TAG, "in onGetSupportedCommands");
        for (int idx = 0; idx < commands.length; idx++) {
            results[idx] = Utils.TEST_COMMAND.equals(commands[idx]);
            Log.i(TAG, "command " + commands[idx] + ", support = " + results[idx]);
        }
        return results;
    }

    @Override
    public void onRequestConfirmation(ConfirmationRequest request) {
        Bundle extras = request.getExtras();
        CharSequence prompt = request.getVoicePrompt().getVoicePromptAt(0);
        Log.i(TAG, "in Session onRequestConfirmation recvd. prompt=" + prompt +
                ", extras=" + Utils.toBundleString(extras));
        assertPromptFromTestApp(prompt, extras);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setExtras(extras);
        if (isTestTypeCancel(extras)) {
            Log.i(TAG, "Sending Cancel.");
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.CONFIRMATION_REQUEST_CANCEL_TEST));
        } else {
            Log.i(TAG, "in Session sending sendConfirmationResult. " +
                    Utils.toBundleString(extras));
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.CONFIRMATION_REQUEST_TEST));
        }
    }

    @Override
    public void onRequestCompleteVoice(CompleteVoiceRequest request) {
        Bundle extras = request.getExtras();
        CharSequence prompt = request.getVoicePrompt().getVoicePromptAt(0);
        Log.i(TAG, "in Session onRequestCompleteVoice recvd. message=" +
                prompt + ", extras=" + Utils.toBundleString(extras));
        assertPromptFromTestApp(prompt, extras);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setExtras(extras);
        if (isTestTypeCancel(extras)) {
            Log.i(TAG, "Sending Cancel.");
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.COMPLETION_REQUEST_CANCEL_TEST));
        } else {
            Log.i(TAG, "in Session sending sendConfirmationResult. " +
                    Utils.toBundleString(extras));
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.COMPLETION_REQUEST_TEST));
        }
    }

    @Override
    public void onRequestAbortVoice(AbortVoiceRequest request) {
        Bundle extras = request.getExtras();
        CharSequence prompt = request.getVoicePrompt().getVoicePromptAt(0);
        Log.i(TAG, "in Session onRequestAbortVoice recvd. message=" +
                prompt + ", extras=" + Utils.toBundleString(extras));
        assertPromptFromTestApp(prompt, extras);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setExtras(extras);
        if (isTestTypeCancel(extras)) {
            Log.i(TAG, "Sending Cancel.");
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.ABORT_REQUEST_CANCEL_TEST));
        } else {
            Log.i(TAG, "in Session sending sendAbortResult. " +
                Utils.toBundleString(extras));
            newTask().execute(asyncTaskArg.setTestType(Utils.TestCaseType.ABORT_REQUEST_TEST));
        }
    }

    @Override
    public void onRequestCommand(CommandRequest request) {
        Bundle extras = request.getExtras();
        Log.i(TAG, "in Session onRequestCommand recvd. Bundle = " +
                Utils.toBundleString(extras));

        // Make sure that the input request has Utils.TEST_COMMAND sent by TestApp
        String command = request.getCommand();
        if (command.equals(Utils.TEST_COMMAND)) {
            Log.i(TAG, "command received ok from TestApp in Session");
        } else {
            Utils.addErrorResult(extras, "Invalid TEST_COMMAND received: " + command);
        }
        // Add a field and value in the bundle to be sent to TestApp.
        // TestApp will ensure that these are transmitted correctly.
        extras.putString(Utils.TEST_ONCOMMAND_RESULT, Utils.TEST_ONCOMMAND_RESULT_VALUE);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request).setExtras(extras);
        if (isTestTypeCancel(extras)) {
            Log.i(TAG, "Sending Cancel.");
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.COMMANDREQUEST_CANCEL_TEST));
        } else {
            Log.i(TAG, "in Session sending sendResult. " +
                    Utils.toBundleString(extras) + ", string_in_bundle: " +
                    Utils.TEST_ONCOMMAND_RESULT + " = " + Utils.TEST_ONCOMMAND_RESULT_VALUE);
            newTask().execute(asyncTaskArg.setTestType(Utils.TestCaseType.COMMANDREQUEST_TEST));
        }
    }

    void assertPickOptionsFromTestApp(VoiceInteractor.PickOptionRequest.Option[] options,
            Bundle extras) {
        if ((options.length != 2) ||
            !options[0].getLabel().toString().equals(Utils.PICKOPTON_1) ||
            !options[1].getLabel().toString().equals(Utils.PICKOPTON_2)) {
            Utils.addErrorResult(extras, "Pickoptions Not received correctly in Session.");
        } else {
            Log.i(TAG, "Pickoptions received ok from TestApp in Session");
        }
    }

    @Override
    public void onRequestPickOption(PickOptionRequest request) {
        Bundle extras = request.getExtras();
        CharSequence prompt = request.getVoicePrompt().getVoicePromptAt(0);
        Log.i(TAG, "in Session onRequestPickOption recvd. message=" +
                prompt + ", options = " + Utils.toOptionsString(request.getOptions()) +
                ", extras=" + Utils.toBundleString(extras));
        VoiceInteractor.PickOptionRequest.Option[] picked
            = new VoiceInteractor.PickOptionRequest.Option[1];
        assertPromptFromTestApp(prompt, extras);
        assertPickOptionsFromTestApp(request.getOptions(), extras);
        picked[0] = new VoiceInteractor.PickOptionRequest.Option(Utils.PICKOPTON_3, 0);
        AsyncTaskArg asyncTaskArg = new AsyncTaskArg().setRequest(request)
                .setExtras(extras)
                .setPickedOptions(picked);
        if (isTestTypeCancel(extras)) {
            Log.i(TAG, "in MainInteractionSession, Sending Cancel.");
            newTask().execute(
                    asyncTaskArg.setTestType(Utils.TestCaseType.PICKOPTION_REQUEST_CANCEL_TEST));
        } else {
            Log.i(TAG, "in MainInteractionSession sending sendPickOptionResult. " +
                    Utils.toBundleString(extras));
            newTask().execute(asyncTaskArg.setTestType(Utils.TestCaseType.PICKOPTION_REQUEST_TEST));
        }
    }

    public static final boolean isTestTypeCancel(Bundle extras) {
        Utils.TestCaseType testCaseType;
        try {
            testCaseType = Utils.TestCaseType.valueOf(extras.getString(Utils.TESTCASE_TYPE));
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.wtf(TAG, "unexpected testCaseType value in Bundle received", e);
            return true;
        }
        return testCaseType == Utils.TestCaseType.COMPLETION_REQUEST_CANCEL_TEST ||
                testCaseType == Utils.TestCaseType.COMMANDREQUEST_CANCEL_TEST ||
                testCaseType == Utils.TestCaseType.CONFIRMATION_REQUEST_CANCEL_TEST ||
                testCaseType == Utils.TestCaseType.PICKOPTION_REQUEST_CANCEL_TEST ||
                testCaseType == Utils.TestCaseType.ABORT_REQUEST_CANCEL_TEST;
    }

    private class AsyncTaskArg {
        ConfirmationRequest confReq;
        CommandRequest commandReq;
        CompleteVoiceRequest compReq;
        AbortVoiceRequest abortReq;
        PickOptionRequest pickReq;
        Bundle extras;
        VoiceInteractor.PickOptionRequest.Option[] picked;
        Utils.TestCaseType testType;

        AsyncTaskArg setTestType(Utils.TestCaseType t) {testType = t; return this;}
        AsyncTaskArg setRequest(CommandRequest r) {commandReq = r; return this;}
        AsyncTaskArg setRequest(ConfirmationRequest r) {confReq = r; return this;}
        AsyncTaskArg setRequest(CompleteVoiceRequest r) {compReq = r; return this;}
        AsyncTaskArg setRequest(AbortVoiceRequest r) {abortReq = r; return this;}
        AsyncTaskArg setRequest(PickOptionRequest r) {pickReq = r; return this;}
        AsyncTaskArg setExtras(Bundle e) {extras = e;  return this;}
        AsyncTaskArg setPickedOptions(VoiceInteractor.PickOptionRequest.Option[] p) {
            picked = p;
            return this;
        }
    }

    private class MyTask extends AsyncTask<AsyncTaskArg, Void, Void> {
        @Override
        protected Void doInBackground(AsyncTaskArg... params) {
            AsyncTaskArg arg = params[0];
            Log.i(TAG, "in MyTask - doInBackground: requestType = " +
                    arg.testType.toString());
            switch (arg.testType) {
                case ABORT_REQUEST_CANCEL_TEST:
                    arg.abortReq.cancel();
                    break;
                case ABORT_REQUEST_TEST:
                    arg.abortReq.sendAbortResult(arg.extras);
                    break;
                case COMMANDREQUEST_CANCEL_TEST:
                    arg.commandReq.cancel();
                    break;
                case COMMANDREQUEST_TEST:
                    Log.i(TAG, "in MyTask sendResult. " +
                            Utils.toBundleString(arg.extras) + ", string_in_bundle: " +
                            Utils.TEST_ONCOMMAND_RESULT + " = " +
                            Utils.TEST_ONCOMMAND_RESULT_VALUE);
                    arg.commandReq.sendResult(arg.extras);
                    break;
                case COMPLETION_REQUEST_CANCEL_TEST:
                    arg.compReq.cancel();
                    break;
                case COMPLETION_REQUEST_TEST:
                    arg.compReq.sendCompleteResult(arg.extras);
                    break;
                case CONFIRMATION_REQUEST_CANCEL_TEST:
                     arg.confReq.cancel();
                     break;
                case CONFIRMATION_REQUEST_TEST:
                     arg.confReq.sendConfirmationResult(true, arg.extras);
                     break;
                case PICKOPTION_REQUEST_CANCEL_TEST:
                     arg.pickReq.cancel();
                     break;
                case PICKOPTION_REQUEST_TEST:
                     StringBuilder buf = new StringBuilder();
                     for (VoiceInteractor.PickOptionRequest.Option s : arg.picked) {
                         buf.append("option: " + s.toString() + ", ");
                     }
                     Log.i(TAG, "******** Sending PickoptionResult: " +
                             "picked: size = " + arg.picked.length +
                             ", Options = " + buf.toString() +
                             ", Bundle: " + Utils.toBundleString(arg.extras));
                     arg.pickReq.sendPickOptionResult(arg.picked, arg.extras);
                     break;
               default:
                   Log.i(TAG, "Doing nothing for the testcase type: " + arg.testType);
                   break;
            }
            return null;
        }
    }
}
