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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.GetChars;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GetCharsTest {

    // Returns an array of three GetChars objects, of three different classes:
    // SpannableString, SpannableStringBuilder, and SpannedString.
    private static GetChars[] makeGetChars(String s) {
        return new GetChars[]{
                new SpannableString(s),
                new SpannableStringBuilder(s),
                new SpannedString(s)};
    }

    @Test
    public void testGetChars() {
        final GetChars[] getCharsCases = makeGetChars("\uD83D\uDE00");  // U+1F600 GRINNING FACE
        for (GetChars getChars : getCharsCases) {
            final char[] target = new char[getChars.length()];
            getChars.getChars(0, getChars.length(), target, 0);
            assertEquals('\uD83D', target[0]);
            assertEquals('\uDE00', target[1]);

            try {
                getChars.getChars(-1, getChars.length(), target, 0);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }

            try {
                getChars.getChars(1, 0, target, 0);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }

            try {
                getChars.getChars(0, getChars.length() + 1, target, 0);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }

            try {
                getChars.getChars(0, getChars.length(), target, -1);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }

            try {
                getChars.getChars(0, getChars.length(), target, 1);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }
        }
    }
}


