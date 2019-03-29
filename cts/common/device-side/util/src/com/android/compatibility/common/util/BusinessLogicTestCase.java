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
package com.android.compatibility.common.util;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;

import android.app.Instrumentation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import java.lang.reflect.Field;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Device-side base class for tests leveraging the Business Logic service.
 */
public class BusinessLogicTestCase {

    /* String marking the beginning of the parameter in a test name */
    private static final String PARAM_START = "[";

    /* Test name rule that tracks the current test method under execution */
    @Rule public TestName mTestCase = new TestName();

    private static BusinessLogic mBusinessLogic;

    @BeforeClass
    public static void prepareBusinessLogic() {
        File businessLogicFile = new File(BusinessLogic.DEVICE_FILE);
        mBusinessLogic = BusinessLogicFactory.createFromFile(businessLogicFile);
    }

    @Before
    public void executeBusinessLogic() {
        String methodName = mTestCase.getMethodName();
        if (methodName.contains(PARAM_START)) {
            // Strip parameter suffix (e.g. "[0]") from method name
            methodName = methodName.substring(0, methodName.lastIndexOf(PARAM_START));
        }
        String testName = String.format("%s#%s", this.getClass().getName(), methodName);
        if (mBusinessLogic.hasLogicFor(testName)) {
            Log.i("Finding business logic for test case: ", testName);
            BusinessLogicExecutor executor = new BusinessLogicDeviceExecutor(getContext(), this);
            mBusinessLogic.applyLogicFor(testName, executor);
        }
    }

    protected static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected static Context getContext() {
        return getInstrumentation().getTargetContext();
    }

    public static void skipTest(String message) {
        assumeTrue(message, false);
    }

    public static void failTest(String message) {
        fail(message);
    }

    public void mapPut(String mapName, String key, String value) {
        boolean put = false;
        for (Field f : getClass().getDeclaredFields()) {
            if (f.getName().equalsIgnoreCase(mapName) && Map.class.isAssignableFrom(f.getType())) {
                try {
                    ((Map) f.get(this)).put(key, value);
                    put = true;
                } catch (IllegalAccessException e) {
                    Log.w(String.format("failed to invoke mapPut on field \"%s\". Resuming...",
                            f.getName()), e);
                    // continue iterating through fields, throw exception if no other fields match
                }
            }
        }
        if (!put) {
            throw new RuntimeException(String.format("Failed to find map %s in class %s", mapName,
                    getClass().getName()));
        }
    }
}
