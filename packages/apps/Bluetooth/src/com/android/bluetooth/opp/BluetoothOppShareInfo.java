/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import android.net.Uri;

/**
 * This class stores information about a single OBEX share, e.g. one object
 * send/receive to a destination address.
 */
public class BluetoothOppShareInfo {

    public int mId;

    public Uri mUri;

    public String mHint;

    public String mFilename;

    public String mMimetype;

    public int mDirection;

    public String mDestination;

    public int mVisibility;

    public int mConfirm;

    public int mStatus;

    public long mTotalBytes;

    public long mCurrentBytes;

    public long mTimestamp;

    public boolean mMediaScanned;

    public BluetoothOppShareInfo(int id, Uri uri, String hint, String filename, String mimetype,
            int direction, String destination, int visibility, int confirm, int status,
            long totalBytes, long currentBytes, long timestamp, boolean mediaScanned) {
        mId = id;
        mUri = uri;
        mHint = hint;
        mFilename = filename;
        mMimetype = mimetype;
        mDirection = direction;
        mDestination = destination;
        mVisibility = visibility;
        mConfirm = confirm;
        mStatus = status;
        mTotalBytes = totalBytes;
        mCurrentBytes = currentBytes;
        mTimestamp = timestamp;
        mMediaScanned = mediaScanned;
    }

    public boolean isReadyToStart() {
        /*
         * For outbound 1. status is pending.
         * For inbound share 1. status is pending
         */
        if (mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
            if (mStatus == BluetoothShare.STATUS_PENDING && mUri != null) {
                return true;
            }
        } else if (mDirection == BluetoothShare.DIRECTION_INBOUND) {
            if (mStatus == BluetoothShare.STATUS_PENDING) {
                //&& mConfirm != BluetoothShare.USER_CONFIRMATION_PENDING) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCompletionNotification() {
        if (!BluetoothShare.isStatusCompleted(mStatus)) {
            return false;
        }
        if (mVisibility == BluetoothShare.VISIBILITY_VISIBLE) {
            return true;
        }
        return false;
    }

    /**
     * Check if a ShareInfo is invalid because of previous error
     */
    public boolean isObsolete() {
        if (BluetoothShare.STATUS_RUNNING == mStatus) {
            return true;
        }
        return false;
    }

}
