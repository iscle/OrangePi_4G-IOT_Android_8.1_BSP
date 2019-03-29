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
 * limitations under the License
 */

package android.inputmethodservice.cts;

import static android.inputmethodservice.cts.common.DeviceEventConstants.ACTION_DEVICE_EVENT;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_PARAMS;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_TIME;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_TYPE;
import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_SENDER;
import static android.inputmethodservice.cts.common.DeviceEventConstants.RECEIVER_CLASS;
import static android.inputmethodservice.cts.common.DeviceEventConstants.RECEIVER_PACKAGE;


import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.inputmethodservice.cts.common.DeviceEventConstants;
import android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventType;
import android.inputmethodservice.cts.common.DeviceEventConstants.DeviceEventTypeParam;
import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.inputmethodservice.cts.common.test.TestInfo;
import android.inputmethodservice.cts.db.Entity;
import android.inputmethodservice.cts.db.Field;
import android.inputmethodservice.cts.db.Table;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.android.json.stream.JsonReader;
import com.android.json.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Device event object.
 * <p>Device event is one of IME event and Test event, and is used to test behaviors of Input Method
 * Framework.</p>
 */
public final class DeviceEvent {

    private static final boolean DEBUG_STREAM = false;

    public static final Table<DeviceEvent> TABLE = new DeviceEventTable(EventTableConstants.NAME);

    public static IntentBuilder builder() {
        return new IntentBuilder();
    }

    /**
     * Builder to create an intent to send a device event.
     * The built intent that has event {@code sender}, {@code type}, {@code paramsString}, time from
     * {@link SystemClock#uptimeMillis()}, and target component of event receiver.
     *
     */
    public static final class IntentBuilder {
        String mSender;
        DeviceEventType mType;
        JsonWriter mJsonWriter;
        StringWriter mStringWriter;

        /**
         * @param type an event type defined at {@link DeviceEventType}.
         */
        public IntentBuilder setType(DeviceEventType type) {
            mType = type;
            return this;
        }

        /**
         * @param sender an event sender.
         */
        public void setSender(String sender) {
            mSender = sender;
        }

        public IntentBuilder with(DeviceEventTypeParam eventParam, boolean value) {
            appendToJson(eventParam, value);
            return this;
        }

        public Intent build() {
            Intent intent = new Intent()
                    .setAction(ACTION_DEVICE_EVENT)
                    .setClassName(RECEIVER_PACKAGE, RECEIVER_CLASS)
                    .putExtra(EXTRA_EVENT_SENDER, mSender)
                    .putExtra(EXTRA_EVENT_TYPE, mType.name())
                    .putExtra(EXTRA_EVENT_PARAMS, getJsonString())
                    .putExtra(EXTRA_EVENT_TIME, SystemClock.uptimeMillis());

            mJsonWriter = null;
            mStringWriter = null;
            return intent;
        }

        private String getJsonString() {
            if (mJsonWriter == null) {
                return "";
            }
            try {
                mJsonWriter.endObject();
                mJsonWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException("IntentBuilder.getJsonString() failed.", e);
            }
            return mStringWriter.toString();
        }

        private void appendToJson(DeviceEventTypeParam eventParam, boolean value) {
            final String key = eventParam.getName();
            if (TextUtils.isEmpty(key)) {
                return;
            }
            try {
                if (mJsonWriter == null) {
                    mStringWriter = new StringWriter();
                    mJsonWriter = new JsonWriter(mStringWriter);
                    mJsonWriter.beginObject();
                }
                mJsonWriter.name(key).value(value);
            } catch (IOException e) {
                throw new RuntimeException("IntentBuilder.appendToJson() failed.", e);
            }
        }
    }

    /**
     * Create an {@link DeviceEvent} object from an intent.
     * @param intent a device event intent defined at {@link DeviceEventConstants}.
     * @return {@link DeviceEvent} object that has event sender, type, and time form an
     *         {@code intent}.
     */
    public static DeviceEvent newEvent(final Intent intent) {
        final String sender = intent.getStringExtra(EXTRA_EVENT_SENDER);
        if (sender == null) {
            throw new IllegalArgumentException(
                    "Intent must have " + EXTRA_EVENT_SENDER + ": " + intent);
        }

        final String typeString = intent.getStringExtra(EXTRA_EVENT_TYPE);
        if (typeString == null) {
            throw new IllegalArgumentException(
                    "Intent must have " + EXTRA_EVENT_TYPE + ": " + intent);
        }
        final DeviceEventType type = DeviceEventType.valueOf(typeString);

        if (!intent.hasExtra(EXTRA_EVENT_TIME)) {
            throw new IllegalArgumentException(
                    "Intent must have " + EXTRA_EVENT_TIME + ": " + intent);
        }

        String paramsString = intent.getStringExtra(DeviceEventConstants.EXTRA_EVENT_PARAMS);
        if (paramsString == null) {
            paramsString = "";
        }

        return new DeviceEvent(
                sender,
                type,
                paramsString,
                intent.getLongExtra(EXTRA_EVENT_TIME, 0L));
    }

    /**
     * Build {@link ContentValues} object from {@link DeviceEvent} object.
     * @param event a {@link DeviceEvent} object to be converted.
     * @return a converted {@link ContentValues} object.
     */
    public static ContentValues buildContentValues(final DeviceEvent event) {
        return TABLE.buildContentValues(event);
    }

    /**
     * Build {@link Stream<DeviceEvent>} object from {@link Cursor} comes from Content Provider.
     * @param cursor a {@link Cursor} object to be converted.
     * @return a converted {@link Stream<DeviceEvent>} object.
     */
    public static Stream<DeviceEvent> buildStream(final Cursor cursor) {
        return TABLE.buildStream(cursor);
    }

    /**
     * Build {@link Predicate<DeviceEvent>} whether a device event comes from {@code sender}
     *
     * @param sender event sender.
     * @return {@link Predicate<DeviceEvent>} object.
     */
    public static Predicate<DeviceEvent> isFrom(final String sender) {
        return e -> e.sender.equals(sender);
    }

    /**
     * Build {@link Predicate<DeviceEvent>} whether a device event has an event {@code type}.
     *
     * @param type a event type defined in {@link DeviceEventType}.
     * @return {@link Predicate<DeviceEvent>} object.
     */
    public static Predicate<DeviceEvent> isType(final DeviceEventType type) {
        return e -> e.type == type;
    }

    /**
     * Build {@link Predicate<DeviceEvent>} whether a device event is newer than or equals to
     * {@code time}.
     *
     * @param time a time to compare against.
     * @return {@link Predicate<DeviceEvent>} object.
     */
    public static Predicate<DeviceEvent> isNewerThan(final long time) {
        return e -> e.time >= time;
    }

    /**
     * Event source, either Input Method class name or {@link TestInfo#getTestName()}.
     */
    @NonNull
    public final String sender;

    /**
     * Event type, either IME event or Test event.
     */
    @NonNull
    public final DeviceEventType type;

    /**
     * Event parameters formatted as json string.
     * e.g. {@link DeviceEventTypeParam#ON_START_INPUT_RESTARTING}
     */
    public final String paramsString;

    /**
     * Event time, value is from {@link SystemClock#uptimeMillis()}.
     */
    public final long time;

    private DeviceEvent(
            final String sender, final DeviceEventType type, String paramsString, final long time) {
        this.sender = sender;
        this.type = type;
        this.paramsString = paramsString;
        this.time = time;
    }

    @Override
    public String toString() {
        return "Event{ time:" + time + " type:" + type + " sender:" + sender + " }";
    }

    /**
     * @param eventParam {@link DeviceEventTypeParam} to look for.
     * @param event {@link DeviceEvent} to look in.
     * @return Event parameter for provided key. If key is not found in
     * {@link DeviceEvent#paramsString}, null is returned.
     *
     * TODO: Support other primitive and custom types.
     */
    @Nullable
    public static Boolean getEventParamBoolean(
            DeviceEventTypeParam eventParam, final DeviceEvent event) {
        StringReader stringReader = new StringReader(event.paramsString);
        JsonReader reader = new JsonReader(stringReader);

        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals(eventParam.getName())) {
                    Boolean value = reader.nextBoolean();
                    reader.endObject();
                    return value;
                }
            }
            reader.endObject();
        } catch (IOException e) {
            throw new RuntimeException("DeviceEvent.getEventParamBoolean() failed.", e);
        }
        return null;
    }

    /**
     * Abstraction of device event table in database.
     */
    private static final class DeviceEventTable extends Table<DeviceEvent> {

        private static final String LOG_TAG = DeviceEventTable.class.getSimpleName();

        private final Field SENDER;
        private final Field TYPE;
        private final Field TIME;
        private final Field PARAMS;

        private DeviceEventTable(final String name) {
            super(name, new Entity.Builder<DeviceEvent>()
                    .addField(EventTableConstants.SENDER, Cursor.FIELD_TYPE_STRING)
                    .addField(EventTableConstants.TYPE, Cursor.FIELD_TYPE_STRING)
                    .addField(EventTableConstants.TIME, Cursor.FIELD_TYPE_INTEGER)
                    .addField(EventTableConstants.EXTRAS, Cursor.FIELD_TYPE_STRING)
                    .build());
            SENDER = getField(EventTableConstants.SENDER);
            TYPE = getField(EventTableConstants.TYPE);
            TIME = getField(EventTableConstants.TIME);
            PARAMS = getField(EventTableConstants.EXTRAS);
        }

        @Override
        public ContentValues buildContentValues(final DeviceEvent event) {
            final ContentValues values = new ContentValues();
            SENDER.putString(values, event.sender);
            TYPE.putString(values, event.type.name());
            PARAMS.putString(values, event.paramsString);
            TIME.putLong(values, event.time);
            return values;
        }

        @Override
        public Stream<DeviceEvent> buildStream(Cursor cursor) {
            if (DEBUG_STREAM) {
                Log.d(LOG_TAG, "buildStream:");
            }
            final Stream.Builder<DeviceEvent> builder = Stream.builder();
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                final DeviceEvent event = new DeviceEvent(
                        SENDER.getString(cursor),
                        DeviceEventType.valueOf(TYPE.getString(cursor)),
                        PARAMS.getString(cursor),
                        TIME.getLong(cursor));
                builder.accept(event);
                if (DEBUG_STREAM) {
                    Log.d(LOG_TAG, " event=" +event);
                }
            }
            return builder.build();
        }
    }
}
