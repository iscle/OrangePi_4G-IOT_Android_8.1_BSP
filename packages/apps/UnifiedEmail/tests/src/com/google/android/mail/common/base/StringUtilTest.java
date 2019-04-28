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

package com.google.android.mail.common.base;

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

@SmallTest
public class StringUtilTest extends TestCase {
    public void testUnescapeHtml() {
        // Unicode Character 'KISSING CAT FACE WITH CLOSED EYES' (U+1F63D)
        final String unescaped1 = StringUtil.unescapeHTML("&#128573;");
        assertEquals(unescaped1, "\uD83D\uDE3D");

        // Unicode Character 'KISSING CAT FACE WITH CLOSED EYES' (U+1F63D)
        final String unescaped2 = StringUtil.unescapeHTML("&#x1f63d;");
        assertEquals(unescaped2, "\uD83D\uDE3D");

        // Unpaired surrogate, should not be converted
        final String unescaped3 = StringUtil.unescapeHTML("&#D83D;");
        assertEquals(unescaped3, "&#D83D;");

        // Paired surrogate, also should not be converted according to HTML spec
        final String unescaped4 = StringUtil.unescapeHTML("&#D83D;&#DE3D;");
        assertEquals(unescaped4, "&#D83D;&#DE3D;");

        // Named entity lowercase
        final String unescaped5 = StringUtil.unescapeHTML("&alpha;");
        assertEquals(unescaped5, "\u03B1");

        // Named entity uppercase
        final String unescaped6 = StringUtil.unescapeHTML("&Alpha;");
        assertEquals(unescaped6, "\u0391");
    }
}
