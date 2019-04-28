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

package com.android.documentsui.testing;

import com.android.documentsui.dirlist.DocumentsAdapter;
import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionManager.SelectionMode;
import com.android.documentsui.selection.SelectionManager.SelectionPredicate;

import java.util.Collections;
import java.util.List;

public class SelectionManagers {
    private SelectionManagers() {}

    public static SelectionManager createTestInstance() {
        return createTestInstance(Collections.emptyList());
    }

    public static SelectionManager createTestInstance(List<String> docs) {
        return createTestInstance(docs, SelectionManager.MODE_MULTIPLE);
    }

    public static SelectionManager createTestInstance(
            List<String> docs, @SelectionMode int mode) {
        return createTestInstance(
                new TestDocumentsAdapter(docs),
                mode,
                (String id, boolean nextState) -> true);
    }

    public static SelectionManager createTestInstance(
            DocumentsAdapter adapter, @SelectionMode int mode, SelectionPredicate canSetState) {
        SelectionManager manager = new SelectionManager(mode);
        manager.reset(adapter, canSetState);

        return manager;
    }
}
