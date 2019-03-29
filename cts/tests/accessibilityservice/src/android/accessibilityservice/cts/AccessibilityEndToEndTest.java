/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.accessibilityservice.cts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.test.suitebuilder.annotation.MediumTest;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import android.accessibilityservice.cts.R;

import java.util.Iterator;
import java.util.List;

/**
 * This class performs end-to-end testing of the accessibility feature by
 * creating an {@link Activity} and poking around so {@link AccessibilityEvent}s
 * are generated and their correct dispatch verified.
 */
public class AccessibilityEndToEndTest extends
        AccessibilityActivityTestCase<AccessibilityEndToEndActivity> {

    private static final String LOG_TAG = "AccessibilityEndToEndTest";

    /**
     * Creates a new instance for testing {@link AccessibilityEndToEndActivity}.
     */
    public AccessibilityEndToEndTest() {
        super(AccessibilityEndToEndActivity.class);
    }

    @MediumTest
    public void testTypeViewSelectedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_SELECTED);
        expected.setClassName(ListView.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(getActivity().getString(R.string.second_list_item));
        expected.setItemCount(2);
        expected.setCurrentItemIndex(1);
        expected.setEnabled(true);
        expected.setScrollable(false);
        expected.setFromIndex(0);
        expected.setToIndex(1);

        final ListView listView = (ListView) getActivity().findViewById(R.id.listview);

        AccessibilityEvent awaitedEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listView.setSelection(1);
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    public void testTypeViewClickedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(getActivity().getString(R.string.button_title));
        expected.setEnabled(true);

        final Button button = (Button) getActivity().findViewById(R.id.button);

        AccessibilityEvent awaitedEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.performClick();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    public void testTypeViewLongClickedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(getActivity().getString(R.string.button_title));
        expected.setEnabled(true);

        final Button button = (Button) getActivity().findViewById(R.id.button);

        AccessibilityEvent awaitedEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.performLongClick();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    public void testTypeViewFocusedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        expected.setClassName(Button.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(getActivity().getString(R.string.button_title));
        expected.setItemCount(3);
        expected.setCurrentItemIndex(2);
        expected.setEnabled(true);

        final Button button = (Button) getActivity().findViewById(R.id.button);

        AccessibilityEvent awaitedEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.requestFocus();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    public void testTypeViewTextChangedAccessibilityEvent() throws Throwable {
        // focus the edit text
        final EditText editText = (EditText) getActivity().findViewById(R.id.edittext);

        AccessibilityEvent awaitedFocusEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editText.requestFocus();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED;
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected focuss event.", awaitedFocusEvent);

        final String beforeText = getActivity().getString(R.string.text_input_blah);
        final String newText = getActivity().getString(R.string.text_input_blah_blah);
        final String afterText = beforeText.substring(0, 3) + newText;

        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        expected.setClassName(EditText.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(afterText);
        expected.setBeforeText(beforeText);
        expected.setFromIndex(3);
        expected.setAddedCount(9);
        expected.setRemovedCount(1);
        expected.setEnabled(true);

        AccessibilityEvent awaitedTextChangeEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        editText.getEditableText().replace(3, 4, newText);
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedTextChangeEvent);
    }

    @MediumTest
    public void testTypeWindowStateChangedAccessibilityEvent() throws Throwable {
        // create and populate the expected event
        final AccessibilityEvent expected = AccessibilityEvent.obtain();
        expected.setEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        expected.setClassName(AlertDialog.class.getName());
        expected.setPackageName(getActivity().getPackageName());
        expected.getText().add(getActivity().getString(R.string.alert_title));
        expected.getText().add(getActivity().getString(R.string.alert_message));
        expected.setEnabled(true);

        AccessibilityEvent awaitedEvent =
            getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                new Runnable() {
            @Override
            public void run() {
                // trigger the event
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        (new AlertDialog.Builder(getActivity()).setTitle(R.string.alert_title)
                                .setMessage(R.string.alert_message)).create().show();
                    }
                });
            }},
            new UiAutomation.AccessibilityEventFilter() {
                // check the received event
                @Override
                public boolean accept(AccessibilityEvent event) {
                    return equalsAccessiblityEvent(event, expected);
                }
            },
            TIMEOUT_ASYNC_PROCESSING);
        assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
    }

    @MediumTest
    @SuppressWarnings("deprecation")
    public void testTypeNotificationStateChangedAccessibilityEvent() throws Throwable {
        // No notification UI on televisions.
        if ((getActivity().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.i(LOG_TAG, "Skipping: testTypeNotificationStateChangedAccessibilityEvent" +
                    " - No notification UI on televisions.");
            return;
        }
        PackageManager pm = getInstrumentation().getTargetContext().getPackageManager();
        if (pm.hasSystemFeature(pm.FEATURE_WATCH)) {
            Log.i(LOG_TAG, "Skipping: testTypeNotificationStateChangedAccessibilityEvent" +
                    " - Watches have different notification system.");
            return;
        }

        String message = getActivity().getString(R.string.notification_message);

        final NotificationManager notificationManager =
                (NotificationManager) getActivity().getSystemService(Service.NOTIFICATION_SERVICE);
        final NotificationChannel channel =
                new NotificationChannel("id", "name", NotificationManager.IMPORTANCE_DEFAULT);
        try {
            // create the notification to send
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setBypassDnd(true);
            notificationManager.createNotificationChannel(channel);
            NotificationChannel created =
                    notificationManager.getNotificationChannel(channel.getId());
            final int notificationId = 1;
            final Notification notification =
                    new Notification.Builder(getActivity(), channel.getId())
                            .setSmallIcon(android.R.drawable.stat_notify_call_mute)
                            .setContentIntent(PendingIntent.getActivity(getActivity(), 0,
                                    new Intent(),
                                    PendingIntent.FLAG_CANCEL_CURRENT))
                            .setTicker(message)
                            .setContentTitle("")
                            .setContentText("")
                            .setPriority(Notification.PRIORITY_MAX)
                            // Mark the notification as "interruptive" by specifying a vibration
                            // pattern. This ensures it's announced properly on watch-type devices.
                            .setVibrate(new long[]{})
                            .build();

            // create and populate the expected event
            final AccessibilityEvent expected = AccessibilityEvent.obtain();
            expected.setEventType(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
            expected.setClassName(Notification.class.getName());
            expected.setPackageName(getActivity().getPackageName());
            expected.getText().add(message);
            expected.setParcelableData(notification);

            AccessibilityEvent awaitedEvent =
                    getInstrumentation().getUiAutomation().executeAndWaitForEvent(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // trigger the event
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            // trigger the event
                                            notificationManager
                                                    .notify(notificationId, notification);
                                            getActivity().finish();
                                        }
                                    });
                                }
                            },
                            new UiAutomation.AccessibilityEventFilter() {
                                // check the received event
                                @Override
                                public boolean accept(AccessibilityEvent event) {
                                    return equalsAccessiblityEvent(event, expected);
                                }
                            },
                            TIMEOUT_ASYNC_PROCESSING);
            assertNotNull("Did not receive expected event: " + expected, awaitedEvent);
        } finally {
            notificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    @MediumTest
    public void testInterrupt_notifiesService() {
        getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        InstrumentedAccessibilityService service = InstrumentedAccessibilityService.enableService(
                getInstrumentation(), InstrumentedAccessibilityService.class);
        try {
            assertFalse(service.wasOnInterruptCalled());

            getActivity().runOnUiThread(() -> {
                AccessibilityManager accessibilityManager = (AccessibilityManager) getActivity()
                        .getSystemService(Service.ACCESSIBILITY_SERVICE);
                accessibilityManager.interrupt();
            });

            Object waitObject = service.getInterruptWaitObject();
            synchronized (waitObject) {
                if (!service.wasOnInterruptCalled()) {
                    try {
                        waitObject.wait(TIMEOUT_ASYNC_PROCESSING);
                    } catch (InterruptedException e) {
                        // Do nothing
                    }
                }
            }
            assertTrue(service.wasOnInterruptCalled());
        } finally {
            service.disableSelfAndRemove();
        }
    }

    /**
     * Compares all properties of the <code>first</code> and the
     * <code>second</code>.
     */
    private boolean equalsAccessiblityEvent(AccessibilityEvent first, AccessibilityEvent second) {
         return first.getEventType() == second.getEventType()
            && first.isChecked() == second.isChecked()
            && first.getCurrentItemIndex() == second.getCurrentItemIndex()
            && first.isEnabled() == second.isEnabled()
            && first.getFromIndex() == second.getFromIndex()
            && first.getItemCount() == second.getItemCount()
            && first.isPassword() == second.isPassword()
            && first.getRemovedCount() == second.getRemovedCount()
            && first.isScrollable()== second.isScrollable()
            && first.getToIndex() == second.getToIndex()
            && first.getRecordCount() == second.getRecordCount()
            && first.getScrollX() == second.getScrollX()
            && first.getScrollY() == second.getScrollY()
            && first.getAddedCount() == second.getAddedCount()
            && TextUtils.equals(first.getBeforeText(), second.getBeforeText())
            && TextUtils.equals(first.getClassName(), second.getClassName())
            && TextUtils.equals(first.getContentDescription(), second.getContentDescription())
            && equalsNotificationAsParcelableData(first, second)
            && equalsText(first, second);
    }

    /**
     * Compares the {@link android.os.Parcelable} data of the
     * <code>first</code> and <code>second</code>.
     */
    private boolean equalsNotificationAsParcelableData(AccessibilityEvent first,
            AccessibilityEvent second) {
        Notification firstNotification = (Notification) first.getParcelableData();
        Notification secondNotification = (Notification) second.getParcelableData();
        if (firstNotification == null) {
            return (secondNotification == null);
        } else if (secondNotification == null) {
            return false;
        }
        return TextUtils.equals(firstNotification.tickerText, secondNotification.tickerText);
    }

    /**
     * Compares the text of the <code>first</code> and <code>second</code> text.
     */
    private boolean equalsText(AccessibilityEvent first, AccessibilityEvent second) {
        List<CharSequence> firstText = first.getText();
        List<CharSequence> secondText = second.getText();
        if (firstText.size() != secondText.size()) {
            return false;
        }
        Iterator<CharSequence> firstIterator = firstText.iterator();
        Iterator<CharSequence> secondIterator = secondText.iterator();
        for (int i = 0; i < firstText.size(); i++) {
            if (!firstIterator.next().toString().equals(secondIterator.next().toString())) {
                return false;
            }
        }
        return true;
    }
}
