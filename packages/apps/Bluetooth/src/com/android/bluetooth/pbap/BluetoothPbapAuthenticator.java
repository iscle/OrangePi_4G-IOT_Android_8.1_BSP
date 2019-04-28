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

package com.android.bluetooth.pbap;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import javax.obex.Authenticator;
import javax.obex.PasswordAuthentication;

/**
 * BluetoothPbapAuthenticator is a used by BluetoothObexServer for obex
 * authentication procedure.
 */
public class BluetoothPbapAuthenticator implements Authenticator {
    private static final String TAG = "BluetoothPbapAuthenticator";

    private boolean mChallenged;

    private boolean mAuthCancelled;

    private String mSessionKey;

    private Handler mCallback;

    public BluetoothPbapAuthenticator(final Handler callback) {
        mCallback = callback;
        mChallenged = false;
        mAuthCancelled = false;
        mSessionKey = null;
    }

    public final synchronized void setChallenged(final boolean bool) {
        mChallenged = bool;
    }

    public final synchronized void setCancelled(final boolean bool) {
        mAuthCancelled = bool;
    }

    public final synchronized void setSessionKey(final String string) {
        mSessionKey = string;
    }

    private void waitUserConfirmation() {
        Message msg = Message.obtain(mCallback);
        msg.what = BluetoothPbapService.MSG_OBEX_AUTH_CHALL;
        msg.sendToTarget();
        synchronized (this) {
            while (!mChallenged && !mAuthCancelled) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting on isChalled");
                }
            }
        }
    }

    public PasswordAuthentication onAuthenticationChallenge(final String description,
            final boolean isUserIdRequired, final boolean isFullAccess) {
        waitUserConfirmation();
        if (mSessionKey.trim().length() != 0) {
            PasswordAuthentication pa = new PasswordAuthentication(null, mSessionKey.getBytes());
            return pa;
        }
        return null;
    }

    // TODO: Reserved for future use only, in case PSE challenge PCE
    public byte[] onAuthenticationResponse(final byte[] userName) {
        byte[] b = null;
        return b;
    }
}
