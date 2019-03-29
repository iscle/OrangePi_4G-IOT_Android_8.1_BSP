/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.animation.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.animation.Keyframe;
import android.animation.TimeInterpolator;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.animation.AccelerateInterpolator;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyframeTest {
    @Test
    public void testGetFraction() {
        Keyframe keyFrame = Keyframe.ofInt(0.0f);
        float fraction = keyFrame.getFraction();
        assertEquals(0.0f, fraction, 0.0f);
    }

    @Test
    public void testSetFraction() {
        Keyframe keyFrame = Keyframe.ofInt(0.0f);
        keyFrame.setFraction(0.5f);
        float fraction = keyFrame.getFraction();
        assertEquals(0.5f, fraction, 0.0f);
    }

    @Test
    public void testOfFloat() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f);
        float fraction = keyFrame.getFraction();
        assertEquals(0.0f, fraction, 0.0f);
    }

    @Test
    public void testOfIntValue() {
        Keyframe keyFrame = Keyframe.ofInt(0.0f,10);
        assertTrue(keyFrame.hasValue());
        assertEquals(keyFrame.getValue(),10);
    }

    @Test
    public void testOfFloatValue() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f,9.0f);
        assertTrue(keyFrame.hasValue());
        assertEquals(9.0f, (float) keyFrame.getValue(), 0.0f);
    }

    @Test
    public void testOfObject() {
        Keyframe keyFrame = Keyframe.ofObject(0.0f);
        float fraction = keyFrame.getFraction();
        assertEquals(0.0f, fraction, 0.0f);
    }

    @Test
    public void testOfObjectValue() {
        String value = "test";
        Keyframe keyFrame = Keyframe.ofObject(0.0f, value);
        assertTrue(keyFrame.hasValue());
        assertEquals(keyFrame.getValue(), value);
    }

    @Test
    public void testGetType() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f);
        Class typeClass = keyFrame.getType();
        String typeName = typeClass.getName();
        assertEquals(typeName, "float");
    }

    @Test
    public void testClone() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f);
        Keyframe clone = keyFrame.clone();
        assertEquals(keyFrame.getFraction(), clone.getFraction(), 0.0f);
    }

    @Test
    public void testSetInterpolator() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f);
        TimeInterpolator interpolator = new AccelerateInterpolator();
        keyFrame.setInterpolator(interpolator);
        assertEquals(interpolator, keyFrame.getInterpolator());
    }

    @Test
    public void testSetValue() {
        Keyframe keyFrame = Keyframe.ofFloat(0.0f);
        Float value = new Float(100.0f);
        keyFrame.setValue(value);
        Float actualValue = (Float) keyFrame.getValue();
        assertEquals(value, actualValue);
    }
}

