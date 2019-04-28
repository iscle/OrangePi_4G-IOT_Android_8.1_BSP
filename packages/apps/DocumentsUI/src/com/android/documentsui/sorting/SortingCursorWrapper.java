/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.sorting;

import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Shared;
import com.android.documentsui.sorting.SortModel.SortDimensionId;

/**
 * Cursor wrapper that presents a sorted view of the underlying cursor. Handles
 * common {@link Document} sorting modes, such as ordering directories first.
 */
class SortingCursorWrapper extends AbstractCursor {
    private final Cursor mCursor;

    private final int[] mPosition;

    public SortingCursorWrapper(
            Cursor cursor, SortDimension dimension, Lookup<String, String> fileTypeLookup) {
        mCursor = cursor;

        final int count = cursor.getCount();
        mPosition = new int[count];
        boolean[] isDirs = new boolean[count];
        String[] stringValues = null;
        long[] longValues = null;
        String[] ids = new String[count];

        final @SortDimensionId int id = dimension.getId();
        switch (id) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
            case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                stringValues = new String[count];
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
            case SortModel.SORT_DIMENSION_ID_SIZE:
                longValues = new long[count];
                break;
        }

        cursor.moveToPosition(-1);
        for (int i = 0; i < count; i++) {
            cursor.moveToNext();
            mPosition[i] = i;

            final String mimeType = getCursorString(mCursor, Document.COLUMN_MIME_TYPE);
            isDirs[i] = Document.MIME_TYPE_DIR.equals(mimeType);
            ids[i] = getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);

            switch(id) {
                case SortModel.SORT_DIMENSION_ID_TITLE:
                    final String displayName = getCursorString(
                            mCursor, Document.COLUMN_DISPLAY_NAME);
                    stringValues[i] = displayName;
                    break;
                case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                    stringValues[i] = fileTypeLookup.lookup(mimeType);
                    break;
                case SortModel.SORT_DIMENSION_ID_DATE:
                    longValues[i] = getLastModified(mCursor);
                    break;
                case SortModel.SORT_DIMENSION_ID_SIZE:
                    longValues[i] = getCursorLong(mCursor, Document.COLUMN_SIZE);
                    break;
            }

        }

        switch (id) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
            case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                binarySort(stringValues, isDirs, mPosition, ids, dimension.getSortDirection());
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
            case SortModel.SORT_DIMENSION_ID_SIZE:
                binarySort(longValues, isDirs, mPosition, ids, dimension.getSortDirection());
                break;
        }

    }

    @Override
    public void close() {
        super.close();
        mCursor.close();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(mPosition[newPosition]);
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public int getCount() {
        return mCursor.getCount();
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    /**
     * @return Timestamp for the given document. Some docs (e.g. active downloads) have a null
     * timestamp - these will be replaced with MAX_LONG so that such files get sorted to the top
     * when sorting descending by date.
     */
    private static long getLastModified(Cursor cursor) {
        long l = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
        return (l == -1) ? Long.MAX_VALUE : l;
    }

    /**
     * Borrowed from TimSort.binarySort(), but modified to sort two column
     * dataset.
     */
    private static void binarySort(
            String[] sortKey,
            boolean[] isDirs,
            int[] positions,
            String[] ids,
            @SortDimension.SortDirection int direction) {
        final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final String pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = (left + right) >>> 1;

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final String lhs = pivotValue;
                    final String rhs = sortKey[mid];
                    switch (direction) {
                        case SortDimension.SORT_DIRECTION_ASCENDING:
                            compare = Shared.compareToIgnoreCaseNullable(lhs, rhs);
                            break;
                        case SortDimension.SORT_DIRECTION_DESCENDING:
                            compare = -Shared.compareToIgnoreCaseNullable(lhs, rhs);
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unknown sorting direction: " + direction);
                    }
                }

                // Use document ID as a tie breaker to achieve stable sort result.
                if (compare == 0) {
                    compare = pivotId.compareTo(ids[mid]);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
        }
    }

    /**
     * Borrowed from TimSort.binarySort(), but modified to sort two column
     * dataset.
     */
    private static void binarySort(
            long[] sortKey,
            boolean[] isDirs,
            int[] positions,
            String[] ids,
            @SortDimension.SortDirection int direction) {
        final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final long pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = ((left + right) >>> 1);

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final long lhs = pivotValue;
                    final long rhs = sortKey[mid];
                    switch (direction) {
                        case SortDimension.SORT_DIRECTION_ASCENDING:
                            compare = Long.compare(lhs, rhs);
                            break;
                        case SortDimension.SORT_DIRECTION_DESCENDING:
                            compare = -Long.compare(lhs, rhs);
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unknown sorting direction: " + direction);
                    }
                }

                // If numerical comparison yields a tie, use document ID as a tie breaker.  This
                // will yield stable results even if incoming items are continually shuffling and
                // have identical numerical sort keys.  One common example of this scenario is seen
                // when sorting a set of active downloads by mod time.
                if (compare == 0) {
                    compare = pivotId.compareTo(ids[mid]);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                    ids[left + 2] = ids[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    ids[left + 1] = ids[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
                    System.arraycopy(ids, left, ids, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
            ids[left] = pivotId;
        }
    }
}
