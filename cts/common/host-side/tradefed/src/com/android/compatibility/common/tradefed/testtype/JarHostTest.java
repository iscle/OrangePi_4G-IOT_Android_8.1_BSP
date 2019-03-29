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
package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;

import junit.framework.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Test runner for host-side JUnit tests.
 */
public class JarHostTest extends HostTest {

    @Option(name="jar", description="The jars containing the JUnit test class to run.",
            importance = Importance.IF_UNSET)
    private Set<String> mJars = new HashSet<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected HostTest createHostTest(Class<?> classObj) {
        JarHostTest test = (JarHostTest) super.createHostTest(classObj);
        // clean the jar option since we are loading directly from classes after.
        test.mJars = new HashSet<>();
        return test;
    }

    /**
     * Create a {@link CompatibilityBuildHelper} from the build info provided.
     */
    @VisibleForTesting
    CompatibilityBuildHelper createBuildHelper(IBuildInfo info) {
        return new CompatibilityBuildHelper(info);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Class<?>> getClasses() throws IllegalArgumentException  {
        List<Class<?>> classes = super.getClasses();
        CompatibilityBuildHelper helper = createBuildHelper(getBuild());
        for (String jarName : mJars) {
            JarFile jarFile = null;
            try {
                File file = helper.getTestFile(jarName);
                jarFile = new JarFile(file);
                Enumeration<JarEntry> e = jarFile.entries();
                URL[] urls = {
                        new URL(String.format("jar:file:%s!/", file.getAbsolutePath()))
                };
                URLClassLoader cl = URLClassLoader.newInstance(urls);

                while (e.hasMoreElements()) {
                    JarEntry je = e.nextElement();
                    if (je.isDirectory() || !je.getName().endsWith(".class")
                            || je.getName().contains("$")) {
                        continue;
                    }
                    String className = getClassName(je.getName());
                    try {
                        Class<?> cls = cl.loadClass(className);
                        int modifiers = cls.getModifiers();
                        if ((IRemoteTest.class.isAssignableFrom(cls)
                                || Test.class.isAssignableFrom(cls)
                                || hasJUnit4Annotation(cls))
                                && !Modifier.isStatic(modifiers)
                                && !Modifier.isPrivate(modifiers)
                                && !Modifier.isProtected(modifiers)
                                && !Modifier.isInterface(modifiers)
                                && !Modifier.isAbstract(modifiers)) {
                            classes.add(cls);
                        }
                    } catch (ClassNotFoundException cnfe) {
                        throw new IllegalArgumentException(
                                String.format("Cannot find test class %s", className));
                    }
                }
            } catch (IOException e) {
                CLog.e(e);
                throw new RuntimeException(e);
            } finally {
                StreamUtil.close(jarFile);
            }
        }
        return classes;
    }

    private static String getClassName(String name) {
        // -6 because of .class
        return name.substring(0, name.length() - 6).replace('/', '.');
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        int numTests = countTestCases();
        long startTime = System.currentTimeMillis();
        listener.testRunStarted(getClass().getName(), numTests);
        super.run(new HostTestListener(listener));
        listener.testRunEnded(System.currentTimeMillis() - startTime, Collections.emptyMap());
    }

    /**
     * Wrapper listener that forwards all events except testRunStarted() and testRunEnded() to
     * the embedded listener. Each test class in the jar will invoke these events, which
     * HostTestListener withholds from listeners for console logging and result reporting.
     */
    public class HostTestListener extends ResultForwarder {

        public HostTestListener(ITestInvocationListener listener) {
            super(listener);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void testRunStarted(String name, int numTests) {
            CLog.d("HostTestListener.testRunStarted(%s, %d)", name, numTests);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
            CLog.d("HostTestListener.testRunEnded(%d, %s)", elapsedTime, metrics.toString());
        }
    }
}
