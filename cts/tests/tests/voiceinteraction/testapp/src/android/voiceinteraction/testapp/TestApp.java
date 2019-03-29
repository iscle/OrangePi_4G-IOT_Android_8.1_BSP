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

package android.voiceinteraction.testapp;

import android.app.Activity;
import android.app.VoiceInteractor;
import android.app.VoiceInteractor.AbortVoiceRequest;
import android.app.VoiceInteractor.CommandRequest;
import android.app.VoiceInteractor.CompleteVoiceRequest;
import android.app.VoiceInteractor.ConfirmationRequest;
import android.app.VoiceInteractor.PickOptionRequest;
import android.app.VoiceInteractor.PickOptionRequest.Option;
import android.app.VoiceInteractor.Prompt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

import android.voiceinteraction.common.Utils;

public class TestApp extends Activity {
    static final String TAG = "TestApp";

    VoiceInteractor mInteractor;
    Bundle mTestinfo = new Bundle();
    Bundle mTotalInfo = new Bundle();
    Utils.TestCaseType mTestInProgress;
    int mIndex = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "TestApp created");
    }

    @Override
    public void onResume() {
        super.onResume();
        mInteractor = getVoiceInteractor();
        continueTests();
    }

    private void continueTests() {
        if (mIndex == Utils.TestCaseType.values().length) {
            // all tests done
            Log.i(TAG, "Ready to broadcast");
            broadcastResults();
            finish();
            return;
        }
        mTestInProgress = (Utils.TestCaseType.values())[mIndex++];
        testSetup();
        switch (mTestInProgress) {
          case ABORT_REQUEST_TEST:
          case ABORT_REQUEST_CANCEL_TEST:
              abortRequest();
              break;

          case COMPLETION_REQUEST_TEST:
          case COMPLETION_REQUEST_CANCEL_TEST:
              completionRequest();
              break;

          case CONFIRMATION_REQUEST_TEST:
          case CONFIRMATION_REQUEST_CANCEL_TEST:
              confirmationRequest();
              break;

          case PICKOPTION_REQUEST_TEST:
          case PICKOPTION_REQUEST_CANCEL_TEST:
              pickOptionRequest();
              break;

          case COMMANDREQUEST_TEST:
          case COMMANDREQUEST_CANCEL_TEST:
              commandRequest();
              break;

          case SUPPORTS_COMMANDS_TEST:
              String[] commands = {Utils.TEST_COMMAND};
              boolean[] supported = mInteractor.supportsCommands(commands);
              Log.i(TAG, "from supportsCommands: " + Arrays.toString(supported));
              if (supported.length == 1 && supported[0]) {
                addTestResult(Utils.SUPPORTS_COMMANDS_SUCCESS);
              } else {
                addTestResult(Utils.TEST_ERROR + " supported commands failure!");
              }
              saveTestResults();
              continueTests();
              break;
        }
    }

    private void testSetup() {
        mTestinfo.putStringArrayList(Utils.TESTINFO, new ArrayList<String>());
        mTestinfo.putString(Utils.TESTCASE_TYPE, mTestInProgress.toString());
    }

    private void saveTestResults() {
        ArrayList<String> info = mTestinfo.getStringArrayList(Utils.TESTINFO);
        if (info != null) {
            StringBuilder buf = new StringBuilder();
            int count = 0;
            for (String s : info) {
                buf.append(s);
                if (count++ > 0) {
                    buf.append(", ");
                }
            }
            mTotalInfo.putString(mTestInProgress.toString(), buf.toString());
            Log.i(TAG, "results for " + mTestInProgress + " = " +
                    mTotalInfo.getString(mTestInProgress.toString()));
        }
    }

    private void broadcastResults() {
        Intent intent = new Intent(Utils.BROADCAST_INTENT);
        intent.putExtras(mTotalInfo);
        Log.i(TAG, "broadcasting: " + intent.toString() + ", Bundle = " + mTotalInfo.toString());
        sendOrderedBroadcast(intent, null, new DoneReceiver(),
                             null, Activity.RESULT_OK, null, null);
    }

    private void confirmationRequest() {
        Prompt prompt = new Prompt(Utils.TEST_PROMPT);
        ConfirmationRequest req = new VoiceInteractor.ConfirmationRequest(prompt, mTestinfo) {
            @Override
            public void onCancel() {
                Log.i(TAG, "confirmation request Canceled!");
                addTestResult(Utils.CONFIRMATION_REQUEST_CANCEL_SUCCESS);
                saveTestResults();
                continueTests();
            }

            @Override
            public void onConfirmationResult(boolean confirmed, Bundle result) {
                mTestinfo = result;
                Log.i(TAG, "Confirmation result: confirmed=" + confirmed +
                        ", recvd bundle =" + Utils.toBundleString(result));
                addTestResult(Utils.CONFIRMATION_REQUEST_SUCCESS);
                saveTestResults();
                continueTests();
            }
        };
        mInteractor.submitRequest(req);
    }

    private void completionRequest() {
        Prompt prompt = new Prompt(Utils.TEST_PROMPT);
        CompleteVoiceRequest req = new VoiceInteractor.CompleteVoiceRequest(prompt, mTestinfo) {
            @Override
            public void onCancel() {
                Log.i(TAG, "completionRequest Canceled!");
                addTestResult(Utils.COMPLETION_REQUEST_CANCEL_SUCCESS);
                saveTestResults();
                continueTests();
            }

            @Override
            public void onCompleteResult(Bundle result) {
                mTestinfo = result;
                Log.i(TAG, "Completion result: recvd bundle =" +
                        Utils.toBundleString(result));
                addTestResult(Utils.COMPLETION_REQUEST_SUCCESS);
                saveTestResults();
                continueTests();
            }
        };
        mInteractor.submitRequest(req);
    }

    private void abortRequest() {
        Prompt prompt = new Prompt(Utils.TEST_PROMPT);
        AbortVoiceRequest req = new VoiceInteractor.AbortVoiceRequest(prompt, mTestinfo) {
            @Override
            public void onCancel() {
                Log.i(TAG, "abortRequest Canceled!");
                addTestResult(Utils.ABORT_REQUEST_CANCEL_SUCCESS);
                saveTestResults();
                continueTests();
            }

            @Override
            public void onAbortResult(Bundle result) {
                mTestinfo = result;
                Log.i(TAG, "Abort result: recvd bundle =" + Utils.toBundleString(result));
                addTestResult(Utils.ABORT_REQUEST_SUCCESS);
                saveTestResults();
                continueTests();
            }
        };
        mInteractor.submitRequest(req);
    }

    private void pickOptionRequest() {
        Prompt prompt = new Prompt(Utils.TEST_PROMPT);
        PickOptionRequest.Option[] options = new VoiceInteractor.PickOptionRequest.Option[2];
        options[0] = new Option(Utils.PICKOPTON_1, -1);
        options[1] = new Option(Utils.PICKOPTON_2, -1);
        Log.i(TAG, "pickOptionRequest initiated with Bundle = ");
        PickOptionRequest req = new VoiceInteractor.PickOptionRequest(prompt, options, mTestinfo) {
            @Override
            public void onCancel() {
                Log.i(TAG, "pickOptionRequest Canceled!");
                addTestResult(Utils.PICKOPTION_REQUEST_CANCEL_SUCCESS);
                saveTestResults();
                continueTests();
            }

            @Override
            public void onPickOptionResult(boolean finished, Option[] selections, Bundle result) {
                mTestinfo = result;
                Log.i(TAG, "PickOption result: finished = " + finished +
                        ", selections = " + Utils.toOptionsString(selections) +
                        ", recvd bundle =" + Utils.toBundleString(result));
                if ((selections.length != 1) ||
                    !selections[0].getLabel().toString().equals(Utils.PICKOPTON_3)) {
                    Utils.addErrorResult(result,
                            "Pickoption Selections Not received correctly in TestApp.");
                } else {
                    addTestResult(Utils.PICKOPTION_REQUEST_SUCCESS);
                }
                saveTestResults();
                continueTests();
            }
        };
        mInteractor.submitRequest(req);
    }

    private void commandRequest() {
        CommandRequest req = new VoiceInteractor.CommandRequest(Utils.TEST_COMMAND, mTestinfo) {
            @Override
            public void onCancel() {
                Log.i(TAG, "commandRequest Canceled!");
                addTestResult(Utils.COMMANDREQUEST_CANCEL_SUCCESS);
                saveTestResults();
                continueTests();
            }

            @Override
            public void onCommandResult(boolean isCompleted, Bundle result) {
                mTestinfo = result;
                Log.i(TAG, "CommandRequest onCommandResult result: isCompleted = " + isCompleted +
                        ", recvd bundle =" + Utils.toBundleString(result));
                String received = result.getString(Utils.TEST_ONCOMMAND_RESULT);
                if (received != null && received.equals(Utils.TEST_ONCOMMAND_RESULT_VALUE)) {
                    addTestResult(Utils.COMMANDREQUEST_SUCCESS);
                } else {
                    Utils.addErrorResult(result,
                            "Invalid commandrequest result received: " + received);
                }
                saveTestResults();
                continueTests();
            }
        };
        mInteractor.submitRequest(req);
    }

    private void addTestResult(final String msg) {
        mTestinfo.getStringArrayList(Utils.TESTINFO).add(msg);
    }

    class DoneReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Done_broadcast " + intent.getAction());
        }
    }
}
