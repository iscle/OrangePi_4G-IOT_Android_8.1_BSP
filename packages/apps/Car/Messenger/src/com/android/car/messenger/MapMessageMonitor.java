/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.SdpMasRecord;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.car.apps.common.LetterTileDrawable;
import com.android.car.messenger.tts.TTSHelper;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Monitors for incoming messages and posts/updates notifications.
 * <p>
 * It also handles notifications requests e.g. sending auto-replies and message play-out.
 * <p>
 * It will receive broadcasts for new incoming messages as long as the MapClient is connected in
 * {@link MessengerService}.
 */
class MapMessageMonitor {
    public static final String ACTION_MESSAGE_PLAY_START =
            "car.messenger.action_message_play_start";
    public static final String ACTION_MESSAGE_PLAY_STOP = "car.messenger.action_message_play_stop";
    // reply or "upload" feature is indicated by the 3rd bit
    private static final int REPLY_FEATURE_POS = 3;

    private static final int REQUEST_CODE_VOICE_PLATE = 1;
    private static final int REQUEST_CODE_AUTO_REPLY = 2;
    private static final int ACTION_COUNT = 2;
    private static final String TAG = "Messenger.MsgMonitor";
    private static final boolean DBG = MessengerService.DBG;

    private final Context mContext;
    private final BluetoothMapReceiver mBluetoothMapReceiver;
    private final BluetoothSdpReceiver mBluetoothSdpReceiver;
    private final NotificationManager mNotificationManager;
    private final Map<MessageKey, MapMessage> mMessages = new HashMap<>();
    private final Map<SenderKey, NotificationInfo> mNotificationInfos = new HashMap<>();
    private final TTSHelper mTTSHelper;
    private final HashMap<String, Boolean> mReplyFeatureMap = new HashMap<>();
    private final AudioManager mAudioManager;
    private final AudioManager.OnAudioFocusChangeListener mNoOpAFChangeListener = (f) -> {};

    MapMessageMonitor(Context context) {
        mContext = context;
        mBluetoothMapReceiver = new BluetoothMapReceiver();
        mBluetoothSdpReceiver = new BluetoothSdpReceiver();
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mTTSHelper = new TTSHelper(mContext);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean isPlaying() {
        return mTTSHelper.isSpeaking();
    }

    private void handleNewMessage(Intent intent) {
        if (DBG) {
            Log.d(TAG, "Handling new message");
        }
        try {
            MapMessage message = MapMessage.parseFrom(intent);
            if (MessengerService.DBG) {
                Log.v(TAG, "Parsed message: " + message);
            }
            MessageKey messageKey = new MessageKey(message);
            boolean repeatMessage = mMessages.containsKey(messageKey);
            mMessages.put(messageKey, message);
            if (!repeatMessage) {
                updateNotificationInfo(message, messageKey);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Dropping invalid MAP message", e);
        }
    }

    private void updateNotificationInfo(MapMessage message, MessageKey messageKey) {
        SenderKey senderKey = new SenderKey(message);
        // check the version/feature of the
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        adapter.getRemoteDevice(senderKey.mDeviceAddress).sdpSearch(BluetoothUuid.MAS);

        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            notificationInfo =
                    new NotificationInfo(message.getSenderName(), message.getSenderContactUri());
            mNotificationInfos.put(senderKey, notificationInfo);
        }
        notificationInfo.mMessageKeys.add(messageKey);
        updateNotificationFor(senderKey, notificationInfo);
    }

    private static final String[] CONTACT_ID = new String[] {
            ContactsContract.PhoneLookup._ID
    };

    private static int getContactIdFromName(ContentResolver cr, String name) {
        if (DBG) {
            Log.d(TAG, "getting contactId for: " + name);
        }
        if (TextUtils.isEmpty(name)) {
            return 0;
        }

        String[] mSelectionArgs = { name };

        Cursor cursor =
                cr.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACT_ID,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?",
                        mSelectionArgs,
                        null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
                return id;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    private void updateNotificationFor(SenderKey senderKey, NotificationInfo notificationInfo) {
        if (DBG) {
            Log.d(TAG, "updateNotificationFor" + notificationInfo);
        }
        String contentText = mContext.getResources().getQuantityString(
                R.plurals.notification_new_message, notificationInfo.mMessageKeys.size(),
                notificationInfo.mMessageKeys.size());
        long lastReceivedTimeMs =
                mMessages.get(notificationInfo.mMessageKeys.getLast()).getReceivedTimeMs();

        Uri photoUri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI, getContactIdFromName(
                        mContext.getContentResolver(), notificationInfo.mSenderName));
        if (DBG) {
            Log.d(TAG, "start Glide loading... " + photoUri);
        }
        Glide.with(mContext)
                .asBitmap()
                .load(photoUri)
                .apply(RequestOptions.circleCropTransform())
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap bitmap,
                            Transition<? super Bitmap> transition) {
                        sendNotification(bitmap);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable fallback) {
                        sendNotification(null);
                    }

                    private void sendNotification(Bitmap bitmap) {
                        if (DBG) {
                            Log.d(TAG, "Glide loaded. " + bitmap);
                        }
                        if (bitmap == null) {
                            LetterTileDrawable letterTileDrawable =
                                    new LetterTileDrawable(mContext.getResources());
                            letterTileDrawable.setContactDetails(
                                    notificationInfo.mSenderName, notificationInfo.mSenderName);
                            letterTileDrawable.setIsCircular(true);
                            bitmap = letterTileDrawable.toBitmap(
                                    mContext.getResources().getDimensionPixelSize(
                                            R.dimen.notification_contact_photo_size));
                        }
                        PendingIntent LaunchPlayMessageActivityIntent = PendingIntent.getActivity(
                                mContext,
                                REQUEST_CODE_VOICE_PLATE,
                                getPlayMessageIntent(senderKey, notificationInfo),
                                0);

                        Notification.Builder builder = new Notification.Builder(
                                mContext, NotificationChannel.DEFAULT_CHANNEL_ID)
                                        .setContentIntent(LaunchPlayMessageActivityIntent)
                                        .setLargeIcon(bitmap)
                                        .setSmallIcon(R.drawable.ic_message)
                                        .setContentTitle(notificationInfo.mSenderName)
                                        .setContentText(contentText)
                                        .setWhen(lastReceivedTimeMs)
                                        .setShowWhen(true)
                                        .setActions(getActionsFor(senderKey, notificationInfo))
                                        .setDeleteIntent(buildIntentFor(
                                                MessengerService.ACTION_CLEAR_NOTIFICATION_STATE,
                                                senderKey, notificationInfo));
                        if (notificationInfo.muted) {
                            builder.setPriority(Notification.PRIORITY_MIN);
                        } else {
                            builder.setPriority(Notification.PRIORITY_HIGH)
                                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI);
                        }
                        mNotificationManager.notify(
                                notificationInfo.mNotificationId, builder.build());
                    }
                });
    }

    private Intent getPlayMessageIntent(SenderKey senderKey, NotificationInfo notificationInfo) {
        Intent intent = new Intent(mContext, PlayMessageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PlayMessageActivity.EXTRA_MESSAGE_KEY, senderKey);
        intent.putExtra(
                PlayMessageActivity.EXTRA_SENDER_NAME,
                notificationInfo.mSenderName);
        if (!supportsReply(senderKey.mDeviceAddress)) {
            intent.putExtra(
                    PlayMessageActivity.EXTRA_REPLY_DISABLED_FLAG,
                    true);
        }
        return intent;
    }

    private boolean supportsReply(String deviceAddress) {
        return mReplyFeatureMap.containsKey(deviceAddress)
                && mReplyFeatureMap.get(deviceAddress);
    }

    private Notification.Action[] getActionsFor(
            SenderKey senderKey,
            NotificationInfo notificationInfo) {
        // Icon doesn't appear to be used; using fixed icon for all actions.
        final Icon icon = Icon.createWithResource(mContext, android.R.drawable.ic_media_play);

        List<Notification.Action.Builder> builders = new ArrayList<>(ACTION_COUNT);

        // show auto reply options of device supports it
        if (supportsReply(senderKey.mDeviceAddress)) {
            Intent replyIntent = getPlayMessageIntent(senderKey, notificationInfo);
            replyIntent.putExtra(PlayMessageActivity.EXTRA_SHOW_REPLY_LIST_FLAG, true);
            PendingIntent autoReplyIntent = PendingIntent.getActivity(
                    mContext, REQUEST_CODE_AUTO_REPLY, replyIntent, 0);
            builders.add(new Notification.Action.Builder(icon,
                    mContext.getString(R.string.action_reply), autoReplyIntent));
        }

        // add mute/unmute.
        if (notificationInfo.muted) {
            PendingIntent muteIntent = buildIntentFor(MessengerService.ACTION_UNMUTE_CONVERSATION,
                    senderKey, notificationInfo);
            builders.add(new Notification.Action.Builder(icon,
                    mContext.getString(R.string.action_unmute), muteIntent));
        } else {
            PendingIntent muteIntent = buildIntentFor(MessengerService.ACTION_MUTE_CONVERSATION,
                    senderKey, notificationInfo);
            builders.add(new Notification.Action.Builder(icon,
                    mContext.getString(R.string.action_mute), muteIntent));
        }

        Notification.Action actions[] = new Notification.Action[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            actions[i] = builders.get(i).build();
        }
        return actions;
    }

    private PendingIntent buildIntentFor(String action, SenderKey senderKey,
            NotificationInfo notificationInfo) {
        Intent intent = new Intent(mContext, MessengerService.class)
                .setAction(action)
                .putExtra(MessengerService.EXTRA_SENDER_KEY, senderKey);
        return PendingIntent.getService(mContext,
                notificationInfo.mNotificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    void clearNotificationState(SenderKey senderKey) {
        if (DBG) {
            Log.d(TAG, "Clearing notification state for: " + senderKey);
        }
        mNotificationInfos.remove(senderKey);
    }

    void playMessages(SenderKey senderKey) {
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            Log.e(TAG, "Unknown senderKey! " + senderKey);
            return;
        }
        List<CharSequence> ttsMessages = new ArrayList<>();
        // TODO: play unread messages instead of the last.
        String ttsMessage =
                notificationInfo.mMessageKeys.stream().map((key) -> mMessages.get(key).getText())
                        .collect(Collectors.toCollection(LinkedList::new)).getLast();
        // Insert something like "foo says" before their message content.
        ttsMessages.add(mContext.getString(R.string.tts_sender_says, notificationInfo.mSenderName));
        ttsMessages.add(ttsMessage);

        int result = mAudioManager.requestAudioFocus(mNoOpAFChangeListener,
                // Use the music stream.
                AudioManager.STREAM_MUSIC,
                // Request permanent focus.
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mTTSHelper.requestPlay(ttsMessages,
                    new TTSHelper.Listener() {
                        @Override
                        public void onTTSStarted() {
                            Intent intent = new Intent(ACTION_MESSAGE_PLAY_START);
                            mContext.sendBroadcast(intent);
                        }

                        @Override
                        public void onTTSStopped(boolean error) {
                            mAudioManager.abandonAudioFocus(mNoOpAFChangeListener);
                            Intent intent = new Intent(ACTION_MESSAGE_PLAY_STOP);
                            mContext.sendBroadcast(intent);
                            if (error) {
                                Toast.makeText(mContext, R.string.tts_failed_toast,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            Log.w(TAG, "failed to require audio focus.");
        }
    }

    void stopPlayout() {
        mTTSHelper.requestStop();
    }

    void toggleMuteConversation(SenderKey senderKey, boolean mute) {
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            Log.e(TAG, "Unknown senderKey! " + senderKey);
            return;
        }
        notificationInfo.muted = mute;
        updateNotificationFor(senderKey, notificationInfo);
    }

    boolean sendAutoReply(SenderKey senderKey, BluetoothMapClient mapClient, String message) {
        if (DBG) {
            Log.d(TAG, "Sending auto-reply to: " + senderKey);
        }
        BluetoothDevice device =
                BluetoothAdapter.getDefaultAdapter().getRemoteDevice(senderKey.mDeviceAddress);
        NotificationInfo notificationInfo = mNotificationInfos.get(senderKey);
        if (notificationInfo == null) {
            Log.w(TAG, "No notificationInfo found for senderKey: " + senderKey);
            return false;
        }
        if (notificationInfo.mSenderContactUri == null) {
            Log.w(TAG, "Do not have contact URI for sender!");
            return false;
        }
        Uri recipientUris[] = { Uri.parse(notificationInfo.mSenderContactUri) };

        final int requestCode = senderKey.hashCode();
        PendingIntent sentIntent =
                PendingIntent.getBroadcast(mContext, requestCode, new Intent(
                                BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY),
                        PendingIntent.FLAG_ONE_SHOT);
        return mapClient.sendMessage(device, recipientUris, message, sentIntent, null);
    }

    void handleMapDisconnect() {
        cleanupMessagesAndNotifications((key) -> true);
    }

    void handleDeviceDisconnect(BluetoothDevice device) {
        cleanupMessagesAndNotifications((key) -> key.matches(device.getAddress()));
    }

    private void cleanupMessagesAndNotifications(Predicate<CompositeKey> predicate) {
        Iterator<Map.Entry<MessageKey, MapMessage>> messageIt = mMessages.entrySet().iterator();
        while (messageIt.hasNext()) {
            if (predicate.test(messageIt.next().getKey())) {
                messageIt.remove();
            }
        }
        Iterator<Map.Entry<SenderKey, NotificationInfo>> notificationIt =
                mNotificationInfos.entrySet().iterator();
        while (notificationIt.hasNext()) {
            Map.Entry<SenderKey, NotificationInfo> entry = notificationIt.next();
            if (predicate.test(entry.getKey())) {
                mNotificationManager.cancel(entry.getValue().mNotificationId);
                notificationIt.remove();
            }
        }
    }

    void cleanup() {
        mBluetoothMapReceiver.cleanup();
        mBluetoothSdpReceiver.cleanup();
        mTTSHelper.cleanup();
    }

    private class BluetoothSdpReceiver extends BroadcastReceiver {
        BluetoothSdpReceiver() {
            if (DBG) {
                Log.d(TAG, "Registering receiver for sdp");
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothDevice.ACTION_SDP_RECORD);
            mContext.registerReceiver(this, intentFilter);
        }

        void cleanup() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_SDP_RECORD.equals(intent.getAction())) {
                if (DBG) {
                    Log.d(TAG, "get SDP record: " + intent.getExtras());
                }
                Parcelable parcelable = intent.getParcelableExtra(BluetoothDevice.EXTRA_SDP_RECORD);
                if (!(parcelable instanceof SdpMasRecord)) {
                    if (DBG) {
                        Log.d(TAG, "not SdpMasRecord: " + parcelable);
                    }
                    return;
                }
                SdpMasRecord masRecord = (SdpMasRecord) parcelable;
                int features = masRecord.getSupportedFeatures();
                int version = masRecord.getProfileVersion();
                boolean supportsReply = false;
                // we only consider the device supports reply feature of the version
                // is higher than 1.02 and the feature flag is turned on.
                if (version >= 0x102 && isOn(features, REPLY_FEATURE_POS)) {
                    supportsReply = true;
                }
                BluetoothDevice bluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mReplyFeatureMap.put(bluetoothDevice.getAddress(), supportsReply);
            } else {
                Log.w(TAG, "Ignoring unknown broadcast " + intent.getAction());
            }
        }

        private boolean isOn(int input, int postion) {
            return ((input >> postion) & 1) == 1;
        }
    }

    // Used to monitor for new incoming messages and sent-message broadcast.
    private class BluetoothMapReceiver extends BroadcastReceiver {
        BluetoothMapReceiver() {
            if (DBG) {
                Log.d(TAG, "Registering receiver for bluetooth MAP");
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY);
            intentFilter.addAction(BluetoothMapClient.ACTION_MESSAGE_RECEIVED);
            mContext.registerReceiver(this, intentFilter);
        }

        void cleanup() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothMapClient.ACTION_MESSAGE_SENT_SUCCESSFULLY.equals(intent.getAction())) {
                if (DBG) {
                    Log.d(TAG, "SMS was sent successfully!");
                }
            } else if (BluetoothMapClient.ACTION_MESSAGE_RECEIVED.equals(intent.getAction())) {
                if (DBG) {
                    Log.d(TAG, "SMS message received");
                }
                handleNewMessage(intent);
            } else {
                Log.w(TAG, "Ignoring unknown broadcast " + intent.getAction());
            }
        }
    }

    /**
     * Key used in HashMap that is composed from a BT device-address and device-specific "sub key"
     */
    private abstract static class CompositeKey {
        final String mDeviceAddress;
        final String mSubKey;

        CompositeKey(String deviceAddress, String subKey) {
            mDeviceAddress = deviceAddress;
            mSubKey = subKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CompositeKey that = (CompositeKey) o;
            return Objects.equals(mDeviceAddress, that.mDeviceAddress)
                    && Objects.equals(mSubKey, that.mSubKey);
        }

        boolean matches(String deviceAddress) {
            return mDeviceAddress.equals(deviceAddress);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeviceAddress, mSubKey);
        }

        @Override
        public String toString() {
            return String.format("%s, deviceAddress: %s, subKey: %s",
                    getClass().getSimpleName(), mDeviceAddress, mSubKey);
        }
    }

    /**
     * {@link CompositeKey} subclass used to identify specific messages; it uses message-handle as
     * the secondary key.
     */
    private static class MessageKey extends CompositeKey {
        MessageKey(MapMessage message) {
            super(message.getDevice().getAddress(), message.getHandle());
        }
    }

    /**
     * CompositeKey used to identify Notification info for a sender; it uses a combination of
     * senderContactUri and senderContactName as the secondary key.
     */
    static class SenderKey extends CompositeKey implements Parcelable {
        private SenderKey(String deviceAddress, String key) {
            super(deviceAddress, key);
        }

        SenderKey(MapMessage message) {
            // Use a combination of senderName and senderContactUri for key. Ideally we would use
            // only senderContactUri (which is encoded phone no.). However since some phones don't
            // provide these, we fall back to senderName. Since senderName may not be unique, we
            // include senderContactUri also to provide uniqueness in cases it is available.
            this(message.getDevice().getAddress(),
                    message.getSenderName() + "/" + message.getSenderContactUri());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mDeviceAddress);
            dest.writeString(mSubKey);
        }

        public static final Parcelable.Creator<SenderKey> CREATOR =
                new Parcelable.Creator<SenderKey>() {
                    @Override
                    public SenderKey createFromParcel(Parcel source) {
                        return new SenderKey(source.readString(), source.readString());
                    }

                    @Override
                    public SenderKey[] newArray(int size) {
                        return new SenderKey[size];
                    }
                };
    }

    /**
     * Information about a single notification that is displayed.
     */
    private static class NotificationInfo {
        private static int NEXT_NOTIFICATION_ID = 0;

        final int mNotificationId = NEXT_NOTIFICATION_ID++;
        final String mSenderName;
        @Nullable
        final String mSenderContactUri;
        final LinkedList<MessageKey> mMessageKeys = new LinkedList<>();
        boolean muted = false;

        NotificationInfo(String senderName, @Nullable String senderContactUri) {
            mSenderName = senderName;
            mSenderContactUri = senderContactUri;
        }
    }
}
