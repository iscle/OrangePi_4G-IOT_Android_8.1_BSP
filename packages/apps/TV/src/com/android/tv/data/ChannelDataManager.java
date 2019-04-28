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

package com.android.tv.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteException;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvInputManager.TvInputCallback;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.util.Log;
import android.util.MutableInt;

import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.WeakHandler;
import com.android.tv.util.AsyncDbTask;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The class to manage channel data.
 * Basic features: reading channel list and each channel's current program, and updating
 * the values of {@link Channels#COLUMN_BROWSABLE}, {@link Channels#COLUMN_LOCKED}.
 * This class is not thread-safe and under an assumption that its public methods are called in
 * only the main thread.
 */
@AnyThread
public class ChannelDataManager {
    private static final String TAG = "ChannelDataManager";
    private static final boolean DEBUG = false;

    private static final int MSG_UPDATE_CHANNELS = 1000;

    private final Context mContext;
    private final TvInputManagerHelper mInputManager;
    private boolean mStarted;
    private boolean mDbLoadFinished;
    private QueryAllChannelsTask mChannelsUpdateTask;
    private final List<Runnable> mPostRunnablesAfterChannelUpdate = new ArrayList<>();

    private final Set<Listener> mListeners = new CopyOnWriteArraySet<>();
    // Use container class to support multi-thread safety. This value can be set only on the main
    // thread.
    volatile private UnmodifiableChannelData mData = new UnmodifiableChannelData();
    private final Channel.DefaultComparator mChannelComparator;

    private final Handler mHandler;
    private final Set<Long> mBrowsableUpdateChannelIds = new HashSet<>();
    private final Set<Long> mLockedUpdateChannelIds = new HashSet<>();

    private final ContentResolver mContentResolver;
    private final ContentObserver mChannelObserver;
    private final boolean mStoreBrowsableInSharedPreferences;
    private final SharedPreferences mBrowsableSharedPreferences;

    private final TvInputCallback mTvInputCallback = new TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            boolean channelAdded = false;
            ChannelData data = new ChannelData(mData);
            for (ChannelWrapper channel : mData.channelWrapperMap.values()) {
                if (channel.mChannel.getInputId().equals(inputId)) {
                    channel.mInputRemoved = false;
                    addChannel(data, channel.mChannel);
                    channelAdded = true;
                }
            }
            if (channelAdded) {
                Collections.sort(data.channels, mChannelComparator);
                mData = new UnmodifiableChannelData(data);
                notifyChannelListUpdated();
            }
        }

        @Override
        public void onInputRemoved(String inputId) {
            boolean channelRemoved = false;
            ArrayList<ChannelWrapper> removedChannels = new ArrayList<>();
            for (ChannelWrapper channel : mData.channelWrapperMap.values()) {
                if (channel.mChannel.getInputId().equals(inputId)) {
                    channel.mInputRemoved = true;
                    channelRemoved = true;
                    removedChannels.add(channel);
                }
            }
            if (channelRemoved) {
                ChannelData data = new ChannelData();
                data.channelWrapperMap.putAll(mData.channelWrapperMap);
                for (ChannelWrapper channelWrapper : data.channelWrapperMap.values()) {
                    if (!channelWrapper.mInputRemoved) {
                        addChannel(data, channelWrapper.mChannel);
                    }
                }
                Collections.sort(data.channels, mChannelComparator);
                mData = new UnmodifiableChannelData(data);
                notifyChannelListUpdated();
                for (ChannelWrapper channel : removedChannels) {
                    channel.notifyChannelRemoved();
                }
            }
        }
    };

    @MainThread
    public ChannelDataManager(Context context, TvInputManagerHelper inputManager) {
        this(context, inputManager, context.getContentResolver());
    }

    @MainThread
    @VisibleForTesting
    ChannelDataManager(Context context, TvInputManagerHelper inputManager,
            ContentResolver contentResolver) {
        mContext = context;
        mInputManager = inputManager;
        mContentResolver = contentResolver;
        mChannelComparator = new Channel.DefaultComparator(context, inputManager);
        // Detect duplicate channels while sorting.
        mChannelComparator.setDetectDuplicatesEnabled(true);
        mHandler = new ChannelDataManagerHandler(this);
        mChannelObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (!mHandler.hasMessages(MSG_UPDATE_CHANNELS)) {
                    mHandler.sendEmptyMessage(MSG_UPDATE_CHANNELS);
                }
            }
        };
        mStoreBrowsableInSharedPreferences = !PermissionUtils.hasAccessAllEpg(mContext);
        mBrowsableSharedPreferences = context.getSharedPreferences(
                SharedPreferencesUtils.SHARED_PREF_BROWSABLE, Context.MODE_PRIVATE);
    }

    @VisibleForTesting
    ContentObserver getContentObserver() {
        return mChannelObserver;
    }

    /**
     * Starts the manager. If data is ready, {@link Listener#onLoadFinished()} will be called.
     */
    @MainThread
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        // Should be called directly instead of posting MSG_UPDATE_CHANNELS message to the handler.
        // If not, other DB tasks can be executed before channel loading.
        handleUpdateChannels();
        mContentResolver.registerContentObserver(TvContract.Channels.CONTENT_URI, true,
                mChannelObserver);
        mInputManager.addCallback(mTvInputCallback);
    }

    /**
     * Stops the manager. It clears manager states and runs pending DB operations. Added listeners
     * aren't automatically removed by this method.
     */
    @MainThread
    @VisibleForTesting
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mDbLoadFinished = false;

        mInputManager.removeCallback(mTvInputCallback);
        mContentResolver.unregisterContentObserver(mChannelObserver);
        mHandler.removeCallbacksAndMessages(null);

        clearChannels();
        mPostRunnablesAfterChannelUpdate.clear();
        if (mChannelsUpdateTask != null) {
            mChannelsUpdateTask.cancel(true);
            mChannelsUpdateTask = null;
        }
        applyUpdatedValuesToDb();
    }

    /**
     * Adds a {@link Listener}.
     */
    public void addListener(Listener listener) {
        if (DEBUG) Log.d(TAG, "addListener " + listener);
        SoftPreconditions.checkNotNull(listener);
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    /**
     * Removes a {@link Listener}.
     */
    public void removeListener(Listener listener) {
        if (DEBUG) Log.d(TAG, "removeListener " + listener);
        SoftPreconditions.checkNotNull(listener);
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    /**
     * Adds a {@link ChannelListener} for a specific channel with the channel ID {@code channelId}.
     */
    public void addChannelListener(Long channelId, ChannelListener listener) {
        ChannelWrapper channelWrapper = mData.channelWrapperMap.get(channelId);
        if (channelWrapper == null) {
            return;
        }
        channelWrapper.addListener(listener);
    }

    /**
     * Removes a {@link ChannelListener} for a specific channel with the channel ID
     * {@code channelId}.
     */
    public void removeChannelListener(Long channelId, ChannelListener listener) {
        ChannelWrapper channelWrapper = mData.channelWrapperMap.get(channelId);
        if (channelWrapper == null) {
            return;
        }
        channelWrapper.removeListener(listener);
    }

    /**
     * Checks whether data is ready.
     */
    public boolean isDbLoadFinished() {
        return mDbLoadFinished;
    }

    /**
     * Returns the number of channels.
     */
    public int getChannelCount() {
        return mData.channels.size();
    }

    /**
     * Returns a list of channels.
     */
    public List<Channel> getChannelList() {
        return new ArrayList<>(mData.channels);
    }

    /**
     * Returns a list of browsable channels.
     */
    public List<Channel> getBrowsableChannelList() {
        List<Channel> channels = new ArrayList<>();
        for (Channel channel : mData.channels) {
            if (channel.isBrowsable()) {
                channels.add(channel);
            }
        }
        return channels;
    }

    /**
     * Returns the total channel count for a given input.
     *
     * @param inputId The ID of the input.
     */
    public int getChannelCountForInput(String inputId) {
        MutableInt count = mData.channelCountMap.get(inputId);
        return count == null ? 0 : count.value;
    }

    /**
     * Checks if the channel exists in DB.
     *
     * <p>Note that the channels of the removed inputs can not be obtained from {@link #getChannel}.
     * In that case this method is used to check if the channel exists in the DB.
     */
    public boolean doesChannelExistInDb(long channelId) {
        return mData.channelWrapperMap.get(channelId) != null;
    }

    /**
     * Returns true if and only if there exists at least one channel and all channels are hidden.
     */
    public boolean areAllChannelsHidden() {
        for (Channel channel : mData.channels) {
            if (channel.isBrowsable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the channel with the channel ID {@code channelId}.
     */
    public Channel getChannel(Long channelId) {
        ChannelWrapper channelWrapper = mData.channelWrapperMap.get(channelId);
        if (channelWrapper == null || channelWrapper.mInputRemoved) {
            return null;
        }
        return channelWrapper.mChannel;
    }

    /**
     * The value change will be applied to DB when applyPendingDbOperation is called.
     */
    public void updateBrowsable(Long channelId, boolean browsable) {
        updateBrowsable(channelId, browsable, false);
    }

    /**
     * The value change will be applied to DB when applyPendingDbOperation is called.
     *
     * @param skipNotifyChannelBrowsableChanged If it's true, {@link Listener
     *        #onChannelBrowsableChanged()} is not called, when this method is called.
     *        {@link #notifyChannelBrowsableChanged} should be directly called, once browsable
     *        update is completed.
     */
    public void updateBrowsable(Long channelId, boolean browsable,
            boolean skipNotifyChannelBrowsableChanged) {
        ChannelWrapper channelWrapper = mData.channelWrapperMap.get(channelId);
        if (channelWrapper == null) {
            return;
        }
        if (channelWrapper.mChannel.isBrowsable() != browsable) {
            channelWrapper.mChannel.setBrowsable(browsable);
            if (browsable == channelWrapper.mBrowsableInDb) {
                mBrowsableUpdateChannelIds.remove(channelWrapper.mChannel.getId());
            } else {
                mBrowsableUpdateChannelIds.add(channelWrapper.mChannel.getId());
            }
            channelWrapper.notifyChannelUpdated();
            // When updateBrowsable is called multiple times in a method, we don't need to
            // notify Listener.onChannelBrowsableChanged multiple times but only once. So
            // we send a message instead of directly calling onChannelBrowsableChanged.
            if (!skipNotifyChannelBrowsableChanged) {
                notifyChannelBrowsableChanged();
            }
        }
    }

    public void notifyChannelBrowsableChanged() {
        for (Listener l : mListeners) {
            l.onChannelBrowsableChanged();
        }
    }

    private void notifyChannelListUpdated() {
        for (Listener l : mListeners) {
            l.onChannelListUpdated();
        }
    }

    private void notifyLoadFinished() {
        for (Listener l : mListeners) {
            l.onLoadFinished();
        }
    }

    /**
     * Updates channels from DB. Once the update is done, {@code postRunnable} will
     * be called.
     */
    public void updateChannels(Runnable postRunnable) {
        if (mChannelsUpdateTask != null) {
            mChannelsUpdateTask.cancel(true);
            mChannelsUpdateTask = null;
        }
        mPostRunnablesAfterChannelUpdate.add(postRunnable);
        if (!mHandler.hasMessages(MSG_UPDATE_CHANNELS)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_CHANNELS);
        }
    }

    /**
     * The value change will be applied to DB when applyPendingDbOperation is called.
     */
    public void updateLocked(Long channelId, boolean locked) {
        ChannelWrapper channelWrapper = mData.channelWrapperMap.get(channelId);
        if (channelWrapper == null) {
            return;
        }
        if (channelWrapper.mChannel.isLocked() != locked) {
            channelWrapper.mChannel.setLocked(locked);
            if (locked == channelWrapper.mLockedInDb) {
                mLockedUpdateChannelIds.remove(channelWrapper.mChannel.getId());
            } else {
                mLockedUpdateChannelIds.add(channelWrapper.mChannel.getId());
            }
            channelWrapper.notifyChannelUpdated();
        }
    }

    /**
     * Applies the changed values by {@link #updateBrowsable} and {@link #updateLocked}
     * to DB.
     */
    public void applyUpdatedValuesToDb() {
        ChannelData data = mData;
        ArrayList<Long> browsableIds = new ArrayList<>();
        ArrayList<Long> unbrowsableIds = new ArrayList<>();
        for (Long id : mBrowsableUpdateChannelIds) {
            ChannelWrapper channelWrapper = data.channelWrapperMap.get(id);
            if (channelWrapper == null) {
                continue;
            }
            if (channelWrapper.mChannel.isBrowsable()) {
                browsableIds.add(id);
            } else {
                unbrowsableIds.add(id);
            }
            channelWrapper.mBrowsableInDb = channelWrapper.mChannel.isBrowsable();
        }
        String column = TvContract.Channels.COLUMN_BROWSABLE;
        if (mStoreBrowsableInSharedPreferences) {
            Editor editor = mBrowsableSharedPreferences.edit();
            for (Long id : browsableIds) {
                editor.putBoolean(getBrowsableKey(getChannel(id)), true);
            }
            for (Long id : unbrowsableIds) {
                editor.putBoolean(getBrowsableKey(getChannel(id)), false);
            }
            editor.apply();
        } else {
            if (!browsableIds.isEmpty()) {
                updateOneColumnValue(column, 1, browsableIds);
            }
            if (!unbrowsableIds.isEmpty()) {
                updateOneColumnValue(column, 0, unbrowsableIds);
            }
        }
        mBrowsableUpdateChannelIds.clear();

        ArrayList<Long> lockedIds = new ArrayList<>();
        ArrayList<Long> unlockedIds = new ArrayList<>();
        for (Long id : mLockedUpdateChannelIds) {
            ChannelWrapper channelWrapper = data.channelWrapperMap.get(id);
            if (channelWrapper == null) {
                continue;
            }
            if (channelWrapper.mChannel.isLocked()) {
                lockedIds.add(id);
            } else {
                unlockedIds.add(id);
            }
            channelWrapper.mLockedInDb = channelWrapper.mChannel.isLocked();
        }
        column = TvContract.Channels.COLUMN_LOCKED;
        if (!lockedIds.isEmpty()) {
            updateOneColumnValue(column, 1, lockedIds);
        }
        if (!unlockedIds.isEmpty()) {
            updateOneColumnValue(column, 0, unlockedIds);
        }
        mLockedUpdateChannelIds.clear();
        if (DEBUG) {
            Log.d(TAG, "applyUpdatedValuesToDb"
                    + "\n browsableIds size:" + browsableIds.size()
                    + "\n unbrowsableIds size:" + unbrowsableIds.size()
                    + "\n lockedIds size:" + lockedIds.size()
                    + "\n unlockedIds size:" + unlockedIds.size());
        }
    }

    @MainThread
    private void addChannel(ChannelData data, Channel channel) {
        data.channels.add(channel);
        String inputId = channel.getInputId();
        MutableInt count = data.channelCountMap.get(inputId);
        if (count == null) {
            data.channelCountMap.put(inputId, new MutableInt(1));
        } else {
            count.value++;
        }
    }

    @MainThread
    private void clearChannels() {
        mData = new UnmodifiableChannelData();
    }

    @MainThread
    private void handleUpdateChannels() {
        if (mChannelsUpdateTask != null) {
            mChannelsUpdateTask.cancel(true);
        }
        mChannelsUpdateTask = new QueryAllChannelsTask(mContentResolver);
        mChannelsUpdateTask.executeOnDbThread();
    }

    /**
     * Reloads channel data.
     */
    public void reload() {
        if (mDbLoadFinished && !mHandler.hasMessages(MSG_UPDATE_CHANNELS)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_CHANNELS);
        }
    }

    /**
     * A listener for ChannelDataManager. The callbacks are called on the main thread.
     */
    public interface Listener {
        /**
         * Called when data load is finished.
         */
        void onLoadFinished();

        /**
         * Called when channels are added, deleted, or updated. But, when browsable is changed,
         * it won't be called. Instead, {@link #onChannelBrowsableChanged} will be called.
         */
        void onChannelListUpdated();

        /**
         * Called when browsable of channels are changed.
         */
        void onChannelBrowsableChanged();
    }

    /**
     * A listener for individual channel change. The callbacks are called on the main thread.
     */
    public interface ChannelListener {
        /**
         * Called when the channel has been removed in DB.
         */
        void onChannelRemoved(Channel channel);

        /**
         * Called when values of the channel has been changed.
         */
        void onChannelUpdated(Channel channel);
    }

    private class ChannelWrapper {
        final Set<ChannelListener> mChannelListeners = new ArraySet<>();
        final Channel mChannel;
        boolean mBrowsableInDb;
        boolean mLockedInDb;
        boolean mInputRemoved;

        ChannelWrapper(Channel channel) {
            mChannel = channel;
            mBrowsableInDb = channel.isBrowsable();
            mLockedInDb = channel.isLocked();
            mInputRemoved = !mInputManager.hasTvInputInfo(channel.getInputId());
        }

        void addListener(ChannelListener listener) {
            mChannelListeners.add(listener);
        }

        void removeListener(ChannelListener listener) {
            mChannelListeners.remove(listener);
        }

        void notifyChannelUpdated() {
            for (ChannelListener l : mChannelListeners) {
                l.onChannelUpdated(mChannel);
            }
        }

        void notifyChannelRemoved() {
            for (ChannelListener l : mChannelListeners) {
                l.onChannelRemoved(mChannel);
            }
        }
    }

    private class CheckChannelLogoExistTask extends AsyncTask<Void, Void, Boolean> {
        private final Channel mChannel;

        CheckChannelLogoExistTask(Channel channel) {
            mChannel = channel;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try (AssetFileDescriptor f = mContext.getContentResolver().openAssetFileDescriptor(
                        TvContract.buildChannelLogoUri(mChannel.getId()), "r")) {
                return true;
            } catch (SQLiteException | IOException | NullPointerException e) {
                // File not found or asset file not found.
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            ChannelWrapper wrapper = mData.channelWrapperMap.get(mChannel.getId());
            if (wrapper != null) {
                wrapper.mChannel.setChannelLogoExist(result);
            }
        }
    }

    private final class QueryAllChannelsTask extends AsyncDbTask.AsyncChannelQueryTask {

        QueryAllChannelsTask(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onPostExecute(List<Channel> channels) {
            mChannelsUpdateTask = null;
            if (channels == null) {
                if (DEBUG) Log.e(TAG, "onPostExecute with null channels");
                return;
            }
            ChannelData data = new ChannelData();
            data.channelWrapperMap.putAll(mData.channelWrapperMap);
            Set<Long> removedChannelIds = new HashSet<>(data.channelWrapperMap.keySet());
            List<ChannelWrapper> removedChannelWrappers = new ArrayList<>();
            List<ChannelWrapper> updatedChannelWrappers = new ArrayList<>();

            boolean channelAdded = false;
            boolean channelUpdated = false;
            boolean channelRemoved = false;
            Map<String, ?> deletedBrowsableMap = null;
            if (mStoreBrowsableInSharedPreferences) {
                deletedBrowsableMap = new HashMap<>(mBrowsableSharedPreferences.getAll());
            }
            for (Channel channel : channels) {
                if (mStoreBrowsableInSharedPreferences) {
                    String browsableKey = getBrowsableKey(channel);
                    channel.setBrowsable(mBrowsableSharedPreferences.getBoolean(browsableKey,
                            false));
                    deletedBrowsableMap.remove(browsableKey);
                }
                long channelId = channel.getId();
                boolean newlyAdded = !removedChannelIds.remove(channelId);
                ChannelWrapper channelWrapper;
                if (newlyAdded) {
                    new CheckChannelLogoExistTask(channel)
                            .executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                    channelWrapper = new ChannelWrapper(channel);
                    data.channelWrapperMap.put(channel.getId(), channelWrapper);
                    if (!channelWrapper.mInputRemoved) {
                        channelAdded = true;
                    }
                } else {
                    channelWrapper = data.channelWrapperMap.get(channelId);
                    if (!channelWrapper.mChannel.hasSameReadOnlyInfo(channel)) {
                        // Channel data updated
                        Channel oldChannel = channelWrapper.mChannel;
                        // We assume that mBrowsable and mLocked are controlled by only TV app.
                        // The values for mBrowsable and mLocked are updated when
                        // {@link #applyUpdatedValuesToDb} is called. Therefore, the value
                        // between DB and ChannelDataManager could be different for a while.
                        // Therefore, we'll keep the values in ChannelDataManager.
                        channel.setBrowsable(oldChannel.isBrowsable());
                        channel.setLocked(oldChannel.isLocked());
                        channelWrapper.mChannel.copyFrom(channel);
                        if (!channelWrapper.mInputRemoved) {
                            channelUpdated = true;
                            updatedChannelWrappers.add(channelWrapper);
                        }
                    }
                }
            }
            if (mStoreBrowsableInSharedPreferences && !deletedBrowsableMap.isEmpty()
                    && PermissionUtils.hasReadTvListings(mContext)) {
                // If hasReadTvListings(mContext) is false, the given channel list would
                // empty. In this case, we skip the browsable data clean up process.
                Editor editor = mBrowsableSharedPreferences.edit();
                for (String key : deletedBrowsableMap.keySet()) {
                    if (DEBUG) Log.d(TAG, "remove key: " + key);
                    editor.remove(key);
                }
                editor.apply();
            }

            for (long id : removedChannelIds) {
                ChannelWrapper channelWrapper = data.channelWrapperMap.remove(id);
                if (!channelWrapper.mInputRemoved) {
                    channelRemoved = true;
                    removedChannelWrappers.add(channelWrapper);
                }
            }
            for (ChannelWrapper channelWrapper : data.channelWrapperMap.values()) {
                if (!channelWrapper.mInputRemoved) {
                    addChannel(data, channelWrapper.mChannel);
                }
            }
            Collections.sort(data.channels, mChannelComparator);
            mData = new UnmodifiableChannelData(data);

            if (!mDbLoadFinished) {
                mDbLoadFinished = true;
                notifyLoadFinished();
            } else if (channelAdded || channelUpdated || channelRemoved) {
                notifyChannelListUpdated();
            }
            for (ChannelWrapper channelWrapper : removedChannelWrappers) {
                channelWrapper.notifyChannelRemoved();
            }
            for (ChannelWrapper channelWrapper : updatedChannelWrappers) {
                channelWrapper.notifyChannelUpdated();
            }
            for (Runnable r : mPostRunnablesAfterChannelUpdate) {
                r.run();
            }
            mPostRunnablesAfterChannelUpdate.clear();
        }
    }

    /**
     * Updates a column {@code columnName} of DB table {@code uri} with the value
     * {@code columnValue}. The selective rows in the ID list {@code ids} will be updated.
     * The DB operations will run on {@link AsyncDbTask#getExecutor()}.
     */
    private void updateOneColumnValue(
            final String columnName, final int columnValue, final List<Long> ids) {
        if (!PermissionUtils.hasAccessAllEpg(mContext)) {
            return;
        }
        AsyncDbTask.executeOnDbThread(new Runnable() {
            @Override
            public void run() {
                String selection = Utils.buildSelectionForIds(Channels._ID, ids);
                ContentValues values = new ContentValues();
                values.put(columnName, columnValue);
                mContentResolver.update(TvContract.Channels.CONTENT_URI, values, selection, null);
            }
        });
    }

    private String getBrowsableKey(Channel channel) {
        return channel.getInputId() + "|" + channel.getId();
    }

    @MainThread
    private static class ChannelDataManagerHandler extends WeakHandler<ChannelDataManager> {
        public ChannelDataManagerHandler(ChannelDataManager channelDataManager) {
            super(Looper.getMainLooper(), channelDataManager);
        }

        @Override
        public void handleMessage(Message msg, @NonNull ChannelDataManager channelDataManager) {
            if (msg.what == MSG_UPDATE_CHANNELS) {
                channelDataManager.handleUpdateChannels();
            }
        }
    }

    /**
     * Container class which includes channel data that needs to be synced. This class is
     * modifiable and used for changing channel data.
     * e.g. TvInputCallback, or AsyncDbTask.onPostExecute.
     */
    @MainThread
    private static class ChannelData {
        final Map<Long, ChannelWrapper> channelWrapperMap;
        final Map<String, MutableInt> channelCountMap;
        final List<Channel> channels;

        ChannelData() {
            channelWrapperMap = new HashMap<>();
            channelCountMap = new HashMap<>();
            channels = new ArrayList<>();
        }

        ChannelData(ChannelData data) {
            channelWrapperMap = new HashMap<>(data.channelWrapperMap);
            channelCountMap = new HashMap<>(data.channelCountMap);
            channels = new ArrayList<>(data.channels);
        }

        ChannelData(Map<Long, ChannelWrapper> channelWrapperMap,
                Map<String, MutableInt> channelCountMap, List<Channel> channels) {
            this.channelWrapperMap = channelWrapperMap;
            this.channelCountMap = channelCountMap;
            this.channels = channels;
        }
    }

    /** Unmodifiable channel data. */
    @MainThread
    private static class UnmodifiableChannelData extends ChannelData {
        UnmodifiableChannelData() {
            super(Collections.unmodifiableMap(new HashMap<>()),
                    Collections.unmodifiableMap(new HashMap<>()),
                    Collections.unmodifiableList(new ArrayList<>()));
        }

        UnmodifiableChannelData(ChannelData data) {
            super(Collections.unmodifiableMap(data.channelWrapperMap),
                    Collections.unmodifiableMap(data.channelCountMap),
                    Collections.unmodifiableList(data.channels));
        }
    }
}
