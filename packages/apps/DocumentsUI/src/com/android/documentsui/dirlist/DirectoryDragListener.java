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

package com.android.documentsui.dirlist;

import android.view.DragEvent;
import android.view.View;

import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.ItemDragListener;

import java.util.TimerTask;

import javax.annotation.Nullable;

class DirectoryDragListener extends ItemDragListener<DragHost<?>> {


    DirectoryDragListener(com.android.documentsui.dirlist.DragHost<?> host) {
        super(host);
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        final boolean result = super.onDrag(v, event);

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENDED:
                // getResult() is true if drag was accepted
                mDragHost.dragStopped(event.getResult());
                break;
            default:
                break;
        }

        return result;
    }

    @Override
    public boolean handleDropEventChecked(View v, DragEvent event) {
        return mDragHost.handleDropEvent(v, event);
    }

    @Override
    public @Nullable TimerTask createOpenTask(final View v, DragEvent event) {
        return mDragHost.canSpringOpen(v)
                ? super.createOpenTask(v, event) : null;
    }
}