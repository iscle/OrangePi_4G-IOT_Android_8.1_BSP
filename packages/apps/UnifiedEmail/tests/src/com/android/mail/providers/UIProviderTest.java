/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.providers;

import android.test.AndroidTestCase;

public class UIProviderTest extends AndroidTestCase {

    public void testReadAndWriteOfLastSyncResult() {
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.NO_SYNC,
                UIProvider.LastSyncResult.STORAGE_ERROR);
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.NO_SYNC,
                UIProvider.LastSyncResult.SECURITY_ERROR);
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.USER_REFRESH,
                UIProvider.LastSyncResult.SUCCESS);
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.USER_REFRESH,
                UIProvider.LastSyncResult.AUTH_ERROR);
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.BACKGROUND_SYNC,
                UIProvider.LastSyncResult.SUCCESS);
        packAndUnpackLastSyncResult(UIProvider.SyncStatus.BACKGROUND_SYNC,
                UIProvider.LastSyncResult.CONNECTION_ERROR);
    }

    private void packAndUnpackLastSyncResult(int syncStatus, int lastSyncResult) {
        final int value = UIProvider.createSyncValue(syncStatus, lastSyncResult);

        assertEquals(syncStatus, UIProvider.getStatusFromLastSyncResult(value));
        assertEquals(lastSyncResult, UIProvider.getResultFromLastSyncResult(value));
    }
}
