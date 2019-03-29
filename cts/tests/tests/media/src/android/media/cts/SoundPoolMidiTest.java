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

import android.media.cts.R;

public class SoundPoolMidiTest extends SoundPoolTest {

    @Override
    protected int getSoundA() {
        return R.raw.midi_a;
    }

    @Override
    protected int getSoundCs() {
        return R.raw.midi_cs;
    }

    @Override
    protected int getSoundE() {
        return R.raw.midi_e;
    }

    @Override
    protected int getSoundB() {
        return R.raw.midi_b;
    }

    @Override
    protected int getSoundGs() {
        return R.raw.midi_gs;
    }

    @Override
    protected String getFileName() {
        return "midi_a.mid";
    }
}
