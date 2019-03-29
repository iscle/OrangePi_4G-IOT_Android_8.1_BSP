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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * A helper class for setting and checking the number of expected test runs.
 */
public class TestRunHandler {

    private static final String MAP_DELIMITER = "->";

    /**
     * Determine the number of expected test runs for the module
     *
     * @param buildHelper the {@link CompatibilityBuildHelper} from which to retrieve invocation
     * failure file
     * @return the number of expected test runs, or 1 if module is not found
     */
    public static int getTestRuns(final CompatibilityBuildHelper buildHelper, String id) {
        try {
            File f = buildHelper.getTestRunsFile();
            if (!f.exists() || f.length() == 0) {
                return 1; // test runs file doesn't exist, expect one test run by default
            }
            String mapString = FileUtil.readStringFromFile(f);
            Map<String, Integer> map = stringToMap(mapString);
            Integer testRuns = map.get(id);
            return (testRuns == null) ? 1 : testRuns;
        } catch (IOException e) {
            CLog.e("Could not read test run file for session %s",
                buildHelper.getDirSuffix(buildHelper.getStartTime()));
            CLog.e(e);
            return 1;
        }
    }

    /**
     * Write the number of expected test runs to the result's test run file.
     *
     * @param buildHelper the {@link CompatibilityBuildHelper} used to write the
     * test run file
     * @param testRuns a mapping of module names to number of test runs expected
     */
    public static void setTestRuns(final CompatibilityBuildHelper buildHelper,
            Map<String, Integer> testRuns) {
        try {
            File f = buildHelper.getTestRunsFile();
            if (!f.exists()) {
                f.createNewFile();
            }
            FileUtil.writeToFile(mapToString(testRuns), f);
        } catch (IOException e) {
            CLog.e("Exception while writing test runs file.");
            CLog.e(e);
        }
    }

    private static String mapToString(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("");
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            sb.append(String.format("%s%s%d\n", entry.getKey(), MAP_DELIMITER, entry.getValue()));
        }
        return sb.toString();
    }

    private static Map<String, Integer> stringToMap(String str) {
        Map<String, Integer> map = new HashMap<>();
        for (String entry : str.split("\n")) {
            String[] parts = entry.split(MAP_DELIMITER);
            map.put(parts[0], Integer.parseInt(parts[1]));
        }
        return map;
    }
}
