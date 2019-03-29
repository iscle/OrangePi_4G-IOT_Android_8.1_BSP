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
 * limitations under the License
 */

package android.server.cts;

import android.content.Context;
import android.util.AttributeSet;

/**
 * An extension of {@link android.support.v4.widget.SwipeRefreshLayout} that calls
 * {@link #setRefreshing} during construction, preventing activity idle.
 */
public class SwipeRefreshLayout extends android.support.v4.widget.SwipeRefreshLayout {
    public SwipeRefreshLayout(Context context) {
        super(context);
        initialize();
    }

    public SwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setRefreshing(true);
    }
}
