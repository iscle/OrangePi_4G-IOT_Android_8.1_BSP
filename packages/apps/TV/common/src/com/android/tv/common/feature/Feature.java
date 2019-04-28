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
 * limitations under the License
 */

package com.android.tv.common.feature;

import android.content.Context;

/**
 * A feature is elements of code that are turned off for most users until a feature is fully
 * launched.
 *
 * <p>Expected usage is:
 * <pre>{@code
 *     if (MY_FEATURE.isEnabled(context) {
 *         showNewCoolUi();
 *     } else{
 *         showOldBoringUi();
 *     }
 * }</pre>
 */
public interface Feature {
    boolean isEnabled(Context context);


}
