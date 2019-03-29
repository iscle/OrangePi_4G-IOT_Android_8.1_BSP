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

package com.android.cts.verifier.projection.video;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.VideoView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.ProjectedPresentation;

/**
 * Play a test video that determines if the video and audio are in sync in projected presentations
 */
public class VideoPresentation extends ProjectedPresentation {

    /**
     * @param outerContext
     * @param display
     */
    public VideoPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.pva_video, null);
        setContentView(view);
        VideoView videoView = (VideoView) view.findViewById(R.id.video_view);
        videoView.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
            }
        });
        String packageName = getContext().getPackageName();
        Uri uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.test_video);
        videoView.setVideoURI(uri);
        videoView.start();
    }
}
