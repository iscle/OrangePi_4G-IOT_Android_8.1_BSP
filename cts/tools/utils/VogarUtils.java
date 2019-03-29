/*
 * Copyright (C) 2011 The Android Open Source Project
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

import vogar.Expectation;
import vogar.ExpectationStore;
import vogar.ModeId;
import vogar.Result;

import com.android.tradefed.util.AbiUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VogarUtils {

    public static boolean isVogarKnownFailure(ExpectationStore[] expectationStores,
            final String testClassName,
            final String testMethodName) {
        for (ExpectationStore expectationStore : expectationStores) {
            if (isVogarKnownFailure(expectationStore, testClassName, testMethodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true iff the class/name is found in the vogar known failure list and it is not
     * a known failure that is a result of an unsupported abi.
     */
    public static boolean isVogarKnownFailure(ExpectationStore expectationStore,
            final String testClassName,
            final String testMethodName) {
        if (expectationStore == null) {
            return false;
        }
        String fullTestName = buildFullTestName(testClassName, testMethodName);
        Expectation expectation = expectationStore.get(fullTestName);
        if (expectation.getResult() == Result.SUCCESS) {
            return false;
        }

        String description = expectation.getDescription();
        boolean foundAbi = AbiUtils.parseAbiList(description).size() > 0;

        return expectation.getResult() != Result.SUCCESS && !foundAbi;
    }

    public static ExpectationStore provideExpectationStore(String dir) throws IOException {
        if (dir == null) {
            return null;
        }
        ExpectationStore result = ExpectationStore.parse(getExpectationFiles(dir), ModeId.DEVICE);
        return result;
    }

    private static Set<File> getExpectationFiles(String dir) {
        Set<File> expectSet = new HashSet<File>();
        File[] files = new File(dir).listFiles(new FilenameFilter() {
            // ignore obviously temporary files
            public boolean accept(File dir, String name) {
                return !name.endsWith("~") && !name.startsWith(".");
            }
        });
        if (files != null) {
            expectSet.addAll(Arrays.asList(files));
        }
        return expectSet;
    }

    /** @return the test name in the form of com.android.myclass.TestClass#testMyMethod */
    public static String buildFullTestName(String testClass, String testMethodName) {
        return String.format("%s#%s", testClass, testMethodName);
    }

    /**
     * This method looks in the description field of the Vogar entry for the ABI_LIST_MARKER
     * and returns the list of abis found there.
     *
     * @return The Set of supported abis parsed from the {@code expectation}'s description.
     */
    public static Set<String> extractSupportedAbis(String architecture, Expectation expectation) {
        Set<String> supportedAbiSet = AbiUtils.getAbisForArch(architecture);
        if (expectation == null || expectation.getDescription().isEmpty()) {
            // Include all abis since there was no limitation found in the description
            return supportedAbiSet;
        }

        // Remove any abis that are not supported for the test.
        supportedAbiSet.removeAll(AbiUtils.parseAbiList(expectation.getDescription()));

        return supportedAbiSet;
    }

    /**
     * Determine the correct set of ABIs for the given className/testName.
     *
     * @return the set of ABIs that can be expected to pass for the given combination of
     * {@code architecture}, {@code className} and {@code testName}.
     */
    public static Set<String> extractSupportedAbis(String architecture,
                                                   ExpectationStore[] expectationStores,
                                                   String className,
                                                   String testName) {

        String fullTestName = buildFullTestName(className, testName);
        Set<String> supportedAbiSet = AbiUtils.getAbisForArch(architecture);
        for (ExpectationStore expectationStore : expectationStores) {
            Expectation expectation = expectationStore.get(fullTestName);
            supportedAbiSet.retainAll(extractSupportedAbis(architecture, expectation));
        }

        return supportedAbiSet;
    }

    /**
     * Returns the greatest timeout in minutes for the test in all
     * expectation stores, or 0 if no timeout was found.
     */
    public static int timeoutInMinutes(ExpectationStore[] expectationStores,
            final String testClassName,
            final String testMethodName) {
        int timeoutInMinutes = 0;
        for (ExpectationStore expectationStore : expectationStores) {
            timeoutInMinutes = Math.max(timeoutInMinutes,
                                        timeoutInMinutes(expectationStore,
                                                         testClassName,
                                                         testMethodName));
        }
        return timeoutInMinutes;
    }

    /**
     * Returns the timeout in minutes for the test in the expectation
     * stores, or 0 if no timeout was found.
     */
    public static int timeoutInMinutes(ExpectationStore expectationStore,
            final String testClassName,
            final String testMethodName) {
        if (expectationStore == null) {
            return 0;
        }
        String fullTestName = buildFullTestName(testClassName, testMethodName);
        return timeoutInMinutes(expectationStore.get(fullTestName));
    }

    /**
     * Returns the timeout in minutes for the expectation. Currently a
     * tag of large results in a 60 minute timeout, otherwise 0 is
     * returned to indicate a default timeout should be used.
     */
    public static int timeoutInMinutes(Expectation expectation) {
        return expectation.getTags().contains("large") ? 60 : 0;
    }
}
