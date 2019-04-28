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

package com.android.tv.search;

import com.android.tv.search.LocalSearchProvider.SearchResult;

import java.util.List;

/**
 * Interface for channel and program search.
 */
public interface SearchInterface {
    int ACTION_TYPE_START = 1;
    int ACTION_TYPE_AMBIGUOUS = 1;
    int ACTION_TYPE_SWITCH_CHANNEL = 2;
    int ACTION_TYPE_SWITCH_INPUT = 3;
    int ACTION_TYPE_END = 3;

    /**
     * Search channels, inputs, or programs.
     * This assumes that parental control settings will not be change while searching.
     *
     * @param action One of {@link #ACTION_TYPE_SWITCH_CHANNEL}, {@link #ACTION_TYPE_SWITCH_INPUT},
     *               or {@link #ACTION_TYPE_AMBIGUOUS},
     */
    List<SearchResult> search(String query, int limit, int action);
}
