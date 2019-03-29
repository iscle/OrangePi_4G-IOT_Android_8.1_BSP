/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.Notification;
import android.app.Notification.MessagingStyle.Message;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.widget.RemoteViews;

import org.mockito.internal.matchers.Not;

public class NotificationTest extends AndroidTestCase {
    private static final String TEXT_RESULT_KEY = "text";
    private static final String DATA_RESULT_KEY = "data";
    private static final String DATA_AND_TEXT_RESULT_KEY = "data and text";

    private Notification.Action mAction;
    private Notification mNotification;
    private Context mContext;

    private static final String TICKER_TEXT = "tickerText";
    private static final String CONTENT_TITLE = "contentTitle";
    private static final String CONTENT_TEXT = "contentText";
    private static final String URI_STRING = "uriString";
    private static final String ACTION_TITLE = "actionTitle";
    private static final int TOLERANCE = 200;
    private static final long TIMEOUT = 4000;
    private static final NotificationChannel CHANNEL = new NotificationChannel("id", "name",
            NotificationManager.IMPORTANCE_HIGH);
    private static final String SHORTCUT_ID = "shortcutId";
    private static final String SETTING_TEXT = "work chats";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mNotification = new Notification();
    }

    public void testConstructor() {
        mNotification = null;
        mNotification = new Notification();
        assertNotNull(mNotification);
        assertTrue(System.currentTimeMillis() - mNotification.when < TOLERANCE);

        mNotification = null;
        final int notificationTime = 200;
        mNotification = new Notification(0, TICKER_TEXT, notificationTime);
        assertEquals(notificationTime, mNotification.when);
        assertEquals(0, mNotification.icon);
        assertEquals(TICKER_TEXT, mNotification.tickerText);
        assertEquals(0, mNotification.number);
    }

    public void testBuilderConstructor() {
        mNotification = new Notification.Builder(mContext, CHANNEL.getId()).build();
        assertEquals(CHANNEL.getId(), mNotification.getChannelId());
        assertEquals(Notification.BADGE_ICON_NONE, mNotification.getBadgeIconType());
        assertNull(mNotification.getShortcutId());
        assertEquals(Notification.GROUP_ALERT_ALL, mNotification.getGroupAlertBehavior());
        assertEquals((long) 0, mNotification.getTimeoutAfter());
    }

    public void testDescribeContents() {
        final int expected = 0;
        mNotification = new Notification();
        assertEquals(expected, mNotification.describeContents());
    }

    public void testWriteToParcel() {

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setBadgeIconType(Notification.BADGE_ICON_SMALL)
                .setShortcutId(SHORTCUT_ID)
                .setTimeoutAfter(TIMEOUT)
                .setSettingsText(SETTING_TEXT)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .build();
        mNotification.icon = 0;
        mNotification.number = 1;
        final Intent intent = new Intent();
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mNotification.contentIntent = pendingIntent;
        final Intent deleteIntent = new Intent();
        final PendingIntent delPendingIntent = PendingIntent.getBroadcast(
                mContext, 0, deleteIntent, 0);
        mNotification.deleteIntent = delPendingIntent;
        mNotification.tickerText = TICKER_TEXT;

        final RemoteViews contentView = new RemoteViews(mContext.getPackageName(),
                android.R.layout.simple_list_item_1);
        mNotification.contentView = contentView;
        mNotification.defaults = 0;
        mNotification.flags = 0;
        final Uri uri = Uri.parse(URI_STRING);
        mNotification.sound = uri;
        mNotification.audioStreamType = 0;
        final long[] longArray = { 1l, 2l, 3l };
        mNotification.vibrate = longArray;
        mNotification.ledARGB = 0;
        mNotification.ledOnMS = 0;
        mNotification.ledOffMS = 0;
        mNotification.iconLevel = 0;

        Parcel parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Test Notification(Parcel)
        Notification result = new Notification(parcel);
        assertEquals(mNotification.icon, result.icon);
        assertEquals(mNotification.when, result.when);
        assertEquals(mNotification.number, result.number);
        assertNotNull(result.contentIntent);
        assertNotNull(result.deleteIntent);
        assertEquals(mNotification.tickerText, result.tickerText);
        assertNotNull(result.contentView);
        assertEquals(mNotification.defaults, result.defaults);
        assertEquals(mNotification.flags, result.flags);
        assertNotNull(result.sound);
        assertEquals(mNotification.audioStreamType, result.audioStreamType);
        assertEquals(mNotification.vibrate[0], result.vibrate[0]);
        assertEquals(mNotification.vibrate[1], result.vibrate[1]);
        assertEquals(mNotification.vibrate[2], result.vibrate[2]);
        assertEquals(mNotification.ledARGB, result.ledARGB);
        assertEquals(mNotification.ledOnMS, result.ledOnMS);
        assertEquals(mNotification.ledOffMS, result.ledOffMS);
        assertEquals(mNotification.iconLevel, result.iconLevel);
        assertEquals(mNotification.getShortcutId(), result.getShortcutId());
        assertEquals(mNotification.getBadgeIconType(), result.getBadgeIconType());
        assertEquals(mNotification.getTimeoutAfter(), result.getTimeoutAfter());
        assertEquals(mNotification.getChannelId(), result.getChannelId());
        assertEquals(mNotification.getSettingsText(), result.getSettingsText());
        assertEquals(mNotification.getGroupAlertBehavior(), result.getGroupAlertBehavior());

        mNotification.contentIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentIntent);

        mNotification.deleteIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.deleteIntent);

        mNotification.tickerText = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.tickerText);

        mNotification.contentView = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentView);

        mNotification.sound = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.sound);
    }

    public void testColorizeNotification() {
        mNotification = new Notification.Builder(mContext, "channel_id")
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setColorized(true)
                .build();

        assertTrue(mNotification.extras.getBoolean(Notification.EXTRA_COLORIZED));
    }

    public void testBuilder() {
        final Intent intent = new Intent();
        final PendingIntent contentIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setContentText(CONTENT_TEXT)
                .setContentIntent(contentIntent)
                .setBadgeIconType(Notification.BADGE_ICON_SMALL)
                .setShortcutId(SHORTCUT_ID)
                .setTimeoutAfter(TIMEOUT)
                .setSettingsText(SETTING_TEXT)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .build();
        assertEquals(CONTENT_TEXT, mNotification.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(CONTENT_TITLE, mNotification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(1, mNotification.icon);
        assertEquals(contentIntent, mNotification.contentIntent);
        assertEquals(CHANNEL.getId(), mNotification.getChannelId());
        assertEquals(Notification.BADGE_ICON_SMALL, mNotification.getBadgeIconType());
        assertEquals(SHORTCUT_ID, mNotification.getShortcutId());
        assertEquals(TIMEOUT, mNotification.getTimeoutAfter());
        assertEquals(SETTING_TEXT, mNotification.getSettingsText());
        assertEquals(Notification.GROUP_ALERT_SUMMARY, mNotification.getGroupAlertBehavior());
    }

    public void testActionBuilder() {
        final Intent intent = new Intent();
        final PendingIntent actionIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mAction = null;
        mAction = new Notification.Action.Builder(0, ACTION_TITLE, actionIntent).build();
        assertEquals(ACTION_TITLE, mAction.title);
        assertEquals(actionIntent, mAction.actionIntent);
        assertEquals(true, mAction.getAllowGeneratedReplies());
    }

    public void testMessagingStyle_historicMessages() {
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setStyle(new Notification.MessagingStyle("self name")
                        .addMessage("text", 0, "sender")
                        .addMessage(new Message("image", 0, "sender")
                                .setData("image/png", Uri.parse("http://example.com/image.png")))
                        .addHistoricMessage(new Message("historic text", 0, "historic sender"))
                        .setConversationTitle("title")
                ).build();

        assertNotNull(
                mNotification.extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES));
    }

    public void testToString() {
        mNotification = new Notification();
        assertNotNull(mNotification.toString());
        mNotification = null;
    }

    public void testNotificationActionBuilder_setDataOnlyRemoteInput() throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput()).build();
        RemoteInput[] textInputs = a.getRemoteInputs();
        assertTrue(textInputs == null || textInputs.length == 0);
        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
    }

    public void testNotificationActionBuilder_setTextAndDataOnlyRemoteInput() throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput())
                .addRemoteInput(newTextRemoteInput())
                .build();

        verifyRemoteInputArrayHasSingleResult(a.getRemoteInputs(), TEXT_RESULT_KEY);
        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
    }

    public void testNotificationActionBuilder_setTextAndDataOnlyAndBothRemoteInput()
            throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput())
                .addRemoteInput(newTextRemoteInput())
                .addRemoteInput(newTextAndDataRemoteInput())
                .build();

        assertTrue(a.getRemoteInputs() != null && a.getRemoteInputs().length == 2);
        assertEquals(TEXT_RESULT_KEY, a.getRemoteInputs()[0].getResultKey());
        assertFalse(a.getRemoteInputs()[0].isDataOnly());
        assertEquals(DATA_AND_TEXT_RESULT_KEY, a.getRemoteInputs()[1].getResultKey());
        assertFalse(a.getRemoteInputs()[1].isDataOnly());

        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
        assertTrue(a.getDataOnlyRemoteInputs()[0].isDataOnly());
    }

    private static RemoteInput newDataOnlyRemoteInput() {
        return new RemoteInput.Builder(DATA_RESULT_KEY)
            .setAllowFreeFormInput(false)
            .setAllowDataType("mimeType", true)
            .build();
    }

    private static RemoteInput newTextAndDataRemoteInput() {
        return new RemoteInput.Builder(DATA_AND_TEXT_RESULT_KEY)
            .setAllowDataType("mimeType", true)
            .build();  // allowFreeForm defaults to true
    }

    private static RemoteInput newTextRemoteInput() {
        return new RemoteInput.Builder(TEXT_RESULT_KEY).build();  // allowFreeForm defaults to true
    }

    private static void verifyRemoteInputArrayHasSingleResult(
            RemoteInput[] remoteInputs, String expectedResultKey) {
        assertTrue(remoteInputs != null && remoteInputs.length == 1);
        assertEquals(expectedResultKey, remoteInputs[0].getResultKey());
    }

    private static Notification.Action.Builder newActionBuilder() {
        return new Notification.Action.Builder(0, "title", null);
    }
}
