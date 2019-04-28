/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.browse;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.test.IsolatedContext;
import android.test.LoaderTestCase;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class EmlMessageLoaderTest extends LoaderTestCase {
    private TestEmlProvider mTestProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockContentResolver resolver = new MockContentResolver();
        IsolatedContext context = new IsolatedContext(resolver, getContext());
        Context wrappedContext = new ContextWrapper(context) {
            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
        setContext(wrappedContext);
        mTestProvider = new TestEmlProvider(wrappedContext);
        resolver.addProvider(TestEmlProvider.AUTHORITY, mTestProvider);
    }

    @SmallTest
    public void testLoadingBlankEml() throws Exception {
        final Uri emptyEmlUri = new Uri.Builder().scheme("content").authority(
                TestProvider.AUTHORITY).path("empty").build();
        final ContentValues cv = new ContentValues(2);
        cv.put(OpenableColumns.DISPLAY_NAME, "Empty.eml");
        cv.put(OpenableColumns.SIZE, 0);
        mTestProvider.insert(emptyEmlUri, cv);
        final OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(
                mTestProvider.makePipeForUri(emptyEmlUri));
        out.write(0);
        out.close();

        EmlMessageLoader loader = new EmlMessageLoader(getContext(), emptyEmlUri);

        getLoaderResultSynchronously(loader);
        // If we don't crash, the test passed.
    }

    public static class TestEmlProvider extends TestProvider {
        private final HashMap<Uri, ParcelFileDescriptor> mFileMap =
                new HashMap<Uri, ParcelFileDescriptor>();

        public TestEmlProvider(Context context) {
            super(context);
        }

        public ParcelFileDescriptor makePipeForUri(Uri uri) throws IOException {
            ParcelFileDescriptor[] descriptors = ParcelFileDescriptor.createPipe();
            mFileMap.put(uri, descriptors[0]);
            return descriptors[1];
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            final ParcelFileDescriptor descriptor = mFileMap.get(uri);
            if (descriptor != null) {
                return descriptor;
            }
            return super.openFile(uri, mode);
        }
    }
}
