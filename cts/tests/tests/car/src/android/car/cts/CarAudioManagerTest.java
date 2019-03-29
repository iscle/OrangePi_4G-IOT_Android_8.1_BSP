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
package android.car.cts;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.media.AudioAttributes;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;


@SmallTest
@RequiresDevice
/** Unit tests for {@link CarAudioManager}. */
public class CarAudioManagerTest extends CarApiTestBase {
    private static final String TAG = CarAudioManagerTest.class.getSimpleName();
    private CarAudioManager mManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
        assertNotNull(mManager);
    }

    public void testGetAudioAttributesForCarUsageForMusic() throws Exception {
        AudioAttributes.Builder musicBuilder = new AudioAttributes.Builder();
        musicBuilder.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA);

        assertEquals(musicBuilder.build(), mManager.getAudioAttributesForCarUsage(
                             CarAudioManager.CAR_AUDIO_USAGE_MUSIC));
    }

    public void testGetAudioAttributesForCarUsageForUnknown() throws Exception {
        AudioAttributes.Builder unknownBuilder = new AudioAttributes.Builder();
        unknownBuilder.setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setUsage(AudioAttributes.USAGE_UNKNOWN);

        assertEquals(unknownBuilder.build(), mManager.getAudioAttributesForCarUsage(10007));
    }
}
