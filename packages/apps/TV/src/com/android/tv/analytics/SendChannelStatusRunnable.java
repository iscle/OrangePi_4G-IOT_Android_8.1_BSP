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
 * limitations under the License
 */

package com.android.tv.analytics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;

import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.util.RecurringRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Periodically sends analytics data with the channel count.
 *
 * <p>
 * <p>This should only be started from a user activity
 * like {@link com.android.tv.MainActivity}.
 */
@MainThread
public class SendChannelStatusRunnable implements Runnable {
    private static final long SEND_CHANNEL_STATUS_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);

    public static RecurringRunner startChannelStatusRecurringRunner(Context context,
            Tracker tracker, ChannelDataManager channelDataManager) {

        final SendChannelStatusRunnable sendChannelStatusRunnable = new SendChannelStatusRunnable(
                channelDataManager, tracker);

        Runnable onStopRunnable = new Runnable() {
            @Override
            public void run() {
                sendChannelStatusRunnable.setDbLoadListener(null);
            }
        };
        final RecurringRunner recurringRunner = new RecurringRunner(context,
                SEND_CHANNEL_STATUS_INTERVAL_MS, sendChannelStatusRunnable, onStopRunnable);

        if (channelDataManager.isDbLoadFinished()) {
            sendChannelStatusRunnable.setDbLoadListener(null);
            recurringRunner.start();
        } else {
            //Start the recurring runnable after the channel DB is finished loading.
            sendChannelStatusRunnable.setDbLoadListener(new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    // This is called inside an iterator of Listeners so the remove step is done
                    // via a post on the main thread
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            sendChannelStatusRunnable.setDbLoadListener(null);
                        }
                    });
                    recurringRunner.start();
                }

                @Override
                public void onChannelListUpdated() { }

                @Override
                public void onChannelBrowsableChanged() { }
            });
        }
        return recurringRunner;
    }

    private final ChannelDataManager mChannelDataManager;
    private final Tracker mTracker;
    private ChannelDataManager.Listener mListener;

    private SendChannelStatusRunnable(ChannelDataManager channelDataManager, Tracker tracker) {
        mChannelDataManager = channelDataManager;
        mTracker = tracker;
    }

    @Override
    public void run() {
        int browsableChannelCount = 0;
        List<Channel> channelList = mChannelDataManager.getChannelList();
        for (Channel channel : channelList) {
            if (channel.isBrowsable()) {
                ++browsableChannelCount;
            }
        }
        mTracker.sendChannelCount(browsableChannelCount, channelList.size());
    }

    private void setDbLoadListener(ChannelDataManager.Listener listener) {
        if (mListener != null) {
            mChannelDataManager.removeListener(mListener);
        }
        mListener = listener;
        if (listener != null) {
            mChannelDataManager.addListener(listener);
        }
    }
}
