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
package com.android.documentsui.dirlist;

import android.os.Bundle;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.sorting.SortDimension.SortDirection;

import javax.annotation.Nullable;

final class DirectoryState {

    private static final String EXTRA_SORT_DIMENSION_ID = "sortDimensionId";
    private static final String EXTRA_SORT_DIRECTION = "sortDirection";

    // Null when viewing Recents directory.
    @Nullable DocumentInfo mDocument;
    // Here we save the clip details of moveTo/copyTo actions when picker shows up.
    // This will be written to saved instance.
    @Nullable FileOperation mPendingOperation;
    int mLastSortDimensionId = SortModel.SORT_DIMENSION_ID_UNKNOWN;
    @SortDirection int mLastSortDirection;

    private RootInfo mRoot;
    private String mConfigKey;

    public void restore(Bundle bundle) {
        mRoot = bundle.getParcelable(Shared.EXTRA_ROOT);
        mDocument = bundle.getParcelable(Shared.EXTRA_DOC);
        mPendingOperation = bundle.getParcelable(FileOperationService.EXTRA_OPERATION);
        mLastSortDimensionId = bundle.getInt(EXTRA_SORT_DIMENSION_ID);
        mLastSortDirection = bundle.getInt(EXTRA_SORT_DIRECTION);
    }

    public void save(Bundle bundle) {
        bundle.putParcelable(Shared.EXTRA_ROOT, mRoot);
        bundle.putParcelable(Shared.EXTRA_DOC, mDocument);
        bundle.putParcelable(FileOperationService.EXTRA_OPERATION, mPendingOperation);
        bundle.putInt(EXTRA_SORT_DIMENSION_ID, mLastSortDimensionId);
        bundle.putInt(EXTRA_SORT_DIRECTION, mLastSortDirection);
    }

    public FileOperation claimPendingOperation() {
        FileOperation op = mPendingOperation;
        mPendingOperation = null;
        return op;
    }

    public void update(RootInfo root, DocumentInfo doc) {
        mRoot = root;
        mDocument = doc;
    }

    String getConfigKey() {
        if (mConfigKey == null) {
            final StringBuilder builder = new StringBuilder();
            builder.append(mRoot != null ? mRoot.authority : "null").append(';');
            builder.append(mRoot != null ? mRoot.rootId : "null").append(';');
            builder.append(mDocument != null ? mDocument.documentId : "null");
            mConfigKey = builder.toString();
        }
        return mConfigKey;
    }
}