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

package com.android.documentsui.testing;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.android.documentsui.dirlist.TestDocumentsAdapter;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;


public class TestRecyclerView extends RecyclerView {

    private List<RecyclerView.ViewHolder> holders = new ArrayList<>();
    private TestDocumentsAdapter adapter;

    public TestRecyclerView(Context context) {
        super(context);
    }

    @Override
    public ViewHolder findViewHolderForAdapterPosition(int position) {
        return holders.get(position);
    }

    @Override
    public void addOnScrollListener(OnScrollListener listener) {
    }

    @Override
    public void smoothScrollToPosition(int position) {
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        return adapter;
    }

    public void setItems(List<String> modelIds) {
        holders = new ArrayList<>();
        for (String modelId: modelIds) {
            holders.add(new TestViewHolder(Views.createTestView()));
        }
        adapter.updateTestModelIds(modelIds);
    }

    public static TestRecyclerView create(List<String> modelIds) {
        final TestRecyclerView view = Mockito.mock(TestRecyclerView.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        view.holders = new ArrayList<>();
        for (String modelId: modelIds) {
            view.holders.add(new TestViewHolder(Views.createTestView()));
        }
        view.adapter = new TestDocumentsAdapter(modelIds);
        return view;
    }

    public void assertItemViewFocused(int pos) {
        Mockito.verify(holders.get(pos).itemView).requestFocus();
    }

    private static class TestViewHolder extends RecyclerView.ViewHolder {
        public TestViewHolder(View itemView) {
            super(itemView);
        }
    }
}
