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
package android.media.cts;

import android.media.MediaDescription;
import android.media.browse.MediaBrowser.MediaItem;
import android.os.Parcel;
import android.test.AndroidTestCase;

/**
 * Test {@link android.media.browse.MediaBrowser.MediaItem}.
 */
public class MediaItemTest extends AndroidTestCase {
    private static final String DESCRIPTION = "test_description";
    private static final String MEDIA_ID = "test_media_id";
    private static final String TITLE = "test_title";
    private static final String SUBTITLE = "test_subtitle";

    public void testBrowsableMediaItem() {
        MediaDescription description = new MediaDescription.Builder()
                .setDescription(DESCRIPTION).setMediaId(MEDIA_ID)
                .setTitle(TITLE).setSubtitle(SUBTITLE).build();
        MediaItem mediaItem = new MediaItem(description, MediaItem.FLAG_BROWSABLE);

        assertEquals(description.toString(), mediaItem.getDescription().toString());
        assertEquals(MEDIA_ID, mediaItem.getMediaId());
        assertEquals(MediaItem.FLAG_BROWSABLE, mediaItem.getFlags());
        assertTrue(mediaItem.isBrowsable());
        assertFalse(mediaItem.isPlayable());
        assertEquals(0, mediaItem.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        mediaItem.writeToParcel(p, 0);
        p.setDataPosition(0);
        assertEquals(mediaItem.getFlags(), p.readInt());
        assertEquals(description.toString(),
                MediaDescription.CREATOR.createFromParcel(p).toString());
        p.recycle();
    }

    public void testPlayableMediaItem() {
        MediaDescription description = new MediaDescription.Builder()
                .setDescription(DESCRIPTION).setMediaId(MEDIA_ID)
                .setTitle(TITLE).setSubtitle(SUBTITLE).build();
        MediaItem mediaItem = new MediaItem(description, MediaItem.FLAG_PLAYABLE);

        assertEquals(description.toString(), mediaItem.getDescription().toString());
        assertEquals(MEDIA_ID, mediaItem.getMediaId());
        assertEquals(MediaItem.FLAG_PLAYABLE, mediaItem.getFlags());
        assertFalse(mediaItem.isBrowsable());
        assertTrue(mediaItem.isPlayable());
        assertEquals(0, mediaItem.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        mediaItem.writeToParcel(p, 0);
        p.setDataPosition(0);
        assertEquals(mediaItem.getFlags(), p.readInt());
        assertEquals(description.toString(),
                MediaDescription.CREATOR.createFromParcel(p).toString());
        p.recycle();
    }
}