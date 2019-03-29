/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.tv.cts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StubTunerTvInputService extends TvInputService {
    private static final List<TvTrackInfo> mTrackList = new ArrayList<>();

    public static void clearTracks() {
        mTrackList.clear();
    }

    public static void injectTrack(TvTrackInfo... tracks) {
        mTrackList.addAll(Arrays.asList(tracks));
    }

    public static void insertChannels(ContentResolver resolver, TvInputInfo info) {
        if (!info.getServiceInfo().name.equals(StubTunerTvInputService.class.getName())) {
            throw new IllegalArgumentException("info mismatch");
        }
        ContentValues redValues = new ContentValues();
        redValues.put(TvContract.Channels.COLUMN_INPUT_ID, info.getId());
        redValues.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, "0");
        redValues.put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Red");
        redValues.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, new byte[] { 0 });
        ContentValues greenValues = new ContentValues();
        greenValues.put(TvContract.Channels.COLUMN_INPUT_ID, info.getId());
        greenValues.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, "1");
        greenValues.put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Green");
        greenValues.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, new byte[] { 1 });
        ContentValues blueValues = new ContentValues();
        blueValues.put(TvContract.Channels.COLUMN_INPUT_ID, info.getId());
        blueValues.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, "2");
        blueValues.put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Blue");
        blueValues.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA, new byte[] { 2 });
        resolver.bulkInsert(TvContract.Channels.CONTENT_URI,
                new ContentValues[] { redValues, greenValues, blueValues });
    }

    public static void deleteChannels(ContentResolver resolver, TvInputInfo info) {
        if (!info.getServiceInfo().name.equals(StubTunerTvInputService.class.getName())) {
            throw new IllegalArgumentException("info mismatch");
        }
        resolver.delete(TvContract.buildChannelsUriForInput(info.getId()), null, null);
    }

    @Override
    public Session onCreateSession(String inputId) {
        return new StubSessionImpl(this);
    }

    static class StubSessionImpl extends Session {
        private static final int[] COLORS = { Color.RED, Color.GREEN, Color.BLUE };
        private Surface mSurface;
        private Object mLock = new Object();
        private int mCurrentIndex = -1;
        private Context mContext;

        StubSessionImpl(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onRelease() {
        }

        private void updateSurfaceLocked() {
            if (mCurrentIndex >= 0 && mSurface != null) {
                try {
                    Canvas c = mSurface.lockCanvas(null);
                    c.drawColor(COLORS[mCurrentIndex]);
                    mSurface.unlockCanvasAndPost(c);
                } catch (IllegalArgumentException e) {
                    mSurface = null;
                }
            }
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            synchronized (mLock) {
                mSurface = surface;
                updateSurfaceLocked();
                return true;
            }
        }

        @Override
        public void onSetStreamVolume(float volume) {
        }

        @Override
        public boolean onTune(Uri channelUri) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            synchronized (mLock) {
                String[] projection = { TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA };
                Cursor cursor = mContext.getContentResolver().query(
                        channelUri, projection, null, null, null);
                try {
                    if (cursor != null && cursor.moveToNext()) {
                        mCurrentIndex = cursor.getBlob(0)[0];
                    } else {
                        mCurrentIndex = -1;
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                updateSurfaceLocked();
                // Notify tracks
                if (mCurrentIndex == 0) {
                    notifyTracksChanged(mTrackList);
                    for (TvTrackInfo track : mTrackList) {
                        if (track.getType() == TvTrackInfo.TYPE_VIDEO) {
                            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, track.getId());
                            break;
                        }
                    }
                    for (TvTrackInfo track : mTrackList) {
                        if (track.getType() == TvTrackInfo.TYPE_AUDIO) {
                            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, track.getId());
                            break;
                        }
                    }
                }
            }
            notifyVideoAvailable();
            return true;
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            notifyTrackSelected(type, trackId);
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
        }
    }
}
