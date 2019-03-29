/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.cts.verifierusbcompanion;

import android.app.Activity;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;

/**
 * Utility to receive callbacks when an USB accessory is attached.
 */
public class AccessoryAttachmentHandler extends Activity {
    private static final ArrayList<AccessoryAttachmentObserver> sObservers = new ArrayList<>();

    /**
     * Register an observer to be called when an USB accessory connects.
     *
     * @param observer The observer that should be called when an USB accessory connects.
     */
    static void addObserver(@NonNull AccessoryAttachmentObserver observer) {
        synchronized (sObservers) {
            sObservers.add(observer);
        }
    }

    /**
     * Remove an observer that was added in {@link #addObserver}.
     *
     * @param observer The observer to remove
     */
    static void removeObserver(@NonNull AccessoryAttachmentObserver observer) {
        synchronized (sObservers) {
            sObservers.remove(observer);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UsbAccessory accessory = getIntent().getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

        synchronized (sObservers) {
            ArrayList<AccessoryAttachmentObserver> observers =
                    (ArrayList<AccessoryAttachmentObserver>) sObservers.clone();

            for (AccessoryAttachmentObserver observer : observers) {
                observer.onAttached(accessory);
            }
        }

        finish();
    }

    /**
     * Callback when an accessory is attached
     */
    interface AccessoryAttachmentObserver {
        void onAttached(UsbAccessory accessory);
    }
}
