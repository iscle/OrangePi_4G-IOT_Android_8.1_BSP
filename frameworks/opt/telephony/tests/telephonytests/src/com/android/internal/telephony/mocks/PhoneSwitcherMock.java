/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.mocks;

import android.os.Handler;
import android.os.Looper;
import android.os.Registrant;
import android.os.RegistrantList;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.PhoneSwitcher;

import java.util.concurrent.atomic.AtomicBoolean;

public class PhoneSwitcherMock extends PhoneSwitcher {
    private final int mNumPhones;
    private final RegistrantList mActivePhoneRegistrants[];
    private final AtomicBoolean mIsActive[];

    public PhoneSwitcherMock(int numPhones, Looper looper) {
        super(looper);

        mNumPhones = numPhones;
        mActivePhoneRegistrants = new RegistrantList[numPhones];
        mIsActive = new AtomicBoolean[numPhones];
        for(int i = 0; i < numPhones; i++) {
            mActivePhoneRegistrants[i] = new RegistrantList();
            mIsActive[i] = new AtomicBoolean(false);
        }
    }

    @Override
    public void resendDataAllowed(int phoneId) {
        throw new RuntimeException("resendPhone not implemented");
    }

    @Override
    public boolean isPhoneActive(int phoneId) {
        return mIsActive[phoneId].get();
    }

    @Override
    public void registerForActivePhoneSwitch(int phoneId, Handler h, int what, Object o) {
        validatePhoneId(phoneId);
        Registrant r = new Registrant(h, what, o);
        mActivePhoneRegistrants[phoneId].add(r);
        r.notifyRegistrant();
    }

    @Override
    public void unregisterForActivePhoneSwitch(int phoneId, Handler h) {
        validatePhoneId(phoneId);
        mActivePhoneRegistrants[phoneId].remove(h);
    }

    private void validatePhoneId(int phoneId) {
        if (phoneId < 0 || phoneId >= mNumPhones) {
            throw new IllegalArgumentException("Invalid PhoneId");
        }
    }

    @VisibleForTesting
    public void setPhoneActive(int phoneId, boolean active) {
        validatePhoneId(phoneId);
        if (mIsActive[phoneId].getAndSet(active) != active) {
            mActivePhoneRegistrants[phoneId].notifyRegistrants();
        }
    }
}
