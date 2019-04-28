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

package com.android.tv.tuner.tvinput;

import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.util.ConvertUtils;
import com.android.tv.util.PermissionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the channel info and EPG data through {@link TvInputManager}.
 */
public class ChannelDataManager implements Handler.Callback {
    private static final String TAG = "ChannelDataManager";

    private static final String[] ALL_PROGRAMS_SELECTION_ARGS = new String[] {
            TvContract.Programs._ID,
            TvContract.Programs.COLUMN_TITLE,
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
            TvContract.Programs.COLUMN_CONTENT_RATING,
            TvContract.Programs.COLUMN_BROADCAST_GENRE,
            TvContract.Programs.COLUMN_CANONICAL_GENRE,
            TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
            TvContract.Programs.COLUMN_VERSION_NUMBER };
    private static final String[] CHANNEL_DATA_SELECTION_ARGS = new String[] {
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
            TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG1};

    private static final int MSG_HANDLE_EVENTS = 1;
    private static final int MSG_HANDLE_CHANNEL = 2;
    private static final int MSG_BUILD_CHANNEL_MAP = 3;
    private static final int MSG_REQUEST_PROGRAMS = 4;
    private static final int MSG_CLEAR_CHANNELS = 6;
    private static final int MSG_CHECK_VERSION = 7;

    // Throttle the batch operations to avoid TransactionTooLargeException.
    private static final int BATCH_OPERATION_COUNT = 100;
    // At most 16 days of program information is delivered through an EIT,
    // according to the Chapter 6.4 of ATSC Recommended Practice A/69.
    private static final long PROGRAM_QUERY_DURATION = TimeUnit.DAYS.toMillis(16);

    /**
     * A version number to enforce consistency of the channel data.
     *
     * WARNING: If a change in the database serialization lead to breaking the backward
     * compatibility, you must increment this value so that the old data are purged,
     * and the user is requested to perform the auto-scan again to generate the new data set.
     */
    private static final int VERSION = 6;

    private final Context mContext;
    private final String mInputId;
    private ProgramInfoListener mListener;
    private ChannelScanListener mChannelScanListener;
    private Handler mChannelScanHandler;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final ConcurrentHashMap<Long, TunerChannel> mTunerChannelMap;
    private final ConcurrentSkipListMap<TunerChannel, Long> mTunerChannelIdMap;
    private final Uri mChannelsUri;

    // Used for scanning
    private final ConcurrentSkipListSet<TunerChannel> mScannedChannels;
    private final ConcurrentSkipListSet<TunerChannel> mPreviousScannedChannels;
    private final AtomicBoolean mIsScanning;
    private final AtomicBoolean scanCompleted = new AtomicBoolean();

    public interface ProgramInfoListener {

        /**
         * Invoked when a request for getting programs of a channel has been processed and passes
         * the requested channel and the programs retrieved from database to the listener.
         */
        void onRequestProgramsResponse(TunerChannel channel, List<EitItem> programs);

        /**
         * Invoked when programs of a channel have been arrived and passes the arrived channel and
         * programs to the listener.
         */
        void onProgramsArrived(TunerChannel channel, List<EitItem> programs);

        /**
         * Invoked when a channel has been arrived and passes the arrived channel to the listener.
         */
        void onChannelArrived(TunerChannel channel);

        /**
         * Invoked when the database schema has been changed and the old-format channels have been
         * deleted. A receiver should notify to a user that re-scanning channels is necessary.
         */
        void onRescanNeeded();
    }

    public interface ChannelScanListener {
        /**
         * Invoked when all pending channels have been handled.
         */
        void onChannelHandlingDone();
    }

    public ChannelDataManager(Context context) {
        mContext = context;
        mInputId = TvContract.buildInputId(new ComponentName(mContext.getPackageName(),
                TunerTvInputService.class.getName()));
        mChannelsUri = TvContract.buildChannelsUriForInput(mInputId);
        mTunerChannelMap = new ConcurrentHashMap<>();
        mTunerChannelIdMap = new ConcurrentSkipListMap<>();
        mHandlerThread = new HandlerThread("TvInputServiceBackgroundThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper(), this);
        mIsScanning = new AtomicBoolean();
        mScannedChannels = new ConcurrentSkipListSet<>();
        mPreviousScannedChannels = new ConcurrentSkipListSet<>();
    }

    // Public methods
    public void checkDataVersion(Context context) {
        int version = TunerPreferences.getChannelDataVersion(context);
        Log.d(TAG, "ChannelDataManager.VERSION=" + VERSION + " (current=" + version + ")");
        if (version == VERSION) {
            // Everything is awesome. Return and continue.
            return;
        }
        setCurrentVersion(context);

        if (version == TunerPreferences.CHANNEL_DATA_VERSION_NOT_SET) {
            mHandler.sendEmptyMessage(MSG_CHECK_VERSION);
        } else {
            // The stored channel data seem outdated. Delete them all.
            mHandler.sendEmptyMessage(MSG_CLEAR_CHANNELS);
        }
    }

    public void setCurrentVersion(Context context) {
        TunerPreferences.setChannelDataVersion(context, VERSION);
    }

    public void setListener(ProgramInfoListener listener) {
        mListener = listener;
    }

    public void setChannelScanListener(ChannelScanListener listener, Handler handler) {
        mChannelScanListener = listener;
        mChannelScanHandler = handler;
    }

    public void release() {
        mHandler.removeCallbacksAndMessages(null);
        releaseSafely();
    }

    public void releaseSafely() {
        mHandlerThread.quitSafely();
        mListener = null;
        mChannelScanListener = null;
        mChannelScanHandler = null;
    }

    public TunerChannel getChannel(long channelId) {
        TunerChannel channel = mTunerChannelMap.get(channelId);
        if (channel != null) {
            return channel;
        }
        mHandler.sendEmptyMessage(MSG_BUILD_CHANNEL_MAP);
        byte[] data = null;
        try (Cursor cursor = mContext.getContentResolver().query(TvContract.buildChannelUri(
                channelId), CHANNEL_DATA_SELECTION_ARGS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                data = cursor.getBlob(1);
            }
        }
        if (data == null) {
            return null;
        }
        channel = TunerChannel.parseFrom(data);
        if (channel == null) {
            return null;
        }
        channel.setChannelId(channelId);
        return channel;
    }

    public void requestProgramsData(TunerChannel channel) {
        mHandler.removeMessages(MSG_REQUEST_PROGRAMS);
        mHandler.obtainMessage(MSG_REQUEST_PROGRAMS, channel).sendToTarget();
    }

    public void notifyEventDetected(TunerChannel channel, List<EitItem> items) {
        mHandler.obtainMessage(MSG_HANDLE_EVENTS, new ChannelEvent(channel, items)).sendToTarget();
    }

    public void notifyChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
        if (mIsScanning.get()) {
            // During scanning, channels should be handle first to improve scan time.
            // EIT items can be handled in background after channel scan.
            mHandler.sendMessageAtFrontOfQueue(mHandler.obtainMessage(MSG_HANDLE_CHANNEL, channel));
        } else {
            mHandler.obtainMessage(MSG_HANDLE_CHANNEL, channel).sendToTarget();
        }
    }

    // For scanning process
    /**
     * Invoked when starting a scanning mode. This method gets the previous channels to detect the
     * obsolete channels after scanning and initializes the variables used for scanning.
     */
    public void notifyScanStarted() {
        mScannedChannels.clear();
        mPreviousScannedChannels.clear();
        try (Cursor cursor = mContext.getContentResolver().query(mChannelsUri,
                CHANNEL_DATA_SELECTION_ARGS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long channelId = cursor.getLong(0);
                    byte[] data = cursor.getBlob(1);
                    TunerChannel channel = TunerChannel.parseFrom(data);
                    if (channel != null) {
                        channel.setChannelId(channelId);
                        mPreviousScannedChannels.add(channel);
                    }
                } while (cursor.moveToNext());
            }
        }
        mIsScanning.set(true);
    }

    /**
     * Invoked when completing the scanning mode. Passes {@code MSG_SCAN_COMPLETED} to the handler
     * in order to wait for finishing the remaining messages in the handler queue. Then removes the
     * obsolete channels, which are previously scanned but are not in the current scanned result.
     */
    public void notifyScanCompleted() {
        // Send a dummy message to check whether there is any MSG_HANDLE_CHANNEL in queue
        // and avoid race conditions.
        scanCompleted.set(true);
        mHandler.sendMessageAtFrontOfQueue(mHandler.obtainMessage(MSG_HANDLE_CHANNEL, null));
    }

    public void scannedChannelHandlingCompleted() {
        mIsScanning.set(false);
        if (!mPreviousScannedChannels.isEmpty()) {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            for (TunerChannel channel : mPreviousScannedChannels) {
                ops.add(ContentProviderOperation.newDelete(
                        TvContract.buildChannelUri(channel.getChannelId())).build());
            }
            try {
                mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
            } catch (RemoteException | OperationApplicationException e) {
                Log.e(TAG, "Error deleting obsolete channels", e);
            }
        }
        if (mChannelScanListener != null && mChannelScanHandler != null) {
            mChannelScanHandler.post(new Runnable() {
                @Override
                public void run() {
                    mChannelScanListener.onChannelHandlingDone();
                }
            });
        } else {
            Log.e(TAG, "Error. mChannelScanListener is null.");
        }
    }

    /**
     * Returns the number of scanned channels in the scanning mode.
     */
    public int getScannedChannelCount() {
        return mScannedChannels.size();
    }

    /**
     * Removes all callbacks and messages in handler to avoid previous messages from last channel.
     */
    public void removeAllCallbacksAndMessages() {
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_HANDLE_EVENTS: {
                ChannelEvent event = (ChannelEvent) msg.obj;
                handleEvents(event.channel, event.eitItems);
                return true;
            }
            case MSG_HANDLE_CHANNEL: {
                TunerChannel channel = (TunerChannel) msg.obj;
                if (channel != null) {
                    handleChannel(channel);
                }
                if (scanCompleted.get() && mIsScanning.get()
                        && !mHandler.hasMessages(MSG_HANDLE_CHANNEL)) {
                    // Complete the scan when all found channels have already been handled.
                    scannedChannelHandlingCompleted();
                }
                return true;
            }
            case MSG_BUILD_CHANNEL_MAP: {
                mHandler.removeMessages(MSG_BUILD_CHANNEL_MAP);
                buildChannelMap();
                return true;
            }
            case MSG_REQUEST_PROGRAMS: {
                if (mHandler.hasMessages(MSG_REQUEST_PROGRAMS)) {
                    return true;
                }
                TunerChannel channel = (TunerChannel) msg.obj;
                if (mListener != null) {
                    mListener.onRequestProgramsResponse(channel, getAllProgramsForChannel(channel));
                }
                return true;
            }
            case MSG_CLEAR_CHANNELS: {
                clearChannels();
                return true;
            }
            case MSG_CHECK_VERSION: {
                checkVersion();
                return true;
            }
        }
        return false;
    }

    // Private methods
    private void handleEvents(TunerChannel channel, List<EitItem> items) {
        long channelId = getChannelId(channel);
        if (channelId <= 0) {
            return;
        }
        channel.setChannelId(channelId);

        // Schedule the audio and caption tracks of the current program and the programs being
        // listed after the current one into TIS.
        if (mListener != null) {
            mListener.onProgramsArrived(channel, items);
        }

        long currentTime = System.currentTimeMillis();
        List<EitItem> oldItems = getAllProgramsForChannel(channel, currentTime,
                currentTime + PROGRAM_QUERY_DURATION);
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        // TODO: Find a right way to check if the programs are added outside.
        boolean addedOutside = false;
        for (EitItem item : oldItems) {
            if (item.getEventId() == 0) {
                // The event has been added outside TV tuner.
                addedOutside = true;
                break;
            }
        }

        // Inserting programs only when there is no overlapping with existing data assuming that:
        // 1. external EPG is more accurate and rich and
        // 2. the data we add here will be updated when we apply external EPG.
        if (addedOutside) {
            // oldItemCount cannot be 0 if addedOutside is true.
            int oldItemCount = oldItems.size();
            for (EitItem newItem : items) {
                if (newItem.getEndTimeUtcMillis() < currentTime) {
                    continue;
                }
                long newItemStartTime = newItem.getStartTimeUtcMillis();
                long newItemEndTime = newItem.getEndTimeUtcMillis();
                if (newItemStartTime < oldItems.get(0).getStartTimeUtcMillis()) {
                    // Start time smaller than that of any old items. Insert if no overlap.
                    if (newItemEndTime > oldItems.get(0).getStartTimeUtcMillis()) continue;
                } else if (newItemStartTime
                        > oldItems.get(oldItemCount - 1).getStartTimeUtcMillis()) {
                    // Start time larger than that of any old item. Insert if no overlap.
                    if (newItemStartTime
                            < oldItems.get(oldItemCount - 1).getEndTimeUtcMillis()) continue;
                } else {
                    int pos = Collections.binarySearch(oldItems, newItem,
                            new Comparator<EitItem>() {
                                @Override
                                public int compare(EitItem lhs, EitItem rhs) {
                                    return Long.compare(lhs.getStartTimeUtcMillis(),
                                            rhs.getStartTimeUtcMillis());
                                }
                            });
                    if (pos >= 0) {
                        // Same start Time found. Overlapped.
                        continue;
                    }
                    int insertPoint = -1 - pos;
                    // Check the two adjacent items.
                    if (newItemStartTime < oldItems.get(insertPoint - 1).getEndTimeUtcMillis()
                            || newItemEndTime > oldItems.get(insertPoint).getStartTimeUtcMillis()) {
                        continue;
                    }
                }
                ops.add(buildContentProviderOperation(ContentProviderOperation.newInsert(
                        TvContract.Programs.CONTENT_URI), newItem, channel));
                if (ops.size() >= BATCH_OPERATION_COUNT) {
                    applyBatch(channel.getName(), ops);
                    ops.clear();
                }
            }
            applyBatch(channel.getName(), ops);
            return;
        }

        List<EitItem> outdatedOldItems = new ArrayList<>();
        Map<Integer, EitItem> newEitItemMap = new HashMap<>();
        for (EitItem item : items) {
            newEitItemMap.put(item.getEventId(), item);
        }
        for (EitItem oldItem : oldItems) {
            EitItem item = newEitItemMap.get(oldItem.getEventId());
            if (item == null) {
                outdatedOldItems.add(oldItem);
                continue;
            }

            // Since program descriptions arrive at different time, the older one may have the
            // correct program description while the newer one has no clue what value is.
            if (oldItem.getDescription() != null && item.getDescription() == null
                    && oldItem.getEventId() == item.getEventId()
                    && oldItem.getStartTime() == item.getStartTime()
                    && oldItem.getLengthInSecond() == item.getLengthInSecond()
                    && Objects.equals(oldItem.getContentRating(), item.getContentRating())
                    && Objects.equals(oldItem.getBroadcastGenre(), item.getBroadcastGenre())
                    && Objects.equals(oldItem.getCanonicalGenre(), item.getCanonicalGenre())) {
                item.setDescription(oldItem.getDescription());
            }
            if (item.compareTo(oldItem) != 0) {
                ops.add(buildContentProviderOperation(ContentProviderOperation.newUpdate(
                        TvContract.buildProgramUri(oldItem.getProgramId())), item, null));
                if (ops.size() >= BATCH_OPERATION_COUNT) {
                    applyBatch(channel.getName(), ops);
                    ops.clear();
                }
            }
            newEitItemMap.remove(item.getEventId());
        }
        for (EitItem unverifiedOldItems : outdatedOldItems) {
            if (unverifiedOldItems.getStartTimeUtcMillis() > currentTime) {
                // The given new EIT item list covers partial time span of EPG. Here, we delete old
                // item only when it has an overlapping with the new EIT item list.
                long startTime = unverifiedOldItems.getStartTimeUtcMillis();
                long endTime = unverifiedOldItems.getEndTimeUtcMillis();
                for (EitItem item : newEitItemMap.values()) {
                    long newItemStartTime = item.getStartTimeUtcMillis();
                    long newItemEndTime = item.getEndTimeUtcMillis();
                    if ((startTime >= newItemStartTime && startTime < newItemEndTime)
                            || (endTime > newItemStartTime && endTime <= newItemEndTime)) {
                        ops.add(ContentProviderOperation.newDelete(TvContract.buildProgramUri(
                                unverifiedOldItems.getProgramId())).build());
                        if (ops.size() >= BATCH_OPERATION_COUNT) {
                            applyBatch(channel.getName(), ops);
                            ops.clear();
                        }
                        break;
                    }
                }
            }
        }
        for (EitItem item : newEitItemMap.values()) {
            if (item.getEndTimeUtcMillis() < currentTime) {
                continue;
            }
            ops.add(buildContentProviderOperation(ContentProviderOperation.newInsert(
                    TvContract.Programs.CONTENT_URI), item, channel));
            if (ops.size() >= BATCH_OPERATION_COUNT) {
                applyBatch(channel.getName(), ops);
                ops.clear();
            }
        }

        applyBatch(channel.getName(), ops);
    }

    private ContentProviderOperation buildContentProviderOperation(
            ContentProviderOperation.Builder builder, EitItem item, TunerChannel channel) {
        if (channel != null) {
            builder.withValue(TvContract.Programs.COLUMN_CHANNEL_ID, channel.getChannelId());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.withValue(TvContract.Programs.COLUMN_RECORDING_PROHIBITED,
                        channel.isRecordingProhibited() ? 1 : 0);
            }
        }
        if (item != null) {
            builder.withValue(TvContract.Programs.COLUMN_TITLE, item.getTitleText())
                    .withValue(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                            item.getStartTimeUtcMillis())
                    .withValue(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                            item.getEndTimeUtcMillis())
                    .withValue(TvContract.Programs.COLUMN_CONTENT_RATING,
                            item.getContentRating())
                    .withValue(TvContract.Programs.COLUMN_AUDIO_LANGUAGE,
                            item.getAudioLanguage())
                    .withValue(TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
                            item.getDescription())
                    .withValue(TvContract.Programs.COLUMN_VERSION_NUMBER,
                            item.getEventId());
        }
        return builder.build();
    }

    private void applyBatch(String channelName, ArrayList<ContentProviderOperation> operations) {
        try {
            mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, operations);
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, "Error updating EPG " + channelName, e);
        }
    }

    private void handleChannel(TunerChannel channel) {
        long channelId = getChannelId(channel);
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_NETWORK_AFFILIATION, channel.getShortName());
        values.put(TvContract.Channels.COLUMN_SERVICE_TYPE, channel.getServiceTypeName());
        values.put(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID, channel.getTsid());
        values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, channel.getDisplayNumber());
        values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.getName());
        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, channel.toByteArray());
        values.put(TvContract.Channels.COLUMN_DESCRIPTION, channel.getDescription());
        values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, channel.getVideoFormat());
        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG1, VERSION);
        values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG2,
                channel.isRecordingProhibited() ? 1 : 0);

        if (channelId <= 0) {
            values.put(TvContract.Channels.COLUMN_INPUT_ID, mInputId);
            values.put(TvContract.Channels.COLUMN_TYPE, "QAM256".equals(channel.getModulation())
                    ? TvContract.Channels.TYPE_ATSC_C : TvContract.Channels.TYPE_ATSC_T);
            values.put(TvContract.Channels.COLUMN_SERVICE_ID, channel.getProgramNumber());

            // ATSC doesn't have original_network_id
            values.put(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, channel.getFrequency());

            Uri channelUri = mContext.getContentResolver().insert(TvContract.Channels.CONTENT_URI,
                    values);
            channelId = ContentUris.parseId(channelUri);
        } else {
            mContext.getContentResolver().update(
                    TvContract.buildChannelUri(channelId), values, null, null);
        }
        channel.setChannelId(channelId);
        mTunerChannelMap.put(channelId, channel);
        mTunerChannelIdMap.put(channel, channelId);
        if (mIsScanning.get()) {
            mScannedChannels.add(channel);
            mPreviousScannedChannels.remove(channel);
        }
        if (mListener != null) {
            mListener.onChannelArrived(channel);
        }
    }

    private void clearChannels() {
        int count = mContext.getContentResolver().delete(mChannelsUri, null, null);
        if (count > 0) {
            // We have just deleted obsolete data. Now tell the user that he or she needs
            // to perform the auto-scan again.
            if (mListener != null) {
                mListener.onRescanNeeded();
            }
        }
    }

    private void checkVersion() {
        if (PermissionUtils.hasAccessAllEpg(mContext)) {
            String selection = TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG1 + "<>?";
            try (Cursor cursor = mContext.getContentResolver().query(mChannelsUri,
                    CHANNEL_DATA_SELECTION_ARGS, selection,
                    new String[] {Integer.toString(VERSION)}, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // The stored channel data seem outdated. Delete them all.
                    clearChannels();
                }
            }
        } else {
            try (Cursor cursor = mContext.getContentResolver().query(mChannelsUri,
                    new String[] { TvContract.Channels.COLUMN_INTERNAL_PROVIDER_FLAG1 },
                    null, null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        int version = cursor.getInt(0);
                        if (version != VERSION) {
                            clearChannels();
                            break;
                        }
                    }
                }
            }
        }
    }

    private long getChannelId(TunerChannel channel) {
        Long channelId = mTunerChannelIdMap.get(channel);
        if (channelId != null) {
            return channelId;
        }
        mHandler.sendEmptyMessage(MSG_BUILD_CHANNEL_MAP);
        try (Cursor cursor = mContext.getContentResolver().query(mChannelsUri,
                CHANNEL_DATA_SELECTION_ARGS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    channelId = cursor.getLong(0);
                    byte[] providerData = cursor.getBlob(1);
                    TunerChannel tunerChannel = TunerChannel.parseFrom(providerData);
                    if (tunerChannel != null && tunerChannel.compareTo(channel) == 0) {
                        channel.setChannelId(channelId);
                        mTunerChannelIdMap.put(channel, channelId);
                        mTunerChannelMap.put(channelId, channel);
                        return channelId;
                    }
                } while (cursor.moveToNext());
            }
        }
        return -1;
    }

    private List<EitItem> getAllProgramsForChannel(TunerChannel channel) {
        return getAllProgramsForChannel(channel, null, null);
    }

    private List<EitItem> getAllProgramsForChannel(TunerChannel channel, @Nullable Long startTimeMs,
            @Nullable Long endTimeMs) {
        List<EitItem> items = new ArrayList<>();
        long channelId = channel.getChannelId();
        Uri programsUri = (startTimeMs == null || endTimeMs == null) ?
                TvContract.buildProgramsUriForChannel(channelId) :
                TvContract.buildProgramsUriForChannel(channelId, startTimeMs, endTimeMs);
        try (Cursor cursor = mContext.getContentResolver().query(programsUri,
                ALL_PROGRAMS_SELECTION_ARGS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    String titleText = cursor.getString(1);
                    long startTime = ConvertUtils.convertUnixEpochToGPSTime(
                            cursor.getLong(2) / DateUtils.SECOND_IN_MILLIS);
                    long endTime = ConvertUtils.convertUnixEpochToGPSTime(
                            cursor.getLong(3) / DateUtils.SECOND_IN_MILLIS);
                    int lengthInSecond = (int) (endTime - startTime);
                    String contentRating = cursor.getString(4);
                    String broadcastGenre = cursor.getString(5);
                    String canonicalGenre = cursor.getString(6);
                    String description = cursor.getString(7);
                    int eventId = cursor.getInt(8);
                    EitItem eitItem = new EitItem(id, eventId, titleText, startTime, lengthInSecond,
                            contentRating, null, null, broadcastGenre, canonicalGenre, description);
                    items.add(eitItem);
                } while (cursor.moveToNext());
            }
        }
        return items;
    }

    private void buildChannelMap() {
        ArrayList<TunerChannel> channels = new ArrayList<>();
        try (Cursor cursor = mContext.getContentResolver().query(mChannelsUri,
                CHANNEL_DATA_SELECTION_ARGS, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    long channelId = cursor.getLong(0);
                    byte[] data = cursor.getBlob(1);
                    TunerChannel channel = TunerChannel.parseFrom(data);
                    if (channel != null) {
                        channel.setChannelId(channelId);
                        channels.add(channel);
                    }
                } while (cursor.moveToNext());
            }
        }
        mTunerChannelMap.clear();
        mTunerChannelIdMap.clear();
        for (TunerChannel channel : channels) {
            mTunerChannelMap.put(channel.getChannelId(), channel);
            mTunerChannelIdMap.put(channel, channel.getChannelId());
        }
    }

    private static class ChannelEvent {
        public final TunerChannel channel;
        public final List<EitItem> eitItems;

        public ChannelEvent(TunerChannel channel, List<EitItem> eitItems) {
            this.channel = channel;
            this.eitItems = eitItems;
        }
    }
}
