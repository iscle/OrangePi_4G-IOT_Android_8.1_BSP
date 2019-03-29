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
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.R;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony.Threads;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationManagerTest extends AndroidTestCase {
    final String TAG = NotificationManagerTest.class.getSimpleName();
    final boolean DEBUG = false;
    final String NOTIFICATION_CHANNEL_ID = "NotificationManagerTest";

    private NotificationManager mNotificationManager;
    private String mId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This will leave a set of channels on the device with each test run.
        mId = UUID.randomUUID().toString();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", NotificationManager.IMPORTANCE_DEFAULT));
        // delay between tests so notifications aren't dropped by the rate limiter
        try {
            Thread.sleep(500);
        } catch(InterruptedException e) {}
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mNotificationManager.cancelAll();
        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
        // Delete all channels.
        for (NotificationChannel nc : channels) {
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            mNotificationManager.deleteNotificationChannel(nc.getId());
        }
    }

    public void testCreateChannelGroup() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(ncg.getId());
        mNotificationManager.createNotificationChannelGroup(ncg);
        try {
            mNotificationManager.createNotificationChannel(channel);

            List<NotificationChannelGroup> ncgs =
                    mNotificationManager.getNotificationChannelGroups();
            assertEquals(1, ncgs.size());
            assertEquals(ncg, ncgs.get(0));
        } finally {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    public void testDeleteChannelGroup() throws Exception {
        final NotificationChannelGroup ncg = new NotificationChannelGroup("a group", "a label");
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup(ncg.getId());
        mNotificationManager.createNotificationChannelGroup(ncg);
        mNotificationManager.createNotificationChannel(channel);

        mNotificationManager.deleteNotificationChannelGroup(ncg.getId());

        assertNull(mNotificationManager.getNotificationChannel(channel.getId()));
        assertEquals(0, mNotificationManager.getNotificationChannelGroups().size());
    }

    public void testCreateChannel() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("bananas");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] {5, 8, 2, 1});
        channel.setSound(new Uri.Builder().scheme("test").build(),
                new AudioAttributes.Builder().setUsage(
                        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED).build());
        channel.enableLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);
        // Lockscreen Visibility and canBypassDnd no longer settable.
        assertTrue(createdChannel.getLockscreenVisibility() != Notification.VISIBILITY_SECRET);
        assertFalse(createdChannel.canBypassDnd());
    }

    public void testCreateChannel_rename() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        channel.setName("new name");
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);

        channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channel);
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT,
                mNotificationManager.getNotificationChannel(mId).getImportance());
    }

    public void testCreateSameChannelDoesNotUpdate() throws Exception {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        final NotificationChannel channelDupe =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channelDupe);
        final NotificationChannel createdChannel =
                mNotificationManager.getNotificationChannel(mId);
        compareChannels(channel, createdChannel);
    }

    public void testCreateChannelAlreadyExistsNoOp() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
        NotificationChannel channelDupe =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channelDupe);
        compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
    }

    public void testCreateChannelWithGroup() throws Exception {
        NotificationChannelGroup ncg = new NotificationChannelGroup("g", "n");
        mNotificationManager.createNotificationChannelGroup(ncg);
        try {
            NotificationChannel channel =
                    new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setGroup(ncg.getId());
            mNotificationManager.createNotificationChannel(channel);
            compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        } finally {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    public void testCreateChannelWithBadGroup() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setGroup("garbage");
        try {
            mNotificationManager.createNotificationChannel(channel);
            fail("Created notification with bad group");
        } catch (IllegalArgumentException e) {}
    }

    public void testCreateChannelInvalidImportance() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_UNSPECIFIED);
        try {
            mNotificationManager.createNotificationChannel(channel);
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testDeleteChannel() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(channel);
        compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        mNotificationManager.deleteNotificationChannel(channel.getId());
        assertNull(mNotificationManager.getNotificationChannel(channel.getId()));
    }

    public void testCannotDeleteDefaultChannel() throws Exception {
        try {
            mNotificationManager.deleteNotificationChannel(NotificationChannel.DEFAULT_CHANNEL_ID);
            fail("Deleted default channel");
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testGetChannel() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", NotificationManager.IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel1);
        mNotificationManager.createNotificationChannel(channel2);
        mNotificationManager.createNotificationChannel(channel3);
        mNotificationManager.createNotificationChannel(channel4);

        compareChannels(channel2,
                mNotificationManager.getNotificationChannel(channel2.getId()));
        compareChannels(channel3,
                mNotificationManager.getNotificationChannel(channel3.getId()));
        compareChannels(channel1,
                mNotificationManager.getNotificationChannel(channel1.getId()));
        compareChannels(channel4,
                mNotificationManager.getNotificationChannel(channel4.getId()));
    }

    public void testGetChannels() throws Exception {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", NotificationManager.IMPORTANCE_MIN);

        Map<String, NotificationChannel> channelMap = new HashMap<>();
        channelMap.put(channel1.getId(), channel1);
        channelMap.put(channel2.getId(), channel2);
        channelMap.put(channel3.getId(), channel3);
        channelMap.put(channel4.getId(), channel4);
        mNotificationManager.createNotificationChannel(channel1);
        mNotificationManager.createNotificationChannel(channel2);
        mNotificationManager.createNotificationChannel(channel3);
        mNotificationManager.createNotificationChannel(channel4);

        mNotificationManager.deleteNotificationChannel(channel3.getId());

        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
        for (NotificationChannel nc : channels) {
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            if (NOTIFICATION_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            assertFalse(channel3.getId().equals(nc.getId()));
            if (!channelMap.containsKey(nc.getId())) {
                // failed cleanup from prior test run; ignore
                continue;
            }
            compareChannels(channelMap.get(nc.getId()), nc);
        }
    }

    public void testRecreateDeletedChannel() throws Exception {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setShowBadge(true);
        NotificationChannel newChannel = new NotificationChannel(
                channel.getId(), channel.getName(), NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(channel);
        mNotificationManager.deleteNotificationChannel(channel.getId());

        mNotificationManager.createNotificationChannel(newChannel);

        compareChannels(channel,
                mNotificationManager.getNotificationChannel(newChannel.getId()));
    }

    public void testNotify() throws Exception {
        mNotificationManager.cancelAll();

        final int id = 1;
        sendNotification(id, R.drawable.black);
        // test updating the same notification
        sendNotification(id, R.drawable.blue);
        sendNotification(id, R.drawable.yellow);

        // assume that sendNotification tested to make sure individual notifications were present
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() != id) {
                fail("we got back other notifications besides the one we posted: "
                        + sbn.getKey());
            }
        }
    }

    public void testCancel() throws Exception {
        final int id = 9;
        sendNotification(id, R.drawable.black);
        mNotificationManager.cancel(id);

        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("canceled notification was still alive, id=" + id);
        }
    }

    public void testCancelAll() throws Exception {
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);

        if (DEBUG) {
            Log.d(TAG, "posted 3 notifications, here they are: ");
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(TAG, "  " + sbn);
            }
            Log.d(TAG, "about to cancel...");
        }
        mNotificationManager.cancelAll();

        for (int id = 1; id <= 3; id++) {
            if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
                fail("Failed to cancel notification id=" + id);
            }
        }

    }

    public void testNotifyWithTimeout() throws Exception {
        mNotificationManager.cancelAll();
        final int id = 128;
        final long timeout = 1000;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setTimeoutAfter(timeout)
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }

        try {
            Thread.sleep(timeout);
        } catch (InterruptedException ex) {
            // pass
        }
        checkNotificationExistence(id, false);
    }

    public void testMediaStyle() throws Exception {
        mNotificationManager.cancelAll();
        final int id = 99;
        MediaSession session = new MediaSession(getContext(), "media");

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "play", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "pause", getPendingIntent()).build())
                        .setStyle(new Notification.MediaStyle()
                                .setShowActionsInCompactView(0, 1)
                                .setMediaSession(session.getSessionToken()))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testInboxStyle() throws Exception {
        final int id = 100;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.InboxStyle().addLine("line")
                                .setSummaryText("summary"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testBigTextStyle() throws Exception {
        final int id = 101;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.BigTextStyle()
                                .setBigContentTitle("big title")
                                .bigText("big text")
                                .setSummaryText("summary"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testBigPictureStyle() throws Exception {
        final int id = 102;

        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.black)
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_black),
                                "a1", getPendingIntent()).build())
                        .addAction(new Notification.Action.Builder(
                                Icon.createWithResource(getContext(), R.drawable.icon_blue),
                                "a2", getPendingIntent()).build())
                        .setStyle(new Notification.BigPictureStyle()
                        .setBigContentTitle("title")
                        .bigPicture(Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565))
                        .bigLargeIcon(Icon.createWithResource(getContext(), R.drawable.icon_blue))
                        .setSummaryText("summary"))
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    public void testAutogrouping() throws Exception {
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);
        sendNotification(4, R.drawable.yellow);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();
    }

    public void testAutogrouping_autogroupStaysUntilAllNotificationsCanceled() throws Exception {
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);
        sendNotification(4, R.drawable.yellow);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // Assert all notis stay in the same autogroup until all children are canceled
        for (int i = 4; i > 1; i--) {
            cancelAndPoll(i);
            assertNotificationCount(i);
            assertAllPostedNotificationsAutogrouped();
        }
        cancelAndPoll(1);
        assertNotificationCount(0);
    }

    public void testAutogrouping_autogroupStaysUntilAllNotificationsAddedToGroup()
            throws Exception {
        String newGroup = "new!";
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);
        sendNotification(4, R.drawable.yellow);

        List<Integer> postedIds = new ArrayList<>();
        postedIds.add(1);
        postedIds.add(2);
        postedIds.add(3);
        postedIds.add(4);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // Assert all notis stay in the same autogroup until all children are canceled
        for (int i = 4; i > 1; i--) {
            sendNotification(i, newGroup, R.drawable.blue);
            postedIds.remove(postedIds.size() - 1);
            assertNotificationCount(5);
            assertOnlySomeNotificationsAutogrouped(postedIds);
        }
        sendNotification(1, newGroup, R.drawable.blue);
        assertNotificationCount(4); // no more autogroup summary
        postedIds.remove(0);
        assertOnlySomeNotificationsAutogrouped(postedIds);
    }

    public void testNewNotificationsAddedToAutogroup_ifOriginalNotificationsCanceled()
        throws Exception {
        String newGroup = "new!";
        sendNotification(10, R.drawable.black);
        sendNotification(20, R.drawable.blue);
        sendNotification(30, R.drawable.yellow);
        sendNotification(40, R.drawable.yellow);

        List<Integer> postedIds = new ArrayList<>();
        postedIds.add(10);
        postedIds.add(20);
        postedIds.add(30);
        postedIds.add(40);

        assertNotificationCount(5);
        assertAllPostedNotificationsAutogrouped();

        // regroup all but one of the children
        for (int i = postedIds.size() - 1; i > 0; i--) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                // pass
            }
            int id = postedIds.remove(i);
            sendNotification(id, newGroup, R.drawable.blue);
            assertNotificationCount(5);
            assertOnlySomeNotificationsAutogrouped(postedIds);
        }

        // send a new non-grouped notification. since the autogroup summary still exists,
        // the notification should be added to it
        sendNotification(50, R.drawable.blue);
        postedIds.add(50);
        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            // pass
        }
        assertOnlySomeNotificationsAutogrouped(postedIds);
    }

    private PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(
                getContext(), 0, new Intent(getContext(), this.getClass()), 0);
    }

    private boolean isGroupSummary(Notification n) {
        return n.getGroup() != null && (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
    }

    private void assertOnlySomeNotificationsAutogrouped(List<Integer> autoGroupedIds) {
        String expectedGroupKey = null;
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (isGroupSummary(sbn.getNotification())
                    || autoGroupedIds.contains(sbn.getId())) {
                assertTrue(sbn.getKey() + " is unexpectedly not autogrouped",
                        sbn.getOverrideGroupKey() != null);
                if (expectedGroupKey == null) {
                    expectedGroupKey = sbn.getGroupKey();
                }
                assertEquals(expectedGroupKey, sbn.getGroupKey());
            } else {
                assertTrue(sbn.isGroup());
                assertTrue(sbn.getKey() + " is unexpectedly autogrouped,",
                        sbn.getOverrideGroupKey() == null);
                assertTrue(sbn.getKey() + " has an unusual group key",
                        sbn.getGroupKey() != expectedGroupKey);
            }
        }
    }

    private void assertAllPostedNotificationsAutogrouped() {
        String expectedGroupKey = null;
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            // all notis should be in a group determined by autogrouping
            assertTrue(sbn.getOverrideGroupKey() != null);
            if (expectedGroupKey == null) {
                expectedGroupKey = sbn.getGroupKey();
            }
            // all notis should be in the same group
            assertEquals(expectedGroupKey, sbn.getGroupKey());
        }
    }

    private void cancelAndPoll(int id) {
        mNotificationManager.cancel(id);

        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("canceled notification was still alive, id=" + 1);
        }
    }

    private void sendNotification(final int id, final int icon) throws Exception {
        sendNotification(id, null, icon);
    }

    private void sendNotification(final int id, String groupKey, final int icon) throws Exception {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(icon)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setContentIntent(pendingIntent)
                        .setGroup(groupKey)
                        .build();
        mNotificationManager.notify(id, notification);

        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    private boolean checkNotificationExistence(int id, boolean shouldExist) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to 300ms before giving up
        boolean found = false;
        for (int tries = 3; tries--> 0;) {
            // Need reset flag.
            found = false;
            final StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (found == shouldExist) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return found == shouldExist;
    }

    private void assertNotificationCount(int expectedCount) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to 400ms before giving up
        int lastCount = 0;
        for (int tries = 4; tries-- > 0;) {
            final StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            lastCount = sbns.length;
            if (expectedCount == lastCount) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        fail("Expected " + expectedCount + " posted notifications, were " +  lastCount);
    }

    private void compareChannels(NotificationChannel expected, NotificationChannel actual) {
        if (actual == null) {
            fail("actual channel is null");
            return;
        }
        if (expected == null) {
            fail("expected channel is null");
            return;
        }
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.shouldVibrate(), actual.shouldVibrate());
        assertEquals(expected.shouldShowLights(), actual.shouldShowLights());
        assertEquals(expected.getImportance(), actual.getImportance());
        if (expected.getSound() == null) {
            assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, actual.getSound());
            assertEquals(Notification.AUDIO_ATTRIBUTES_DEFAULT, actual.getAudioAttributes());
        } else {
            assertEquals(expected.getSound(), actual.getSound());
            assertEquals(expected.getAudioAttributes(), actual.getAudioAttributes());
        }
        assertTrue(Arrays.equals(expected.getVibrationPattern(), actual.getVibrationPattern()));
        assertEquals(expected.getGroup(), actual.getGroup());
    }
}
