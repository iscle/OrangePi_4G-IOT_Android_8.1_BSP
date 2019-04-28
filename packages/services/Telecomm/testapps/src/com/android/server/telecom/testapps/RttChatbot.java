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

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.telecom.Connection;
import android.telecom.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Random;

public class RttChatbot {
    private static final String LOG_TAG = RttChatbot.class.getSimpleName();
    private static final long PER_CHARACTER_DELAY_MS = 100;
    private static final long MSG_WAIT_DELAY_MS = 3999;
    private static final double ONE_LINER_FREQUENCY = 0.1;
    private static final String REPLY_PREFIX = "You said: ";

    private static final int BEGIN_SEND_REPLY_MESSAGE = 1;
    private static final int SEND_CHARACTER = 2;
    private static final int APPEND_TO_INPUT_BUFFER = 3;

    private final Connection.RttTextStream mRttTextStream;
    private final Random mRandom = new Random();
    private final String[] mOneLiners;
    private Handler mHandler;
    private HandlerThread mSenderThread;
    private Thread mReceiverThread;

    private final class ReplyHandler extends Handler {
        private StringBuilder mInputSoFar;

        public ReplyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BEGIN_SEND_REPLY_MESSAGE:
                    removeMessages(SEND_CHARACTER);
                    sendReplyMessage();
                    break;
                case SEND_CHARACTER:
                    try {
                        mRttTextStream.write((String) msg.obj);
                    } catch (IOException e) {
                    }
                    break;
                case APPEND_TO_INPUT_BUFFER:
                    removeMessages(BEGIN_SEND_REPLY_MESSAGE);
                    sendEmptyMessageDelayed(BEGIN_SEND_REPLY_MESSAGE, MSG_WAIT_DELAY_MS);
                    String toAppend = (String) msg.obj;
                    if (mInputSoFar == null) {
                        mInputSoFar = new StringBuilder(toAppend);
                    } else {
                        mInputSoFar.append(toAppend);
                    }
                    Log.d(LOG_TAG, "Got %s to append, total text now %s",
                            toAppend, mInputSoFar.toString());
                    break;
            }
        }

        private void sendReplyMessage() {
            String messageToSend;
            if (mRandom.nextDouble() < ONE_LINER_FREQUENCY) {
                messageToSend = mOneLiners[mRandom.nextInt(mOneLiners.length)];
            } else {
                messageToSend = REPLY_PREFIX + mInputSoFar.toString();
            }
            mInputSoFar = null;
            Log.i(LOG_TAG, "Begin send reply message: %s", messageToSend);
            int[] charsToSend = messageToSend.codePoints().toArray();
            for (int i = 0; i < charsToSend.length; i++) {
                Message msg = obtainMessage(SEND_CHARACTER,
                        new String(new int[] {charsToSend[i]}, 0, 1));
                sendMessageDelayed(msg, PER_CHARACTER_DELAY_MS * i);
            }
        }
    }

    public RttChatbot(Context context, Connection.RttTextStream textStream) {
        mOneLiners = context.getResources().getStringArray(R.array.rtt_reply_one_liners);
        mRttTextStream = textStream;
    }

    public void start() {
        Log.i(LOG_TAG, "Starting RTT chatbot.");
        HandlerThread ht = new HandlerThread("RttChatbotSender");
        ht.start();
        mSenderThread = ht;
        mHandler = new ReplyHandler(ht.getLooper());
        mReceiverThread = new Thread(() -> {
            while (true) {
                String charsReceived;
                try {
                    charsReceived = mRttTextStream.read();
                } catch (IOException e) {
                    break;
                }
                if (charsReceived == null) {
                    if (Thread.currentThread().isInterrupted()) {
                        Log.w(LOG_TAG, "Thread interrupted");
                        break;
                    }
                    Log.w(LOG_TAG, "Stream closed");
                    break;
                }
                if (charsReceived.length() == 0) {
                    continue;
                }
                mHandler.obtainMessage(APPEND_TO_INPUT_BUFFER, charsReceived)
                        .sendToTarget();
            }
        }, "RttChatbotReceiver");
        mReceiverThread.start();
    }

    public void stop() {
        if (mSenderThread != null && mSenderThread.isAlive()) {
            mSenderThread.quit();
        }
        if (mReceiverThread != null && mReceiverThread.isAlive()) {
            mReceiverThread.interrupt();
        }
    }
}
