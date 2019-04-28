/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer.telecom;

import android.net.Uri;

/**
 * Represents a single call on UI. It is an abstraction of {@code android.telecom.Call}.
 */
public class UiCall {
    private final int mId;

    private int mState;
    private boolean mHasParent;
    private String mNumber;
    private CharSequence mDisconnectCause;
    private boolean mHasChildren;
    private Uri mGatewayInfoOriginalAddress;
    private long connectTimeMillis;

    public UiCall(int id) {
        mId = id;
    }

    public int getId() {
        return mId;
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }

    public boolean hasParent() {
        return mHasParent;
    }

    public void setHasParent(boolean hasParent) {
        mHasParent = hasParent;
    }

    public void setHasChildren(boolean hasChildren) {
        mHasChildren = hasChildren;
    }

    public boolean hasChildren() {
        return mHasChildren;
    }

    public String getNumber() {
        return mNumber;
    }

    public void setNumber(String number) {
        mNumber = number;
    }

    public CharSequence getDisconnectCause() {
        return mDisconnectCause;
    }

    public void setDisconnectCause(CharSequence disconnectCause) {
        mDisconnectCause = disconnectCause;
    }

    public Uri getGatewayInfoOriginalAddress() {
        return mGatewayInfoOriginalAddress;
    }

    public void setGatewayInfoOriginalAddress(Uri gatewayInfoOriginalAddress) {
        mGatewayInfoOriginalAddress = gatewayInfoOriginalAddress;
    }

    public long getConnectTimeMillis() {
        return connectTimeMillis;
    }

    public void setConnectTimeMillis(long connectTimeMillis) {
        this.connectTimeMillis = connectTimeMillis;
    }
}
