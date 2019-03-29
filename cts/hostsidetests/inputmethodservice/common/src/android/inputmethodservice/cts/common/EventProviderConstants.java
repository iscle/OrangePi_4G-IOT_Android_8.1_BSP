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

package android.inputmethodservice.cts.common;

/**
 * Constants of CtsInputMethodServiceEventProvider.apk that keeps IME event records.
 */
public final class EventProviderConstants {

    // This is constants holding class, can't instantiate.
    private EventProviderConstants() {}

    /** Package name of the IME event provider. */
    public static final String PACKAGE = "android.inputmethodservice.cts.provider";

    /** Class name of IME event provider. */
    public static final String CLASS =
            "android.inputmethodservice.cts.provider.EventProviderConstants";

    /** APK file name. */
    public static final String APK = "CtsInputMethodServiceEventProvider.apk";

    /** The authority of IME event provider. */
    public static final String AUTHORITY = "android.inputmethodservice.cts.provider";

    /** The base URI of IME event provider. */
    private static final String BASE_URI = "content://" + AUTHORITY;

    /**
     * The Android platform's base MIME type for a content: URI containing a Cursor of a single
     * item. Copied from android.content.ContentResolver.CURSOR_ITEM_BASE_TYPE.
     */
    private static final String CURSOR_ITEM_BASE_TYPE = "vnd.android.cursor.item";

    /**
     * The Android platform's base MIME type for a content: URI containing a Cursor of zero or more
     * items. Copied from android.content.ContentResolver.CURSOR_DIR_BASE_TYPE.
     */
    private static final String CURSOR_DIR_BASE_TYPE = "vnd.android.cursor.dir";

    /** Constants of Events table of IME event provider. */
    public static final class EventTableConstants {

        // This is constants holding class, can't instantiate.
        private EventTableConstants() {}

        /** Column name of the table that holds Event extras in json format. */
        public static final String EXTRAS = "extras";

        /** Name of the table in content provider and database. */
        public static final String NAME = "events";

        /** Column name of the table that holds who sends an event. */
        public static final String SENDER = "sender";

        /** Column name of the table that holds what type of event is. */
        public static final String TYPE = "type";

        /** Column name of the table that holds when an event happens. */
        public static final String TIME = "time";

        /** Content URI of the table. */
        public static final String CONTENT_URI = BASE_URI + "/" + NAME;

        /** MIME type of a cursor of zero or more items of the table. */
        public static final String TYPE_DIR =
                CURSOR_DIR_BASE_TYPE + "/" + AUTHORITY + ".ime_event";

        /** MIME tye of a cursor of a single item of the table. */
        public static final String TYPE_ITEM =
                CURSOR_ITEM_BASE_TYPE + "/" + AUTHORITY + ".ime_event";
    }
}
