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
package com.android.managedprovisioning.common;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Locale;


@SmallTest
public class StringConcatenatorTest {
    private StringConcatenator mInstance;
    private LocaleTestUtils mLocaleTestUtils;

    @Before
    public void setUp() throws Exception {
        mLocaleTestUtils = new LocaleTestUtils(InstrumentationRegistry.getTargetContext());
        mLocaleTestUtils.setLocale(Locale.US); // sets a stable locale so tests behave consistently

        Resources resources = InstrumentationRegistry.getTargetContext().getResources();
        mInstance = new StringConcatenator(resources);
    }

    @After
    public void tearDown() throws Exception {
        mLocaleTestUtils.restoreLocale();
    }

    @Test
    public void joinNull() {
        assertCorrect(null, null);
    }

    @Test
    public void joinEmpty() {
        assertCorrect(emptyList(), "");
    }

    @Test
    public void joinOne() {
        assertCorrect(singletonList("word1"), "word1");
    }

    @Test
    public void joinTwo() {
        assertCorrect(asList("word1", "word2"), "word1 and word2");
    }

    @Test
    public void joinThree() {
        assertCorrect(asList("word1", "word2", "word3"), "word1, word2, and word3");
    }

    @Test
    public void joinMany() {
        assertCorrect(asList("word1", "word2", "word3", "word4", "word5", "word6", "word7"),
                "word1, word2, word3, word4, word5, word6, and word7"
        );
    }

    private void assertCorrect(List<String> input, String expected) {
        assertThat(mInstance.join(input), equalTo(expected));
    }
}
