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

import android.os.Handler;

/**
 * Interface for control the state of an OBEX Session.
 */
public interface BluetoothOppObexSession {

    /**
     * Message to notify when a transfer is completed For outbound share, it
     * means one file has been sent. For inbounds share, it means one file has
     * been received.
     */
    int MSG_SHARE_COMPLETE = 0;

    /**
     * Message to notify when a session is completed For outbound share, it
     * should be a consequence of session.stop() For inbounds share, it should
     * be a consequence of remote disconnect
     */
    int MSG_SESSION_COMPLETE = 1;

    /** Message to notify when a BluetoothOppObexSession get any error condition */
    int MSG_SESSION_ERROR = 2;

    /**
     * Message to notify when a BluetoothOppObexSession is interrupted when
     * waiting for remote
     */
    int MSG_SHARE_INTERRUPTED = 3;

    int MSG_CONNECT_TIMEOUT = 4;

    int SESSION_TIMEOUT = 50000;

    void start(Handler sessionHandler, int numShares);

    void stop();

    void addShare(BluetoothOppShareInfo share);

    void unblock();

}
