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
package com.android.documentsui.inspector;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import com.android.documentsui.InspectorProvider;
import android.test.suitebuilder.annotation.MediumTest;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.documentsui.testing.TestLoaderManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

/**
 * This test relies the inspector providers test.txt file in inspector root.
 */
@MediumTest
public class DocumentLoaderTest extends TestCase {

    private static final String TEST_DOC_NAME = "test.txt";
    private static final String DIR_TOP = "Top";
    private static final String NOT_DIRECTORY = "OpenInProviderTest";

    private Context mContext;
    private TestLoaderManager mLoaderManager;
    private Loader mLoader;
    private ContentResolver mResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
        mLoaderManager = new TestLoaderManager();
        mLoader = new DocumentLoader(mContext, mLoaderManager);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /**
     * Tests the loader using the Inspector Content provider. This test that we got valid info back
     * from the loader.
     *
     * @throws Exception
     */
    @Test
    public void testLoadsDocument() throws Exception {
        Uri validUri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, TEST_DOC_NAME);
        TestDocConsumer consumer = new TestDocConsumer(1);
        mLoader.loadDocInfo(validUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.latch.await(1000, TimeUnit.MILLISECONDS);

        assertNotNull(consumer.info);
        assertEquals(consumer.info.displayName, TEST_DOC_NAME);
        assertEquals(consumer.info.size, 0);
    }

    /**
     * Test invalid uri, DocumentInfo returned should be null.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidInput() throws Exception {
        Uri invalidUri = Uri.parse("content://poodles/chuckleberry/ham");
        TestDocConsumer consumer = new TestDocConsumer(1);
        mLoader.loadDocInfo(invalidUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.latch.await(1000, TimeUnit.MILLISECONDS);
        assertNull(consumer.info);
    }

    @Test
    public void testNonContentUri() {

        Uri invalidUri = Uri.parse("http://poodles/chuckleberry/ham");
        TestDocConsumer consumer = new TestDocConsumer(1);

        try {
            mLoader.loadDocInfo(invalidUri, consumer);

            // this is a test double that requires explicitly loading. @see TestLoaderManager
            mLoaderManager.getLoader(0).startLoading();
            fail("Should have thrown exception.");
        } catch (Exception expected) {}
    }

    @Test
    public void testDir_loadNumberOfChildren() throws Exception {
        Uri dirUri = DocumentsContract.buildDocumentUri(
            InspectorProvider.AUTHORITY, DIR_TOP);

        DocumentInfo info = DocumentInfo.fromUri(mResolver, dirUri);

        TestDirConsumer consumer = new TestDirConsumer(1);
        mLoader.loadDirCount(info, consumer);
        mLoaderManager.getLoader(0).startLoading();

        consumer.latch.await(1000, TimeUnit.MILLISECONDS);
        assertEquals(consumer.childCount, 4);
    }

    @Test
    public void testDir_notADirectory() throws Exception {
        Uri uri = DocumentsContract.buildDocumentUri(
            InspectorProvider.AUTHORITY, NOT_DIRECTORY);

        DocumentInfo info = DocumentInfo.fromUri(mResolver, uri);
        TestDirConsumer consumer = new TestDirConsumer(1);

        try {
            mLoader.loadDirCount(info, consumer);
            mLoaderManager.getLoader(0).startLoading();
            fail("should have thrown exception");
        } catch (Exception expected) {}
    }

    /**
     * Helper function for testing async processes.
     */
    private static class TestDocConsumer implements Consumer<DocumentInfo> {

        private DocumentInfo info;
        private CountDownLatch latch;

        public TestDocConsumer(int expectedCount) {
            latch = new CountDownLatch(expectedCount);
        }

        @Nullable
        @Override
        public void accept(DocumentInfo documentInfo) {
            info = documentInfo;
            latch.countDown();
        }
    }

    private static class TestDirConsumer implements Consumer<Integer> {

        private int childCount;
        private CountDownLatch latch;

        public TestDirConsumer(int expectedCount) {
            latch = new CountDownLatch(expectedCount);
        }

        @Override
        public void accept(Integer integer) {
            childCount = integer;
            latch.countDown();
        }
    }
}