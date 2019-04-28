/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import android.content.Context;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton entry point for the telephony-services app. Initializes ongoing systems relating to
 * PSTN calls. This is started when the device starts and will be restarted automatically
 * if it goes away for any reason (e.g., crashes).
 * This is separate from the actual Application class because we only support one instance of this
 * app - running as the default user. {@link com.android.phone.PhoneApp} determines whether or not
 * we are running as the default user and if we are, then initializes and runs this class's
 * {@link #onCreate}.
 */
public class TelephonyGlobals {
    private static TelephonyGlobals sInstance;

    /** The application context. */
    private final Context mContext;

    // For supporting MSIM phone, change Phone and TtyManager as 1 to 1
    private List<TtyManager> mTtyManagers = new ArrayList<>();

    /**
     * Persists the specified parameters.
     *
     * @param context The application context.
     */
    public TelephonyGlobals(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized TelephonyGlobals getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TelephonyGlobals(context);
        }
        return sInstance;
    }

    public void onCreate() {
        // Make this work with Multi-SIM devices
        Phone[] phones = PhoneFactory.getPhones();
        for (Phone phone : phones) {
            mTtyManagers.add(new TtyManager(mContext, phone));
        }

        TelecomAccountRegistry.getInstance(mContext).setupOnBoot();
    }
}
