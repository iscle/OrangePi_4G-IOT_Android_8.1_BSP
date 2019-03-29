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

import android.database.Cursor;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraction of SQLite database row.
 */
public final class Entity<E> {

    private final List<Field> mFields;
    private final Map<String, Field> mFieldMap;

    private Entity(final Builder<E> builder) {
        mFields = builder.mFields;
        mFieldMap = builder.mFieldMap;
    }

    /**
     * Returns SQL statement to create this entity/row, such that
     * "(_id INTEGER PRIMARY KEY AUTOINCREMENT, column2_name column2_type, ...)".
     */
    public String createEntitySql() {
        final StringBuilder sb = new StringBuilder("(");
        for (final Field field : mFields) {
            if (field.pos > 0) sb.append(", ");
            sb.append(field.name).append(" ").append(field.sqLiteType);
            if (field.name.equals(BaseColumns._ID)) {
                sb.append(" PRIMARY KEY AUTOINCREMENT");
            }
        }
        return sb.append(")").toString();
    }

    public Field getField(final String fieldName) {
        return mFieldMap.get(fieldName);
    }

    /**
     * {@link Entity} builder.
     */
    public static final class Builder<E> {
        private final List<Field> mFields = new ArrayList<>();
        private final Map<String, Field> mFieldMap = new HashMap<>();
        private int mPos = 0;

        public Builder() {
            addFieldInternal(BaseColumns._ID, Cursor.FIELD_TYPE_INTEGER);
        }

        public Builder<E> addField(@NonNull final String name, final int fieldType) {
            addFieldInternal(name, fieldType);
            return this;
        }

        public Entity<E> build() {
            return new Entity<>(this);
        }

        private void addFieldInternal(final String name, final int fieldType) {
            if (mFieldMap.containsKey(name)) {
                throw new IllegalArgumentException("Field " + name + " already exists at "
                        + mFieldMap.get(name).pos);
            }
            final Field field = Field.newInstance(mPos++, name, fieldType);
            mFields.add(field);
            mFieldMap.put(field.name, field);
        }
    }
}
