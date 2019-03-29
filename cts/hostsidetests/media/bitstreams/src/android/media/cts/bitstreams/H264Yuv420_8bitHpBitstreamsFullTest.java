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
package android.media.cts.bitstreams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

@FullPackage
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
public class H264Yuv420_8bitHpBitstreamsFullTest extends MediaBitstreamsTest {

    @Parameters(name = "{1}")
    public static Iterable<Object[]> bitstreams() {
        return MediaBitstreamsTest.bitstreams("h264/yuv420/8bit/hp", BitstreamPackage.FULL);
    }

    public H264Yuv420_8bitHpBitstreamsFullTest(String prefix, String path,
            BitstreamPackage pkg, BitstreamPackage packageToRun) {
        super(prefix, path, pkg, packageToRun);
    }

    @Test
    @Override
    @FullPackage
    public void testBitstreamsConformance() {
        super.testBitstreamsConformance();
    }
}
