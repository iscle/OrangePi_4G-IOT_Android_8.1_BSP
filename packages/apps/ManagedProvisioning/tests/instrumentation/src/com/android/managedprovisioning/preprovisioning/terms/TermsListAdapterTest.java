/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

import android.graphics.Color;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.view.LayoutInflater;

import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.HtmlToSpannedParser;
import com.android.managedprovisioning.preprovisioning.WebActivity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

@SmallTest
public class TermsListAdapterTest {
    private @Mock LayoutInflater mLayoutInflater;
    private @Mock AccessibilityContextMenuMaker mContextMenuMaker;

    private List<TermsDocument> mDocs;
    private HtmlToSpannedParser mHtmlToSpannedParser;
    private TermsListAdapter.GroupExpandedInfo mGroupInfoAlwaysCollapsed = i -> false;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        TermsDocument doc1 = TermsDocument.createInstance("h1", "c1");
        TermsDocument doc2 = TermsDocument.createInstance("h2", "c2");
        TermsDocument doc3 = TermsDocument.createInstance("h3", "c3");
        mDocs = Arrays.asList(doc1, doc2, doc3);

        mHtmlToSpannedParser = new HtmlToSpannedParser(new ClickableSpanFactory(Color.MAGENTA),
                url -> WebActivity.createIntent(InstrumentationRegistry.getTargetContext(), url,
                        Color.BLUE));
    }

    @Test
    public void returnsCorrectDocument() {
        // given: an adapter
        TermsListAdapter adapter = new TermsListAdapter(mDocs, mLayoutInflater, mContextMenuMaker,
                mHtmlToSpannedParser, mGroupInfoAlwaysCollapsed);

        // when: asked for a document from the initially passed-in list
        for (int i = 0; i < mDocs.size(); i++) {
            // then: elements from that list are returned
            assertThat(adapter.getChild(i, 0), sameInstance(mDocs.get(i)));
            assertThat(adapter.getGroup(i), sameInstance(mDocs.get(i)));
        }
    }
}