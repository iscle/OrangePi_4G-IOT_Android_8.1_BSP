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
package com.android.tv.testing.uihelper;

import android.content.res.Resources;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;

/**
 * Convenience methods for creating {@link BySelector}s using resource ids.
 */
public final class ByResource {

    /**
     * Creates a BySelector for the {@code resId} from {@code resources}
     *
     * @see By#res(String)
     */
    public static BySelector id(Resources resources, int resId) {
        String id = resources.getResourceName(resId);
        return By.res(id);
    }

    /**
     * Creates a BySelector for the text of {@code stringRes} from {@code resources}.
     *
     * @see By#text(String)
     */
    public static BySelector text(Resources resources, int stringRes) {
        String text = resources.getString(stringRes);
        return By.text(text);
    }

    private ByResource() {
    }
}
