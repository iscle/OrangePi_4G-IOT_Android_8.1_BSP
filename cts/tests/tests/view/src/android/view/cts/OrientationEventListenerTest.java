/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.OrientationEventListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link OrientationEventListener}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OrientationEventListenerTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testConstructor() {
        new MyOrientationEventListener(mContext);

        new MyOrientationEventListener(mContext, SensorManager.SENSOR_DELAY_UI);
    }

    @Test
    public void testEnableAndDisable() {
        MyOrientationEventListener listener = new MyOrientationEventListener(mContext);
        listener.enable();
        listener.disable();
    }

    @Test
    public void testCanDetectOrientation() {
        SensorManager sm = (SensorManager)mContext.getSystemService(Context.SENSOR_SERVICE);
        // Orientation can only be detected if there is an accelerometer
        boolean hasSensor = (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);

        MyOrientationEventListener listener = new MyOrientationEventListener(mContext);
        assertEquals(hasSensor, listener.canDetectOrientation());
    }

    private static class MyOrientationEventListener extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        public MyOrientationEventListener(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int orientation) {
        }
    }
}
