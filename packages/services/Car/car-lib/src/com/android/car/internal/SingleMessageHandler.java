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
 * limitations under the License.
 */

package com.android.car.internal;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles call back into clients for Car managers.
 * @hide
 */
public abstract class SingleMessageHandler<EventType> implements Callback {
    private final int mHandledMessageWhat;
    private final Handler mHandler;

    public SingleMessageHandler(Looper looper, int handledMessage) {
        mHandledMessageWhat = handledMessage;
        mHandler = new Handler(looper, this);
    }

    protected abstract void handleEvent(EventType event);

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == mHandledMessageWhat) {
            List<EventType> events = (List<EventType>) msg.obj;
            events.forEach(new Consumer<EventType>() {
                @Override
                public void accept(EventType event) {
                    handleEvent(event);
                }
            });
        }

        return true;
    }

    public void sendEvents(List<EventType> events) {
        mHandler.sendMessage(mHandler.obtainMessage(mHandledMessageWhat, events));
    }
}
