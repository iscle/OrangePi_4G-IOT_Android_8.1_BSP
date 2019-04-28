/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import android.app.DialogFragment;

/**
 * Implementations of this interface must be lightweight, so that it is cheap to discard them.
 * <p>Intended for use in places where the {@link DialogFragment} to-be-built might not be needed.
 */
public interface DialogBuilder {
    /**
     * Only called when an instance of a {@link DialogFragment} is actually needed.
     * Put all the heavy lifting here.
     */
    DialogFragment build();
}
