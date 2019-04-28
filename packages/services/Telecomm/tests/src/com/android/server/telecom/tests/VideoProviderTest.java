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

package com.android.server.telecom.tests;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.exceptions.ExceptionIncludingMockitoWarnings;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import android.app.AppOpsManager;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService;
import android.telecom.InCallService.VideoCall;
import android.telecom.VideoCallImpl;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Surface;

import com.google.common.base.Predicate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static android.test.MoreAsserts.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Performs tests of the {@link VideoProvider} and {@link VideoCall} APIs.  Ensures that requests
 * sent from an InCallService are routed through Telecom to a VideoProvider, and that callbacks are
 * correctly routed.
 */
public class VideoProviderTest extends TelecomSystemTest {
    private static final int ORIENTATION_0 = 0;
    private static final int ORIENTATION_90 = 90;
    private static final float ZOOM_LEVEL = 3.0f;

    @Mock private VideoCall.Callback mVideoCallCallback;
    private IdPair mCallIds;
    private InCallService.VideoCall mVideoCall;
    private VideoCallImpl mVideoCallImpl;
    private ConnectionServiceFixture.ConnectionInfo mConnectionInfo;
    private CountDownLatch mVerificationLock;
    private AppOpsManager mAppOpsManager;

    private Answer mVerification = new Answer() {
        @Override
        public Object answer(InvocationOnMock i) {
            mVerificationLock.countDown();
            return null;
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);

        mCallIds = startAndMakeActiveOutgoingCall(
                "650-555-1212",
                mPhoneAccountA0.getAccountHandle(),
                mConnectionServiceFixtureA);

        // Set the video provider on the connection.
        mConnectionServiceFixtureA.sendSetVideoProvider(
                mConnectionServiceFixtureA.mLatestConnectionId);

        // Provide a mocked VideoCall.Callback to receive callbacks via.
        mVideoCallCallback = mock(InCallService.VideoCall.Callback.class);

        mVideoCall = mInCallServiceFixtureX.getCall(mCallIds.mCallId).getVideoCallImpl(
                mInCallServiceComponentNameX.getPackageName(), Build.VERSION.SDK_INT);
        mVideoCallImpl = (VideoCallImpl) mVideoCall;
        mVideoCall.registerCallback(mVideoCallCallback);

        mConnectionInfo = mConnectionServiceFixtureA.mConnectionById.get(mCallIds.mConnectionId);
        mVerificationLock = new CountDownLatch(1);
        waitForHandlerAction(new Handler(Looper.getMainLooper()), TEST_TIMEOUT);

        doNothing().when(mContext).enforcePermission(anyString(), anyInt(), anyInt(), anyString());
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).noteOp(anyInt(), anyInt(),
                anyString());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests the {@link VideoCall#setCamera(String)}, {@link VideoProvider#onSetCamera(String)},
     * and {@link VideoCall.Callback#onCameraCapabilitiesChanged(CameraCapabilities)}
     * APIS.
     */
    @MediumTest
    public void testCameraChange() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onCameraCapabilitiesChanged(any(CameraCapabilities.class));

        // Make 2 setCamera requests.
        mVideoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        mVideoCall.setCamera(MockVideoProvider.CAMERA_BACK);

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the video profile reported via the callback.
        ArgumentCaptor<CameraCapabilities> cameraCapabilitiesCaptor =
                ArgumentCaptor.forClass(CameraCapabilities.class);

        // Verify that the callback was called twice and capture the callback arguments.
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT).times(2))
                .onCameraCapabilitiesChanged(cameraCapabilitiesCaptor.capture());

        assertEquals(2, cameraCapabilitiesCaptor.getAllValues().size());

        List<CameraCapabilities> cameraCapabilities = cameraCapabilitiesCaptor.getAllValues();
        // Ensure dimensions are as expected.
        assertEquals(MockVideoProvider.CAMERA_FRONT_DIMENSIONS,
                cameraCapabilities.get(0).getHeight());
        assertEquals(MockVideoProvider.CAMERA_BACK_DIMENSIONS,
                cameraCapabilities.get(1).getHeight());
    }

    /**
     * Tests the caller permission check in {@link VideoCall#setCamera(String)} to ensure a camera
     * change from a non-permitted caller is ignored.
     */
    @MediumTest
    public void testCameraChangePermissionFail() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback).onCallSessionEvent(anyInt());

        // ensure permission check fails.
        doThrow(new SecurityException()).when(mContext)
                .enforcePermission(anyString(), anyInt(), anyInt(), anyString());

        // Set the target SDK version to to > N-MR1.
        mVideoCallImpl.setTargetSdkVersion(Build.VERSION_CODES.CUR_DEVELOPMENT);
        // Make a request to change the camera
        mVideoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the session event reported via the callback.
        ArgumentCaptor<Integer> sessionEventCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT)).onCallSessionEvent(
                sessionEventCaptor.capture());

        assertEquals(VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR,
                sessionEventCaptor.getValue().intValue());
    }

    /**
     * Tests the caller app ops check in {@link VideoCall#setCamera(String)} to ensure a camera
     * change from a non-permitted caller is ignored.
     */
    @MediumTest
    public void testCameraChangeAppOpsFail() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback).onCallSessionEvent(anyInt());

        // ensure app ops check fails.
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOpsManager).noteOp(anyInt(), anyInt(),
                anyString());

        // Set the target SDK version to > N-MR1.
        mVideoCallImpl.setTargetSdkVersion(Build.VERSION_CODES.CUR_DEVELOPMENT);
        // Make a request to change the camera
        mVideoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the session event reported via the callback.
        ArgumentCaptor<Integer> sessionEventCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT)).onCallSessionEvent(
                sessionEventCaptor.capture());

        assertEquals(VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR,
                sessionEventCaptor.getValue().intValue());
    }

    /**
     * Tests the caller app ops check in {@link VideoCall#setCamera(String)} to ensure a camera
     * change from a non-permitted caller is ignored. For < N-MR1, throw a CAMERA_FAILURE instead
     * of a CAMERA_PERMISSION_ERROR.
     */
    @MediumTest
    public void testCameraChangeAppOpsBelowNMR1Fail() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback).onCallSessionEvent(anyInt());

        // ensure app ops check fails.
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOpsManager).noteOp(anyInt(), anyInt(),
                anyString());

        // Set the target SDK version to below N-MR1
        mVideoCallImpl.setTargetSdkVersion(Build.VERSION_CODES.N);

        // Make a request to change the camera
        mVideoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the session event reported via the callback.
        ArgumentCaptor<Integer> sessionEventCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT)).onCallSessionEvent(
                sessionEventCaptor.capture());

        assertEquals(VideoProvider.SESSION_EVENT_CAMERA_FAILURE,
                sessionEventCaptor.getValue().intValue());
    }

    /**
     * Tests the caller user handle check in {@link VideoCall#setCamera(String)} to ensure a camera
     * change from a background user is not permitted.
     */
    @MediumTest
    public void testCameraChangeUserFail() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback).onCallSessionEvent(anyInt());

        // Set a fake user to be the current foreground user.
        mTelecomSystem.getCallsManager().onUserSwitch(new UserHandle(1000));

        // Set the target SDK version to > N-MR1
        mVideoCallImpl.setTargetSdkVersion(Build.VERSION_CODES.CUR_DEVELOPMENT);
        // Make a request to change the camera
        mVideoCall.setCamera(MockVideoProvider.CAMERA_FRONT);
        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the session event reported via the callback.
        ArgumentCaptor<Integer> sessionEventCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT)).onCallSessionEvent(
                sessionEventCaptor.capture());

        assertEquals(VideoProvider.SESSION_EVENT_CAMERA_PERMISSION_ERROR,
                sessionEventCaptor.getValue().intValue());
    }

    /**
     * Tests the caller permission check in {@link VideoCall#setCamera(String)} to ensure the
     * caller can null out the camera, even if they do not have camera permission.
     */
    @MediumTest
    public void testCameraChangeNullNoPermission() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback).onCallSessionEvent(anyInt());

        // ensure permission check fails.
        doThrow(new SecurityException()).when(mContext)
                .enforcePermission(anyString(), anyInt(), anyInt(), anyString());

        // Make a request to null the camera; we expect the permission check won't happen.
        mVideoCall.setCamera(null);
        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the session event reported via the callback.
        ArgumentCaptor<Integer> sessionEventCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT)).onCallSessionEvent(
                sessionEventCaptor.capture());

        // See the MockVideoProvider class; for convenience when the camera is nulled we just send
        // back a "camera ready" event.
        assertEquals(VideoProvider.SESSION_EVENT_CAMERA_READY,
                sessionEventCaptor.getValue().intValue());
    }

    /**
     * Tests the {@link VideoCall#setPreviewSurface(Surface)} and
     * {@link VideoProvider#onSetPreviewSurface(Surface)} APIs.
     */
    @MediumTest
    public void testSetPreviewSurface() throws Exception {
        final Surface surface = new Surface(new SurfaceTexture(1));
        mVideoCall.setPreviewSurface(surface);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getPreviewSurface() == surface;
            }
        });

        mVideoCall.setPreviewSurface(null);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getPreviewSurface() == null;
            }
        });
    }

    /**
     * Tests the {@link VideoCall#setDisplaySurface(Surface)} and
     * {@link VideoProvider#onSetDisplaySurface(Surface)} APIs.
     */
    @MediumTest
    public void testSetDisplaySurface() throws Exception {
        final Surface surface = new Surface(new SurfaceTexture(1));
        mVideoCall.setDisplaySurface(surface);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getDisplaySurface() == surface;
            }
        });

        mVideoCall.setDisplaySurface(null);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getDisplaySurface() == null;
            }
        });
    }

    /**
     * Tests the {@link VideoCall#setDeviceOrientation(int)} and
     * {@link VideoProvider#onSetDeviceOrientation(int)} APIs.
     */
    @MediumTest
    public void testSetDeviceOrientation() throws Exception {
        mVideoCall.setDeviceOrientation(ORIENTATION_0);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getDeviceOrientation() == ORIENTATION_0;
            }
        });

        mVideoCall.setDeviceOrientation(ORIENTATION_90);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getDeviceOrientation() == ORIENTATION_90;
            }
        });
    }

    /**
     * Tests the {@link VideoCall#setZoom(float)} and {@link VideoProvider#onSetZoom(float)} APIs.
     */
    @MediumTest
    public void testSetZoom() throws Exception {
        mVideoCall.setZoom(ZOOM_LEVEL);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mConnectionInfo.mockVideoProvider.getZoom() == ZOOM_LEVEL;
            }
        });
    }

    /**
     * Tests the {@link VideoCall#sendSessionModifyRequest(VideoProfile)},
     * {@link VideoProvider#onSendSessionModifyRequest(VideoProfile, VideoProfile)},
     * {@link VideoProvider#receiveSessionModifyResponse(int, VideoProfile, VideoProfile)}, and
     * {@link VideoCall.Callback#onSessionModifyResponseReceived(int, VideoProfile, VideoProfile)}
     * APIs.
     *
     * Emulates a scenario where an InCallService sends a request to upgrade to video, which the
     * peer accepts as-is.
     */
    @MediumTest
    public void testSessionModifyRequest() throws Exception {
        VideoProfile requestProfile = new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL);

        // Set the starting video state on the video call impl; normally this would be set based on
        // the original android.telecom.Call instance.
        mVideoCallImpl.setVideoState(VideoProfile.STATE_RX_ENABLED);

        doAnswer(mVerification).when(mVideoCallCallback)
                .onSessionModifyResponseReceived(anyInt(), any(VideoProfile.class),
                        any(VideoProfile.class));

        // Send the request.
        mVideoCall.sendSessionModifyRequest(requestProfile);

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        // Capture the video profiles from the callback.
        ArgumentCaptor<VideoProfile> fromVideoProfileCaptor =
                ArgumentCaptor.forClass(VideoProfile.class);
        ArgumentCaptor<VideoProfile> toVideoProfileCaptor =
                ArgumentCaptor.forClass(VideoProfile.class);

        // Verify we got a response and capture the profiles.
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onSessionModifyResponseReceived(eq(VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS),
                        fromVideoProfileCaptor.capture(), toVideoProfileCaptor.capture());

        assertEquals(VideoProfile.STATE_RX_ENABLED,
                fromVideoProfileCaptor.getValue().getVideoState());
        assertEquals(VideoProfile.STATE_BIDIRECTIONAL,
                toVideoProfileCaptor.getValue().getVideoState());
    }

    /**
     * Tests the {@link VideoCall#sendSessionModifyResponse(VideoProfile)},
     * and {@link VideoProvider#onSendSessionModifyResponse(VideoProfile)} APIs.
     */
    @MediumTest
    public void testSessionModifyResponse() throws Exception {
        VideoProfile sessionModifyResponse = new VideoProfile(VideoProfile.STATE_TX_ENABLED);

        mVideoCall.sendSessionModifyResponse(sessionModifyResponse);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                VideoProfile response = mConnectionInfo.mockVideoProvider
                        .getSessionModifyResponse();
                return response != null && response.getVideoState() == VideoProfile.STATE_TX_ENABLED;
            }
        });
    }

    /**
     * Tests the {@link VideoCall#requestCameraCapabilities()} ()},
     * {@link VideoProvider#onRequestCameraCapabilities()} ()}, and
     * {@link VideoCall.Callback#onCameraCapabilitiesChanged(CameraCapabilities)} APIs.
     */
    @MediumTest
    public void testRequestCameraCapabilities() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onCameraCapabilitiesChanged(any(CameraCapabilities.class));

        mVideoCall.requestCameraCapabilities();

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onCameraCapabilitiesChanged(any(CameraCapabilities.class));
    }

    /**
     * Tests the {@link VideoCall#setPauseImage(Uri)}, and
     * {@link VideoProvider#onSetPauseImage(Uri)} APIs.
     */
    @MediumTest
    public void testSetPauseImage() throws Exception {
        final Uri testUri = Uri.fromParts("file", "test.jpg", null);
        mVideoCall.setPauseImage(testUri);

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                Uri pauseImage = mConnectionInfo.mockVideoProvider.getPauseImage();
                return pauseImage != null && pauseImage.equals(testUri);
            }
        });
    }

    /**
     * Tests the {@link VideoCall#requestCallDataUsage()},
     * {@link VideoProvider#onRequestConnectionDataUsage()}, and
     * {@link VideoCall.Callback#onCallDataUsageChanged(long)} APIs.
     */
    @MediumTest
    public void testRequestDataUsage() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onCallDataUsageChanged(anyLong());

        mVideoCall.requestCallDataUsage();

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onCallDataUsageChanged(eq(MockVideoProvider.DATA_USAGE));
    }

    /**
     * Tests the {@link VideoProvider#receiveSessionModifyRequest(VideoProfile)},
     * {@link VideoCall.Callback#onSessionModifyRequestReceived(VideoProfile)} APIs.
     */
    @MediumTest
    public void testReceiveSessionModifyRequest() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onSessionModifyRequestReceived(any(VideoProfile.class));

        mConnectionInfo.mockVideoProvider.sendMockSessionModifyRequest();

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        ArgumentCaptor<VideoProfile> requestProfileCaptor =
                ArgumentCaptor.forClass(VideoProfile.class);
        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onSessionModifyRequestReceived(requestProfileCaptor.capture());
        assertEquals(VideoProfile.STATE_BIDIRECTIONAL,
                requestProfileCaptor.getValue().getVideoState());
    }


    /**
     * Tests the {@link VideoProvider#handleCallSessionEvent(int)}, and
     * {@link VideoCall.Callback#onCallSessionEvent(int)} APIs.
     */
    @MediumTest
    public void testSessionEvent() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onCallSessionEvent(anyInt());

        mConnectionInfo.mockVideoProvider.sendMockSessionEvent(
                VideoProvider.SESSION_EVENT_CAMERA_READY);

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onCallSessionEvent(eq(VideoProvider.SESSION_EVENT_CAMERA_READY));
    }

    /**
     * Tests the {@link VideoProvider#changePeerDimensions(int, int)} and
     * {@link VideoCall.Callback#onPeerDimensionsChanged(int, int)} APIs.
     */
    @MediumTest
    public void testPeerDimensionChange() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onPeerDimensionsChanged(anyInt(), anyInt());

        mConnectionInfo.mockVideoProvider.sendMockPeerDimensions(MockVideoProvider.PEER_DIMENSIONS,
                MockVideoProvider.PEER_DIMENSIONS);

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onPeerDimensionsChanged(eq(MockVideoProvider.PEER_DIMENSIONS),
                        eq(MockVideoProvider.PEER_DIMENSIONS));
    }

    /**
     * Tests the {@link VideoProvider#changeVideoQuality(int)} and
     * {@link VideoCall.Callback#onVideoQualityChanged(int)} APIs.
     */
    @MediumTest
    public void testVideoQualityChange() throws Exception {
        // Wait until the callback has been received before performing verification.
        doAnswer(mVerification).when(mVideoCallCallback)
                .onVideoQualityChanged(anyInt());

        mConnectionInfo.mockVideoProvider.sendMockVideoQuality(VideoProfile.QUALITY_HIGH);

        mVerificationLock.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);

        verify(mVideoCallCallback, timeout(TEST_TIMEOUT))
                .onVideoQualityChanged(eq(VideoProfile.QUALITY_HIGH));
    }
}
