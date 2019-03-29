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
package android.app.cts.backgroundrestrictions;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class MyReceiver extends BroadcastReceiver {

    private static final AtomicReference<Consumer<Intent>> sCallback
            = new AtomicReference<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        final Consumer<Intent>callback = sCallback.get();
        if (callback != null) {
            callback.accept(intent);
        }
    }

    public static void setCallback(Consumer<Intent> callback) {
        sCallback.set(callback);
    }

    public static void clearCallback() {
        sCallback.set(null);
    }

    public static ComponentName getComponent() {
        return new ComponentName(InstrumentationRegistry.getContext(), MyReceiver.class);
    }
}
