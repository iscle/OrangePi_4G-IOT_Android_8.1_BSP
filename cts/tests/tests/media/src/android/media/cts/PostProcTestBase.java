/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Looper;
import android.test.AndroidTestCase;

public class PostProcTestBase extends AndroidTestCase {
    protected int mSession = -1;
    protected boolean mHasControl = false;
    protected boolean mIsEnabled = false;
    protected boolean mInitialized = false;
    protected Looper mLooper = null;
    protected final Object mLock = new Object();
    protected int mChangedParameter = -1;
    protected final static String BUNDLE_VOLUME_EFFECT_UUID =
            "119341a0-8469-11df-81f9-0002a5d5c51b";

    protected boolean hasAudioOutput() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    protected boolean isBassBoostAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_BASS_BOOST);
    }

    protected boolean isVirtualizerAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_VIRTUALIZER);
    }

    protected boolean isPresetReverbAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_PRESET_REVERB);
    }

    protected boolean isEnvReverbAvailable() {
        return AudioEffect.isEffectTypeAvailable(AudioEffect.EFFECT_TYPE_ENV_REVERB);
    }

    protected int getSessionId() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("could not get AudioManager", am);
        int sessionId = am.generateAudioSessionId();
        assertTrue("Could not generate session id", sessionId>0);
        return sessionId;
    }

}
