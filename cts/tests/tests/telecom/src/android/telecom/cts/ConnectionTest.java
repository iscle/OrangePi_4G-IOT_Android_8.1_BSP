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

import static android.telecom.cts.TestUtils.shouldTestTelecom;

import com.android.compatibility.common.util.ApiLevelUtil;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.test.AndroidTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ConnectionTest extends AndroidTestCase {

    public void testStateCallbacks() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);

        waitForStateChange(lock);
        assertEquals(Connection.STATE_NEW, connection.getState());

        connection.setInitializing();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_INITIALIZING, connection.getState());

        connection.setInitialized();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_NEW, connection.getState());

        connection.setRinging();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_RINGING, connection.getState());

        connection.setDialing();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_DIALING, connection.getState());

        connection.setActive();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_ACTIVE, connection.getState());

        connection.setOnHold();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_HOLDING, connection.getState());

        connection.setPulling();
        waitForStateChange(lock);
        assertEquals(Connection.STATE_PULLING_CALL, connection.getState());

        connection.setDisconnected(
                new DisconnectCause(DisconnectCause.LOCAL, "Test call"));
        waitForStateChange(lock);
        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());

        connection.setRinging();
        waitForStateChange(lock);
        assertEquals("Connection should not move out of STATE_DISCONNECTED.",
                Connection.STATE_DISCONNECTED, connection.getState());
    }

    /**
     * {@link UnsupportedOperationException} is only thrown in L MR1+.
     */
    public void testFailedState() {
        if (ApiLevelUtil.isBefore(Build.VERSION_CODES.LOLLIPOP_MR1)) {
            return;
        }
        Connection connection = Connection.createFailedConnection(
                new DisconnectCause(DisconnectCause.LOCAL, "Test call"));
        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());

        try {
            connection.setRinging();
        } catch (UnsupportedOperationException e) {
            return;
        }
        fail("Connection should not move out of STATE_DISCONNECTED");
    }

    /**
     * {@link UnsupportedOperationException} is only thrown in L MR1+.
     */
    public void testCanceledState() {
        if (ApiLevelUtil.isBefore(Build.VERSION_CODES.LOLLIPOP_MR1)) {
            return;
        }
        Connection connection = Connection.createCanceledConnection();
        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());

        try {
            connection.setDialing();
        } catch (UnsupportedOperationException e) {
            return;
        }
        fail("Connection should not move out of STATE_DISCONNECTED");
    }

    public void testSetAndGetCallerDisplayName() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        connection.setCallerDisplayName("Test User", TelecomManager.PRESENTATION_ALLOWED);
        assertEquals("Test User", connection.getCallerDisplayName());
        assertEquals(TelecomManager.PRESENTATION_ALLOWED,
                connection.getCallerDisplayNamePresentation());
    }

    public void testSetAndGetAddress() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        final Uri address = Uri.fromParts("tel", "1234567", null);
        connection.setAddress(address, TelecomManager.PRESENTATION_UNKNOWN);
        assertEquals(address, connection.getAddress());
        assertEquals(TelecomManager.PRESENTATION_UNKNOWN, connection.getAddressPresentation());
    }

    public void testSetAndGetConnectionCapabilities() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        final int capabilities = Connection.CAPABILITY_HOLD | Connection.CAPABILITY_CAN_PAUSE_VIDEO
                | Connection.CAPABILITY_MANAGE_CONFERENCE | Connection.CAPABILITY_RESPOND_VIA_TEXT;

        connection.setConnectionCapabilities(capabilities);

        assertEquals(capabilities, connection.getConnectionCapabilities());
    }

    public void testSetAndGetConnectionProperties() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        final int properties = Connection.PROPERTY_IS_EXTERNAL_CALL;

        connection.setConnectionProperties(properties);

        assertEquals(properties, connection.getConnectionProperties());
    }

    public void testSetAndGetDisconnectCause() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);
        assertEquals(Connection.STATE_NEW, connection.getState());

        final DisconnectCause disconnectCause = new DisconnectCause(DisconnectCause.REJECTED,
                "No friends", "No friends to talk to", "No friends to talk to");

        connection.setDisconnected(disconnectCause);

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(disconnectCause, connection.getDisconnectCause());
    }

    public void testSetAndGetAudioModeIsVoip() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertFalse(connection.getAudioModeIsVoip());
        connection.setAudioModeIsVoip(true);
        assertTrue(connection.getAudioModeIsVoip());
    }

    public void testSetAndGetExtras() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertEquals(null, connection.getExtras());

        final Bundle extras = new Bundle();
        extras.putBoolean("test-extra-key", true);
        connection.setExtras(extras);

        final Bundle retrieved = connection.getExtras();
        assertNotNull(retrieved);
        assertTrue(extras.getBoolean("test-extra-key"));
    }

    /**
     * Basic local test of adding extra keys via {@link Connection#removeExtras(List)}.
     *
     * Extended end-to-end passing of extras is verified in
     * {@link CallDetailsTest#testConnectionPutExtras()} and
     * @link CallDetailsTest#testConnectionRemoveExtras()}.
     */
    public void testPutExtras() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertEquals(null, connection.getExtras());

        final Bundle extras = new Bundle();
        extras.putBoolean("test-extra-key", true);
        connection.putExtras(extras);

        final Bundle retrieved = connection.getExtras();
        assertNotNull(retrieved);
        assertTrue(extras.getBoolean("test-extra-key"));
    }

    /**
     * Basic local test of removing extra keys via {@link Connection#removeExtras(List)}.
     *
     * Extended end-to-end passing of extras is verified in
     * {@link CallDetailsTest#testConnectionPutExtras()} and
     * @link CallDetailsTest#testConnectionRemoveExtras()}.
     */
    public void testRemoveExtras() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertEquals(null, connection.getExtras());

        final Bundle extras = new Bundle();
        extras.putBoolean("test-extra-key", true);
        connection.putExtras(extras);
        connection.removeExtras(Arrays.asList("test-extra-key"));

        final Bundle retrieved = connection.getExtras();
        assertNotNull(retrieved);
        assertFalse(retrieved.containsKey("test-extra-key"));
    }

    /**
     * Basic local test of removing extra keys via {@link Connection#removeExtras(String...)}.
     *
     * Extended end-to-end passing of extras is verified in
     * {@link CallDetailsTest#testConnectionPutExtras()} and
     * @link CallDetailsTest#testConnectionRemoveExtras()}.
     */
    public void testRemoveExtrasVariable() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertEquals(null, connection.getExtras());

        final Bundle extras = new Bundle();
        extras.putBoolean("test-extra-key", true);
        extras.putBoolean("test-extra-key2", true);
        connection.putExtras(extras);
        connection.removeExtras("test-extra-key", "test-extra-key2");

        final Bundle retrieved = connection.getExtras();
        assertNotNull(retrieved);
        assertFalse(retrieved.containsKey("test-extra-key"));
        assertFalse(retrieved.containsKey("test-extra-key2"));
    }

    /**
     * Tests that the {@link Connection#sendConnectionEvent(String, Bundle)} method exists and can
     * be called.
     *
     * Actual end-to-end tests can be found in {@link CallDetailsTest#testConnectionEvent()}.
     */
    public void testSendConnectionEvent() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        connection.sendConnectionEvent("test", null);
    }

    public void testSetAndGetStatusHints() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertEquals(null, connection.getStatusHints());

        final StatusHints statusHints = new StatusHints("Test", null, null);
        connection.setStatusHints(statusHints);
        assertEquals(statusHints, connection.getStatusHints());
    }

    public void testSetAndGetRingbackRequested() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertFalse(connection.isRingbackRequested());

        connection.setRingbackRequested(true);
        assertTrue(connection.isRingbackRequested());
    }

    public void testSetAndGetVideoProvider() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        final Semaphore lock = new Semaphore(0);
        Connection connection = createConnection(lock);
        waitForStateChange(lock);

        assertNull(connection.getVideoProvider());

        final Connection.VideoProvider videoProvider = new MockVideoProvider(null);
        connection.setVideoProvider(videoProvider);
        assertEquals(videoProvider, connection.getVideoProvider());
    }

    public void testStateToString() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        assertEquals("INITIALIZING", Connection.stateToString(Connection.STATE_INITIALIZING));
        assertEquals("NEW", Connection.stateToString(Connection.STATE_NEW));
        assertEquals("RINGING", Connection.stateToString(Connection.STATE_RINGING));
        assertEquals("DIALING", Connection.stateToString(Connection.STATE_DIALING));
        assertEquals("ACTIVE", Connection.stateToString(Connection.STATE_ACTIVE));
        assertEquals("HOLDING", Connection.stateToString(Connection.STATE_HOLDING));
        assertEquals("DISCONNECTED", Connection.stateToString(Connection.STATE_DISCONNECTED));
    }

    public void testCapabilitiesToString() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        assertEquals("[Capabilities: CAPABILITY_HOLD]",
                Connection.capabilitiesToString(Connection.CAPABILITY_HOLD));
        assertEquals("[Capabilities: CAPABILITY_MUTE]",
                Connection.capabilitiesToString(Connection.CAPABILITY_MUTE));
        assertEquals("[Capabilities: CAPABILITY_HOLD "
                + "CAPABILITY_RESPOND_VIA_TEXT "
                + "CAPABILITY_MANAGE_CONFERENCE]",
                Connection.capabilitiesToString(Connection.CAPABILITY_HOLD
                        | Connection.CAPABILITY_RESPOND_VIA_TEXT
                        | Connection.CAPABILITY_MANAGE_CONFERENCE));
    }

    /**
     * Tests the {@link Connection#propertiesToString(int)} method.
     */
    public void testPropertiesToString() {
        if (!shouldTestTelecom(getContext())) {
            return;
        }

        assertEquals("[Properties: PROPERTY_IS_EXTERNAL_CALL]",
                Connection.propertiesToString(Connection.PROPERTY_IS_EXTERNAL_CALL));
    }

    private static Connection createConnection(final Semaphore lock) {
        BasicConnection connection = new BasicConnection();
        connection.setLock(lock);
        return connection;
    }

    private static void waitForStateChange(Semaphore lock) {
        try {
            lock.tryAcquire(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("State transition timed out");
        }
    }

    private static final class BasicConnection extends Connection {
        private Semaphore mLock;

        public void setLock(Semaphore lock) {
            mLock = lock;
        }

        @Override
        public void onStateChanged(int state) {
            mLock.release();
        }
    }
}
