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
 * limitations under the License
 */

package android.telecom.cts;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import static android.telecom.cts.TestUtils.shouldTestTelecom;

/**
 * Suites of tests that use {@link MockVideoProvider} and {@link MockVideoCallCallback} to verify
 * the functionality of the video APIs.
 *
 * Note: You'll notice the use of {@code work}, and
 * {@code doWorkAndWaitUntilConditionIsTrueOrTimeout} here.  The problem is the
 * {@link MockVideoProvider} is running using a Handler.  To get it to emit mock data that is
 * in sync with the setup operations performed on the handler, we'd need access to its handler.
 * The handler of the {@link Connection.VideoProvider} is, however, not public.  As a workaround
 * we will call local methods on the MockVideoProvider.  This means there is a chance the
 * VideoProvider will emit the data we're interested in before the callbacks (on the handler)
 * are even set up.  Consequently, the callbacks we're depending on in our test may not get
 * called.  To compensate we will call the test methods on the provider repeatedly until we
 * hear back via our callback.  Suboptimal, but it works.
 */
public class VideoCallTest extends BaseTelecomTestWithMockServices {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    /**
     * Tests ability to start a 2-way video call and retrieve its video state.
     */
    public void testMakeTwoWayVideoCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        assertCallState(call, Call.STATE_DIALING);
        connection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);

        assertVideoState(call, VideoProfile.STATE_BIDIRECTIONAL);
        assertVideoCallbackRegistered(inCallService, call, true);
    }

    /**
     * Tests ability to start a 1-way video call and retrieve its video state.
     */
    public void testMakeOneWayVideoCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_TX_ENABLED);
        verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        assertVideoState(call, VideoProfile.STATE_TX_ENABLED);
        assertVideoCallbackRegistered(inCallService, call, true);
    }

    /**
     * Tests ability to upgrade an audio-only call to a video call.
     */
    public void testUpgradeToVideo() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_AUDIO_ONLY);
        verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoState(call, VideoProfile.STATE_AUDIO_ONLY);
        assertVideoCallbackRegistered(inCallService, call, true);

        // Send request to upgrade to video.
        InCallService.VideoCall videoCall = call.getVideoCall();
        videoCall.sendSessionModifyRequest(new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));
        assertVideoState(call, VideoProfile.STATE_BIDIRECTIONAL);
        assertResponseVideoProfileReceived(inCallService.getVideoCallCallback(call),
                VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * Tests ability to receive a session modification request.
     */
    public void testReceiveSessionModifyRequest() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_AUDIO_ONLY);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        assertVideoState(call, VideoProfile.STATE_AUDIO_ONLY);
        assertVideoCallbackRegistered(inCallService, call, true);

        // Have the video profile mock reception of a request.
        assertRequestVideoProfileReceived(inCallService.getVideoCallCallback(call),
                VideoProfile.STATE_BIDIRECTIONAL,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockSessionModifyRequest(
                                new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));
                    }
                });
    }

    /**
     * Tests ability to send a session modification response.
     */
    public void testSendSessionModifyResponse() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_AUDIO_ONLY);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        assertVideoState(call, VideoProfile.STATE_AUDIO_ONLY);
        assertVideoCallbackRegistered(inCallService, call, true);

        InCallService.VideoCall videoCall = call.getVideoCall();
        videoCall.sendSessionModifyResponse(new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));
        assertSessionModifyResponse(mockVideoProvider, VideoProfile.STATE_BIDIRECTIONAL);
    }

    /**
     * Test handling of session modify responses.
     */
    public void testReceiveSessionModifyResponse() {
        if (!mShouldTestTelecom) {
            return;
        }

        VideoProfile fromVideoProfile = new VideoProfile(VideoProfile.STATE_AUDIO_ONLY);
        VideoProfile toVideoProfile = new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL);

        placeAndVerifyCall(VideoProfile.STATE_AUDIO_ONLY);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        assertVideoCallbackRegistered(inCallService, call, true);

        final MockVideoCallCallback callback = inCallService.getVideoCallCallback(call);

        mockVideoProvider.sendMockSessionModifyResponse(
                VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS, fromVideoProfile,
                toVideoProfile);
        assertRequestReceived(callback, VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS,
                fromVideoProfile, toVideoProfile);

        mockVideoProvider.sendMockSessionModifyResponse(
                VideoProvider.SESSION_MODIFY_REQUEST_FAIL, fromVideoProfile,
                toVideoProfile);
        assertRequestReceived(callback, VideoProvider.SESSION_MODIFY_REQUEST_FAIL,
                fromVideoProfile, toVideoProfile);

        mockVideoProvider.sendMockSessionModifyResponse(
                VideoProvider.SESSION_MODIFY_REQUEST_INVALID, fromVideoProfile,
                toVideoProfile);
        assertRequestReceived(callback, VideoProvider.SESSION_MODIFY_REQUEST_INVALID,
                fromVideoProfile, toVideoProfile);

        mockVideoProvider.sendMockSessionModifyResponse(
                VideoProvider.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE, fromVideoProfile,
                toVideoProfile);
        assertRequestReceived(callback, VideoProvider.SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE,
                fromVideoProfile, toVideoProfile);

        mockVideoProvider.sendMockSessionModifyResponse(
                VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT, fromVideoProfile,
                toVideoProfile);
        assertRequestReceived(callback, VideoProvider.SESSION_MODIFY_REQUEST_TIMED_OUT,
                fromVideoProfile, toVideoProfile);
    }

    /**
     * Tests ability to start a video call, delaying the creation of the provider until after
     * the call has been initiated (rather than immediately when the call is created).  This more
     * closely mimics the lifespan of a {@code VideoProvider} instance as it is reasonable to
     * expect there will be some overhead associated with configuring the camera at the start of
     * the call.
     */
    public void testVideoCallDelayProvider() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Don't create video provider when call is created initially; we will do this later.
        try {
            connectionService.setCreateVideoProvider(false);
            placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
            final MockConnection connection = verifyConnectionForOutgoingCall();

            final MockInCallService inCallService = mInCallCallbacks.getService();
            final Call call = inCallService.getLastCall();

            assertVideoState(call, VideoProfile.STATE_BIDIRECTIONAL);
            // After initial connection creation there should not be a video provider or callbacks
            // registered.
            assertVideoCallbackRegistered(inCallService, call, false);

            // Trigger delayed creation of video provider and registration of callbacks and assert
            // that it happened.
            connection.createMockVideoProvider();
            assertVideoCallbackRegistered(inCallService, call, true);

            // Ensure video providers are created in the future.
        } finally {
            connectionService.setCreateVideoProvider(true);
        }
    }


    /**
     * Tests ability to change the current camera.  Ensures that the camera capabilities are sent
     * back in response.
     */
    public void testChangeCamera() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final InCallService.VideoCall videoCall = call.getVideoCall();

        videoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        assertCameraCapabilitiesReceived(inCallService.getVideoCallCallback(call),
                MockVideoProvider.CAMERA_FRONT_DIMENSIONS);

        videoCall.setCamera(MockVideoProvider.CAMERA_BACK);
        assertCameraCapabilitiesReceived(inCallService.getVideoCallCallback(call),
                MockVideoProvider.CAMERA_BACK_DIMENSIONS);
    }

    /**
     * Tests ability to request the camera capabilities from the video provider.
     */
    public void testRequestCameraCapabilities() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final InCallService.VideoCall videoCall = call.getVideoCall();

        // First, set the camera.
        videoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        // Retrieve the camera capabilities that are automatically send when the camera is set --
        // ensures the cached value is cleared first.
        inCallService.getVideoCallCallback(call).getCameraCapabilities();

        // Now, request capabilities.
        videoCall.requestCameraCapabilities();
        assertCameraCapabilitiesReceived(inCallService.getVideoCallCallback(call),
                MockVideoProvider.CAMERA_FRONT_DIMENSIONS);
    }

    /**
     * Tests ability to request data usage from the video provider.
     */
    public void testRequestDataUsage() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final InCallService.VideoCall videoCall = call.getVideoCall();

        videoCall.requestCallDataUsage();
        assertCallDataUsageReceived(inCallService.getVideoCallCallback(call),
                MockVideoProvider.DATA_USAGE);
    }

    /**
     * Tests ability to receive changes to the video quality from the video provider.
     */
    public void testReceiveVideoQuality() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);

        assertVideoQualityReceived(inCallService.getVideoCallCallback(call),
                VideoProfile.QUALITY_HIGH,
                new Work() {
                    @Override
                    public void doWork() {
                        connection
                                .sendMockVideoQuality(VideoProfile.QUALITY_HIGH);
                    }
                });

        assertVideoQualityReceived(inCallService.getVideoCallCallback(call),
                VideoProfile.QUALITY_MEDIUM,
                new Work() {
                    @Override
                    public void doWork() {
                        connection
                                .sendMockVideoQuality(VideoProfile.QUALITY_MEDIUM);
                    }
                });
    }

    /**
     * Tests ability to receive call session events from the video provider.
     */
    public void testReceiveCallSessionEvent() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_CAMERA_READY,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_CAMERA_READY);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_CAMERA_FAILURE);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_TX_START,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_TX_START);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_TX_STOP,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_TX_STOP);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_RX_PAUSE,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_RX_PAUSE);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                Connection.VideoProvider.SESSION_EVENT_RX_RESUME,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_RX_RESUME);
                    }
                });

        assertCallSessionEventReceived(inCallService.getVideoCallCallback(call),
                VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockCallSessionEvent(
                                Connection.VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR);
                    }
                });
    }

    /**
     * Tests ability to receive changes to the peer dimensions from the video provider.
     */
    public void testReceivePeerDimensionChange() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);

        assertPeerWidthChanged(inCallService.getVideoCallCallback(call),
                MockVideoProvider.CAMERA_BACK_DIMENSIONS,
                new Work() {
                    @Override
                    public void doWork() {
                        connection.sendMockPeerWidth(MockVideoProvider.CAMERA_BACK_DIMENSIONS);
                    }
                });
    }

    /**
     * Tests ability to set the device orientation via the provider.
     */
    public void testSetDeviceOrientation() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        final InCallService.VideoCall videoCall = call.getVideoCall();

        // Set device orientation and ensure provider knows about it.
        videoCall.setDeviceOrientation(90);
        assertDeviceOrientationChanged(mockVideoProvider, 90);
    }

    /**
     * Tests ability to set the preview surface via the provider.
     */
    public void testSetPreviewSurface() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        final InCallService.VideoCall videoCall = call.getVideoCall();

        Surface surface = new Surface(new SurfaceTexture(1));
        // Set a surface
        videoCall.setPreviewSurface(surface);
        assertPreviewSurfaceChanged(mockVideoProvider, true);

        // Clear the surface
        videoCall.setPreviewSurface(null);
        assertPreviewSurfaceChanged(mockVideoProvider, false);
    }

    /**
     * Tests ability to set the display surface via the provider.
     */
    public void testSetDisplaySurface() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        final InCallService.VideoCall videoCall = call.getVideoCall();

        // Set a surface
        Surface surface = new Surface(new SurfaceTexture(1));
        videoCall.setDisplaySurface(surface);
        assertDisplaySurfaceChanged(mockVideoProvider, true);

        // Clear the surface
        videoCall.setDisplaySurface(null);
        assertDisplaySurfaceChanged(mockVideoProvider, false);
    }

    /**
     * Tests ability to set the camera zoom via the provider.
     */
    public void testSetZoom() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        final InCallService.VideoCall videoCall = call.getVideoCall();

        videoCall.setZoom(0.0f);
        assertZoomChanged(mockVideoProvider, 0.0f);

        videoCall.setZoom(10.0f);
        assertZoomChanged(mockVideoProvider, 10.0f);

        call.disconnect();
    }

    public void testSetPauseImage() {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall(VideoProfile.STATE_BIDIRECTIONAL);
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        assertVideoCallbackRegistered(inCallService, call, true);
        final MockVideoProvider mockVideoProvider = connection.getMockVideoProvider();
        final InCallService.VideoCall videoCall = call.getVideoCall();

        final Uri pauseImageUri = Uri.fromParts("file", "test.png", "");
        videoCall.setPauseImage(pauseImageUri);
        assertPauseUriChanged(mockVideoProvider, pauseImageUri);
    }

    /**
     * Asserts that a call video state is as expected.
     *
     * @param call The call.
     * @param videoState The expected video state.
     */
    private void assertVideoState(final Call call, final int videoState) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return videoState;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getVideoState();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should be in videoState " + videoState
        );
    }

    /**
     * Asserts whether the InCallService has registered a video call back (and hence a video call)
     * for a call.
     *
     * @param inCallService The incall service.
     * @param call The call.
     * @param isRegistered The expected registration state.
     */
    private void assertVideoCallbackRegistered(final MockInCallService inCallService,
            final Call call, final Boolean isRegistered) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isRegistered;
                    }

                    @Override
                    public Object actual() {
                        return inCallService.isVideoCallbackRegistered(call);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Video callback registration state should be " + isRegistered
        );
    }

    /**
     * Asserts whether the camera capabilities have changed to an expected value.  Compares the
     * camera height only (the {@link MockVideoProvider} sets height and width to be the same.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedCameraWidthHeight The expected width and height.
     */
    private void assertCameraCapabilitiesReceived(final MockVideoCallCallback videoCallCallback,
            final int expectedCameraWidthHeight) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedCameraWidthHeight;
                    }

                    @Override
                    public Object actual() {
                        VideoProfile.CameraCapabilities cameraCapabilities =
                                videoCallCallback.getCameraCapabilities();
                        return cameraCapabilities == null ? 0 : cameraCapabilities.getHeight();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Camera width and height should be " + expectedCameraWidthHeight
        );
    }

    /**
     * Asserts whether the call data usage has changed to the expected value.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedDataUsage The expected data usage.
     */
    private void assertCallDataUsageReceived(final MockVideoCallCallback videoCallCallback,
            final long expectedDataUsage) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedDataUsage;
                    }

                    @Override
                    public Object actual() {
                        return videoCallCallback.getDataUsage();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Data usage should be " + expectedDataUsage
        );
    }

    /**
     * Asserts whether the video quality has changed to the expected value.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedVideoQuality The expected video quality.
     * @param work The work to perform to have the provider emit the video quality.
     */
    private void assertVideoQualityReceived(final MockVideoCallCallback videoCallCallback,
            final int expectedVideoQuality, final Work work) {
        doWorkAndWaitUntilConditionIsTrueOrTimeout(
                work,
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedVideoQuality;
                    }

                    @Override
                    public Object actual() {
                        return videoCallCallback.getVideoQuality();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Video quality should be " + expectedVideoQuality
        );
    }

    /**
     * Asserts whether the call session event has changed to the expected value.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedEvent The expected event.
     * @param work The work to be performed to send the call session event from the provider.
     */
    private void assertCallSessionEventReceived(final MockVideoCallCallback videoCallCallback,
            final int expectedEvent, final Work work) {
        doWorkAndWaitUntilConditionIsTrueOrTimeout(
                work,
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedEvent;
                    }

                    @Override
                    public Object actual() {
                        return videoCallCallback.getCallSessionEvent();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call session event should be " + expectedEvent
        );
    }

    /**
     * Asserts whether the peer width has changed to the expected value.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedWidth The expected width.
     * @param work The work to be performed to send the peer width from the provider.
     */
    private void assertPeerWidthChanged(final MockVideoCallCallback videoCallCallback,
            final int expectedWidth, final Work work) {
        doWorkAndWaitUntilConditionIsTrueOrTimeout(
                work,
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedWidth;
                    }

                    @Override
                    public Object actual() {
                        return videoCallCallback.getPeerWidth();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Peer width should be " + expectedWidth
        );
    }

    /**
     * Asserts whether the device orientation has changed to the expected value.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected The expected device orientation.
     */
    private void assertDeviceOrientationChanged(final MockVideoProvider mockVideoProvider,
            final int expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return mockVideoProvider.getDeviceOrientation();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Orientation should be " + expected
        );
    }

    /**
     * Asserts whether the preview surface has been set or not.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected {@code true} if it is expected the preview surface is not null, {@code false}
     *                             if it is expected the preview surface is null.
     */
    private void assertPreviewSurfaceChanged(final MockVideoProvider mockVideoProvider,
            final boolean expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return mockVideoProvider.getPreviewSurface() != null;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Preview should be set? " + expected
        );
    }

    /**
     * Asserts whether the display surface has been set or not.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected {@code true} if it is expected the display surface is not null, {@code false}
     *                             if it is expected the display surface is null.
     */
    private void assertDisplaySurfaceChanged(final MockVideoProvider mockVideoProvider,
            final boolean expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return mockVideoProvider.getDisplaySurface() != null;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Display should be set? " + expected
        );
    }

    /**
     * Asserts whether the zoom has changed to the expected value.  Note: To make comparisons easier
     * the floats are cast to ints, so ensure only whole values are used.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected The expected zoom.
     */
    private void assertZoomChanged(final MockVideoProvider mockVideoProvider,
            final float expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        // Cast to int so we're not doing float equality
                        return (int)expected;
                    }

                    @Override
                    public Object actual() {
                        // Cast to int so we're not doing float equality
                        return (int)mockVideoProvider.getZoom();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Zoom should be " + expected
        );
    }

    /**
     * Asserts whether the pause image URI has changed to the expected value.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected The expected URI.
     */
    private void assertPauseUriChanged(final MockVideoProvider mockVideoProvider,
            final Uri expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return mockVideoProvider.getPauseImageUri();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Pause image URI should be " + expected
        );
    }

    /**
     * Asserts whether a response video profile has been received
     *
     * @param videoCallCallback The video call callback.
     * @param expected The expected video state.
     */
    private void assertResponseVideoProfileReceived(final MockVideoCallCallback videoCallCallback,
            final int expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        VideoProfile videoProfile = videoCallCallback.getResponseProfile();
                        return videoProfile == null ? -1 : videoProfile.getVideoState();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Video state should be " + expected
        );
    }

    /**
     * Asserts whether a session modification request has been received.
     *
     * @param videoCallCallback The video call callback.
     * @param expected The expected video state.
     * @param work The work to be performed to cause the session modification request to be emit
     *             from the provider.
     */
    private void assertRequestVideoProfileReceived(final MockVideoCallCallback videoCallCallback,
            final int expected, final Work work) {
        doWorkAndWaitUntilConditionIsTrueOrTimeout(
                work,
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        VideoProfile videoProfile = videoCallCallback.getRequestProfile();
                        return videoProfile == null ? -1 : videoProfile.getVideoState();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Video state should be " + expected
        );
    }

    /**
     * Asserts whether the provider got a session modify response with the expected value.
     *
     * @param mockVideoProvider The mock video provider.
     * @param expected The expected video state of the session modify response.
     */
    private void assertSessionModifyResponse(final MockVideoProvider mockVideoProvider,
            final int expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        VideoProfile responseProfile = mockVideoProvider.getSessionModifyResponse();
                        return responseProfile == null ? -1 : responseProfile.getVideoState();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Session modify response video state should be " + expected
        );
    }

    /**
     * Asserts whether a session modify response has been received with the expected values.
     *
     * @param videoCallCallback The video call callback.
     * @param expectedResponseStatus The expected status.
     * @param expectedFromProfile The expected from profile.
     * @param expectedToProfile The expected to profile.
     */
    private void assertRequestReceived(final MockVideoCallCallback videoCallCallback,
            final int expectedResponseStatus, final VideoProfile expectedFromProfile,
            final VideoProfile expectedToProfile) {

        final String expected = buildRequestString(expectedResponseStatus, expectedFromProfile,
                expectedToProfile);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        int responseStatus = videoCallCallback.getResponseStatus();
                        VideoProfile fromProfile = videoCallCallback.getRequestedProfile();
                        VideoProfile toProfile = videoCallCallback.getResponseProfile();
                        return buildRequestString(responseStatus, fromProfile, toProfile);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Session modify response should match expected."
        );
    }

    /**
     * Creates a string representation of the parameters passed to
     * {@link android.telecom.InCallService.VideoCall.Callback#onSessionModifyResponseReceived(int,
     * VideoProfile, VideoProfile)}.
     *
     * @param status The status.
     * @param fromProfile The from profile.
     * @param toProfile The to profile.
     * @return String representation.
     */
    private String buildRequestString(int status, VideoProfile fromProfile, VideoProfile toProfile) {
        StringBuilder expectedSb = new StringBuilder();
        expectedSb.append("Status: ");
        expectedSb.append(status);
        expectedSb.append(" From: ");
        expectedSb.append(fromProfile);
        expectedSb.append(" To: ");
        expectedSb.append(toProfile);
        return expectedSb.toString();
    }
}
