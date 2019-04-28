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
 * limitations under the License
 */

package com.android.server.telecom.testapps;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telecom.Call;
import android.telecom.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class TestRttActivity extends Activity {
    private static final String LOG_TAG = TestRttActivity.class.getSimpleName();
    private static final long NEWLINE_DELAY_MILLIS = 3000;

    private static final int UPDATE_RECEIVED_TEXT = 1;
    private static final int UPDATE_SENT_TEXT = 2;
    private static final int RECEIVED_MESSAGE_GAP = 3;
    private static final int SENT_MESSAGE_GAP = 4;

    private TextView mReceivedText;
    private TextView mSentText;
    private EditText mTypingBox;

    private TestCallList mCallList;

    private Handler mTextDisplayHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String text;
            switch (msg.what) {
                case UPDATE_RECEIVED_TEXT:
                    text = (String) msg.obj;
                    mReceivedText.append(text);
                    break;
                case UPDATE_SENT_TEXT:
                    text = (String) msg.obj;
                    mSentText.append(text);
                    break;
                case RECEIVED_MESSAGE_GAP:
                    mReceivedText.append("\n> ");
                    break;
                case SENT_MESSAGE_GAP:
                    mSentText.append("\n> ");
                    mTypingBox.setText("");
                    break;
                default:
                    Log.w(LOG_TAG, "Invalid message %d", msg.what);
            }
        }
    };

    private Thread mReceiveReader = new Thread() {
        @Override
        public void run() {
            // outer loop
            while (true) {
                begin :
                // sleep and wait if there are no calls
                while (mCallList.size() > 0) {
                    Call.RttCall rttCall = mCallList.getCall(0).getRttCall();
                    if (rttCall == null) {
                        break;
                    }
                    // inner read loop
                    while (true) {
                        String receivedText;
                        receivedText = rttCall.read();
                        if (receivedText == null) {
                            if (Thread.currentThread().isInterrupted()) {
                                break begin;
                            }
                            break;
                        }
                        Log.d(LOG_TAG, "Received %s", receivedText);
                        mTextDisplayHandler.removeMessages(RECEIVED_MESSAGE_GAP);
                        mTextDisplayHandler.sendEmptyMessageDelayed(RECEIVED_MESSAGE_GAP,
                                NEWLINE_DELAY_MILLIS);
                        mTextDisplayHandler.obtainMessage(UPDATE_RECEIVED_TEXT, receivedText)
                                .sendToTarget();
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.rtt_incall_screen);

        mReceivedText = (TextView) findViewById(R.id.received_messages_text);
        mSentText = (TextView) findViewById(R.id.sent_messages_text);
        mTypingBox = (EditText) findViewById(R.id.rtt_typing_box);

        Button endRttButton = (Button) findViewById(R.id.end_rtt_button);
        Spinner rttModeSelector = (Spinner) findViewById(R.id.rtt_mode_selection_spinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.rtt_mode_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rttModeSelector.setAdapter(adapter);

        mCallList = TestCallList.getInstance();
        mCallList.addListener(new TestCallList.Listener() {
            @Override
            public void onCallRemoved(Call call) {
                if (mCallList.size() == 0) {
                    Log.i(LOG_TAG, "Ending the RTT UI");
                    finish();
                }
            }

            @Override
            public void onRttStopped(Call call) {
                TestRttActivity.this.finish();
            }
        });

        endRttButton.setOnClickListener((view) -> {
            Call call = mCallList.getCall(0);
            call.stopRtt();
        });

        rttModeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CharSequence selection = (CharSequence) parent.getItemAtPosition(position);
                Call.RttCall call = mCallList.getCall(0).getRttCall();
                switch (selection.toString()) {
                    case "Full":
                        call.setRttMode(Call.RttCall.RTT_MODE_FULL);
                        break;
                    case "HCO":
                        call.setRttMode(Call.RttCall.RTT_MODE_HCO);
                        break;
                    case "VCO":
                        call.setRttMode(Call.RttCall.RTT_MODE_VCO);
                        break;
                    default:
                        Log.w(LOG_TAG, "Bad name for rtt mode: %s", selection.toString());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mTypingBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count == 0 || count < before) {
                    // ignore deletions and clears
                    return;
                }
                // Only appending at the end is supported.
                int numCharsInserted = count - before;
                String toAppend =
                        s.subSequence(s.length() - numCharsInserted, s.length()).toString();

                if (toAppend.isEmpty()) {
                    return;
                }
                try {
                    mCallList.getCall(0).getRttCall().write(toAppend);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Exception sending text %s: %s", toAppend, e);
                }
                mTextDisplayHandler.removeMessages(SENT_MESSAGE_GAP);
                mTextDisplayHandler.sendEmptyMessageDelayed(SENT_MESSAGE_GAP, NEWLINE_DELAY_MILLIS);
                mTextDisplayHandler.obtainMessage(UPDATE_SENT_TEXT, toAppend).sendToTarget();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        mReceiveReader.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mReceiveReader.interrupt();
    }

}
