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

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.graphics.Bitmap.CompressFormat;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.support.annotation.MainThread;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.util.BitmapUtils;
import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;
import com.android.tv.util.PermissionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Fetches channel logos from the cloud into the database. It's for the channels which have no logos
 * or need update logos. This class is thread safe.
 */
public class ChannelLogoFetcher {
    private static final String TAG = "ChannelLogoFetcher";
    private static final boolean DEBUG = false;

    private static final String PREF_KEY_IS_FIRST_TIME_FETCH_CHANNEL_LOGO =
            "is_first_time_fetch_channel_logo";

    private static FetchLogoTask sFetchTask;

    /**
     * Fetches the channel logos from the cloud data and insert them into TvProvider.
     * The previous task is canceled and a new task starts.
     */
    @MainThread
    public static void startFetchingChannelLogos(
            Context context, List<Channel> channels) {
        if (!PermissionUtils.hasAccessAllEpg(context)) {
            // TODO: support this feature for non-system LC app. b/23939816
            return;
        }
        if (sFetchTask != null) {
            sFetchTask.cancel(true);
            sFetchTask = null;
        }
        if (DEBUG) Log.d(TAG, "Request to start fetching logos.");
        if (channels == null || channels.isEmpty()) {
            return;
        }
        sFetchTask = new FetchLogoTask(context.getApplicationContext(), channels);
        sFetchTask.execute();
    }

    private ChannelLogoFetcher() {
    }

    private static final class FetchLogoTask extends AsyncTask<Void, Void, Void> {
        private final Context mContext;
        private final List<Channel> mChannels;

        private FetchLogoTask(Context context, List<Channel> channels) {
            mContext = context;
            mChannels = channels;
        }

        @Override
        protected Void doInBackground(Void... arg) {
            if (isCancelled()) {
                if (DEBUG) Log.d(TAG, "Fetching the channel logos has been canceled");
                return null;
            }
            List<Channel> channelsToUpdate = new ArrayList<>();
            List<Channel> channelsToRemove = new ArrayList<>();
            // Updates or removes the logo by comparing the logo uri which is got from the cloud
            // and the stored one. And we assume that the data got form the cloud is 100%
            // correct and completed.
            SharedPreferences sharedPreferences =
                    mContext.getSharedPreferences(
                            SharedPreferencesUtils.SHARED_PREF_CHANNEL_LOGO_URIS,
                            Context.MODE_PRIVATE);
            SharedPreferences.Editor sharedPreferencesEditor = sharedPreferences.edit();
            Map<String, ?> uncheckedChannels = sharedPreferences.getAll();
            boolean isFirstTimeFetchChannelLogo = sharedPreferences.getBoolean(
                    PREF_KEY_IS_FIRST_TIME_FETCH_CHANNEL_LOGO, true);
            // Iterating channels.
            for (Channel channel : mChannels) {
                String channelIdString = Long.toString(channel.getId());
                String storedChannelLogoUri = (String) uncheckedChannels.remove(channelIdString);
                if (!TextUtils.isEmpty(channel.getLogoUri())
                        && !TextUtils.equals(storedChannelLogoUri, channel.getLogoUri())) {
                    channelsToUpdate.add(channel);
                    sharedPreferencesEditor.putString(channelIdString, channel.getLogoUri());
                } else if (TextUtils.isEmpty(channel.getLogoUri())
                        && (!TextUtils.isEmpty(storedChannelLogoUri)
                        || isFirstTimeFetchChannelLogo)) {
                    channelsToRemove.add(channel);
                    sharedPreferencesEditor.remove(channelIdString);
                }
            }

            // Removes non existing channels from SharedPreferences.
            for (String channelId : uncheckedChannels.keySet()) {
                sharedPreferencesEditor.remove(channelId);
            }

            // Updates channel logos.
            for (Channel channel : channelsToUpdate) {
                if (isCancelled()) {
                    if (DEBUG) Log.d(TAG, "Fetching the channel logos has been canceled");
                    return null;
                }
                // Downloads the channel logo.
                String logoUri = channel.getLogoUri();
                ScaledBitmapInfo bitmapInfo = BitmapUtils.decodeSampledBitmapFromUriString(
                        mContext, logoUri, Integer.MAX_VALUE, Integer.MAX_VALUE);
                if (bitmapInfo == null) {
                    Log.e(TAG, "Failed to load bitmap. {channelName=" + channel.getDisplayName()
                            + ", " + "logoUri=" + logoUri + "}");
                    continue;
                }
                if (isCancelled()) {
                    if (DEBUG) Log.d(TAG, "Fetching the channel logos has been canceled");
                    return null;
                }

                // Inserts the logo to DB.
                Uri dstLogoUri = TvContract.buildChannelLogoUri(channel.getId());
                try (OutputStream os = mContext.getContentResolver().openOutputStream(dstLogoUri)) {
                    bitmapInfo.bitmap.compress(CompressFormat.PNG, 100, os);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write " + logoUri + "  to " + dstLogoUri, e);
                    // Removes it from the shared preference for the failed channels to make it
                    // retry next time.
                    sharedPreferencesEditor.remove(Long.toString(channel.getId()));
                    continue;
                }
                if (DEBUG) {
                    Log.d(TAG, "Inserting logo file to DB succeeded. {from=" + logoUri + ", to="
                            + dstLogoUri + "}");
                }
            }

            // Removes the logos for the channels that have logos before but now
            // their logo uris are null.
            boolean deleteChannelLogoFailed = false;
            if (!channelsToRemove.isEmpty()) {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                for (Channel channel : channelsToRemove) {
                    ops.add(ContentProviderOperation.newDelete(
                        TvContract.buildChannelLogoUri(channel.getId())).build());
                }
                try {
                    mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                } catch (RemoteException | OperationApplicationException e) {
                    deleteChannelLogoFailed = true;
                    Log.e(TAG, "Error deleting obsolete channels", e);
                }
            }
            if (isFirstTimeFetchChannelLogo && !deleteChannelLogoFailed) {
                sharedPreferencesEditor.putBoolean(
                        PREF_KEY_IS_FIRST_TIME_FETCH_CHANNEL_LOGO, false);
            }
            sharedPreferencesEditor.commit();
            if (DEBUG) Log.d(TAG, "Fetching logos has been finished successfully.");
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            sFetchTask = null;
        }
    }
}
