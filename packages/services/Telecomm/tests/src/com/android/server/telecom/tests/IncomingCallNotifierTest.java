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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.telecom.VideoProfile;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.HandoverState;
import com.android.server.telecom.ui.IncomingCallNotifier;

import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link com.android.server.telecom.ui.IncomingCallNotifier} class.
 */
public class IncomingCallNotifierTest extends TelecomTestCase {

    @Mock private IncomingCallNotifier.CallsManagerProxy mCallsManagerProxy;
    @Mock private Call mAudioCall;
    @Mock private Call mVideoCall;
    @Mock private Call mRingingCall;
    private IncomingCallNotifier mIncomingCallNotifier;
    private NotificationManager mNotificationManager;

    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        ApplicationInfo info = new ApplicationInfo();
        info.targetSdkVersion = Build.VERSION_CODES.N_MR1;
        doReturn(info).when(mContext).getApplicationInfo();
        doReturn(null).when(mContext).getTheme();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mIncomingCallNotifier = new IncomingCallNotifier(mContext);
        mIncomingCallNotifier.setCallsManagerProxy(mCallsManagerProxy);

        when(mAudioCall.getVideoState()).thenReturn(VideoProfile.STATE_AUDIO_ONLY);
        when(mAudioCall.getTargetPhoneAccountLabel()).thenReturn("Bar");
        when(mVideoCall.getVideoState()).thenReturn(VideoProfile.STATE_BIDIRECTIONAL);
        when(mVideoCall.getTargetPhoneAccountLabel()).thenReturn("Bar");
        when(mRingingCall.isSelfManaged()).thenReturn(true);
        when(mRingingCall.isIncoming()).thenReturn(true);
        when(mRingingCall.getState()).thenReturn(CallState.RINGING);
        when(mRingingCall.getVideoState()).thenReturn(VideoProfile.STATE_AUDIO_ONLY);
        when(mRingingCall.getTargetPhoneAccountLabel()).thenReturn("Foo");
        when(mRingingCall.getHandoverState()).thenReturn(HandoverState.HANDOVER_NONE);
    }

    /**
     * Add a call that isn't ringing.
     */
    @SmallTest
    public void testSingleCall() {
        mIncomingCallNotifier.onCallAdded(mAudioCall);
        verify(mNotificationManager, never()).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());
    }

    /**
     * Add a ringing call when there is no other ongoing call.
     */
    @SmallTest
    public void testIncomingDuringOngoingCall() {
        when(mCallsManagerProxy.hasCallsForOtherPhoneAccount(any())).thenReturn(false);
        mIncomingCallNotifier.onCallAdded(mRingingCall);
        verify(mNotificationManager, never()).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());
    }

    /**
     * Add a ringing call with another call ongoing, not from a different phone account.
     */
    @SmallTest
    public void testIncomingDuringOngoingCall2() {
        when(mCallsManagerProxy.hasCallsForOtherPhoneAccount(any())).thenReturn(false);
        when(mCallsManagerProxy.getNumCallsForOtherPhoneAccount(any())).thenReturn(0);
        when(mCallsManagerProxy.getActiveCall()).thenReturn(mAudioCall);

        mIncomingCallNotifier.onCallAdded(mAudioCall);
        mIncomingCallNotifier.onCallAdded(mRingingCall);
        verify(mNotificationManager, never()).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());;
    }

    /**
     * Remove ringing call with another call ongoing.
     */
    @SmallTest
    public void testCallRemoved() {
        when(mCallsManagerProxy.hasCallsForOtherPhoneAccount(any())).thenReturn(true);
        when(mCallsManagerProxy.getNumCallsForOtherPhoneAccount(any())).thenReturn(1);
        when(mCallsManagerProxy.getActiveCall()).thenReturn(mAudioCall);

        mIncomingCallNotifier.onCallAdded(mAudioCall);
        mIncomingCallNotifier.onCallAdded(mRingingCall);
        verify(mNotificationManager).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());
        mIncomingCallNotifier.onCallRemoved(mRingingCall);
        verify(mNotificationManager).cancel(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL));
    }

    /**
     * Ensure notification doesn't show during handover.
     */
    @SmallTest
    public void testDontShowDuringHandover1() {
        when(mCallsManagerProxy.hasCallsForOtherPhoneAccount(any())).thenReturn(true);
        when(mCallsManagerProxy.getNumCallsForOtherPhoneAccount(any())).thenReturn(1);
        when(mCallsManagerProxy.getActiveCall()).thenReturn(mAudioCall);
        when(mRingingCall.getHandoverState()).thenReturn(HandoverState.HANDOVER_FROM_STARTED);

        mIncomingCallNotifier.onCallAdded(mAudioCall);
        mIncomingCallNotifier.onCallAdded(mRingingCall);

        // Incoming call is in the middle of a handover, don't expect to be notified.
        verify(mNotificationManager, never()).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());;
    }

    /**
     * Ensure notification doesn't show during handover.
     */
    @SmallTest
    public void testDontShowDuringHandover2() {
        when(mCallsManagerProxy.hasCallsForOtherPhoneAccount(any())).thenReturn(true);
        when(mCallsManagerProxy.getNumCallsForOtherPhoneAccount(any())).thenReturn(1);
        when(mCallsManagerProxy.getActiveCall()).thenReturn(mAudioCall);
        when(mRingingCall.getHandoverState()).thenReturn(HandoverState.HANDOVER_COMPLETE);

        mIncomingCallNotifier.onCallAdded(mAudioCall);
        mIncomingCallNotifier.onCallAdded(mRingingCall);

        // Incoming call is done a handover, don't expect to be notified.
        verify(mNotificationManager, never()).notify(eq(IncomingCallNotifier.NOTIFICATION_TAG),
                eq(IncomingCallNotifier.NOTIFICATION_INCOMING_CALL), any());;
    }
}
