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

package com.android.cts.verifier.projection.cube;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.ProjectedPresentation;



/**
 * Render tumbling cubes
 *
 */
public class CubePresentation extends ProjectedPresentation {
    public CubePresentation(Context context, Display display) {
        super(context, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.pca_cubes, null);
        setContentView(view);

        GLSurfaceView cubeView = (GLSurfaceView) view.findViewById(R.id.cube_view);
        final CubeRenderer renderer = new CubeRenderer(true);
        cubeView.setRenderer(renderer);

        cubeView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                renderer.explode();
                return true;
            }
        });
    }
}
