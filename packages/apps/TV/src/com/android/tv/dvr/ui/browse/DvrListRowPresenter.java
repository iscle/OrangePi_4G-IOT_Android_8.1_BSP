/*
 * Copyright (c) 2017 The Android Open Source Project
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

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.view.ViewGroup;

import com.android.tv.R;

/** A list row presenter to display expand/fold card views list. */
public class DvrListRowPresenter extends ListRowPresenter {
    public DvrListRowPresenter(Context context) {
        super();
        setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        setExpandedRowHeight(
                context.getResources()
                        .getDimensionPixelSize(R.dimen.dvr_library_expanded_row_height));
    }
}
