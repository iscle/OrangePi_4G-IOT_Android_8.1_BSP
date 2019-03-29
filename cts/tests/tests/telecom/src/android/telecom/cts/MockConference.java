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

package android.telecom.cts;

import android.os.Bundle;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.TelecomManager;

/**
 * {@link Conference} subclass that immediately performs any state changes that are a result of
 * callbacks sent from Telecom.
 */
public class MockConference extends Conference {

    private RemoteConference mRemoteConference = null;
    private String mDtmfString = "";
    public TestUtils.InvokeCounter mOnExtrasChanged =
            new TestUtils.InvokeCounter("onExtrasChanged");

    public MockConference(PhoneAccountHandle phoneAccount) {
        super(phoneAccount);
    }

    public MockConference(MockConnection a, MockConnection b) {
        super(a.getPhoneAccountHandle());
        addConnection(a);
        addConnection(b);
        // a is the primary connection, so inherit the properties from it.
        setConnectionCapabilities(a.getConnectionCapabilities());
        setStatusHints(a.getStatusHints());
        setExtras(a.getExtras());
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        for (Connection c : getConnections()) {
            c.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
            c.destroy();
        }
        destroy();
        if (mRemoteConference != null) {
            mRemoteConference.disconnect();
        }
    }

    @Override
    public void onSeparate(Connection connection) {
        super.onSeparate(connection);
        if (getConnections().contains(connection)) {
            removeConnection(connection);
        }
        if (mRemoteConference != null) {
            mRemoteConference.separate(((MockConnection)connection).getRemoteConnection());
        }
    }

    @Override
    public void onMerge() {
        super.onMerge();
        // Let's just change the connection display name for testing that onMerge was invoked.
        for (Connection c : getConnections()) {
            c.setCallerDisplayName(
                    TestUtils.MERGE_CALLER_NAME, TelecomManager.PRESENTATION_ALLOWED);
        }
        if (mRemoteConference != null) {
            mRemoteConference.merge();
        }
    }

    @Override
    public void onSwap() {
        super.onSwap();
        // Let's just change the connection display name for testing that onSwap was invoked.
        for (Connection c : getConnections()) {
            c.setCallerDisplayName(
                    TestUtils.SWAP_CALLER_NAME, TelecomManager.PRESENTATION_ALLOWED);
        }
        if (mRemoteConference != null) {
            mRemoteConference.swap();
        }
    }

    @Override
    public void onHold() {
        super.onHold();
        for (Connection c : getConnections()) {
            c.setOnHold();
        }
        setOnHold();
        if (mRemoteConference != null) {
            mRemoteConference.hold();
        }
    }

    @Override
    public void onUnhold() {
        super.onUnhold();
        for (Connection c : getConnections()) {
            c.setActive();
        }
        setActive();
        if (mRemoteConference != null) {
            mRemoteConference.unhold();
        }
    }

    @Override
    public void onPlayDtmfTone(char c) {
        super.onPlayDtmfTone(c);
        mDtmfString += c;
        if (mRemoteConference != null) {
            mRemoteConference.playDtmfTone(c);
        }
    }

    @Override
    public void onStopDtmfTone() {
        super.onStopDtmfTone();
        mDtmfString += ".";
        if (mRemoteConference != null) {
            mRemoteConference.stopDtmfTone();
        }
    }

    public void setRemoteConference(RemoteConference remoteConference) {
        mRemoteConference = remoteConference;
        Bundle bundle = remoteConference.getExtras();
        if (bundle != null) {
            this.putExtras(bundle);
        }
    }

    public RemoteConference getRemoteConference() {
        return mRemoteConference;
    }

    public String getDtmfString() {
        return mDtmfString;
    }

    @Override
    public void onExtrasChanged(Bundle extras) {
        setExtras(extras);
        mOnExtrasChanged.invoke(extras);
    }
}
