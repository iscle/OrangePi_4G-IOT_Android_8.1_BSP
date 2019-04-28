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

package com.android.storagemanager;

import android.view.ViewGroup;
import android.widget.Button;

/**
 * The ButtonBarProvider interface can be applied to any activity which contains a button bar with
 * a next, skip, and back button.
 */
public interface ButtonBarProvider {
    /**
     * Returns a button bar.
     */
    ViewGroup getButtonBar();

    /**
     * Returns the next button on the button bar.
     */
    Button getNextButton();

    /**
     * Returns the skip button on the button bar.
     */
    Button getSkipButton();
}
