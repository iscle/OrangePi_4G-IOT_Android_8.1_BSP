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

package android.media.tv.cts;

import android.media.tv.TvTrackInfo;
import android.os.Bundle;
import android.os.Parcel;
import android.test.AndroidTestCase;

/**
 * Test {@link android.media.tv.TvTrackInfo}.
 */
public class TvTrackInfoTest extends AndroidTestCase {

    public void testAudioTrackInfoOp() {
        if (!Utils.hasTvInputFramework(getContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, "id_audio")
                .setAudioChannelCount(2)
                .setAudioSampleRate(48000)
                .setLanguage("eng")
                .setExtra(bundle)
                .build();
        assertEquals(TvTrackInfo.TYPE_AUDIO, info.getType());
        assertEquals("id_audio", info.getId());
        assertEquals(2, info.getAudioChannelCount());
        assertEquals(48000, info.getAudioSampleRate());
        assertEquals("eng", info.getLanguage());
        assertEquals(bundle.get("testTrue"), info.getExtra().get("testTrue"));
        assertEquals(0, info.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        TvTrackInfo infoFromParcel = TvTrackInfo.CREATOR.createFromParcel(p);
        assertEquals(TvTrackInfo.TYPE_AUDIO, infoFromParcel.getType());
        assertEquals("id_audio", infoFromParcel.getId());
        assertEquals(2, infoFromParcel.getAudioChannelCount());
        assertEquals(48000, infoFromParcel.getAudioSampleRate());
        assertEquals("eng", infoFromParcel.getLanguage());
        assertEquals(bundle.get("testTrue"), infoFromParcel.getExtra().get("testTrue"));
        assertEquals(0, infoFromParcel.describeContents());
        p.recycle();
    }

    public void testVideoTrackInfoOp() {
        if (!Utils.hasTvInputFramework(getContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putBoolean("testTrue", true);
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_VIDEO, "id_video")
                .setVideoWidth(1920)
                .setVideoHeight(1080)
                .setVideoFrameRate(29.97f)
                .setVideoPixelAspectRatio(1.0f)
                .setVideoActiveFormatDescription((byte) 8)
                .setLanguage("eng")
                .setExtra(bundle)
                .build();
        assertEquals(TvTrackInfo.TYPE_VIDEO, info.getType());
        assertEquals("id_video", info.getId());
        assertEquals(1920, info.getVideoWidth());
        assertEquals(1080, info.getVideoHeight());
        assertEquals(29.97f, info.getVideoFrameRate());
        assertEquals(1.0f, info.getVideoPixelAspectRatio());
        assertEquals((byte) 8, info.getVideoActiveFormatDescription());
        assertEquals("eng", info.getLanguage());
        assertEquals(bundle.get("testTrue"), info.getExtra().get("testTrue"));
        assertEquals(0, info.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        TvTrackInfo infoFromParcel = TvTrackInfo.CREATOR.createFromParcel(p);
        assertEquals(TvTrackInfo.TYPE_VIDEO, infoFromParcel.getType());
        assertEquals("id_video", infoFromParcel.getId());
        assertEquals(1920, infoFromParcel.getVideoWidth());
        assertEquals(1080, infoFromParcel.getVideoHeight());
        assertEquals(29.97f, infoFromParcel.getVideoFrameRate());
        assertEquals(1.0f, infoFromParcel.getVideoPixelAspectRatio());
        assertEquals((byte) 8, infoFromParcel.getVideoActiveFormatDescription());
        assertEquals("eng", infoFromParcel.getLanguage());
        assertEquals(bundle.get("testTrue"), infoFromParcel.getExtra().get("testTrue"));
        assertEquals(0, infoFromParcel.describeContents());
        p.recycle();
    }

    public void testSubtitleTrackInfoOp() {
        if (!Utils.hasTvInputFramework(getContext())) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putBoolean("testTrue", true);
        final TvTrackInfo info = new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "id_subtitle")
                .setLanguage("eng")
                .setExtra(bundle)
                .build();
        assertEquals(TvTrackInfo.TYPE_SUBTITLE, info.getType());
        assertEquals("id_subtitle", info.getId());
        assertEquals("eng", info.getLanguage());
        assertEquals(bundle.get("testTrue"), info.getExtra().get("testTrue"));
        assertEquals(0, info.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        info.writeToParcel(p, 0);
        p.setDataPosition(0);
        TvTrackInfo infoFromParcel = TvTrackInfo.CREATOR.createFromParcel(p);
        assertEquals(TvTrackInfo.TYPE_SUBTITLE, infoFromParcel.getType());
        assertEquals("id_subtitle", infoFromParcel.getId());
        assertEquals("eng", infoFromParcel.getLanguage());
        assertEquals(bundle.get("testTrue"), infoFromParcel.getExtra().get("testTrue"));
        assertEquals(0, infoFromParcel.describeContents());
        p.recycle();
    }
}
