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

package com.android.documentsui.testing;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.util.TimerTask;

/**
 * A test double of {@link Handler}, backed by {@link TestTimer}.
 */
public class TestHandler extends Handler {
    private TestTimer mTimer = new TestTimer();

    // Handler uses SystemClock.uptimeMillis() when scheduling task to get current time, but
    // TestTimer has its own warped time for us to "fast forward" into the future. Therefore after
    // we "fast forwarded" TestTimer once Handler may schedule tasks running in the "past" relative
    // to the fast-forwarded TestTimer and cause problems. This value is used to track how much we
    // fast-forward into the future to make sure we schedule tasks in the future of TestTimer as
    // well.
    private long mTimeDelta = 0;

    public TestHandler() {
        // Use main looper to trick underlying handler, we're not using it at all.
        super(Looper.getMainLooper());
    }

    public boolean hasScheduledMessage() {
        return mTimer.hasScheduledTask();
    }

    public void dispatchNextMessage() {
        mTimer.fastForwardToNextTask();

        mTimeDelta = mTimer.getNow() - SystemClock.uptimeMillis();
    }

    public void dispatchAllScheduledMessages() {
        while (hasScheduledMessage()) {
            dispatchNextMessage();
        }
    }

    public void dispatchAllMessages() {
        while (hasScheduledMessage()) {
            dispatchAllScheduledMessages();
        }
    }

    @Override
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        msg.setTarget(this);
        TimerTask task = new MessageTimerTask(msg);
        mTimer.scheduleAtTime(new TestTimer.Task(task), uptimeMillis + mTimeDelta);
        return true;
    }

    private static class MessageTimerTask extends TimerTask {
        private Message mMessage;

        private MessageTimerTask(Message message) {
            mMessage = message;
        }

        @Override
        public void run() {
            mMessage.getTarget().dispatchMessage(mMessage);
        }
    }
}
