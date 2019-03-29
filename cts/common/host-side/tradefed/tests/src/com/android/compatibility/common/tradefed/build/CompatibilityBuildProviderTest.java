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
package com.android.compatibility.common.tradefed.build;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/**
 * Unit tests for {@link CompatibilityBuildProvider}.
 */
@RunWith(JUnit4.class)
public class CompatibilityBuildProviderTest {

    private CompatibilityBuildProvider mProvider;
    private ITestDevice mMockDevice;
    private File mRootDir;

    @Before
    public void setUp() throws Exception {
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mRootDir = FileUtil.createTempDir("cts-root-dir");
        mProvider = new CompatibilityBuildProvider() {
            @Override
            String getRootDirPath() {
                return mRootDir.getAbsolutePath();
            }
        };
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mRootDir);
    }

    /**
     * Tests getting the build info without using the device information.
     */
    @Test
    public void testBaseGetBuild() throws Exception {
        EasyMock.replay(mMockDevice);
        IBuildInfo info = mProvider.getBuild(mMockDevice);
        EasyMock.verify(mMockDevice);
        assertFalse(info instanceof IDeviceBuildInfo);
    }

    /**
     * Tests building build infos using the device information.
     */
    @Test
    public void testBaseGetBuild_withDevice() throws Exception {
        OptionSetter setter = new OptionSetter(mProvider);
        setter.setOptionValue("use-device-build-info", "true");
        setter.setOptionValue("branch", "build_branch");
        EasyMock.expect(mMockDevice.getBuildId()).andReturn("8888");
        EasyMock.expect(mMockDevice.getBuildFlavor()).andReturn("flavor");
        EasyMock.expect(mMockDevice.getBuildAlias()).andReturn("alias");
        EasyMock.replay(mMockDevice);
        IBuildInfo info = mProvider.getBuild(mMockDevice);
        EasyMock.verify(mMockDevice);
        assertTrue(info instanceof IDeviceBuildInfo);
        // tests dir should be populated
        assertNotNull(((IDeviceBuildInfo)info).getTestsDir());
        // ensure that tests dir is never clean up.
        mProvider.cleanUp(info);
        assertNotNull(((IDeviceBuildInfo)info).getTestsDir());
    }
}
