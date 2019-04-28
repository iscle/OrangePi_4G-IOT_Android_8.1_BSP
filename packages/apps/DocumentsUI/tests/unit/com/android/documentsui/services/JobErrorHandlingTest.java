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

package com.android.documentsui.services;

import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.google.common.collect.Lists.newArrayList;

import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.test.filters.MediumTest;

import java.util.List;

/**
 * With this test we're interested in testing a common ancestor class shared by DeleteJob, CopyJob,
 * and MoveJob. Since DeleteJob is simplest, we use that.
 */
@MediumTest
public class JobErrorHandlingTest extends AbstractJobTest<DeleteJob> {

    public void testRecoversFromInvalidUri() throws Exception {
        Uri invalidUri1 = Uri.parse("content://poodles/chuckleberry/ham");
        Uri validUri = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        Uri invalidUri2 = Uri.parse("content://poodles/chuckleberry/ham");
        mDocs.writeDocument(validUri, FRUITY_BYTES);

        createJob(newArrayList(invalidUri1, validUri, invalidUri2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId)).run();
        mJobListener.waitForFinished();
        mJobListener.assertFinished();

        // verify that the document associated with the one valid uri was deleted.
        mDocs.assertChildCount(mSrcRoot, 0);
    }

    public void testRecordsInvalidUris() throws Exception {
        Uri invalidUri1 = Uri.parse("content://poodles/chuckleberry/ham");
        Uri validUri = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        Uri invalidUri2 = Uri.parse("content://poodles/chuckleberry/ham");
        mDocs.writeDocument(validUri, FRUITY_BYTES);

        createJob(newArrayList(invalidUri1, validUri, invalidUri2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId)).run();
        mJobListener.waitForFinished();

        mJobListener.assertUriFailed(invalidUri1);
        mJobListener.assertUriFailed(invalidUri2);
    }

    public void testReportsCorrectFailureCount() throws Exception {
        Uri invalidUri1 = Uri.parse("content://poodles/chuckleberry/ham");
        Uri validUri = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        Uri invalidUri2 = Uri.parse("content://poodles/chuckleberry/ham");
        mDocs.writeDocument(validUri, FRUITY_BYTES);

        createJob(newArrayList(invalidUri1, validUri, invalidUri2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId)).run();
        mJobListener.waitForFinished();

        mJobListener.assertFailureCount(2);
    }

    /**
     * Creates a job with a stack consisting to the default src directory.
     */
    private final DeleteJob createJob(List<Uri> srcs, Uri srcParent) throws Exception {
        Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        return createJob(OPERATION_DELETE, srcs, srcParent, stack);
    }
}
