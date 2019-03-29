/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.accessibilityservice.cts;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.media.AudioManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.content.Context.AUDIO_SERVICE;
import static org.junit.Assert.assertEquals;

/**
 * Verify that accessibility services can control the accessibility volume.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityVolumeTest {
    Instrumentation mInstrumentation;
    AudioManager mAudioManager;
    // If a platform collects all volumes into one, these tests aren't relevant
    boolean mSingleVolume;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mAudioManager =
                (AudioManager) mInstrumentation.getContext().getSystemService(AUDIO_SERVICE);
        // TVs have a single volume
        PackageManager pm = mInstrumentation.getContext().getPackageManager();
        mSingleVolume = (pm != null) && (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
    }

    @Test
    public void testChangeAccessibilityVolume_outsideValidAccessibilityService_shouldFail() {
        if (mSingleVolume) {
            return;
        }
        int startingVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
        int otherVolume = (startingVolume == 0) ? 1 : startingVolume - 1;
        mAudioManager.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, otherVolume, 0);
        assertEquals("Non accessibility service should not be able to change accessibility volume",
                startingVolume, mAudioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY));
    }

    @Test
    public void testChangeAccessibilityVolume_inAccessibilityService_shouldWork() {
        if (mSingleVolume) {
            return;
        }
        int startingVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY);
        int otherVolume = (startingVolume == 0) ? 1 : startingVolume - 1;
        InstrumentedAccessibilityService service = InstrumentedAccessibilityService.enableService(
                mInstrumentation, InstrumentedAccessibilityService.class);

        service.runOnServiceSync(() ->
                mAudioManager.setStreamVolume(AudioManager.STREAM_ACCESSIBILITY, otherVolume, 0));
        assertEquals("Accessibility service should be able to change accessibility volume",
                otherVolume, mAudioManager.getStreamVolume(AudioManager.STREAM_ACCESSIBILITY));
        service.runOnServiceSync(() -> mAudioManager.setStreamVolume(
                AudioManager.STREAM_ACCESSIBILITY, startingVolume, 0));
    }
}
