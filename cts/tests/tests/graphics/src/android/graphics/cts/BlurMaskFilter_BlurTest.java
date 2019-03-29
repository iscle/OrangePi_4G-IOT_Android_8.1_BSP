/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.BlurMaskFilter;
import android.graphics.BlurMaskFilter.Blur;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BlurMaskFilter_BlurTest {
    @Test
    public void testValueOf(){
        assertEquals(Blur.NORMAL, Blur.valueOf("NORMAL"));
        assertEquals(Blur.SOLID, Blur.valueOf("SOLID"));
        assertEquals(Blur.OUTER, Blur.valueOf("OUTER"));
        assertEquals(Blur.INNER, Blur.valueOf("INNER"));
    }

    @Test
    public void testValues(){
        Blur[] blur = Blur.values();

        assertEquals(4, blur.length);
        assertEquals(Blur.NORMAL, blur[0]);
        assertEquals(Blur.SOLID, blur[1]);
        assertEquals(Blur.OUTER, blur[2]);
        assertEquals(Blur.INNER, blur[3]);

        //Blur is used as a argument here for all the methods that use it
        assertNotNull(new BlurMaskFilter(10.24f, Blur.INNER));
        assertNotNull(new BlurMaskFilter(10.24f, Blur.NORMAL));
        assertNotNull(new BlurMaskFilter(10.24f, Blur.OUTER));
        assertNotNull(new BlurMaskFilter(10.24f, Blur.SOLID));
    }
}
