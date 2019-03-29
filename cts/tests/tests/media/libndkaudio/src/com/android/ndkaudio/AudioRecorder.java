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
package com.android.ndkaudio;

import android.media.AudioRouting;

public class AudioRecorder {
    public AudioRecorder() {
        Create();
    }

    public native void Create();
    public native void Destroy();

    public native void RealizeRecorder();
    public native void RealizeRoutingProxy();

    public native void Start();
    public native void Stop();

    public native AudioRouting GetRoutingInterface();
    public native void ReleaseRoutingInterface(AudioRouting proxyObj);

    public native int GetNumBufferSamples();
    public native void GetBufferData(float[] dstBuff);

    public native long GetLastSLResult();
    public native void ClearLastSLResult();
}
