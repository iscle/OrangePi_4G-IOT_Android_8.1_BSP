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

import android.annotation.Nullable;
import android.content.ClipData;
import android.net.Uri;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.MenuManager;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.IconHelper;
import com.android.documentsui.services.FileOperations;

import java.util.List;

public class TestDragAndDropManager implements DragAndDropManager {

    public final TestEventListener<List<DocumentInfo>> startDragHandler = new TestEventListener<>();
    public final TestEventHandler<Pair<ClipData, RootInfo>> dropOnRootHandler =
            new TestEventHandler<>();
    public final TestEventHandler<Pair<ClipData, DocumentStack>> dropOnDocumentHandler =
            new TestEventHandler<>();

    @Override
    public void onKeyEvent(KeyEvent event) {}

    @Override
    public void startDrag(View v, List<DocumentInfo> srcs, RootInfo root,  List<Uri> invalidDest,
            SelectionDetails details, IconHelper iconHelper, @Nullable DocumentInfo parent) {
        startDragHandler.accept(srcs);
    }

    @Override
    public boolean canSpringOpen(RootInfo root, DocumentInfo doc) {
        return false;
    }

    @Override
    public void updateStateToNotAllowed(View v) {}

    @Override
    public int updateState(View v, RootInfo destRoot, @Nullable DocumentInfo destDoc) {
        return 0;
    }

    @Override
    public void resetState(View v) {}

    @Override
    public boolean drop(ClipData clipData, Object localState, RootInfo root, ActionHandler actions,
            FileOperations.Callback callback) {
        return dropOnRootHandler.accept(Pair.create(clipData, root));
    }

    @Override
    public boolean drop(ClipData clipData, Object localState, DocumentStack dstStack,
            FileOperations.Callback callback) {
        return dropOnDocumentHandler.accept(Pair.create(clipData, dstStack));
    }

    @Override
    public void dragEnded() {}
}
