/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.bluetooth;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

class TestAdapter extends BaseAdapter {
    Context context;
    List<Test> tests;
    LayoutInflater inflater;

    private class Test {
        private boolean mPassed;
        private final int mInstructions;

        protected Test(int instructions) {
            this.mPassed = false;
            this.mInstructions = instructions;
        }
    }

    public TestAdapter(Context context, List<Integer> testInstructions) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        this.tests = new ArrayList<Test>();
        for (int t : testInstructions) {
            this.tests.add(new Test(t));
        }
    }

    @Override
    public int getCount() {
        return tests.size();
    }

    @Override
    public Object getItem(int position) {
        return tests.get(position);
    }

    public void setTestPass(int position) {
        tests.get(position).mPassed = true;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewGroup vg;

        if (convertView != null) {
            vg = (ViewGroup) convertView;
        } else {
            vg = (ViewGroup) inflater.inflate(R.layout.ble_test_item, null);
        }

        Test test = tests.get(position);
        if (test.mPassed) {
            ((ImageView) vg.findViewById(R.id.status)).setImageResource(R.drawable.fs_good);
        } else {
            ((ImageView) vg.findViewById(R.id.status)).
                            setImageResource(R.drawable.fs_indeterminate);
        }
        ((TextView) vg.findViewById(R.id.instructions)).setText(test.mInstructions);

        return vg;
    }
}
