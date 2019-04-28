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

package com.android.documentsui.testing;

import com.android.documentsui.ActivityConfig;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.State;

public class TestActivityConfig extends ActivityConfig {

    public boolean nextSelectType = false;
    public boolean nextDocumentEnabled = false;
    public boolean nextManagedModeEnabled = false;
    public boolean nextDragAndDropEnabled = false;

    public boolean canSelectType(String docMimeType, int docFlags, State state) {
        return nextSelectType;
    }

    public boolean isDocumentEnabled(String docMimeType, int docFlags, State state) {
        return nextDocumentEnabled;
    }

    /**
     * When managed mode is enabled, active downloads will be visible in the UI.
     * Presumably this should only be true when in the downloads directory.
     */
    public boolean managedModeEnabled(DocumentStack stack) {
        return nextManagedModeEnabled;
    }

    /**
     * Whether drag n' drop is allowed in this context
     */
    public boolean dragAndDropEnabled() {
        return nextDragAndDropEnabled;
    }
}
