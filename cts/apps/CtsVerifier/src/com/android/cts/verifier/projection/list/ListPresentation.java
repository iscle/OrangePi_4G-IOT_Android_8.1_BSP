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

package com.android.cts.verifier.projection.list;

import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.ProjectedPresentation;

import java.util.ArrayList;

/**
 * Render a list view that scrolls
 */
public class ListPresentation extends ProjectedPresentation {
    private ArrayList<String> mItemList = new ArrayList<String>();
    private static final int NUM_ITEMS = 50; // Enough to make the list scroll

    /**
     * @param outerContext
     * @param display
     */
    public ListPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.pla_list, null);
        setContentView(view);

        for (int i = 0; i < NUM_ITEMS; ++i) {
            mItemList.add("Item #" + (1 + i));
        }

        ListView listView = (ListView) view.findViewById(R.id.pla_list);

        listView.setAdapter(new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_list_item_1, mItemList));
    }

}
