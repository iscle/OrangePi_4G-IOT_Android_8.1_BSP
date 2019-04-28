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
package com.android.tv.testing;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static helper methods for working with {@link android.media.tv.TvContract}.
 */
public class ChannelUtils {
    private static final String TAG = "ChannelUtils";
    private static final boolean DEBUG = false;

    /**
     * Query and return the map of (channel_id, ChannelInfo).
     * See: {@link ChannelInfo#fromCursor(Cursor)}.
     */
    @WorkerThread
    public static Map<Long, ChannelInfo> queryChannelInfoMapForTvInput(
            Context context, String inputId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        Map<Long, ChannelInfo> map = new HashMap<>();

        String[] projections = new String[ChannelInfo.PROJECTION.length + 1];
        projections[0] = Channels._ID;
        System.arraycopy(ChannelInfo.PROJECTION, 0, projections, 1, ChannelInfo.PROJECTION.length);
        try (Cursor cursor = context.getContentResolver()
                .query(uri, projections, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    map.put(cursor.getLong(0), ChannelInfo.fromCursor(cursor));
                }
            }
            return map;
        }
    }

    @WorkerThread
    public static void updateChannels(Context context, String inputId, List<ChannelInfo> channels) {
        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Long> existingChannelsMap = new SparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(channelsUri, projection, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                existingChannelsMap.put(originalNetworkId, rowId);
            }
        }

        Map<Uri, String> logos = new HashMap<>();
        for (ChannelInfo channel : channels) {
            // If a channel exists, update it. If not, insert a new one.
            ContentValues values = new ContentValues();
            values.put(Channels.COLUMN_INPUT_ID, inputId);
            values.put(Channels.COLUMN_DISPLAY_NUMBER, channel.number);
            values.put(Channels.COLUMN_DISPLAY_NAME, channel.name);
            values.put(Channels.COLUMN_ORIGINAL_NETWORK_ID, channel.originalNetworkId);
            String videoFormat = channel.getVideoFormat();
            if (videoFormat != null) {
                values.put(Channels.COLUMN_VIDEO_FORMAT, videoFormat);
            } else {
                values.putNull(Channels.COLUMN_VIDEO_FORMAT);
            }
            if (!TextUtils.isEmpty(channel.appLinkText)) {
                values.put(Channels.COLUMN_APP_LINK_TEXT, channel.appLinkText);
            }
            if (channel.appLinkColor != 0) {
                values.put(Channels.COLUMN_APP_LINK_COLOR, channel.appLinkColor);
            }
            if (!TextUtils.isEmpty(channel.appLinkPosterArtUri)) {
                values.put(Channels.COLUMN_APP_LINK_POSTER_ART_URI, channel.appLinkPosterArtUri);
            }
            if (!TextUtils.isEmpty(channel.appLinkIconUri)) {
                values.put(Channels.COLUMN_APP_LINK_ICON_URI, channel.appLinkIconUri);
            }
            if (!TextUtils.isEmpty(channel.appLinkIntentUri)) {
                values.put(Channels.COLUMN_APP_LINK_INTENT_URI, channel.appLinkIntentUri);
            }
            Long rowId = existingChannelsMap.get(channel.originalNetworkId);
            Uri uri;
            if (rowId == null) {
                if (DEBUG) Log.d(TAG, "Inserting "+ channel);
                uri = resolver.insert(TvContract.Channels.CONTENT_URI, values);
            } else {
                if (DEBUG) Log.d(TAG, "Updating "+ channel);
                uri = TvContract.buildChannelUri(rowId);
                resolver.update(uri, values, null, null);
                existingChannelsMap.remove(channel.originalNetworkId);
            }
            if (!TextUtils.isEmpty(channel.logoUrl)) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.logoUrl);
            }
        }
        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }

        // Deletes channels which don't exist in the new feed.
        int size = existingChannelsMap.size();
        for (int i = 0; i < size; ++i) {
            Long rowId = existingChannelsMap.valueAt(i);
            resolver.delete(TvContract.buildChannelUri(rowId), null, null);
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    private ChannelUtils() {
        // Prevent instantiation.
    }

    public static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final Context mContext;

        InsertLogosTask(Context context) {
            mContext = context;
        }

        @SafeVarargs
        @Override
        public final Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    if (uri == null) {
                        continue;
                    }
                    Uri logoUri = Uri.parse(logos.get(uri));
                    try (InputStream is = mContext.getContentResolver().openInputStream(logoUri);
                            OutputStream os = mContext.getContentResolver().openOutputStream(uri)) {
                        copy(is, os);
                    } catch (IOException ioe) {
                        Log.e(TAG, "Failed to write " + logoUri + "  to " + uri, ioe);
                    }
                }
            }
            return null;
        }
    }
}
