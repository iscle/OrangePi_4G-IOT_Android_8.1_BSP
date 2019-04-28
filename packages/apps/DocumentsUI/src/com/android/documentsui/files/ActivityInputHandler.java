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

package com.android.documentsui.files;

import android.view.KeyEvent;

/**
 * Used by {@link FilesActivity} to manage global keyboard shortcuts tied to file actions
 */
final class ActivityInputHandler {

    private final Runnable mDeleteHandler;

    ActivityInputHandler(Runnable deleteHandler) {
        mDeleteHandler = deleteHandler;
    }

    boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FORWARD_DEL:
                mDeleteHandler.run();
                return true;
            case KeyEvent.KEYCODE_DEL:
                if (event.isAltPressed()) {
                    mDeleteHandler.run();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
}
