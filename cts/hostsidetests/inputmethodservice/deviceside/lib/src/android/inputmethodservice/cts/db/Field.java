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

package android.inputmethodservice.cts.db;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Abstraction of SQLite database column.
 */
public abstract class Field {

    /** Field position in a row. */
    final int pos;

    /** Column name of this field. */
    final String name;

    /** Field type of SQLite. */
    final String sqLiteType;

    public static Field newInstance(final int pos, final String name, final int fieldType) {
        switch (fieldType) {
            case Cursor.FIELD_TYPE_INTEGER:
                return new IntegerField(pos, name);
            case Cursor.FIELD_TYPE_STRING:
                return new StringField(pos, name);
            default:
                throw new IllegalArgumentException("Unknown field type: " + fieldType);
        }
    }

    private Field(final int pos, final String name, final int fieldType) {
        this.pos = pos;
        this.name = name;
        this.sqLiteType = toSqLiteType(fieldType);
    }

    public long getLong(final Cursor cursor) {
        throw buildException(Cursor.FIELD_TYPE_INTEGER);
    }

    public String getString(final Cursor cursor) {
        throw buildException(Cursor.FIELD_TYPE_STRING);
    }

    public void putLong(final ContentValues values, final long value) {
        throw buildException(Cursor.FIELD_TYPE_INTEGER);
    }

    public void putString(final ContentValues values, final String value) {
        throw buildException(Cursor.FIELD_TYPE_STRING);
    }

    private UnsupportedOperationException buildException(final int expectedFieldType) {
        return new UnsupportedOperationException("Illegal type: " + name + " is " + sqLiteType
                + ", expected " + toSqLiteType(expectedFieldType));
    }

    private static String toSqLiteType(final int fieldType) {
        switch (fieldType) {
            case Cursor.FIELD_TYPE_NULL:
                return "NULL";
            case Cursor.FIELD_TYPE_INTEGER:
                return "INTEGER";
            case Cursor.FIELD_TYPE_FLOAT:
                return "REAL";
            case Cursor.FIELD_TYPE_STRING:
                return "TEXT";
            case Cursor.FIELD_TYPE_BLOB:
                return "BLOB";
            default:
                throw new IllegalArgumentException("Unknown field type: " + fieldType);
        }
    }

    /**
     * Abstraction of INTEGER type field.
     */
    private static final class IntegerField extends Field {

        IntegerField(final int pos, final String name) {
            super(pos, name, Cursor.FIELD_TYPE_INTEGER);
        }

        @Override
        public long getLong(final Cursor cursor) {
            return cursor.getLong(pos);
        }

        @Override
        public void putLong(final ContentValues values, final long value) {
            values.put(name, value);
        }
    }

    /**
     * Abstraction of STRING type field.
     */
    private static final class StringField extends Field {

        StringField(final int pos, final String name) {
            super(pos, name, Cursor.FIELD_TYPE_STRING);
        }

        @Override
        public String getString(final Cursor cursor) {
            return cursor.getString(pos);
        }

        @Override
        public void putString(final ContentValues values, final String value) {
            values.put(name, value);
        }
    }
}
