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
package com.android.documentsui.ui;

import android.app.FragmentManager;

import com.android.documentsui.base.ConfirmationCallback;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperations;

import junit.framework.Assert;

import java.util.List;

public class TestDialogController implements DialogController {

    public int mNextConfirmationCode;
    private int mFileOpStatus;
    private boolean mNoApplicationFound;
    private boolean mDocumentsClipped;
    private boolean mViewInArchivesUnsupported;
    private boolean mShowOperationUnsupported;
    private DocumentInfo mOverwriteTarget;

    public TestDialogController() {
        // by default, always confirm
        mNextConfirmationCode = ConfirmationCallback.CONFIRM;
    }

    @Override
    public void confirmDelete(List<DocumentInfo> docs, ConfirmationCallback callback) {
        callback.accept(mNextConfirmationCode);
    }

    @Override
    public void showFileOperationStatus(int status, int opType, int docCount) {
        mFileOpStatus = status;
    }

    @Override
    public void showProgressDialog(String jobId, FileOperation operation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void showNoApplicationFound() {
        mNoApplicationFound = true;
    }

    @Override
    public void showOperationUnsupported() {
        mShowOperationUnsupported = true;
    }

    @Override
    public void showViewInArchivesUnsupported() {
        mViewInArchivesUnsupported = true;
    }

    @Override
    public void showDocumentsClipped(int size) {
        mDocumentsClipped = true;
    }

    @Override
    public void confirmOverwrite(FragmentManager fm, DocumentInfo overwriteTarget) {
        mOverwriteTarget = overwriteTarget;
    }

    public void assertNoFileFailures() {
        Assert.assertEquals(FileOperations.Callback.STATUS_ACCEPTED, mFileOpStatus);
    }

    public void assertFileOpFailed() {
        Assert.assertEquals(FileOperations.Callback.STATUS_FAILED, mFileOpStatus);
    }

    public void assertNoAppFoundShown() {
        Assert.assertFalse(mNoApplicationFound);
    }

    public void assertShowOperationUnsupported() {
        Assert.assertTrue(mShowOperationUnsupported);
    }
    public void assertViewInArchivesShownUnsupported() {
        Assert.assertTrue(mViewInArchivesUnsupported);
    }

    public void assertDocumentsClippedNotShown() {
        Assert.assertFalse(mDocumentsClipped);
    }

    public void assertOverwriteConfirmed(DocumentInfo expected) {
        Assert.assertEquals(expected, mOverwriteTarget);
    }

    public void confirmNext() {
        mNextConfirmationCode = ConfirmationCallback.CONFIRM;
    }

    public void rejectNext() {
        mNextConfirmationCode = ConfirmationCallback.REJECT;
    }
}
