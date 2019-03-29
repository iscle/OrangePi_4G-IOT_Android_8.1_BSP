/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.verifier.vr;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;


import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VrListenerVerifierActivity extends PassFailButtons.Activity {

    private static final String TAG = "VrListenerActivity";
    public static final String ENABLED_VR_LISTENERS = "enabled_vr_listeners";
    private static final String STATE = "state";
    private static final int POLL_DELAY_MS = 2000;
    static final String EXTRA_LAUNCH_SECOND_INTENT = "do2intents";

    private LayoutInflater mInflater;
    private InteractiveTestCase[] mTests;
    private ViewGroup mTestViews;
    private int mCurrentIdx;
    private Handler mMainHandler;
    private Handler mTestHandler;
    private HandlerThread mTestThread;
    private PersistentTestStatusHandler persistentTestStatusHandler;

    public enum Status {
        SETUP,
        RUNNING,
        PASS,
        FAIL,
        WAIT_FOR_USER;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mCurrentIdx = (savedState == null) ? 0 : savedState.getInt(STATE, 0);

        persistentTestStatusHandler = new PersistentTestStatusHandler();
        mTestThread = new HandlerThread("VrTestThread");
        mTestThread.start();
        mTestHandler = new Handler(mTestThread.getLooper());
        mInflater = getLayoutInflater();
        View v = mInflater.inflate(R.layout.vr_main, null);
        setContentView(v);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.vr_test_title, R.string.vr_info, -1);

        mTestViews = (ViewGroup) v.findViewById(R.id.vr_test_items);
        mTests = new InteractiveTestCase[] {
                new IsDefaultDisabledTest(),
                new UserEnableTest(),
                new VrModeSwitchTest(),
                new VrModeMultiSwitchTest(),
                new UserDisableTest(),
        };

        for (InteractiveTestCase test : mTests) {
            test.setStatus((savedState == null) ? Status.SETUP :
                    Status.values()[savedState.getInt(test.getClass().getSimpleName(), 0)]);
            mTestViews.addView(test.getView(mTestViews));
        }

        updateUiState();

        mMainHandler = new Handler();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTestThread != null) {
            mTestThread.quit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE, mCurrentIdx);
        for (InteractiveTestCase i : mTests) {
            outState.putInt(i.getClass().getSimpleName(), i.getStatus().ordinal());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final InteractiveTestCase current = mTests[mCurrentIdx];
        final Status currentTestStatus = current.getStatus();
        if (currentTestStatus == Status.RUNNING) {
          // The previous instance of this class was interurpted while a test
          // was running most likely due to a configuration change
          // We must wait for the previous test results to be written to the
          // shared configuration file before continuing with the next test
          waitForPreviousRunningTest();
        } else if (currentTestStatus == Status.PASS) {
          // The previous instance of this class was interrupted after a test
          // has finished running most likely due to a configuration change
          selectNext();
        } else {
          runNext();
        }
    }

    private void waitForPreviousRunningTest() {
      final InteractiveTestCase current = mTests[mCurrentIdx];
      ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();

      s.scheduleAtFixedRate(new Runnable() {
        private void handleTestFinished() {
          selectNext();
          s.shutdown();
        }

        @Override
        public void run() {
          Status status = persistentTestStatusHandler.getStatusOfTest(mCurrentIdx);

          if (status == Status.PASS) {
            current.markPassed();
            handleTestFinished();
          } else if (status == Status.FAIL) {
            current.markFailed();
            handleTestFinished();
          }
        }
      }, 0, POLL_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void updateUiState() {
        boolean allPassed = true;
        for (InteractiveTestCase t : mTests) {
            t.updateViews();
            if (t.getStatus() != Status.PASS) {
                allPassed = false;
            }
        }

        if (allPassed) {
            getPassButton().setEnabled(true);
        }
    }

    protected void logWithStack(String message) {
        logWithStack(message, null);
    }

    protected void logWithStack(String message, Throwable stackTrace) {
        if (stackTrace == null) {
            stackTrace = new Throwable();
            stackTrace.fillInStackTrace();
        }
        Log.e(TAG, message, stackTrace);
    }

    private void selectNext() {
        mCurrentIdx++;
        if (mCurrentIdx >= mTests.length) {
            done();
            return;
        }
        final InteractiveTestCase current = mTests[mCurrentIdx];
        current.markWaiting();
    }

    private void runNext() {
        if (mCurrentIdx >= mTests.length) {
            done();
            return;
        }
        final InteractiveTestCase current = mTests[mCurrentIdx];
        mTestHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Starting test: " + current.getClass().getSimpleName());
                persistentTestStatusHandler.setStatusOfTest(mCurrentIdx, Status.RUNNING);
                boolean passed = true;
                try {
                    current.setUp();
                    current.test();
                } catch (Throwable e) {
                    logWithStack("Failed " + current.getClass().getSimpleName() + " with: ", e);
                    setFailed(current);
                    passed = false;
                } finally {
                    try {
                        current.tearDown();
                    } catch (Throwable e) {
                        logWithStack("Failed tearDown of " + current.getClass().getSimpleName() +
                                " with: ", e);
                        setFailed(current);
                        passed = false;
                    }
                }

                // Write to persistent store in the event that the activity was
                // destroyed while this test was running
                persistentTestStatusHandler.setStatusOfTest(mCurrentIdx, passed ? Status.PASS : Status.FAIL);

                if (passed) {
                    current.markPassed();
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            selectNext();
                        }
                    });
                }
                Log.i(TAG, "Done test: " + current.getClass().getSimpleName());
            }
        });
    }

    private void done() {
        updateUiState();
        persistentTestStatusHandler.clear();
        Log.i(TAG, "Completed run!");
    }


    private void setFailed(final InteractiveTestCase current) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                getPassButton().setEnabled(false);
                current.markFailed();
            }
        });
    }

    protected View createUserInteractionTestView(ViewGroup parent, int stringId, int messageId) {
        View v = mInflater.inflate(R.layout.vr_item, parent, false);
        TextView instructions = (TextView) v.findViewById(R.id.vr_instructions);
        instructions.setText(getString(messageId));
        Button b = (Button) v.findViewById(R.id.vr_action_button);
        b.setText(stringId);
        b.setTag(stringId);
        return v;
    }

    protected View createAutoTestView(ViewGroup parent, int messageId) {
        View v = mInflater.inflate(R.layout.vr_item, parent, false);
        TextView instructions = (TextView) v.findViewById(R.id.vr_instructions);
        instructions.setText(getString(messageId));
        Button b = (Button) v.findViewById(R.id.vr_action_button);
        b.setVisibility(View.GONE);
        return v;
    }

    protected abstract class InteractiveTestCase {
        protected static final String TAG = "InteractiveTest";
        private Status status;
        private View view;

        abstract View inflate(ViewGroup parent);

        View getView(ViewGroup parent) {
            if (view == null) {
                view = inflate(parent);
            }
            return view;
        }

        abstract void test() throws Throwable;

        void setUp() throws Throwable {
            // Noop
        }

        void tearDown() throws Throwable {
            // Noop
        }

        Status getStatus() {
            return status;
        }

        void setStatus(Status s) {
            status = s;
        }

        void markFailed() {
            Log.i(TAG, "FAILED test: " + this.getClass().getSimpleName());
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    InteractiveTestCase.this.setStatus(Status.FAIL);
                    updateViews();
                }
            });
        }

        void markPassed() {
            Log.i(TAG, "PASSED test: " + this.getClass().getSimpleName());
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    InteractiveTestCase.this.setStatus(Status.PASS);
                    updateViews();
                }
            });
        }

        void markFocused() {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    InteractiveTestCase.this.setStatus(Status.SETUP);
                    updateViews();
                }
            });
        }

        void markWaiting() {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    InteractiveTestCase.this.setStatus(Status.WAIT_FOR_USER);
                    updateViews();
                }
            });
        }

        void markRunning() {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    InteractiveTestCase.this.setStatus(Status.RUNNING);
                    updateViews();
                }
            });
        }

        private void updateViews() {
            View item = view;
            ImageView statusView = (ImageView) item.findViewById(R.id.vr_status);
            View button = item.findViewById(R.id.vr_action_button);
            switch (status) {
                case WAIT_FOR_USER:
                    statusView.setImageResource(R.drawable.fs_warning);
                    button.setEnabled(true);
                    break;
                case SETUP:
                    statusView.setImageResource(R.drawable.fs_indeterminate);
                    button.setEnabled(false);
                    break;
                case RUNNING:
                    statusView.setImageResource(R.drawable.fs_clock);
                    break;
                case FAIL:
                    statusView.setImageResource(R.drawable.fs_error);
                    break;
                case PASS:
                    statusView.setImageResource(R.drawable.fs_good);
                    button.setClickable(false);
                    button.setEnabled(false);
                    break;
            }
            statusView.invalidate();
        }
    }

    private static void assertTrue(String message, boolean b) {
        if (!b) {
            throw new IllegalStateException(message);
        }
    }

    private static <E> void assertIn(String message, E elem, E[] c) {
        if (!Arrays.asList(c).contains(elem)) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertEventIn(String message, MockVrListenerService.Event elem,
                                      MockVrListenerService.EventType[] c) {
        if (!Arrays.asList(c).contains(elem.type)) {
            throw new IllegalStateException(message);
        }
    }

    protected void launchVrListenerSettings() {
        VrListenerVerifierActivity.this.startActivity(
                new Intent(Settings.ACTION_VR_LISTENER_SETTINGS));
    }

    protected void launchVrActivity() {
        VrListenerVerifierActivity.this.startActivity(
                new Intent(VrListenerVerifierActivity.this, MockVrActivity.class));
    }

    protected void launchDoubleVrActivity() {
        VrListenerVerifierActivity.this.startActivity(
                new Intent(VrListenerVerifierActivity.this, MockVrActivity.class).
                        putExtra(EXTRA_LAUNCH_SECOND_INTENT, true));
    }

    public void actionPressed(View v) {
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            int id = ((Integer) tag).intValue();
            if (id == R.string.vr_start_settings) {
                launchVrListenerSettings();
            } else if (id == R.string.vr_start_vr_activity) {
                launchVrActivity();
            } else if (id == R.string.vr_start_double_vr_activity) {
                launchDoubleVrActivity();
            }
        }
    }

    private class IsDefaultDisabledTest extends InteractiveTestCase {

        @Override
        View inflate(ViewGroup parent) {
            return createAutoTestView(parent, R.string.vr_check_disabled);
        }

        @Override
        void setUp() {
            markFocused();
        }

        @Override
        void test() {
            assertTrue("VR listeners should not be bound by default.",
                    MockVrListenerService.getNumBoundMockVrListeners() == 0);
        }
    }

    private class UserEnableTest extends InteractiveTestCase {

        @Override
        View inflate(ViewGroup parent) {
            return createUserInteractionTestView(parent, R.string.vr_start_settings,
                    R.string.vr_enable_service);
        }

        @Override
        void setUp() {
            markWaiting();
        }

        @Override
        void test() {
            String helpers = Settings.Secure.getString(getContentResolver(), ENABLED_VR_LISTENERS);
            ComponentName c = new ComponentName(VrListenerVerifierActivity.this,
                    MockVrListenerService.class);
            if (MockVrListenerService.getPendingEvents().size() > 0) {
                MockVrListenerService.getPendingEvents().clear();
                throw new IllegalStateException("VrListenerService bound before entering VR mode!");
            }
            assertTrue("Settings must now contain " + c.flattenToString(),
                    helpers != null && helpers.contains(c.flattenToString()));
        }
    }

    private class VrModeSwitchTest extends InteractiveTestCase {

        @Override
        View inflate(ViewGroup parent) {
            return createUserInteractionTestView(parent, R.string.vr_start_vr_activity,
                    R.string.vr_start_vr_activity_desc);
        }

        @Override
        void setUp() {
            markWaiting();
        }

        @Override
        void test() throws Throwable {
            markRunning();

            ArrayBlockingQueue<MockVrListenerService.Event> q =
                    MockVrListenerService.getPendingEvents();
            MockVrListenerService.Event e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive onCreate or onBind event from VrListenerService.",
                    e != null);
            assertEventIn("First listener service event must be onCreate or onBind, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONCREATE,
                    MockVrListenerService.EventType.ONBIND
            });
            if (e.type == MockVrListenerService.EventType.ONCREATE) {
                e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
                assertTrue("Timed out before receive onBind event from VrListenerService.",
                        e != null);
                assertEventIn("Second listener service event must be onBind, but was " +
                        e.type, e, new MockVrListenerService.EventType[]{
                        MockVrListenerService.EventType.ONBIND
                });
            }

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive onCurrentVrModeActivityChanged event " +
                    "from VrListenerService.", e != null);
            assertTrue("Listener service must receive onCurrentVrModeActivityChanged, but was " +
                    e.type,
                    e.type == MockVrListenerService.EventType.ONCURRENTVRMODEACTIVITYCHANGED);
            ComponentName expected = new ComponentName(VrListenerVerifierActivity.this,
                    MockVrActivity.class);
            assertTrue("Activity component must be " + expected + ", but was: " + e.arg1,
                    Objects.equals(expected, e.arg1));

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive unbind event from VrListenerService.", e != null);
            assertEventIn("Listener service must receive onUnbind, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONUNBIND
            });

            // Consume onDestroy
            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive onDestroy event from VrListenerService.",
                    e != null);
            assertEventIn("Listener service must receive onDestroy, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONDESTROY
            });

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            if (e != null) {
                throw new IllegalStateException("Spurious event received after onDestroy: "
                        + e.type);
            }
        }
    }

    private class VrModeMultiSwitchTest extends InteractiveTestCase {

        @Override
        View inflate(ViewGroup parent) {
            return createUserInteractionTestView(parent, R.string.vr_start_double_vr_activity,
                    R.string.vr_start_vr_double_activity_desc);
        }

        @Override
        void setUp() {
            markWaiting();
        }

        @Override
        void test() throws Throwable {
            markRunning();

            ArrayBlockingQueue<MockVrListenerService.Event> q =
                    MockVrListenerService.getPendingEvents();
            MockVrListenerService.Event e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive event from VrListenerService.", e != null);
            assertEventIn("First listener service event must be onCreate or onBind, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONCREATE,
                    MockVrListenerService.EventType.ONBIND
            });
            if (e.type == MockVrListenerService.EventType.ONCREATE) {
                e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
                assertTrue("Timed out before receive event from VrListenerService.", e != null);
                assertEventIn("Second listener service event must be onBind, but was " +
                        e.type, e, new MockVrListenerService.EventType[]{
                        MockVrListenerService.EventType.ONBIND
                });
            }

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive event from VrListenerService.", e != null);
            assertTrue("Listener service must receive onCurrentVrModeActivityChanged, but received "
                    + e.type, e.type ==
                    MockVrListenerService.EventType.ONCURRENTVRMODEACTIVITYCHANGED);
            ComponentName expected = new ComponentName(VrListenerVerifierActivity.this,
                    MockVrActivity.class);
            assertTrue("Activity component must be " + expected + ", but was: " + e.arg1,
                    Objects.equals(expected, e.arg1));

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive event from VrListenerService.", e != null);
            assertTrue("Listener service must receive onCurrentVrModeActivityChanged, but received "
                    + e.type, e.type ==
                    MockVrListenerService.EventType.ONCURRENTVRMODEACTIVITYCHANGED);
            ComponentName expected2 = new ComponentName(VrListenerVerifierActivity.this,
                    MockVrActivity2.class);
            assertTrue("Activity component must be " + expected2 + ", but was: " + e.arg1,
                    Objects.equals(expected2, e.arg1));

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive event from VrListenerService.", e != null);
            assertTrue("Listener service must receive onCurrentVrModeActivityChanged, but received "
                    + e.type, e.type ==
                    MockVrListenerService.EventType.ONCURRENTVRMODEACTIVITYCHANGED);
            assertTrue("Activity component must be " + expected + ", but was: " + e.arg1,
                    Objects.equals(expected, e.arg1));

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive event from VrListenerService.", e != null);
            assertEventIn("Listener service must receive onUnbind, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONUNBIND
            });

            // Consume onDestroy
            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue("Timed out before receive onDestroy event from VrListenerService.",
                    e != null);
            assertEventIn("Listener service must receive onDestroy, but was " +
                    e.type, e, new MockVrListenerService.EventType[]{
                    MockVrListenerService.EventType.ONDESTROY
            });

            e = q.poll(POLL_DELAY_MS, TimeUnit.MILLISECONDS);
            if (e != null) {
                throw new IllegalStateException("Spurious event received after onDestroy: "
                        + e.type);
            }
        }
    }

    private class UserDisableTest extends InteractiveTestCase {

        @Override
        View inflate(ViewGroup parent) {
            return createUserInteractionTestView(parent, R.string.vr_start_settings,
                    R.string.vr_disable_service);
        }

        @Override
        void setUp() {
            markWaiting();
        }

        @Override
        void test() {
            String helpers = Settings.Secure.getString(getContentResolver(), ENABLED_VR_LISTENERS);
            ComponentName c = new ComponentName(VrListenerVerifierActivity.this,
                    MockVrListenerService.class);
            assertTrue("Settings must no longer contain " + c.flattenToString(),
                    helpers == null || !(helpers.contains(c.flattenToString())));
        }
    }

    private class PersistentTestStatusHandler {

      private final String PREFERENCE_FILE_NAME = "VrListenerVerifierActivityTestStatus";
      private final SharedPreferences sharedPref = getSharedPreferences(PREFERENCE_FILE_NAME, 0);

      Status getStatusOfTest(int testIndex) {
        String key = Integer.toString(testIndex);
        String stringStatus = sharedPref.getString(key, Status.WAIT_FOR_USER.name());

        return Status.valueOf(stringStatus);
      }

      void setStatusOfTest(int testIndex, Status status) {
        String key = Integer.toString(testIndex);
        String stringStatus = status.name();

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, stringStatus);
        editor.apply();
      }

      void clear() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.clear();
        editor.apply();
      }
    }
}
