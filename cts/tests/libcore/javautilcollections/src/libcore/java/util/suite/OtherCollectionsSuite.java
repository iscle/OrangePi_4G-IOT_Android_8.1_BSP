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
 *
 */

package libcore.java.util.suite;

import junit.framework.Test;
import junit.framework.TestSuite;

import libcore.java.util.tests.AndroidTestsForListsInJavaUtil;
import libcore.java.util.tests.AndroidTestsForMapsInJavaUtil;
import libcore.java.util.tests.AndroidTestsForMapsInJavaUtil.MapsToTest;
import libcore.java.util.tests.AndroidTestsForQueuesInJavaUtil;
import libcore.java.util.tests.AndroidTestsForSetsInJavaUtil;

/**
 * A suite of all guava-testlib Collection tests not covered by the other suites in this
 * package.
 */
public class OtherCollectionsSuite extends TestSuite {
    public static Test suite() {
        TestSuite result = new TestSuite();
        result.addTest(new AndroidTestsForListsInJavaUtil().allTests());
        result.addTest(new AndroidTestsForMapsInJavaUtil(MapsToTest.OTHER).allTests());
        result.addTest(new AndroidTestsForQueuesInJavaUtil().allTests());
        result.addTest(new AndroidTestsForSetsInJavaUtil().allTests());
        return result;
    }
}
