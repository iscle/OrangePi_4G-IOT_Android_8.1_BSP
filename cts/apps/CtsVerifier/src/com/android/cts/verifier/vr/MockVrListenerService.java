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
package com.android.cts.verifier.vr;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.service.vr.VrListenerService;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MockVrListenerService extends VrListenerService {
    private static final String TAG = "MockVrListener";
    private static final AtomicInteger sNumBound = new AtomicInteger();

    private static final ArrayBlockingQueue<Event> sEventQueue = new ArrayBlockingQueue<>(4096);

    public static ArrayBlockingQueue<Event> getPendingEvents() {
        return sEventQueue;
    }

    public static int getNumBoundMockVrListeners() {
        return sNumBound.get();
    }

    public enum EventType{
        ONBIND,
        ONREBIND,
        ONUNBIND,
        ONCREATE,
        ONDESTROY,
        ONCURRENTVRMODEACTIVITYCHANGED
    }

    public static class Event {
        public final VrListenerService instance;
        public final EventType type;
        public final Object arg1;

        private Event(VrListenerService i, EventType t, Object o) {
            instance = i;
            type = t;
            arg1 = o;
        }

        public static Event build(VrListenerService instance, EventType type, Object argument1) {
            return new Event(instance, type, argument1);
        }

        public static Event build(VrListenerService instance, EventType type) {
            return new Event(instance, type, null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind called");
        sNumBound.getAndIncrement();
        try {
            sEventQueue.put(Event.build(this, EventType.ONBIND, intent));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
        return super.onBind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.i(TAG, "onRebind called");
        try {
            sEventQueue.put(Event.build(this, EventType.ONREBIND, intent));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind called");
        sNumBound.getAndDecrement();
        try {
            sEventQueue.put(Event.build(this, EventType.ONUNBIND, intent));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate called");
        try {
            sEventQueue.put(Event.build(this, EventType.ONCREATE));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy called");
        try {
            sEventQueue.put(Event.build(this, EventType.ONDESTROY));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
        super.onDestroy();
    }

    @Override
    public void onCurrentVrActivityChanged(ComponentName component) {
        Log.i(TAG, "onCurrentVrActivityChanged called with: " + component);
        try {
            sEventQueue.put(Event.build(this, EventType.ONCURRENTVRMODEACTIVITYCHANGED, component));
        } catch (InterruptedException e) {
            Log.e(TAG, "Service thread interrupted: " + e);
        }
    }

}
