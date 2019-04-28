/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.stream;

/**
 * A class that holds various common constants used through the Stream service.
 */
public class StreamServiceConstants {
    /**
     * The id that should be used for all media cards. Using a common id for all media cards
     * ensure that only one will show at a time.
     */
    public static long MEDIA_CARD_ID = -1L;

    /**
     * The id within the {@link MediaPlaybackExtension} that indicates this MediaPlaybackExtension
     * is coming from a non-radio application.
     *
     * <p>The reason that this id is necessary is because the radio does not use the MediaSession
     * to notify of playback state. Thus, notifications about playback state for media apps and
     * radio are not guaranteed to be in order. This id along with {@link #MEDIA_EXTENSION_ID_RADIO}
     * will help differentiate which application is firing a change.
     */
    public static long MEDIA_EXTENSION_ID_NON_RADIO = -1L;

    /**
     * The id within the {@link MediaPlaybackExtension} that indicates this MediaPlaybackExtension
     * is coming from a radio application.
     *
     * @see {@link #MEDIA_EXTENSION_ID_NON_RADIO}
     */
    public static long MEDIA_EXTENSION_ID_RADIO= -2L;


    private StreamServiceConstants() {}
}
