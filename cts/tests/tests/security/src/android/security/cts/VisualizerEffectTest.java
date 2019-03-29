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
package android.security.cts;

import junit.framework.TestCase;

import android.content.Context;
import android.platform.test.annotations.SecurityTest;
import android.media.audiofx.AudioEffect;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.test.AndroidTestCase;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;


@SecurityTest
public class VisualizerEffectTest extends AndroidTestCase {
    private String TAG = "VisualizerEffectTest";
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    //Testing security bug: 30229821
    @SecurityTest
    public void testVisualizer_MalformedConstructor() throws Exception {
        final String VISUALIZER_TYPE = "e46b26a0-dddd-11db-8afd-0002a5d5c51b";
        final int VISUALIZER_CMD_MEASURE = 0x10001;

        AudioEffect.Descriptor[] descriptors = AudioEffect.queryEffects();
        int i, visualizerIndex = -1;
        for (i = 0; i < descriptors.length; ++i) {
            AudioEffect.Descriptor descriptor = descriptors[i];
            if (descriptor.type.compareTo(UUID.fromString(VISUALIZER_TYPE)) == 0) {
                visualizerIndex = i;

                AudioEffect ae = null;
                MediaPlayer mp = null;
                try {
                    mp = MediaPlayer.create(getContext(), R.raw.good);
                    Constructor ct = AudioEffect.class.getConstructor(UUID.class, UUID.class,
                            int.class, int.class);
                    ae = (AudioEffect) ct.newInstance(descriptors[visualizerIndex].type,
                            descriptors[visualizerIndex].uuid, 0, mp.getAudioSessionId());
                    Method command = AudioEffect.class.getDeclaredMethod("command", int.class,
                            byte[].class, byte[].class);
                    Integer ret = (Integer) command.invoke(ae, new Object[]{VISUALIZER_CMD_MEASURE,
                            new byte[0], new byte[0]});
                    assertTrue("Audio server might have crashed", ret != -7);
                } catch (Exception e) {
                    Log.w(TAG,"Problem testing visualizer");
                } finally {
                    if (ae != null) {
                        ae.release();
                    }
                    if (mp != null) {
                        mp.release();
                    }
                }
            }
        }

        if (visualizerIndex == -1) {
            Log.w(TAG,"No visualizer found to test");
        }
    }
}