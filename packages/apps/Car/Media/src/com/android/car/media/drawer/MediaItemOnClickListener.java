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
package com.android.car.media.drawer;

import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;

/**
 * Interface for an object that will be notified when an item in the play queue has been clicked.
 */
interface MediaItemOnClickListener {
    /**
     * Called when an item in the queue has been clicked.
     *
     * @param queueItem The {@link MediaSession.QueueItem} corresponding to the one that has been
     *                  clicked.
     */
    void onQueueItemClicked(MediaSession.QueueItem queueItem);

    /**
     * Called when an item in a list of playable media items has been clicked.
     *
     * @param mediaItem The {@link MediaBrowser.MediaItem} corresponding to the one that been
     *                  clicked.
     */
    void onMediaItemClicked(MediaBrowser.MediaItem mediaItem);
}
