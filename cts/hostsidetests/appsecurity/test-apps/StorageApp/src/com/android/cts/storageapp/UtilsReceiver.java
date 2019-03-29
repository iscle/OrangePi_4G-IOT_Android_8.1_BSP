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

package com.android.cts.storageapp;

import static com.android.cts.storageapp.Utils.TAG;
import static com.android.cts.storageapp.Utils.makeUniqueFile;
import static com.android.cts.storageapp.Utils.useFallocate;
import static com.android.cts.storageapp.Utils.useWrite;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.util.Objects;
import java.util.UUID;

public class UtilsReceiver extends BroadcastReceiver {
    public static final String EXTRA_FRACTION = "fraction";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_TIME = "time";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle res = doAllocation(context, intent.getExtras());
        if (res != null) {
            setResultCode(Activity.RESULT_OK);
            setResultExtras(res);
        } else {
            setResultCode(Activity.RESULT_CANCELED);
        }
    }

    public static Bundle doAllocation(Context context, Bundle extras) {
        final StorageManager sm = context.getSystemService(StorageManager.class);

        long allocated = 0;
        try {
            // When shared storage is backed by internal, then pivot our cache
            // files between the two locations to ensure clearing logic works.
            final File intDir = context.getCacheDir();
            final File extDir = context.getExternalCacheDir();
            final UUID intUuid = sm.getUuidForPath(intDir);
            final UUID extUuid = sm.getUuidForPath(extDir);

            Log.d(TAG, "Found internal " + intUuid + " and external " + extUuid);
            final boolean doPivot = Objects.equals(intUuid, extUuid);

            final double fraction = extras.getDouble(EXTRA_FRACTION, 0);
            final long quota = sm.getCacheQuotaBytes(intUuid);
            final long bytes = (long) (quota * fraction);
            final long time = extras.getLong(EXTRA_TIME, System.currentTimeMillis());

            int i = 0;
            while (allocated < bytes) {
                final File target;
                if (doPivot) {
                    target = (i++ % 2) == 0 ? intDir : extDir;
                } else {
                    target = intDir;
                }

                final File f = makeUniqueFile(target);
                final long size = 1024 * 1024;
                if (target == intDir) {
                    useFallocate(f, size);
                } else {
                    useWrite(f, size);
                }
                f.setLastModified(time);
                allocated += Os.stat(f.getAbsolutePath()).st_blocks * 512;
            }

            Log.d(TAG, "Quota " + quota + ", target " + bytes + ", allocated " + allocated);

            final Bundle res = new Bundle();
            res.putLong(EXTRA_BYTES, allocated);
            return res;
        } catch (Exception e) {
            Log.e(TAG, "Failed to allocate cache files", e);
            return null;
        }
    }
}
