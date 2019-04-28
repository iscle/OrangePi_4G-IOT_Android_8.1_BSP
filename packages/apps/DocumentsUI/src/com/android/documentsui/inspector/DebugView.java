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
package com.android.documentsui.inspector;

import android.content.Context;
import android.util.AttributeSet;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;

import java.util.function.Consumer;

/**
 * Organizes and Displays the basic details about a file
 */
public class DebugView extends TableView implements Consumer<DocumentInfo> {

    public DebugView(Context context) {
        this(context, null);
    }

    public DebugView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DebugView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void accept(DocumentInfo info) {
        setTitle(this, R.string.inspector_debug_section);

        put("Content uri", info.derivedUri);
        put("Document id", info.documentId);
        put("Mimetype: ", info.mimeType);
        put("Is archive", info.isArchive());
        put("Is container", info.isContainer());
        put("Is partial", info.isPartial());
        put("Is virtual", info.isVirtual());
        put("Supports create", info.isCreateSupported());
        put("Supports delete", info.isDeleteSupported());
        put("Supports rename", info.isRenameSupported());
        put("Supports settings", info.isSettingsSupported());
        put("Supports thumbnail", info.isThumbnailSupported());
        put("Supports weblink", info.isWeblinkSupported());
        put("Supports write", info.isWriteSupported());
    }

    private void put(String key, Object value) {
        put(key, String.valueOf(value));
    }
}
