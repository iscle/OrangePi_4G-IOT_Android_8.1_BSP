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

package com.android.documentsui;

import android.annotation.CallSuper;
import android.view.View;

import com.android.documentsui.services.FileOperationService;

/**
 * Provides common functionality for a {@link ItemDragListener.DragHost}.
 */
public abstract class AbstractDragHost implements ItemDragListener.DragHost {

    protected DragAndDropManager mDragAndDropManager;

    public AbstractDragHost(DragAndDropManager dragAndDropManager) {
        mDragAndDropManager = dragAndDropManager;
    }

    @CallSuper
    @Override
    public void onDragExited(View v) {
        mDragAndDropManager.resetState(v);
    }

    @CallSuper
    @Override
    public void onDragEnded() {
        mDragAndDropManager.dragEnded();
    }
}
