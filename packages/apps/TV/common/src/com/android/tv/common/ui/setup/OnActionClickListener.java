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

package com.android.tv.common.ui.setup;

import android.os.Bundle;

/**
 * A listener for the action click.
 */
public interface OnActionClickListener {
    /**
     * Called when the action is clicked.
     * <p>
     * The method should return {@code true} if the action is handled, otherwise {@code false}.
     *
     * @param category The action category.
     * @param id The action id.
     * @param params The parameter for the action.
     */
    boolean onActionClick(String category, int id, Bundle params);
}
