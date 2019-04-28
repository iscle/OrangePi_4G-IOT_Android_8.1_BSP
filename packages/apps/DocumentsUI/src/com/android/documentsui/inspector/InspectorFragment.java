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
package com.android.documentsui.inspector;

import static com.android.internal.util.Preconditions.checkArgument;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;
import com.android.documentsui.inspector.InspectorController.Loader;

/**
 * Displays the Properties view in Files.
 */
public class InspectorFragment extends Fragment {

    private static final String DOC_URI_ARG = "docUri";
    private InspectorController mController;
    private ScrollView mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        final Loader loader = new DocumentLoader(getActivity(), getLoaderManager());

        mView = (ScrollView) inflater.inflate(R.layout.document_inspector_fragment,
                container, false);
        boolean showDebug = (boolean) getArguments().get(Shared.EXTRA_SHOW_DEBUG);
        mController = new InspectorController(getActivity(), loader, mView, showDebug);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Uri uri = (Uri) getArguments().get(DOC_URI_ARG);
        mController.loadInfo(uri);
    }

    @Override
    public void onStop() {
        super.onStop();
        mController.reset();
    }

    /**
     * Creates a fragment and sets a Uri as an argument.
     */
    public static InspectorFragment newInstance(Intent intent) {
        Uri uri = intent.getData();
        boolean showDebug = intent.getBooleanExtra(Shared.EXTRA_SHOW_DEBUG, false);

        checkArgument(uri.getScheme().equals("content"));
        Bundle args = new Bundle();
        args.putParcelable(DOC_URI_ARG, uri);
        args.putBoolean(Shared.EXTRA_SHOW_DEBUG, showDebug);

        InspectorFragment fragment = new InspectorFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
