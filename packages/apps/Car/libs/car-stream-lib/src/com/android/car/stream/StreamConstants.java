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

public class StreamConstants {
    public static final int CARD_TYPE_CURRENT_CALL = 1;
    public static final int CARD_TYPE_RECENT_CALL = 2;
    public static final int CARD_TYPE_MEDIA = 3;

    /**
     * An id that is used for {@link StreamCard}s that come from a media application that is not
     * the radio. For the latter, {@link #RADIO_CARD_ID} will be used.
     */
    public static long MEDIA_CARD_ID = -1L;

    /**
     * An id that is used for {@link StreamCard}s that come from a radio application.
     */
    public static long RADIO_CARD_ID = -2L;

    public static final String STREAM_PRODUCER_BIND_ACTION = "stream_producer_bind_action";
    public static final String STREAM_CONSUMER_BIND_ACTION = "stream_consumer_bind_action";

    /**
     * Extra used by stream permissions activity. When set to true, the activity will only check
     * for permissions but not request them.
     */
    public static final String STREAM_PERMISSION_CHECK_PERMISSIONS_ONLY = "stream_permission_check";
}
