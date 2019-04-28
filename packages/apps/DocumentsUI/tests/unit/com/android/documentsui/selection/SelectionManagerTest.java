/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.selection;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.SparseBooleanArray;

import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.testing.SelectionManagers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SelectionManagerTest {

    private static final List<String> ITEMS = TestData.create(100);

    private final Set<String> mIgnored = new HashSet<>();
    private TestDocumentsAdapter mAdapter;
    private SelectionManager mManager;
    private TestSelectionListener mCallback;
    private TestItemSelectionListener mItemCallback;
    private SelectionProbe mSelection;

    @Before
    public void setUp() throws Exception {
        mCallback = new TestSelectionListener();
        mItemCallback = new TestItemSelectionListener();
        mAdapter = new TestDocumentsAdapter(ITEMS);
        mManager = SelectionManagers.createTestInstance(
                mAdapter,
                SelectionManager.MODE_MULTIPLE,
                (String id, boolean nextState) -> (!nextState || !mIgnored.contains(id)));
        mManager.addCallback(mCallback);
        mManager.addItemCallback(mItemCallback);

        mSelection = new SelectionProbe(mManager, mItemCallback);

        mIgnored.clear();
    }

    @Test
    public void testSelection() {
        // Check selection.
        mManager.toggleSelection(ITEMS.get(7));
        mSelection.assertSelection(7);
        // Check deselection.
        mManager.toggleSelection(ITEMS.get(7));
        mSelection.assertNoSelection();
    }

    @Test
    public void testSelection_DoNothingOnUnselectableItem() {
        mIgnored.add(ITEMS.get(7));

        mManager.toggleSelection(ITEMS.get(7));
        mSelection.assertNoSelection();
    }

    @Test
    public void testSelection_NotifiesSelectionChanged() {
        // Selection should notify.
        mManager.toggleSelection(ITEMS.get(7));
        mCallback.assertSelectionChanged();
        // Deselection should notify.
        mManager.toggleSelection(ITEMS.get(7));
        mCallback.assertSelectionChanged();
    }

    @Test
    public void testSelection_PersistsOnUpdate() {
        mManager.toggleSelection(ITEMS.get(7));

        mAdapter.updateTestModelIds(ITEMS);

        mSelection.assertSelection(7);
    }

    @Test
    public void testSelection_IntersectsWithNewDataSet() {
        mManager.toggleSelection(ITEMS.get(99));
        mManager.toggleSelection(ITEMS.get(7));

        mAdapter.updateTestModelIds(TestData.create(50));

        mSelection.assertSelection(7);
    }

    @Test
    public void testSetItemsSelected() {
        mManager.setItemsSelected(getStringIds(6, 7, 8), true);

        mSelection.assertRangeSelected(6, 8);
    }

    @Test
    public void testSetItemsSelected_SkipUnselectableItem() {
        mIgnored.add(ITEMS.get(7));

        mManager.setItemsSelected(getStringIds(6, 7, 8), true);

        mSelection.assertSelected(6);
        mSelection.assertNotSelected(7);
        mSelection.assertSelected(8);
    }

    @Test
    public void testRangeSelection() {
        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(19);
        mSelection.assertRangeSelection(15, 19);
    }

    @Test
    public void testRangeSelection_SkipUnselectableItem() {
        mIgnored.add(ITEMS.get(17));

        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(19);

        mSelection.assertRangeSelected(15, 16);
        mSelection.assertNotSelected(17);
        mSelection.assertRangeSelected(18, 19);
    }

    @Test
    public void testRangeSelection_snapExpand() {
        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(19);
        mManager.snapRangeSelection(27);
        mSelection.assertRangeSelection(15, 27);
    }

    @Test
    public void testRangeSelection_snapContract() {
        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(27);
        mManager.snapRangeSelection(19);
        mSelection.assertRangeSelection(15, 19);
    }

    @Test
    public void testRangeSelection_snapInvert() {
        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(27);
        mManager.snapRangeSelection(3);
        mSelection.assertRangeSelection(3, 15);
    }

    @Test
    public void testRangeSelection_multiple() {
        mManager.startRangeSelection(15);
        mManager.snapRangeSelection(27);
        mManager.endRangeSelection();
        mManager.startRangeSelection(42);
        mManager.snapRangeSelection(57);
        mSelection.assertSelectionSize(29);
        mSelection.assertRangeSelected(15, 27);
        mSelection.assertRangeSelected(42, 57);
    }

    @Test
    public void testProvisionalRangeSelection() {
        mManager.startRangeSelection(13);
        mManager.snapProvisionalRangeSelection(15);
        mSelection.assertRangeSelection(13, 15);
        mManager.getSelection().applyProvisionalSelection();
        mManager.endRangeSelection();
        mSelection.assertSelectionSize(3);
    }

    @Test
    public void testProvisionalRangeSelection_endEarly() {
        mManager.startRangeSelection(13);
        mManager.snapProvisionalRangeSelection(15);
        mSelection.assertRangeSelection(13, 15);

        mManager.endRangeSelection();
        // If we end range selection prematurely for provision selection, nothing should be selected
        // except the first item
        mSelection.assertSelectionSize(1);
    }

    @Test
    public void testProvisionalRangeSelection_snapExpand() {
        mManager.startRangeSelection(13);
        mManager.snapProvisionalRangeSelection(15);
        mSelection.assertRangeSelection(13, 15);
        mManager.getSelection().applyProvisionalSelection();
        mManager.snapRangeSelection(18);
        mSelection.assertRangeSelection(13, 18);
    }

    @Test
    public void testCombinationRangeSelection_IntersectsOldSelection() {
        mManager.startRangeSelection(13);
        mManager.snapRangeSelection(15);
        mSelection.assertRangeSelection(13, 15);

        mManager.startRangeSelection(11);
        mManager.snapProvisionalRangeSelection(18);
        mSelection.assertRangeSelected(11, 18);
        mManager.endRangeSelection();
        mSelection.assertRangeSelected(13, 15);
        mSelection.assertRangeSelected(11, 11);
        mSelection.assertSelectionSize(4);
    }

    @Test
    public void testProvisionalSelection() {
        Selection s = mManager.getSelection();
        mSelection.assertNoSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mItemCallback.onItemStateChanged(ITEMS.get(1), true);
        mItemCallback.onItemStateChanged(ITEMS.get(2), true);

        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_Replace() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mItemCallback.onItemStateChanged(ITEMS.get(1), true);
        mItemCallback.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));

        mItemCallback.onItemStateChanged(ITEMS.get(1), false);
        mItemCallback.onItemStateChanged(ITEMS.get(2), false);
        provisional.clear();

        mItemCallback.onItemStateChanged(ITEMS.get(3), true);
        mItemCallback.onItemStateChanged(ITEMS.get(4), true);
        provisional.append(3, true);
        provisional.append(4, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(3, 4);
    }

    @Test
    public void testProvisionalSelection_IntersectsExistingProvisionalSelection() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mItemCallback.onItemStateChanged(ITEMS.get(1), true);
        mItemCallback.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));

        mItemCallback.onItemStateChanged(ITEMS.get(1), false);
        mItemCallback.onItemStateChanged(ITEMS.get(2), false);
        provisional.clear();

        mItemCallback.onItemStateChanged(ITEMS.get(1), true);
        provisional.append(1, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1);
    }

    @Test
    public void testProvisionalSelection_Apply() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mItemCallback.onItemStateChanged(ITEMS.get(1), true);
        mItemCallback.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));
        s.applyProvisionalSelection();

        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_Cancel() {
        mManager.toggleSelection(ITEMS.get(1));
        mManager.toggleSelection(ITEMS.get(2));
        Selection s = mManager.getSelection();

        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(3, true);
        provisional.append(4, true);
        s.setProvisionalSelection(getItemIds(provisional));
        s.cancelProvisionalSelection();

        // Original selection should remain.
        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_IntersectsAppliedSelection() {
        mManager.toggleSelection(ITEMS.get(1));
        mManager.toggleSelection(ITEMS.get(2));
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mItemCallback.onItemStateChanged(ITEMS.get(3), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(2, true);
        provisional.append(3, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1, 2, 3);
    }

    private static Set<String> getItemIds(SparseBooleanArray selection) {
        Set<String> ids = new HashSet<>();

        int count = selection.size();
        for (int i = 0; i < count; ++i) {
            ids.add(ITEMS.get(selection.keyAt(i)));
        }

        return ids;
    }

    private static Iterable<String> getStringIds(int... ids) {
        List<String> stringIds = new ArrayList<>(ids.length);
        for (int id : ids) {
            stringIds.add(ITEMS.get(id));
        }
        return stringIds;
    }
}
