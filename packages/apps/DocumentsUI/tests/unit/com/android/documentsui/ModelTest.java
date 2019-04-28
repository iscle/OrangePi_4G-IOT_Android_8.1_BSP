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

package com.android.documentsui;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.provider.DocumentsContract.Document;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.TestFeatures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.BitSet;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ModelTest {

    private static final int ITEM_COUNT = 10;
    private static final String AUTHORITY = "test_authority";

    private static final String[] COLUMNS = new String[]{
        RootCursorWrapper.COLUMN_AUTHORITY,
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_FLAGS,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE
    };

    private static final String[] NAMES = new String[] {
            "4",
            "foo",
            "1",
            "bar",
            "*(Ljifl;a",
            "0",
            "baz",
            "2",
            "3",
            "%$%VD"
        };

    private Cursor cursor;
    private Model model;
    private TestFeatures features;

    @Before
    public void setUp() {
        features = new TestFeatures();

        Random rand = new Random();

        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE);
            // Generate random document names and sizes. This forces the model's internal sort code
            // to actually do something.
            row.add(Document.COLUMN_DISPLAY_NAME, NAMES[i]);
            row.add(Document.COLUMN_SIZE, rand.nextInt());
        }
        cursor = c;

        DirectoryResult r = new DirectoryResult();
        r.cursor = cursor;

        // Instantiate the model with a dummy view adapter and listener that (for now) do nothing.
        model = new Model(features);
        // not sure why we add a listener here at all.
        model.addUpdateListener(new TestEventListener<>());
        model.update(r);
    }

    // Tests that the item count is correct.
    @Test
    public void testItemCount() {
        assertEquals(ITEM_COUNT, model.getItemCount());
    }

    // Tests multiple authorities with clashing document IDs.
    @Test
    public void testModelIdIsUnique() {
        MatrixCursor cIn1 = new MatrixCursor(COLUMNS);
        MatrixCursor cIn2 = new MatrixCursor(COLUMNS);

        // Make two sets of items with the same IDs, under different authorities.
        final String AUTHORITY0 = "auth0";
        final String AUTHORITY1 = "auth1";

        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row0 = cIn1.newRow();
            row0.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY0);
            row0.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));

            MatrixCursor.RowBuilder row1 = cIn2.newRow();
            row1.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY1);
            row1.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
        }

        Cursor cIn = new MergeCursor(new Cursor[] { cIn1, cIn2 });

        // Update the model, then make sure it contains all the expected items.
        DirectoryResult r = new DirectoryResult();
        r.cursor = cIn;
        model.update(r);

        assertEquals(ITEM_COUNT * 2, model.getItemCount());
        BitSet b0 = new BitSet(ITEM_COUNT);
        BitSet b1 = new BitSet(ITEM_COUNT);

        for (String id: model.getModelIds()) {
            Cursor cOut = model.getItem(id);
            String authority =
                    DocumentInfo.getCursorString(cOut, RootCursorWrapper.COLUMN_AUTHORITY);
            String docId = DocumentInfo.getCursorString(cOut, Document.COLUMN_DOCUMENT_ID);

            switch (authority) {
                case AUTHORITY0:
                    b0.set(Integer.parseInt(docId));
                    break;
                case AUTHORITY1:
                    b1.set(Integer.parseInt(docId));
                    break;
                default:
                    fail("Unrecognized authority string");
            }
        }

        assertEquals(ITEM_COUNT, b0.cardinality());
        assertEquals(ITEM_COUNT, b1.cardinality());
    }

    // Tests the base case for Model.getItem.
    @Test
    public void testGetItem() {
        String[] ids = model.getModelIds();
        assertEquals(ITEM_COUNT, ids.length);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            Cursor c = model.getItem(ids[i]);
            assertEquals(i, c.getPosition());
        }
    }

    @Test
    public void testResetAfterGettingException() {
        DirectoryResult result = new DirectoryResult();
        result.exception = new Exception();

        model.update(result);

        assertEquals(0, model.getItemCount());
    }
}
