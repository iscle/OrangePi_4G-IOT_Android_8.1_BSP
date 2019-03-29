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
import android.support.annotation.NonNull;

import java.util.stream.Stream;

/**
 * Abstraction of SQLite database table.
 */
public abstract class Table<E> {

    public final String mName;
    private final Entity<E> mEntity;

    protected Table(final String name, final Entity<E> entity) {
        mName = name;
        mEntity = entity;
    }

    public String name() {
        return mName;
    }

    public abstract ContentValues buildContentValues(final E entity);

    public abstract Stream<E> buildStream(final Cursor cursor);

    /**
     * Returns SQL statement to create this table, such that
     * "CREATE TABLE IF NOT EXISTS table_name \
     *  (_id INTEGER PRIMARY KEY AUTOINCREMENT, column2_name column2_type, ...)"
     */
    @NonNull
    public String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + mName + " " + mEntity.createEntitySql();
    }

    protected Field getField(final String fieldName) {
        return mEntity.getField(fieldName);
    }
}
