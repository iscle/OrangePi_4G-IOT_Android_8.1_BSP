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
package com.android.car.systemupdater;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class DeviceListFragment extends Fragment {
    private ListView mFolderListView;
    private SystemUpdaterActivity mActivity;
    private File[] mFileNames = new File[0];
    private FileAdapter mAdapter;
    private Button mBackButton;
    private TextView mTitle;
    private String mTitleText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (SystemUpdaterActivity) getActivity();
        mAdapter = new FileAdapter(mActivity, R.layout.folder_entry, mFileNames);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.folder_list, container, false);
        mTitle = (TextView) v.findViewById(R.id.title);
        if (mTitleText != null) {
            mTitle.setText(mTitleText);
        }
        mFolderListView = (ListView) v.findViewById(R.id.folder_list);
        mFolderListView.setAdapter(mAdapter);
        mFolderListView.setOnItemClickListener(mItemClickListener);
        mBackButton = (Button) v.findViewById(R.id.back);
        mBackButton.setOnClickListener(mBackButtonListener);
        return v;
    }

    public void updateList(File[] locations) {
        if (locations != null) {
            mFileNames = locations;
            if (mAdapter != null) {
                mAdapter.setLocations(mFileNames);
            }
        }
    }

    public void updateTitle(String title) {
        if (mTitle != null) {
            mTitle.setText(title);
        } else {
            mTitleText = title;
        }
    }

    private final View.OnClickListener mBackButtonListener =
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mActivity.onBackPressed();
                }
            };

    private final AdapterView.OnItemClickListener mItemClickListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view,
                                        int position, long id) {
                    if (mFileNames[position].getName().endsWith(".zip")) {
                        mActivity.checkPackage(mFileNames[position]);
                    } else if (mFileNames[position].isDirectory()) {
                        mActivity.showFolderContent(mFileNames[position]);
                    } else {
                        Toast.makeText(mActivity, "This is not a valid file for updating",
                                Toast.LENGTH_LONG).show();
                    }
                }
            };
}
