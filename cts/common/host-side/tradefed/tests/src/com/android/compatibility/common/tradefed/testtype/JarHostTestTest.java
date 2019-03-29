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
package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link JarHostTest}.
 */
public class JarHostTestTest extends TestCase {

    private static final String TEST_JAR1 = "/testtype/testJar1.jar";
    private static final String TEST_JAR2 = "/testtype/testJar2.jar";
    private JarHostTest mTest;
    private File mTestDir = null;

    /**
     * More testable version of {@link JarHostTest}
     */
    public static class JarHostTestable extends JarHostTest {

        public static File mTestDir;
        public JarHostTestable() {}

        public JarHostTestable(File testDir) {
            mTestDir = testDir;
        }

        @Override
        CompatibilityBuildHelper createBuildHelper(IBuildInfo info) {
            return new CompatibilityBuildHelper(info) {
                @Override
                public File getTestsDir() throws FileNotFoundException {
                    return mTestDir;
                }
            };
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTest = new JarHostTest();
        mTestDir = FileUtil.createTempDir("jarhostest");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestDir);
        super.tearDown();
    }

    /**
     * Helper to read a file from the res/testtype directory and return it.
     *
     * @param filename the name of the file in the res/testtype directory
     * @param parentDir dir where to put the jar. Null if in default tmp directory.
     * @return the extracted jar file.
     */
    protected File getJarResource(String filename, File parentDir) throws IOException {
        InputStream jarFileStream = getClass().getResourceAsStream(filename);
        File jarFile = FileUtil.createTempFile("test", ".jar", parentDir);
        FileUtil.writeToFile(jarFileStream, jarFile);
        return jarFile;
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass  {
        public Junit4TestClass() {}
        @org.junit.Test
        public void testPass1() {}
    }

    /**
     * Test class, we have to annotate with full org.junit.Test to avoid name collision in import.
     */
    @RunWith(JUnit4.class)
    public static class Junit4TestClass2  {
        public Junit4TestClass2() {}
        @org.junit.Test
        public void testPass2() {}
    }

    /**
     * Test that {@link JarHostTest#split()} inherited from {@link HostTest} is still good.
     */
    public void testSplit_withoutJar() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("class", "com.android.compatibility.common.tradefed.testtype."
                + "JarHostTestTest$Junit4TestClass");
        setter.setOptionValue("class", "com.android.compatibility.common.tradefed.testtype."
                + "JarHostTestTest$Junit4TestClass2");
        // sharCount is ignored; will split by number of classes
        List<IRemoteTest> res = (List<IRemoteTest>)mTest.split(1);
        assertEquals(2, res.size());
        assertTrue(res.get(0) instanceof JarHostTest);
        assertTrue(res.get(1) instanceof JarHostTest);
    }

    /**
     * Test that {@link JarHostTest#split()} can split classes coming from a jar.
     */
    public void testSplit_withJar() throws Exception {
        File testJar = getJarResource(TEST_JAR1, mTestDir);
        mTest = new JarHostTestable(mTestDir);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("jar", testJar.getName());
        // sharCount is ignored; will split by number of classes
        List<IRemoteTest> res = (List<IRemoteTest>)mTest.split(1);
        assertEquals(2, res.size());
        assertTrue(res.get(0) instanceof JarHostTest);
        assertEquals("[android.ui.cts.TaskSwitchingTest]",
                ((JarHostTest)res.get(0)).getClassNames().toString());
        assertTrue(res.get(1) instanceof JarHostTest);
        assertEquals("[android.ui.cts.InstallTimeTest]",
                ((JarHostTest)res.get(1)).getClassNames().toString());
    }

    /**
     * Test that {@link JarHostTest#getTestShard(int, int)} can split classes coming from a jar.
     */
    public void testGetTestShard_withJar() throws Exception {
        File testJar = getJarResource(TEST_JAR2, mTestDir);
        mTest = new JarHostTestLoader(mTestDir, testJar);
        mTest.setBuild(new BuildInfo());
        ITestDevice device = EasyMock.createNiceMock(ITestDevice.class);
        mTest.setDevice(device);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("jar", testJar.getName());
        // full class count without sharding
        assertEquals(238, mTest.countTestCases());

        // only one shard
        IRemoteTest oneShard = mTest.getTestShard(1, 0);
        assertTrue(oneShard instanceof JarHostTest);
        ((JarHostTest)oneShard).setBuild(new BuildInfo());
        ((JarHostTest)oneShard).setDevice(device);
        assertEquals(238, ((JarHostTest)oneShard).countTestCases());

        // 5 shards total the number of tests.
        int total = 0;
        IRemoteTest shard1 = mTest.getTestShard(5, 0);
        assertTrue(shard1 instanceof JarHostTest);
        ((JarHostTest)shard1).setBuild(new BuildInfo());
        ((JarHostTest)shard1).setDevice(device);
        assertEquals(58, ((JarHostTest)shard1).countTestCases());
        total += ((JarHostTest)shard1).countTestCases();

        IRemoteTest shard2 = mTest.getTestShard(5, 1);
        assertTrue(shard2 instanceof JarHostTest);
        ((JarHostTest)shard2).setBuild(new BuildInfo());
        ((JarHostTest)shard2).setDevice(device);
        assertEquals(60, ((JarHostTest)shard2).countTestCases());
        total += ((JarHostTest)shard2).countTestCases();

        IRemoteTest shard3 = mTest.getTestShard(5, 2);
        assertTrue(shard3 instanceof JarHostTest);
        ((JarHostTest)shard3).setBuild(new BuildInfo());
        ((JarHostTest)shard3).setDevice(device);
        assertEquals(60, ((JarHostTest)shard3).countTestCases());
        total += ((JarHostTest)shard3).countTestCases();

        IRemoteTest shard4 = mTest.getTestShard(5, 3);
        assertTrue(shard4 instanceof JarHostTest);
        ((JarHostTest)shard4).setBuild(new BuildInfo());
        ((JarHostTest)shard4).setDevice(device);
        assertEquals(30, ((JarHostTest)shard4).countTestCases());
        total += ((JarHostTest)shard4).countTestCases();

        IRemoteTest shard5 = mTest.getTestShard(5, 4);
        assertTrue(shard5 instanceof JarHostTest);
        ((JarHostTest)shard5).setBuild(new BuildInfo());
        ((JarHostTest)shard5).setDevice(device);
        assertEquals(30, ((JarHostTest)shard5).countTestCases());
        total += ((JarHostTest)shard5).countTestCases();

        assertEquals(238, total);
    }

    /**
     * Testable version of {@link JarHostTest} that allows adding jar to classpath for testing
     * purpose.
     */
    public static class JarHostTestLoader extends JarHostTestable {

        private static File mTestJar;

        public JarHostTestLoader() {}

        public JarHostTestLoader(File testDir, File jar) {
            super(testDir);
            mTestJar = jar;
        }

        @Override
        CompatibilityBuildHelper createBuildHelper(IBuildInfo info) {
            return new CompatibilityBuildHelper(info) {
                @Override
                public File getTestsDir() throws FileNotFoundException {
                    return mTestDir;
                }
            };
        }
        @Override
        protected ClassLoader getClassLoader() {
            ClassLoader child = super.getClassLoader();
            try {
                child = new URLClassLoader(Arrays.asList(mTestJar.toURI().toURL())
                        .toArray(new URL[]{}), super.getClassLoader());
            } catch (MalformedURLException e) {
                CLog.e(e);
            }
            return child;
        }
    }
}
