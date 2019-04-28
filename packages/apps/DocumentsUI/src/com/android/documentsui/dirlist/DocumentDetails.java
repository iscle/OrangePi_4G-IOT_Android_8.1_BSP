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

import com.android.documentsui.base.Events.InputEvent;

/**
 * Interface providing a loose coupling between DocumentHolder.
 */
public interface DocumentDetails {
    boolean hasModelId();
    String getModelId();
    int getAdapterPosition();
    boolean isInSelectionHotspot(InputEvent event);
    boolean isInDragHotspot(InputEvent event);

    /**
     * Given a mouse input event, this method does a hit-test to see if the cursor
     * is currently positioned over the document icon or checkbox in the case where
     * the document is selected.
     */
    boolean isOverDocIcon(InputEvent event);
}
