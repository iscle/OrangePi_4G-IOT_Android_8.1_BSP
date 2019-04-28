/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.telecom.testapps;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.Log;
import android.widget.Toast;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service which provides fake calls to test the ConnectionService interface.
 * TODO: Rename all classes in the directory to Dummy* (e.g., DummyConnectionService).
 */
public class TestConnectionService extends ConnectionService {
    /**
     * Intent extra used to pass along the video state for a new test call.
     */
    public static final String EXTRA_START_VIDEO_STATE = "extra_start_video_state";

    public static final String EXTRA_HANDLE = "extra_handle";

    private static final String LOG_TAG = TestConnectionService.class.getSimpleName();
    /**
     * Random number generator used to generate phone numbers.
     */
    private Random mRandom = new Random();

    private final class TestConference extends Conference {

        private final Connection.Listener mConnectionListener = new Connection.Listener() {
            @Override
            public void onDestroyed(Connection c) {
                removeConnection(c);
                if (getConnections().size() == 0) {
                    setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                    destroy();
                }
            }
        };

        public TestConference(Connection a, Connection b) {
            super(null);
            setConnectionCapabilities(
                    Connection.CAPABILITY_SUPPORT_HOLD |
                    Connection.CAPABILITY_HOLD |
                    Connection.CAPABILITY_MUTE |
                    Connection.CAPABILITY_MANAGE_CONFERENCE);
            addConnection(a);
            addConnection(b);

            a.addConnectionListener(mConnectionListener);
            b.addConnectionListener(mConnectionListener);

            a.setConference(this);
            b.setConference(this);

            setActive();
        }

        @Override
        public void onDisconnect() {
            for (Connection c : getConnections()) {
                c.setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                c.destroy();
            }
        }

        @Override
        public void onSeparate(Connection connection) {
            if (getConnections().contains(connection)) {
                connection.setConference(null);
                removeConnection(connection);
                connection.removeConnectionListener(mConnectionListener);
            }
        }

        @Override
        public void onHold() {
            for (Connection c : getConnections()) {
                c.setOnHold();
            }
            setOnHold();
        }

        @Override
        public void onUnhold() {
            for (Connection c : getConnections()) {
                c.setActive();
            }
            setActive();
        }
    }

    final class TestConnection extends Connection {
        private final boolean mIsIncoming;

        /** Used to cleanup camera and media when done with connection. */
        private TestVideoProvider mTestVideoCallProvider;
        private ConnectionRequest mOriginalRequest;
        private RttChatbot mRttChatbot;

        private BroadcastReceiver mHangupReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setDisconnected(new DisconnectCause(DisconnectCause.MISSED));
                destroyCall(TestConnection.this);
                destroy();
            }
        };

        private BroadcastReceiver mUpgradeRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int request = Integer.parseInt(intent.getData().getSchemeSpecificPart());
                final VideoProfile videoProfile = new VideoProfile(request);
                mTestVideoCallProvider.receiveSessionModifyRequest(videoProfile);
            }
        };

        private BroadcastReceiver mRttUpgradeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendRemoteRttRequest();
            }
        };

        TestConnection(boolean isIncoming, ConnectionRequest request) {
            mIsIncoming = isIncoming;
            mOriginalRequest = request;
            // Assume all calls are video capable.
            int capabilities = getConnectionCapabilities();
            capabilities |= CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL;
            capabilities |= CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL;
            capabilities |= CAPABILITY_CAN_UPGRADE_TO_VIDEO;
            capabilities |= CAPABILITY_MUTE;
            capabilities |= CAPABILITY_SUPPORT_HOLD;
            capabilities |= CAPABILITY_HOLD;
            capabilities |= CAPABILITY_RESPOND_VIA_TEXT;
            setConnectionCapabilities(capabilities);

            int properties = getConnectionProperties();
            if (mOriginalRequest.isRequestingRtt()) {
                properties |= PROPERTY_IS_RTT;
            }
            setConnectionProperties(properties);

            if (isIncoming) {
                putExtra(Connection.EXTRA_ANSWERING_DROPS_FG_CALL, true);
            }
            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                    mHangupReceiver, new IntentFilter(TestCallActivity.ACTION_HANGUP_CALLS));
            final IntentFilter filter =
                    new IntentFilter(TestCallActivity.ACTION_SEND_UPGRADE_REQUEST);
            filter.addDataScheme("int");
            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                    mUpgradeRequestReceiver, filter);

            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(
                    mRttUpgradeReceiver,
                    new IntentFilter(TestCallActivity.ACTION_REMOTE_RTT_UPGRADE));
        }

        void startOutgoing() {
            setDialing();
            mHandler.postDelayed(() -> {
                setActive();
                activateCall(TestConnection.this);
            }, 4000);
            if (mOriginalRequest.isRequestingRtt()) {
                Log.i(LOG_TAG, "Is RTT call. Starting chatbot service.");
                mRttChatbot = new RttChatbot(getApplicationContext(),
                        mOriginalRequest.getRttTextStream());
                mRttChatbot.start();
            }
        }

        /** ${inheritDoc} */
        @Override
        public void onAbort() {
            destroyCall(this);
            destroy();
        }

        /** ${inheritDoc} */
        @Override
        public void onAnswer(int videoState) {
            setVideoState(videoState);
            activateCall(this);
            setActive();
            updateConferenceable();
            if (mOriginalRequest.isRequestingRtt()) {
                Log.i(LOG_TAG, "Is RTT call. Starting chatbot service.");
                mRttChatbot = new RttChatbot(getApplicationContext(),
                        mOriginalRequest.getRttTextStream());
                mRttChatbot.start();
            }
        }

        /** ${inheritDoc} */
        @Override
        public void onPlayDtmfTone(char c) {
            if (c == '1') {
                setDialing();
            }
        }

        /** ${inheritDoc} */
        @Override
        public void onStopDtmfTone() { }

        /** ${inheritDoc} */
        @Override
        public void onDisconnect() {
            setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
            destroyCall(this);
            destroy();
        }

        /** ${inheritDoc} */
        @Override
        public void onHold() {
            setOnHold();
        }

        /** ${inheritDoc} */
        @Override
        public void onReject() {
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroyCall(this);
            destroy();
        }

        /** ${inheritDoc} */
        @Override
        public void onUnhold() {
            setActive();
        }

        @Override
        public void onStopRtt() {
            int newProperties = getConnectionProperties() & ~PROPERTY_IS_RTT;
            setConnectionProperties(newProperties);
            mRttChatbot.stop();
            mRttChatbot = null;
        }

        @Override
        public void handleRttUpgradeResponse(RttTextStream rttTextStream) {
            Log.i(this, "RTT request response was %s", rttTextStream == null);
            if (rttTextStream != null) {
                mRttChatbot = new RttChatbot(getApplicationContext(), rttTextStream);
                mRttChatbot.start();
                sendRttInitiationSuccess();
            }
        }

        @Override
        public void onStartRtt(RttTextStream textStream) {
            boolean doAccept = Math.random() < 0.5;
            if (doAccept) {
                Log.i(this, "Accepting RTT request.");
                mRttChatbot = new RttChatbot(getApplicationContext(), textStream);
                mRttChatbot.start();
                sendRttInitiationSuccess();
            } else {
                sendRttInitiationFailure(RttModifyStatus.SESSION_MODIFY_REQUEST_FAIL);
            }
        }

        public void setTestVideoCallProvider(TestVideoProvider testVideoCallProvider) {
            mTestVideoCallProvider = testVideoCallProvider;
        }

        public void cleanup() {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                    mHangupReceiver);
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(
                    mUpgradeRequestReceiver);
        }

        /**
         * Stops playback of test videos.
         */
        private void stopAndCleanupMedia() {
            if (mTestVideoCallProvider != null) {
                mTestVideoCallProvider.stopAndCleanupMedia();
                mTestVideoCallProvider.stopCamera();
            }
        }
    }

    private final List<TestConnection> mCalls = new ArrayList<>();
    private final Handler mHandler = new Handler();

    /** Used to play an audio tone during a call. */
    private MediaPlayer mMediaPlayer;

    @Override
    public boolean onUnbind(Intent intent) {
        log("onUnbind");
        mMediaPlayer = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onConference(Connection a, Connection b) {
        addConference(new TestConference(a, b));
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest originalRequest) {

        final Uri handle = originalRequest.getAddress();
        String number = originalRequest.getAddress().getSchemeSpecificPart();
        log("call, number: " + number);

        // Crash on 555-DEAD to test call service crashing.
        if ("5550340".equals(number)) {
            throw new RuntimeException("Goodbye, cruel world.");
        }

        Bundle extras = originalRequest.getExtras();
        String gatewayPackage = extras.getString(TelecomManager.GATEWAY_PROVIDER_PACKAGE);
        Uri originalHandle = extras.getParcelable(TelecomManager.GATEWAY_ORIGINAL_ADDRESS);

        if (extras.containsKey(TelecomManager.EXTRA_CALL_SUBJECT)) {
            String callSubject = extras.getString(TelecomManager.EXTRA_CALL_SUBJECT);
            log("Got subject: " + callSubject);
            Toast.makeText(getApplicationContext(), "Got subject :" + callSubject,
                    Toast.LENGTH_SHORT).show();
        }

        log("gateway package [" + gatewayPackage + "], original handle [" +
                originalHandle + "]");

        final TestConnection connection =
                new TestConnection(false /* isIncoming */, originalRequest);
        setAddress(connection, handle);

        // If the number starts with 555, then we handle it ourselves. If not, then we
        // use a remote connection service.
        // TODO: Have a special phone number to test the account-picker dialog flow.
        if (number != null && number.startsWith("555")) {
            // Normally we would use the original request as is, but for testing purposes, we are
            // adding ".." to the end of the number to follow its path more easily through the logs.
            final ConnectionRequest request = new ConnectionRequest(
                    originalRequest.getAccountHandle(),
                    Uri.fromParts(handle.getScheme(),
                    handle.getSchemeSpecificPart() + "..", ""),
                    originalRequest.getExtras(),
                    originalRequest.getVideoState());
            connection.setVideoState(originalRequest.getVideoState());
            addVideoProvider(connection);
            addCall(connection);
            connection.startOutgoing();

            for (Connection c : getAllConnections()) {
                c.setOnHold();
            }
        } else {
            log("Not a test number");
        }
        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(this, TestConnectionService.class);

        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            final TestConnection connection = new TestConnection(true, request);
            // Get the stashed intent extra that determines if this is a video call or audio call.
            Bundle extras = request.getExtras();
            int videoState = extras.getInt(EXTRA_START_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
            Uri providedHandle = extras.getParcelable(EXTRA_HANDLE);

            // Use dummy number for testing incoming calls.
            Uri address = providedHandle == null ?
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, getDummyNumber(
                            VideoProfile.isVideo(videoState)), null)
                    : providedHandle;
            connection.setVideoState(videoState);

            Bundle connectionExtras = connection.getExtras();
            if (connectionExtras == null) {
                connectionExtras = new Bundle();
            }

            // Randomly choose a varying length call subject.
            int subjectFormat = mRandom.nextInt(3);
            if (subjectFormat == 0) {
                connectionExtras.putString(Connection.EXTRA_CALL_SUBJECT,
                        "This is a test of call subject lines. Subjects for a call can be long " +
                                " and can go even longer.");
            } else if (subjectFormat == 1) {
                connectionExtras.putString(Connection.EXTRA_CALL_SUBJECT,
                        "This is a test of call subject lines.");
            }

            connection.putExtras(connectionExtras);

            setAddress(connection, address);

            addVideoProvider(connection);

            addCall(connection);

            connection.setVideoState(videoState);
            return connection;
        } else {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR,
                    "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    @Override
    public Connection onCreateUnknownConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        PhoneAccountHandle accountHandle = request.getAccountHandle();
        ComponentName componentName = new ComponentName(this, TestConnectionService.class);
        if (accountHandle != null && componentName.equals(accountHandle.getComponentName())) {
            final TestConnection connection = new TestConnection(false, request);
            final Bundle extras = request.getExtras();
            final Uri providedHandle = extras.getParcelable(EXTRA_HANDLE);

            Uri handle = providedHandle == null ?
                    Uri.fromParts(PhoneAccount.SCHEME_TEL, getDummyNumber(false), null)
                    : providedHandle;

            connection.setAddress(handle,  TelecomManager.PRESENTATION_ALLOWED);
            connection.setDialing();

            addCall(connection);
            return connection;
        } else {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR,
                    "Invalid inputs: " + accountHandle + " " + componentName));
        }
    }

    private void addVideoProvider(TestConnection connection) {
        TestVideoProvider testVideoCallProvider =
                new TestVideoProvider(getApplicationContext(), connection);
        connection.setVideoProvider(testVideoCallProvider);

        // Keep reference to original so we can clean up the media players later.
        connection.setTestVideoCallProvider(testVideoCallProvider);
    }

    private void activateCall(TestConnection connection) {
        if (mMediaPlayer == null) {
            mMediaPlayer = createMediaPlayer();
        }
        if (!mMediaPlayer.isPlaying()) {
            mMediaPlayer.start();
        }
    }

    private void destroyCall(TestConnection connection) {
        connection.cleanup();
        mCalls.remove(connection);

        // Ensure any playing media and camera resources are released.
        connection.stopAndCleanupMedia();

        // Stops audio if there are no more calls.
        if (mCalls.isEmpty() && mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = createMediaPlayer();
        }

        updateConferenceable();
    }

    private void addCall(TestConnection connection) {
        mCalls.add(connection);
        updateConferenceable();
    }

    private void updateConferenceable() {
        List<Connection> freeConnections = new ArrayList<>();
        freeConnections.addAll(mCalls);
        for (int i = 0; i < freeConnections.size(); i++) {
            if (freeConnections.get(i).getConference() != null) {
                freeConnections.remove(i);
            }
        }
        for (int i = 0; i < freeConnections.size(); i++) {
            Connection c = freeConnections.remove(i);
            c.setConferenceableConnections(freeConnections);
            freeConnections.add(i, c);
        }
    }

    private void setAddress(Connection connection, Uri address) {
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        if ("5551234".equals(address.getSchemeSpecificPart())) {
            connection.setCallerDisplayName("Hello World", TelecomManager.PRESENTATION_ALLOWED);
        }
    }

    private MediaPlayer createMediaPlayer() {
        // Prepare the media player to play a tone when there is a call.
        MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.beep_boop);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

    private static void log(String msg) {
        Log.w("telecomtestcs", "[TestConnectionService] " + msg);
    }

    /**
     * Generates a random phone number of format 555YXXX.  Where Y will be {@code 1} if the
     * phone number is for a video call and {@code 0} for an audio call.  XXX is a randomly
     * generated phone number.
     *
     * @param isVideo {@code True} if the call is a video call.
     * @return The phone number.
     */
    private String getDummyNumber(boolean isVideo) {
        int videoDigit = isVideo ? 1 : 0;
        int number = mRandom.nextInt(999);
        return String.format("555%s%03d", videoDigit, number);
    }
}

