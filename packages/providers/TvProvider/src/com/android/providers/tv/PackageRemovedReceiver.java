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
 * limitations under the License
 */

package com.android.providers.tv;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.media.tv.TvContract;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * This will be launched when PACKAGE_FULLY_REMOVED intent is broadcast.
 */
public class PackageRemovedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "PackageRemovedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())
                && intent.getData() != null) {
            String packageName = intent.getData().getSchemeSpecificPart();
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);

            String selection = TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME + "=?";
            String[] selectionArgs = {packageName};

            operations.add(ContentProviderOperation.newDelete(TvContract.Channels.CONTENT_URI)
                    .withSelection(selection, selectionArgs).build());
            operations.add(ContentProviderOperation.newDelete(TvContract.Programs.CONTENT_URI)
                    .withSelection(selection, selectionArgs).build());
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.WatchedPrograms.CONTENT_URI)
                    .withSelection(selection, selectionArgs).build());
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.RecordedPrograms.CONTENT_URI)
                    .withSelection(selection, selectionArgs).build());
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.WatchNextPrograms.CONTENT_URI)
                    .withSelection(selection, selectionArgs).build());

            ContentProviderResult[] results = null;
            try {
                ContentResolver cr = context.getContentResolver();
                results = cr.applyBatch(TvContract.AUTHORITY, operations);
            } catch (RemoteException | OperationApplicationException e) {
                Log.e(TAG, "error in applyBatch", e);
            }

            if (DEBUG) {
                Log.d(TAG, "onPackageFullyRemoved(packageName=" + packageName + ", uid=" + uid
                        + ")");
                Log.d(TAG, "results=" + Arrays.toString(results));
            }
        }
    }
}