/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.documentsui.base.DocumentInfo;

/**
 * Document debug info view.
 */
public class DocumentDebugInfo extends TextView {
    public DocumentDebugInfo(Context context) {
        super(context);

    }

    public DocumentDebugInfo(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void update(DocumentInfo doc) {

        String dbgInfo = new StringBuilder()
                .append("** PROPERTIES **\n\n")
                .append("docid: " + doc.documentId).append("\n")
                .append("name: " + doc.displayName).append("\n")
                .append("mimetype: " + doc.mimeType).append("\n")
                .append("container: " + doc.isContainer()).append("\n")
                .append("virtual: " + doc.isVirtual()).append("\n")
                .append("\n")
                .append("** OPERATIONS **\n\n")
                .append("create: " + doc.isCreateSupported()).append("\n")
                .append("delete: " + doc.isDeleteSupported()).append("\n")
                .append("rename: " + doc.isRenameSupported()).append("\n\n")
                .append("** URI **\n\n")
                .append(doc.derivedUri).append("\n")
                .toString();

        setText(dbgInfo);
    }
}
