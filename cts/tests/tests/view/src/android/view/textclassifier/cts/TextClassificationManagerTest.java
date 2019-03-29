/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.os.LocaleList;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.textclassifier.TextClassification;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;
import android.view.textclassifier.TextSelection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassificationManagerTest {

    private static final LocaleList LOCALES = LocaleList.forLanguageTags("en");
    private static final int START = 1;
    private static final int END = 3;
    private static final String TEXT = "text";

    private TextClassificationManager mManager;
    private TextClassifier mClassifier;

    @Before
    public void setup() {
        mManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(TextClassificationManager.class);
        mManager.setTextClassifier(null); // Resets the classifier.
        mClassifier = mManager.getTextClassifier();
    }

    @Test
    public void testSmartSelection() {
        assertValidResult(mClassifier.suggestSelection(TEXT, START, END, LOCALES));
    }

    @Test
    public void testClassifyText() {
        assertValidResult(mClassifier.classifyText(TEXT, START, END, LOCALES));
    }

    @Test
    public void testNoOpClassifier() {
        mManager.setTextClassifier(TextClassifier.NO_OP);
        mClassifier = mManager.getTextClassifier();

        final TextSelection selection = mClassifier.suggestSelection(TEXT, START, END, LOCALES);
        assertValidResult(selection);
        assertEquals(START, selection.getSelectionStartIndex());
        assertEquals(END, selection.getSelectionEndIndex());
        assertEquals(0, selection.getEntityCount());

        final TextClassification classification =
                mClassifier.classifyText(TEXT, START, END, LOCALES);
        assertValidResult(classification);
        assertNull(classification.getText());
        assertEquals(0, classification.getEntityCount());
        assertNull(classification.getIcon());
        assertNull(classification.getLabel());
        assertNull(classification.getIntent());
        assertNull(classification.getOnClickListener());
    }

    @Test
    public void testSetTextClassifier() {
        final TextClassifier classifier = mock(TextClassifier.class);
        mManager.setTextClassifier(classifier);
        assertEquals(classifier, mManager.getTextClassifier());
    }

    private static void assertValidResult(TextSelection selection) {
        assertNotNull(selection);
        assertTrue(selection.getEntityCount() >= 0);
        for (int i = 0; i < selection.getEntityCount(); i++) {
            final String entity = selection.getEntity(i);
            assertNotNull(entity);
            final float confidenceScore = selection.getConfidenceScore(entity);
            assertTrue(confidenceScore >= 0);
            assertTrue(confidenceScore <= 1);
        }
    }

    private static void assertValidResult(TextClassification classification) {
        assertNotNull(classification);
        assertTrue(classification.getEntityCount() >= 0);
        for (int i = 0; i < classification.getEntityCount(); i++) {
            final String entity = classification.getEntity(i);
            assertNotNull(entity);
            final float confidenceScore = classification.getConfidenceScore(entity);
            assertTrue(confidenceScore >= 0);
            assertTrue(confidenceScore <= 1);
        }
    }
}

