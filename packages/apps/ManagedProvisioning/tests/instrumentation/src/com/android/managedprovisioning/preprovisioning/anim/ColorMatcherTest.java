/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.anim;

import static android.graphics.Color.parseColor;
import static android.graphics.Color.rgb;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import android.support.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class ColorMatcherTest {
    private final ColorMatcher mColorMatcher = new ColorMatcher();

    @Test
    public void findsCloseColor() {
        assertCorrect(rgb(0, 0, 0), rgb(0, 0, 0));
        assertCorrect(rgb(1, 1, 1), rgb(0, 0, 0));
        assertCorrect(rgb(15, 15, 15), rgb(0, 0, 0));
        assertCorrect(rgb(16, 16, 16), rgb(32, 32, 32));
        assertCorrect(rgb(0xff, 0xff, 0xff), rgb(0xff, 0xff, 0xff));
        assertCorrect(rgb(0xfe, 0xfe, 0xfe), rgb(0xff, 0xff, 0xff));
        assertCorrect(rgb(100, 200, 50), rgb(96, 192, 64));
        assertCorrect(rgb(0xd4, 0, 0), rgb(0xe0, 0, 0));
        assertCorrect(parseColor("#d40000"), parseColor("#e00000"));
    }

    private void assertCorrect(int source, int expected) {
        int actual = mColorMatcher.findClosestColor(source);
        assertThat(actual, equalTo(expected));
    }
}