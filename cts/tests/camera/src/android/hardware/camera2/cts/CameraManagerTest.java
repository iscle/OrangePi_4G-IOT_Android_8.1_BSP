/*
 * Copyright 2013 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.AdditionalMatchers.and;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.cts.CameraTestUtils.MockStateCallback;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.ex.camera2.blocking.BlockingStateCallback;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * <p>Basic test for CameraManager class.</p>
 */
public class CameraManagerTest extends AndroidTestCase {
    private static final String TAG = "CameraManagerTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int NUM_CAMERA_REOPENS = 10;

    private PackageManager mPackageManager;
    private CameraManager mCameraManager;
    private NoopCameraListener mListener;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private BlockingStateCallback mCameraListener;
    private CameraErrorCollector mCollector;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager", mCameraManager);
        mPackageManager = context.getPackageManager();
        assertNotNull("Can't get package manager", mPackageManager);
        mListener = new NoopCameraListener();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        mCameraListener = spy(new BlockingStateCallback());

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCollector = new CameraErrorCollector();
    }

    @Override
    protected void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;

        try {
            mCollector.verify();
        } catch (Throwable e) {
            // When new Exception(e) is used, exception info will be printed twice.
            throw new Exception(e.getMessage());
        } finally {
            super.tearDown();
        }
    }

    /**
     * Verifies that the reason is in the range of public-only codes.
     */
    private static int checkCameraAccessExceptionReason(CameraAccessException e) {
        int reason = e.getReason();

        switch (reason) {
            case CameraAccessException.CAMERA_DISABLED:
            case CameraAccessException.CAMERA_DISCONNECTED:
            case CameraAccessException.CAMERA_ERROR:
                return reason;
        }

        fail("Invalid CameraAccessException code: " + reason);

        return -1; // unreachable
    }

    public void testCameraManagerGetDeviceIdList() throws Exception {

        // Test: that the getCameraIdList method runs without exceptions.
        String[] ids = mCameraManager.getCameraIdList();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(ids));

        /**
         * Test: that if there is at least one reported id, then the system must have
         * the FEATURE_CAMERA_ANY feature.
         */
        assertTrue("System camera feature and camera id list don't match",
                ids.length == 0 ||
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));

        /**
         * Test: that if the device has front or rear facing cameras, then there
         * must be matched system features.
         */
        for (int i = 0; i < ids.length; i++) {
            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(ids[i]);
            assertNotNull("Can't get camera characteristics for camera " + ids[i], props);
            Integer lensFacing = props.get(CameraCharacteristics.LENS_FACING);
            assertNotNull("Can't get lens facing info", lensFacing);
            if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                assertTrue("System doesn't have front camera feature",
                        mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
            } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                assertTrue("System doesn't have back camera feature",
                        mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA));
            } else if (lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                assertTrue("System doesn't have external camera feature",
                        mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL));
            } else {
                fail("Unknown camera lens facing " + lensFacing.toString());
            }
        }

        /**
         * Test: that if there is one camera device, then the system must have some
         * specific features.
         */
        assertTrue("Missing system feature: FEATURE_CAMERA_ANY",
               ids.length == 0
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        assertTrue("Missing system feature: FEATURE_CAMERA, FEATURE_CAMERA_FRONT or FEATURE_CAMERA_EXTERNAL",
               ids.length == 0
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
            || mPackageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL));
    }

    // Test: that properties can be queried from each device, without exceptions.
    public void testCameraManagerGetCameraCharacteristics() throws Exception {
        String[] ids = mCameraManager.getCameraIdList();
        for (int i = 0; i < ids.length; i++) {
            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(ids[i]);
            assertNotNull(
                    String.format("Can't get camera characteristics from: ID %s", ids[i]), props);
        }
    }

    // Test: that an exception is thrown if an invalid device id is passed down.
    public void testCameraManagerInvalidDevice() throws Exception {
        String[] ids = mCameraManager.getCameraIdList();
        // Create an invalid id by concatenating all the valid ids together.
        StringBuilder invalidId = new StringBuilder();
        invalidId.append("INVALID");
        for (int i = 0; i < ids.length; i++) {
            invalidId.append(ids[i]);
        }

        try {
            mCameraManager.getCameraCharacteristics(
                invalidId.toString());
            fail(String.format("Accepted invalid camera ID: %s", invalidId.toString()));
        } catch (IllegalArgumentException e) {
            // This is the exception that should be thrown in this case.
        }
    }

    // Test: that each camera device can be opened one at a time, several times.
    public void testCameraManagerOpenCamerasSerially() throws Exception {
        String[] ids = mCameraManager.getCameraIdList();
        for (int i = 0; i < ids.length; i++) {
            for (int j = 0; j < NUM_CAMERA_REOPENS; j++) {
                CameraDevice camera = null;
                try {
                    MockStateCallback mockListener = MockStateCallback.mock();
                    mCameraListener = new BlockingStateCallback(mockListener);

                    mCameraManager.openCamera(ids[i], mCameraListener, mHandler);

                    // Block until unConfigured
                    mCameraListener.waitForState(BlockingStateCallback.STATE_OPENED,
                            CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);

                    // Ensure state transitions are in right order:
                    // -- 1) Opened
                    // Ensure no other state transitions have occurred:
                    camera = verifyCameraStateOpened(ids[i], mockListener);
                } finally {
                    if (camera != null) {
                        camera.close();
                    }
                }
            }
        }
    }

    /**
     * Test: one or more camera devices can be open at the same time, or the right error state
     * is set if this can't be done.
     */
    public void testCameraManagerOpenAllCameras() throws Exception {
        String[] ids = mCameraManager.getCameraIdList();
        assertNotNull("Camera ids shouldn't be null", ids);

        // Skip test if the device doesn't have multiple cameras.
        if (ids.length <= 1) {
            return;
        }

        List<CameraDevice> cameraList = new ArrayList<CameraDevice>();
        List<MockStateCallback> listenerList = new ArrayList<MockStateCallback>();
        List<BlockingStateCallback> blockingListenerList = new ArrayList<BlockingStateCallback>();
        try {
            for (int i = 0; i < ids.length; i++) {
                // Ignore state changes from other cameras
                MockStateCallback mockListener = MockStateCallback.mock();
                mCameraListener = new BlockingStateCallback(mockListener);

                /**
                 * Track whether or not we got a synchronous error from openCamera.
                 *
                 * A synchronous error must also be accompanied by an asynchronous
                 * StateCallback#onError callback.
                 */
                boolean expectingError = false;

                String cameraId = ids[i];
                try {
                    mCameraManager.openCamera(cameraId, mCameraListener,
                            mHandler);
                } catch (CameraAccessException e) {
                    if (checkCameraAccessExceptionReason(e) == CameraAccessException.CAMERA_ERROR) {
                        expectingError = true;
                    } else {
                        // TODO: We should handle a Disabled camera by passing here and elsewhere
                        fail("Camera must not be disconnected or disabled for this test" + ids[i]);
                    }
                }

                List<Integer> expectedStates = new ArrayList<Integer>();
                expectedStates.add(BlockingStateCallback.STATE_OPENED);
                expectedStates.add(BlockingStateCallback.STATE_ERROR);
                int state = mCameraListener.waitForAnyOfStates(
                        expectedStates, CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);

                // It's possible that we got an asynchronous error transition only. This is ok.
                if (expectingError) {
                    assertEquals("Throwing a CAMERA_ERROR exception must be accompanied with a " +
                            "StateCallback#onError callback",
                            BlockingStateCallback.STATE_ERROR, state);
                }

                /**
                 * Two situations are considered passing:
                 * 1) The camera opened successfully.
                 *     => No error must be set.
                 * 2) The camera did not open because there were too many other cameras opened.
                 *     => Only MAX_CAMERAS_IN_USE error must be set.
                 *
                 * Any other situation is considered a failure.
                 *
                 * For simplicity we treat disconnecting asynchronously as a failure, so
                 * camera devices should not be physically unplugged during this test.
                 */

                CameraDevice camera;
                if (state == BlockingStateCallback.STATE_ERROR) {
                    // Camera did not open because too many other cameras were opened
                    // => onError called exactly once with a non-null camera
                    assertTrue("At least one camera must be opened successfully",
                            cameraList.size() > 0);

                    ArgumentCaptor<CameraDevice> argument =
                            ArgumentCaptor.forClass(CameraDevice.class);

                    verify(mockListener)
                            .onError(
                                    argument.capture(),
                                    eq(CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE));
                    verifyNoMoreInteractions(mockListener);

                    camera = argument.getValue();
                    assertNotNull("Expected a non-null camera for the error transition for ID: "
                            + ids[i], camera);
                } else if (state == BlockingStateCallback.STATE_OPENED) {
                    // Camera opened successfully.
                    // => onOpened called exactly once
                    camera = verifyCameraStateOpened(cameraId,
                            mockListener);
                } else {
                    fail("Unexpected state " + state);
                    camera = null; // unreachable. but need this for java compiler
                }

                // Keep track of cameras so we can close it later
                cameraList.add(camera);
                listenerList.add(mockListener);
                blockingListenerList.add(mCameraListener);
            }
        } finally {
            for (CameraDevice camera : cameraList) {
                camera.close();
            }
            for (BlockingStateCallback blockingListener : blockingListenerList) {
                blockingListener.waitForState(
                        BlockingStateCallback.STATE_CLOSED,
                        CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
            }
        }

        /*
         * Ensure that no state transitions have bled through from one camera to another
         * after closing the cameras.
         */
        int i = 0;
        for (MockStateCallback listener : listenerList) {
            CameraDevice camera = cameraList.get(i);

            verify(listener).onClosed(eq(camera));
            verifyNoMoreInteractions(listener);
            i++;
            // Only a #close can happen on the camera since we were done with it.
            // Also nothing else should've happened between the close and the open.
        }
    }

    /**
     * Verifies the camera in this listener was opened and then unconfigured exactly once.
     *
     * <p>This assumes that no other action to the camera has been done (e.g.
     * it hasn't been configured, or closed, or disconnected). Verification is
     * performed immediately without any timeouts.</p>
     *
     * <p>This checks that the state has previously changed first for opened and then unconfigured.
     * Any other state transitions will fail. A test failure is thrown if verification fails.</p>
     *
     * @param cameraId Camera identifier
     * @param listener Listener which was passed to {@link CameraManager#openCamera}
     *
     * @return The camera device (non-{@code null}).
     */
    private static CameraDevice verifyCameraStateOpened(String cameraId,
            MockStateCallback listener) {
        ArgumentCaptor<CameraDevice> argument =
                ArgumentCaptor.forClass(CameraDevice.class);
        InOrder inOrder = inOrder(listener);

        /**
         * State transitions (in that order):
         *  1) onOpened
         *
         * No other transitions must occur for successful #openCamera
         */
        inOrder.verify(listener)
                .onOpened(argument.capture());

        CameraDevice camera = argument.getValue();
        assertNotNull(
                String.format("Failed to open camera device ID: %s", cameraId),
                camera);

        // Do not use inOrder here since that would skip anything called before onOpened
        verifyNoMoreInteractions(listener);

        return camera;
    }

    /**
     * Test: that opening the same device multiple times and make sure the right
     * error state is set.
     */
    public void testCameraManagerOpenCameraTwice() throws Exception {
        String[] ids = mCameraManager.getCameraIdList();

        // Test across every camera device.
        for (int i = 0; i < ids.length; ++i) {
            CameraDevice successCamera = null;
            mCollector.setCameraId(ids[i]);

            try {
                MockStateCallback mockSuccessListener = MockStateCallback.mock();
                MockStateCallback mockFailListener = MockStateCallback.mock();

                BlockingStateCallback successListener =
                        new BlockingStateCallback(mockSuccessListener);
                BlockingStateCallback failListener =
                        new BlockingStateCallback(mockFailListener);

                mCameraManager.openCamera(ids[i], successListener, mHandler);
                mCameraManager.openCamera(ids[i], failListener,
                        mHandler);

                successListener.waitForState(BlockingStateCallback.STATE_OPENED,
                        CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
                ArgumentCaptor<CameraDevice> argument =
                        ArgumentCaptor.forClass(CameraDevice.class);
                verify(mockSuccessListener, atLeastOnce()).onOpened(argument.capture());
                verify(mockSuccessListener, atLeastOnce()).onDisconnected(argument.capture());

                failListener.waitForState(BlockingStateCallback.STATE_OPENED,
                        CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
                verify(mockFailListener, atLeastOnce()).onOpened(argument.capture());

                successCamera = verifyCameraStateOpened(
                        ids[i], mockFailListener);

                verifyNoMoreInteractions(mockFailListener);
            } finally {
                if (successCamera != null) {
                    successCamera.close();
                }
            }
        }
    }

    private class NoopCameraListener extends CameraManager.AvailabilityCallback {
        @Override
        public void onCameraAvailable(String cameraId) {
            // No-op
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            // No-op
        }
    }

    /**
     * Test: that the APIs to register and unregister a listener run successfully;
     * doesn't test that the listener actually gets invoked at the right time.
     * Registering a listener multiple times should have no effect, and unregistering
     * a listener that isn't registered should have no effect.
     */
    public void testCameraManagerListener() throws Exception {
        mCameraManager.unregisterAvailabilityCallback(mListener);
        mCameraManager.registerAvailabilityCallback(mListener, mHandler);
        mCameraManager.registerAvailabilityCallback(mListener, mHandler);
        mCameraManager.unregisterAvailabilityCallback(mListener);
        mCameraManager.unregisterAvailabilityCallback(mListener);
    }

    /**
     * Test that the availability callbacks fire when expected
     */
    public void testCameraManagerListenerCallbacks() throws Exception {
        final int AVAILABILITY_TIMEOUT_MS = 10;

        final LinkedBlockingQueue<String> availableEventQueue = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<String> unavailableEventQueue = new LinkedBlockingQueue<>();

        CameraManager.AvailabilityCallback ac = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                availableEventQueue.offer(cameraId);
            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                unavailableEventQueue.offer(cameraId);
            }
        };

        mCameraManager.registerAvailabilityCallback(ac, mHandler);
        String[] cameras = mCameraManager.getCameraIdList();

        if (cameras.length == 0) {
            Log.i(TAG, "No cameras present, skipping test");
            return;
        }

        // Verify we received available for all cameras' initial state in a reasonable amount of time
        HashSet<String> expectedAvailableCameras = new HashSet<String>(Arrays.asList(cameras));
        while (expectedAvailableCameras.size() > 0) {
            String id = availableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue("Did not receive initial availability notices for some cameras",
                       id != null);
            expectedAvailableCameras.remove(id);
        }
        // Verify no unavailable cameras were reported
        assertTrue("Some camera devices are initially unavailable",
                unavailableEventQueue.size() == 0);

        // Verify transitions for individual cameras
        for (String id : cameras) {
            MockStateCallback mockListener = MockStateCallback.mock();
            mCameraListener = new BlockingStateCallback(mockListener);

            mCameraManager.openCamera(id, mCameraListener, mHandler);

            // Block until opened
            mCameraListener.waitForState(BlockingStateCallback.STATE_OPENED,
                    CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
            // Then verify only open happened, and get the camera handle
            CameraDevice camera = verifyCameraStateOpened(id, mockListener);

            // Verify that we see the expected 'unavailable' event.
            String candidateId = unavailableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(String.format("Received unavailability notice for wrong ID " +
                            "(expected %s, got %s)", id, candidateId),
                    id.equals(candidateId));
            assertTrue("Availability events received unexpectedly",
                    availableEventQueue.size() == 0);

            // Verify that we see the expected 'available' event after closing the camera

            camera.close();

            mCameraListener.waitForState(BlockingStateCallback.STATE_CLOSED,
                    CameraTestUtils.CAMERA_CLOSE_TIMEOUT_MS);

            candidateId = availableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(String.format("Received availability notice for wrong ID " +
                            "(expected %s, got %s)", id, candidateId),
                    id.equals(candidateId));
            assertTrue("Unavailability events received unexpectedly",
                    unavailableEventQueue.size() == 0);

        }

        // Verify that we can unregister the listener and see no more events
        assertTrue("Availability events received unexpectedly",
                availableEventQueue.size() == 0);
        assertTrue("Unavailability events received unexpectedly",
                    unavailableEventQueue.size() == 0);

        mCameraManager.unregisterAvailabilityCallback(ac);

        {
            // Open an arbitrary camera and make sure we don't hear about it

            MockStateCallback mockListener = MockStateCallback.mock();
            mCameraListener = new BlockingStateCallback(mockListener);

            mCameraManager.openCamera(cameras[0], mCameraListener, mHandler);

            // Block until opened
            mCameraListener.waitForState(BlockingStateCallback.STATE_OPENED,
                    CameraTestUtils.CAMERA_IDLE_TIMEOUT_MS);
            // Then verify only open happened, and close the camera
            CameraDevice camera = verifyCameraStateOpened(cameras[0], mockListener);

            camera.close();

            mCameraListener.waitForState(BlockingStateCallback.STATE_CLOSED,
                    CameraTestUtils.CAMERA_CLOSE_TIMEOUT_MS);

            // No unavailability or availability callback should have occured
            String candidateId = unavailableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(String.format("Received unavailability notice for ID %s unexpectedly ",
                            candidateId),
                    candidateId == null);

            candidateId = availableEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(String.format("Received availability notice for ID %s unexpectedly ",
                            candidateId),
                    candidateId == null);


        }

    } // testCameraManagerListenerCallbacks

}
