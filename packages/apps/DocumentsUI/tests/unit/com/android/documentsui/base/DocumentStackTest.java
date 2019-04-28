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

package com.android.documentsui.base;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import android.provider.DocumentsContract;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.testing.Parcelables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DocumentStackTest {
    private static final RootInfo ROOT_1;
    private static final RootInfo ROOT_2;

    private static final DocumentInfo DIR_1;
    private static final DocumentInfo DIR_2;

    private DocumentStack mStack;

    static {
        ROOT_1 = new RootInfo();
        ROOT_1.rootId = "home";
        ROOT_2 = new RootInfo();
        ROOT_2.rootId = "downloads";

        DIR_1 = createDir("first");
        DIR_2 = createDir("second");
    }

    private static DocumentInfo createDir(String docId) {
        DocumentInfo info = new DocumentInfo();
        info.authority = "authority";
        info.documentId = docId;
        info.displayName = docId;
        info.mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
        info.deriveFields();
        return info;
    }

    @Before
    public void setUp() {
        mStack = new DocumentStack();
    }

    @Test
    public void testInitialStateEmpty() {
        assertFalse(mStack.hasLocationChanged());
        assertTrue(mStack.isEmpty());
        assertEquals(0, mStack.size());
        assertNull(mStack.getRoot());
    }

    @Test
    public void testPushDocument_ModifiesStack() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);
        assertEquals(DIR_2, mStack.peek());
    }

    @Test
    public void testPopDocument_ModifiesStack() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);
        mStack.pop();
        assertEquals(DIR_1, mStack.peek());
    }

    @Test
    public void testGetDocument() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);

        assertEquals(DIR_1, mStack.get(0));
        assertEquals(DIR_2, mStack.get(1));
    }

    @Test
    public void testChangeRoot() {
        mStack.changeRoot(ROOT_1);

        assertEquals(ROOT_1, mStack.getRoot());
    }

    @Test
    public void testChangeRoot_ClearsStack() {
        mStack.push(DIR_1);

        mStack.changeRoot(ROOT_1);

        assertTrue(mStack.isEmpty());
        assertEquals(0, mStack.size());
    }

    @Test
    public void testReset() {
        mStack.changeRoot(ROOT_1);
        mStack.push(DIR_1);

        mStack.reset();

        assertNull(mStack.getRoot());
        assertTrue(mStack.isEmpty());
        assertEquals(0, mStack.size());
    }

    @Test
    public void testCopyConstructor() {
        mStack.changeRoot(ROOT_1);
        mStack.push(DIR_1);
        mStack.push(DIR_2);

        DocumentStack stack = new DocumentStack(mStack);

        assertEquals(2, stack.size());
        assertEquals(DIR_1, stack.get(0));
        assertEquals(DIR_2, stack.get(1));
        assertEquals(ROOT_1, stack.getRoot());
    }

    @Test
    public void testCopyConstructor_MakesDeepCopy() {
        mStack.changeRoot(ROOT_1);
        mStack.push(DIR_1);
        mStack.push(DIR_2);

        DocumentStack stack = new DocumentStack(mStack);

        mStack.changeRoot(ROOT_2);

        assertEquals(2, stack.size());
        assertEquals(DIR_1, stack.get(0));
        assertEquals(DIR_2, stack.get(1));
        assertEquals(ROOT_1, stack.getRoot());
    }

    @Test
    public void testPushDocument_ChangesLocation() {
        mStack.push(DIR_1);

        assertTrue(mStack.hasLocationChanged());
    }

    @Test
    public void testParceling() {
        mStack.changeRoot(ROOT_1);
        mStack.push(DIR_1);
        mStack.push(DIR_2);

        Parcelables.assertParcelable(mStack, 0, (DocumentStack left, DocumentStack right) -> {
            if (!Objects.equals(left.getRoot(), right.getRoot()) || left.size() != right.size()) {
                return false;
            }

            for (int i = 0; i < left.size(); ++i) {
                if (!Objects.equals(left.get(i), right.get(i))) {
                    return false;
                }
            }

            return true;
        });
    }
}
