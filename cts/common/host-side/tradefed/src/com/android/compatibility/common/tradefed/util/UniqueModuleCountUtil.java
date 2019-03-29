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
 * limitations under the License.
 */
package com.android.compatibility.common.tradefed.util;

import com.android.compatibility.common.tradefed.testtype.IModuleDef;

import java.util.HashSet;
import java.util.List;

/**
 * Utility to count the number of unique module from list of {@link IModuleDef}.
 */
public class UniqueModuleCountUtil {

    /**
     * Count the number of unique modules within the list using module id. If two IModuleDef have
     * the same id, they are part of the same module.
     *
     * @param listModules list of {@link IModuleDef} to count from
     * @return the count of unique module.
     */
    public static int countUniqueModules(List<IModuleDef> listModules) {
        HashSet<String> uniqueNames = new HashSet<>();
        for (IModuleDef subModule : listModules) {
            uniqueNames.add(subModule.getId());
        }
        return uniqueNames.size();
    }
}
