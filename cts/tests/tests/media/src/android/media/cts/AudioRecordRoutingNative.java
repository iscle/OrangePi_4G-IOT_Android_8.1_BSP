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

package android.media.cts;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.ndkaudio.AudioRecorder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class AudioRecordRoutingNative extends AndroidTestCase {
    private static final String TAG = "AudioRecordRoutingNative";

    private AudioManager mAudioManager;

    static {
        System.loadLibrary("ndkaudioLib");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // get the AudioManager
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        assertNotNull(mAudioManager);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    //
    // Tests
    //

    // Test a basic Aquire/Release cycle on the default recorder.
    public void testAquireDefaultProxy() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecorder recorder = new AudioRecorder();
        recorder.ClearLastSLResult();
        recorder.RealizeRecorder();
        recorder.RealizeRoutingProxy();

        AudioRouting routingObj = recorder.GetRoutingInterface();
        assertNotNull(routingObj);

        // Not allowed to acquire twice
        routingObj = recorder.GetRoutingInterface();
        assertNull(routingObj);
        assertTrue(recorder.GetLastSLResult() != 0);

        recorder.ReleaseRoutingInterface(routingObj);
        assertTrue(recorder.GetLastSLResult() == 0);
    }

    // Test an Aquire before the OpenSL ES recorder is Realized.
    public void testAquirePreRealizeDefaultProxy() throws Exception {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecorder recorder = new AudioRecorder();
        recorder.ClearLastSLResult();
        recorder.RealizeRecorder();
        recorder.RealizeRoutingProxy();
        assertTrue(recorder.GetLastSLResult() == 0);

        AudioRouting routingObj = recorder.GetRoutingInterface();
        assertTrue(recorder.GetLastSLResult() == 0);
        assertNotNull(routingObj);

        recorder.RealizeRecorder();
        assertTrue(recorder.GetLastSLResult() == 0);

        recorder.ReleaseRoutingInterface(routingObj);
        assertTrue(recorder.GetLastSLResult() == 0);
    }

    // Test actually setting the routing through the enumerated devices.
    public void testRouting() {
        if (!hasMicrophone()) {
            return;
        }
        AudioRecorder recorder = new AudioRecorder();
        recorder.ClearLastSLResult();
        recorder.RealizeRecorder();
        recorder.RealizeRoutingProxy();

        AudioRouting routingObj = recorder.GetRoutingInterface();
        assertNotNull(routingObj);

        AudioDeviceInfo[] deviceList;
        deviceList = mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        assertTrue(deviceList != null);
        for (AudioDeviceInfo devInfo : deviceList) {
            assertTrue(routingObj.setPreferredDevice(devInfo));
        }

        recorder.ReleaseRoutingInterface(routingObj);
        assertTrue(recorder.GetLastSLResult() == 0);
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }
}
