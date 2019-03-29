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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.ddmlib.IDevice;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;

import junit.framework.TestCase;

import org.easymock.EasyMock;

public class MediaPreparerTest extends TestCase {

    private MediaPreparer mMediaPreparer;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMediaPreparer = new MediaPreparer();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = new BuildInfo("0", "");
        mOptionSetter = new OptionSetter(mMediaPreparer);
    }

    public void testSetMountPoint() throws Exception {
        EasyMock.expect(mMockDevice.getMountPoint(IDevice.MNT_EXTERNAL_STORAGE)).andReturn(
                "/sdcard").once();
        EasyMock.replay(mMockDevice);
        mMediaPreparer.setMountPoint(mMockDevice);
        assertEquals(mMediaPreparer.mBaseDeviceShortDir, "/sdcard/test/bbb_short/");
        assertEquals(mMediaPreparer.mBaseDeviceFullDir, "/sdcard/test/bbb_full/");
    }

    public void testCopyMediaFiles() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.DEFAULT_MAX_RESOLUTION;
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";
        for (MediaPreparer.Resolution resolution : MediaPreparer.RESOLUTIONS) {
            String shortFile = String.format("%s%s", mMediaPreparer.mBaseDeviceShortDir,
                    resolution.toString());
            String fullFile = String.format("%s%s", mMediaPreparer.mBaseDeviceFullDir,
                    resolution.toString());
            EasyMock.expect(mMockDevice.doesFileExist(shortFile)).andReturn(true).once();
            EasyMock.expect(mMockDevice.doesFileExist(fullFile)).andReturn(true).once();
        }
        EasyMock.replay(mMockDevice);
        mMediaPreparer.copyMediaFiles(mMockDevice);
    }

    public void testMediaFilesExistOnDeviceTrue() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.DEFAULT_MAX_RESOLUTION;
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        mMediaPreparer.mBaseDeviceFullDir = "/sdcard/test/bbb_full/";
        for (MediaPreparer.Resolution resolution : MediaPreparer.RESOLUTIONS) {
            String shortFile = String.format("%s%s", mMediaPreparer.mBaseDeviceShortDir,
                    resolution.toString());
            String fullFile = String.format("%s%s", mMediaPreparer.mBaseDeviceFullDir,
                    resolution.toString());
            EasyMock.expect(mMockDevice.doesFileExist(shortFile)).andReturn(true).anyTimes();
            EasyMock.expect(mMockDevice.doesFileExist(fullFile)).andReturn(true).anyTimes();
        }
        EasyMock.replay(mMockDevice);
        assertTrue(mMediaPreparer.mediaFilesExistOnDevice(mMockDevice));
    }

    public void testMediaFilesExistOnDeviceFalse() throws Exception {
        mMediaPreparer.mMaxRes = MediaPreparer.DEFAULT_MAX_RESOLUTION;
        mMediaPreparer.mBaseDeviceShortDir = "/sdcard/test/bbb_short/";
        String firstFileChecked = "/sdcard/test/bbb_short/176x144";
        EasyMock.expect(mMockDevice.doesFileExist(firstFileChecked)).andReturn(false).once();
        EasyMock.replay(mMockDevice);
        assertFalse(mMediaPreparer.mediaFilesExistOnDevice(mMockDevice));
    }

    public void testSkipMediaDownload() throws Exception {
        mOptionSetter.setOptionValue("skip-media-download", "true");
        EasyMock.replay();
        mMediaPreparer.run(mMockDevice, mMockBuildInfo);
    }

}
