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

package com.android.tv.util;

import android.content.Context;
import android.support.annotation.MainThread;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * A utility class for the toast message.
 */
public class ToastUtils {
    private static WeakReference<Toast> sToast;

    /**
     * Shows the toast message after canceling the previous one.
     */
    @MainThread
    public static void show(Context context, CharSequence text, int duration) {
        if (sToast != null && sToast.get() != null) {
            sToast.get().cancel();
        }
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
        sToast = new WeakReference<>(toast);
    }
}
