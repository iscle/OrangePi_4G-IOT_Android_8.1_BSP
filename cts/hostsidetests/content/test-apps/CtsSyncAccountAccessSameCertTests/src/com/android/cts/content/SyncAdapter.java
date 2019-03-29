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

package com.android.cts.content;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final Object sLock = new Object();

    private static OnPerformSyncDelegate sOnPerformSyncDelegate;

    public interface OnPerformSyncDelegate {
        void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult);
    }

    public static void setOnPerformSyncDelegate(OnPerformSyncDelegate delegate) {
        synchronized (sLock) {
            sOnPerformSyncDelegate = delegate;
        }
    }

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        OnPerformSyncDelegate delegate;
        synchronized (sLock) {
            delegate = sOnPerformSyncDelegate;
        }
        if (delegate != null) {
            delegate.onPerformSync(account, extras, authority, provider, syncResult);
        }
    }
}
