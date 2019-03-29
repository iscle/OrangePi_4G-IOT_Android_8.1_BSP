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

package com.android.cts.apicoverage;

import java.util.ArrayList;
import java.util.List;

/**
 * Util class to support package filtering logic
 * <p>
 * A list of package prefixes can be added to the filter, and {{@link #accept(String)} method will
 * decide if the provided package name matches any of the prefixes.
 */
public class PackageFilter {

    private List<String> mFilters = new ArrayList<>();

    /**
     * Check if a particular package name matches any of the package prefixes configured in filter.
     * If no filters are configured, any package names will be accepted
     * @param packageName
     * @return
     */
    public boolean accept(String packageName) {
        if (mFilters.isEmpty()) {
            return true;
        }
        for (String filter : mFilters) {
            if (packageName.startsWith(filter)) {
                return true;
            }
        }
        return false;
    }

    public void addPrefixToFilter(String prefix) {
        mFilters.add(prefix);
    }

    public void clearFilter() {
        mFilters.clear();
    }
}
