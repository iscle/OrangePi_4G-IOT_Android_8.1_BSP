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

package android.hardware.multiprocess.camera.cts;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.cts.CameraCtsActivity;
import android.os.Handler;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.*;

/**
 * Tests for multi-process camera usage behavior.
 */
public class CameraEvictionTest extends ActivityInstrumentationTestCase2<CameraCtsActivity> {

    public static final String TAG = "CameraEvictionTest";

    private static final int OPEN_TIMEOUT = 2000; // Timeout for camera to open (ms).
    private static final int SETUP_TIMEOUT = 5000; // Remote camera setup timeout (ms).
    private static final int EVICTION_TIMEOUT = 1000; // Remote camera eviction timeout (ms).
    private static final int WAIT_TIME = 2000; // Time to wait for process to launch (ms).
    private static final int UI_TIMEOUT = 10000; // Time to wait for UI event before timeout (ms).
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;

    private ActivityManager mActivityManager;
    private Context mContext;
    private Camera mCamera;
    private CameraDevice mCameraDevice;
    private final Object mLock = new Object();
    private boolean mCompleted = false;
    private int mProcessPid = -1;

    public CameraEvictionTest() {
        super(CameraCtsActivity.class);
    }

    public static class StateCallbackImpl extends CameraDevice.StateCallback {
        CameraDevice mCameraDevice;

        public StateCallbackImpl() {
            super();
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            synchronized(this) {
                mCameraDevice = cameraDevice;
            }
            Log.i(TAG, "CameraDevice onOpened called for main CTS test process.");
        }

        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
            synchronized(this) {
                mCameraDevice = null;
            }
            Log.i(TAG, "CameraDevice onClosed called for main CTS test process.");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            synchronized(this) {
                mCameraDevice = null;
            }
            Log.i(TAG, "CameraDevice onDisconnected called for main CTS test process.");

        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            Log.i(TAG, "CameraDevice onError called for main CTS test process with error " +
                    "code: " + i);
        }

        public synchronized CameraDevice getCameraDevice() {
            return mCameraDevice;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCompleted = false;
        getActivity();
        mContext = getInstrumentation().getTargetContext();
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().toString());
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(mContext);
        mErrorServiceConnection.start();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mProcessPid != -1) {
            android.os.Process.killProcess(mProcessPid);
            mProcessPid = -1;
        }
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mContext = null;
        mActivityManager = null;
        super.tearDown();
    }

    /**
     * Test basic eviction scenarios for the Camera1 API.
     */
    public void testCamera1ActivityEviction() throws Throwable {
        testAPI1ActivityEviction(Camera1Activity.class, "camera1ActivityProcess");
    }

    /**
     * Test basic eviction scenarios for the Camera2 API.
     */
    public void testBasicCamera2ActivityEviction() throws Throwable {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull(manager);
        String[] cameraIds = manager.getCameraIdList();

        if (cameraIds.length == 0) {
            Log.i(TAG, "Skipping testBasicCamera2ActivityEviction, device has no cameras.");
            return;
        }

        assertTrue(mContext.getMainLooper() != null);

        // Setup camera manager
        String chosenCamera = cameraIds[0];
        Handler cameraHandler = new Handler(mContext.getMainLooper());
        final CameraManager.AvailabilityCallback mockAvailCb =
                mock(CameraManager.AvailabilityCallback.class);

        manager.registerAvailabilityCallback(mockAvailCb, cameraHandler);

        Thread.sleep(WAIT_TIME);

        verify(mockAvailCb, times(1)).onCameraAvailable(chosenCamera);
        verify(mockAvailCb, never()).onCameraUnavailable(chosenCamera);

        // Setup camera device
        final CameraDevice.StateCallback spyStateCb = spy(new StateCallbackImpl());
        manager.openCamera(chosenCamera, spyStateCb, cameraHandler);

        verify(spyStateCb, timeout(OPEN_TIMEOUT).times(1)).onOpened(any(CameraDevice.class));
        verify(spyStateCb, never()).onClosed(any(CameraDevice.class));
        verify(spyStateCb, never()).onDisconnected(any(CameraDevice.class));
        verify(spyStateCb, never()).onError(any(CameraDevice.class), anyInt());

        // Open camera from remote process
        startRemoteProcess(Camera2Activity.class, "camera2ActivityProcess");

        // Verify that the remote camera was opened correctly
        List<ErrorLoggingService.LogEvent> allEvents  = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                TestConstants.EVENT_CAMERA_CONNECT);
        assertNotNull("Camera device not setup in remote process!", allEvents);

        // Filter out relevant events for other camera devices
        ArrayList<ErrorLoggingService.LogEvent> events = new ArrayList<>();
        for (ErrorLoggingService.LogEvent e : allEvents) {
            int eventTag = e.getEvent();
            if (eventTag == TestConstants.EVENT_CAMERA_UNAVAILABLE ||
                    eventTag == TestConstants.EVENT_CAMERA_CONNECT ||
                    eventTag == TestConstants.EVENT_CAMERA_AVAILABLE) {
                if (!Objects.equals(e.getLogText(), chosenCamera)) {
                    continue;
                }
            }
            events.add(e);
        }
        int[] eventList = new int[events.size()];
        int eventIdx = 0;
        for (ErrorLoggingService.LogEvent e : events) {
            eventList[eventIdx++] = e.getEvent();
        }
        String[] actualEvents = TestConstants.convertToStringArray(eventList);
        String[] expectedEvents = new String[] {TestConstants.EVENT_CAMERA_UNAVAILABLE_STR,
                TestConstants.EVENT_CAMERA_CONNECT_STR};
        String[] ignoredEvents = new String[] { TestConstants.EVENT_CAMERA_AVAILABLE_STR,
                TestConstants.EVENT_CAMERA_UNAVAILABLE_STR };
        assertOrderedEvents(actualEvents, expectedEvents, ignoredEvents);

        // Verify that the local camera was evicted properly
        verify(spyStateCb, times(1)).onDisconnected(any(CameraDevice.class));
        verify(spyStateCb, never()).onClosed(any(CameraDevice.class));
        verify(spyStateCb, never()).onError(any(CameraDevice.class), anyInt());
        verify(spyStateCb, times(1)).onOpened(any(CameraDevice.class));

        // Verify that we can no longer open the camera, as it is held by a higher priority process
        boolean openException = false;
        try {
            manager.openCamera(chosenCamera, spyStateCb, cameraHandler);
        } catch(CameraAccessException e) {
            assertTrue("Received incorrect camera exception when opening camera: " + e,
                    e.getReason() == CameraAccessException.CAMERA_IN_USE);
            openException = true;
        }

        assertTrue("Didn't receive exception when trying to open camera held by higher priority " +
                "process.", openException);

        // Verify that attempting to open the camera didn't cause anything weird to happen in the
        // other process.
        List<ErrorLoggingService.LogEvent> eventList2 = null;
        boolean timeoutExceptionHit = false;
        try {
            eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
        } catch (TimeoutException e) {
            timeoutExceptionHit = true;
        }

        assertNone("Remote camera service received invalid events: ", eventList2);
        assertTrue("Remote camera service exited early", timeoutExceptionHit);
        android.os.Process.killProcess(mProcessPid);
        mProcessPid = -1;
        forceCtsActivityToTop();
    }


    /**
     * Test basic eviction scenarios for camera used in MediaRecoder
     */
    public void testMediaRecorderCameraActivityEviction() throws Throwable {
        testAPI1ActivityEviction(MediaRecorderCameraActivity.class,
                "mediaRecorderCameraActivityProcess");
    }

    /**
     * Test basic eviction scenarios for Camera1 API.
     *
     * This test will open camera, create a higher priority process to run the specified activity,
     * open camera again, and verify the right clients are evicted.
     *
     * @param activityKlass An activity to run in a higher priority process.
     * @param processName The process name.
     */
    private void testAPI1ActivityEviction (java.lang.Class<?> activityKlass, String processName)
            throws Throwable {
        // Open a camera1 client in the main CTS process's activity
        final Camera.ErrorCallback mockErrorCb1 = mock(Camera.ErrorCallback.class);
        final boolean[] skip = {false};
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Open camera
                mCamera = Camera.open();
                if (mCamera == null) {
                    skip[0] = true;
                } else {
                    mCamera.setErrorCallback(mockErrorCb1);
                }
                notifyFromUI();
            }
        });
        waitForUI();

        if (skip[0]) {
            Log.i(TAG, "Skipping testCamera1ActivityEviction, device has no cameras.");
            return;
        }

        verifyZeroInteractions(mockErrorCb1);

        startRemoteProcess(activityKlass, processName);

        // Make sure camera was setup correctly in remote activity
        List<ErrorLoggingService.LogEvent> events = null;
        try {
            events = mErrorServiceConnection.getLog(SETUP_TIMEOUT,
                    TestConstants.EVENT_CAMERA_CONNECT);
        } finally {
            if (events != null) assertOnly(TestConstants.EVENT_CAMERA_CONNECT, events);
        }

        Thread.sleep(WAIT_TIME);

        // Ensure UI thread has a chance to process callbacks.
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i("CTS", "Did something on UI thread.");
                notifyFromUI();
            }
        });
        waitForUI();

        // Make sure we received correct callback in error listener, and nothing else
        verify(mockErrorCb1, only()).onError(eq(Camera.CAMERA_ERROR_EVICTED), isA(Camera.class));
        mCamera = null;

        // Try to open the camera again (even though other TOP process holds the camera).
        final boolean[] pass = {false};
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Open camera
                try {
                    mCamera = Camera.open();
                } catch (RuntimeException e) {
                    pass[0] = true;
                }
                notifyFromUI();
            }
        });
        waitForUI();

        assertTrue("Did not receive exception when opening camera while camera is held by a" +
                " higher priority client process.", pass[0]);

        // Verify that attempting to open the camera didn't cause anything weird to happen in the
        // other process.
        List<ErrorLoggingService.LogEvent> eventList2 = null;
        boolean timeoutExceptionHit = false;
        try {
            eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
        } catch (TimeoutException e) {
            timeoutExceptionHit = true;
        }

        assertNone("Remote camera service received invalid events: ", eventList2);
        assertTrue("Remote camera service exited early", timeoutExceptionHit);
        android.os.Process.killProcess(mProcessPid);
        mProcessPid = -1;
        forceCtsActivityToTop();
    }

    /**
     * Ensure the CTS activity becomes foreground again instead of launcher.
     */
    private void forceCtsActivityToTop() throws InterruptedException {
        Thread.sleep(WAIT_TIME);
        Activity a = getActivity();
        Intent activityIntent = new Intent(a, CameraCtsActivity.class);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        a.startActivity(activityIntent);
        Thread.sleep(WAIT_TIME);
    }

    /**
     * Block until UI thread calls {@link #notifyFromUI()}.
     * @throws InterruptedException
     */
    private void waitForUI() throws InterruptedException {
        synchronized(mLock) {
            if (mCompleted) return;
            while (!mCompleted) {
                mLock.wait();
            }
            mCompleted = false;
        }
    }

    /**
     * Wake up any threads waiting in calls to {@link #waitForUI()}.
     */
    private void notifyFromUI() {
        synchronized (mLock) {
            mCompleted = true;
            mLock.notifyAll();
        }
    }

    /**
     * Return the PID for the process with the given name in the given list of process info.
     *
     * @param processName the name of the process who's PID to return.
     * @param list a list of {@link ActivityManager.RunningAppProcessInfo} to check.
     * @return the PID of the given process, or -1 if it was not included in the list.
     */
    private static int getPid(String processName,
                              List<ActivityManager.RunningAppProcessInfo> list) {
        for (ActivityManager.RunningAppProcessInfo rai : list) {
            if (processName.equals(rai.processName))
                return rai.pid;
        }
        return -1;
    }

    /**
     * Start an activity of the given class running in a remote process with the given name.
     *
     * @param klass the class of the {@link android.app.Activity} to start.
     * @param processName the remote activity name.
     * @throws InterruptedException
     */
    public void startRemoteProcess(java.lang.Class<?> klass, String processName)
            throws InterruptedException {
        // Ensure no running activity process with same name
        Activity a = getActivity();
        String cameraActivityName = a.getPackageName() + ":" + processName;
        List<ActivityManager.RunningAppProcessInfo> list =
                mActivityManager.getRunningAppProcesses();
        assertEquals(-1, getPid(cameraActivityName, list));

        // Start activity in a new top foreground process
        Intent activityIntent = new Intent(a, klass);
        a.startActivity(activityIntent);
        Thread.sleep(WAIT_TIME);

        // Fail if activity isn't running
        list = mActivityManager.getRunningAppProcesses();
        mProcessPid = getPid(cameraActivityName, list);
        assertTrue(-1 != mProcessPid);
    }

    /**
     * Assert that there is only one event of the given type in the event list.
     *
     * @param event event type to check for.
     * @param events {@link List} of events.
     */
    public static void assertOnly(int event, List<ErrorLoggingService.LogEvent> events) {
        assertTrue("Remote camera activity never received event: " + event, events != null);
        for (ErrorLoggingService.LogEvent e : events) {
            assertFalse("Remote camera activity received invalid event (" + e +
                    ") while waiting for event: " + event,
                    e.getEvent() < 0 || e.getEvent() != event);
        }
        assertTrue("Remote camera activity never received event: " + event, events.size() >= 1);
        assertTrue("Remote camera activity received too many " + event + " events, received: " +
                events.size(), events.size() == 1);
    }

    /**
     * Assert there were no logEvents in the given list.
     *
     * @param msg message to show on assertion failure.
     * @param events {@link List} of events.
     */
    public static void assertNone(String msg, List<ErrorLoggingService.LogEvent> events) {
        if (events == null) return;
        StringBuilder builder = new StringBuilder(msg + "\n");
        for (ErrorLoggingService.LogEvent e : events) {
            builder.append(e).append("\n");
        }
        assertTrue(builder.toString(), events.isEmpty());
    }

    /**
     * Assert array is null or empty.
     *
     * @param array array to check.
     */
    public static <T> void assertNotEmpty(T[] array) {
        assertNotNull(array);
        assertFalse("Array is empty: " + Arrays.toString(array), array.length == 0);
    }

    /**
     * Given an 'actual' array of objects, check that the objects given in the 'expected'
     * array are also present in the 'actual' array in the same order.  Objects in the 'actual'
     * array that are not in the 'expected' array are skipped and ignored if they are given
     * in the 'ignored' array, otherwise this assertion will fail.
     *
     * @param actual the ordered array of objects to check.
     * @param expected the ordered array of expected objects.
     * @param ignored the array of objects that will be ignored if present in actual,
     *                but not in expected (or are out of order).
     * @param <T>
     */
    public static <T> void assertOrderedEvents(T[] actual, T[] expected, T[] ignored) {
        assertNotNull(actual);
        assertNotNull(expected);
        assertNotNull(ignored);

        int expIndex = 0;
        int index = 0;
        for (T i : actual) {
            // If explicitly expected, move to next
            if (expIndex < expected.length && Objects.equals(i, expected[expIndex])) {
                expIndex++;
                continue;
            }

            // Fail if not ignored
            boolean canIgnore = false;
            for (T j : ignored) {
                if (Objects.equals(i, j)) {
                    canIgnore = true;
                    break;
                }

            }

            // Fail if not ignored.
            assertTrue("Event at index " + index + " in actual array " +
                    Arrays.toString(actual) + " was unexpected: expected array was " +
                    Arrays.toString(expected) + ", ignored array was: " +
                    Arrays.toString(ignored), canIgnore);
            index++;
        }
        assertTrue("Only had " + expIndex + " of " + expected.length +
                " expected objects in array " + Arrays.toString(actual) + ", expected was " +
                Arrays.toString(expected), expIndex == expected.length);
    }
}
