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

package com.android.tv.tuner.exoplayer;

import android.content.Context;

import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.android.tv.Features;
import com.android.tv.tuner.exoplayer.MpegTsPlayer.RendererBuilder;
import com.android.tv.tuner.exoplayer.MpegTsPlayer.RendererBuilderCallback;
import com.android.tv.tuner.exoplayer.audio.MpegTsDefaultAudioTrackRenderer;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.tvinput.PlaybackBufferListener;

/**
 * Builder for renderer objects for {@link MpegTsPlayer}.
 */
public class MpegTsRendererBuilder implements RendererBuilder {
    private final Context mContext;
    private final BufferManager mBufferManager;
    private final PlaybackBufferListener mBufferListener;

    public MpegTsRendererBuilder(Context context, BufferManager bufferManager,
            PlaybackBufferListener bufferListener) {
        mContext = context;
        mBufferManager = bufferManager;
        mBufferListener = bufferListener;
    }

    @Override
    public void buildRenderers(MpegTsPlayer mpegTsPlayer, DataSource dataSource,
            boolean mHasSoftwareAudioDecoder, RendererBuilderCallback callback) {
        // Build the video and audio renderers.
        SampleExtractor extractor = dataSource == null ?
                new MpegTsSampleExtractor(mBufferManager, mBufferListener) :
                new MpegTsSampleExtractor(dataSource, mBufferManager, mBufferListener);
        SampleSource sampleSource = new MpegTsSampleSource(extractor);
        MpegTsVideoTrackRenderer videoRenderer = new MpegTsVideoTrackRenderer(mContext,
                sampleSource, mpegTsPlayer.getMainHandler(), mpegTsPlayer);
        // TODO: Only using MpegTsDefaultAudioTrackRenderer for A/V sync issue. We will use
        // {@link MpegTsMediaCodecAudioTrackRenderer} when we use ExoPlayer's extractor.
        TrackRenderer audioRenderer =
                new MpegTsDefaultAudioTrackRenderer(
                        sampleSource,
                        MediaCodecSelector.DEFAULT,
                        mpegTsPlayer.getMainHandler(),
                        mpegTsPlayer,
                        mHasSoftwareAudioDecoder,
                        !Features.AC3_SOFTWARE_DECODE.isEnabled(mContext));
        Cea708TextTrackRenderer textRenderer = new Cea708TextTrackRenderer(sampleSource);

        TrackRenderer[] renderers = new TrackRenderer[MpegTsPlayer.RENDERER_COUNT];
        renderers[MpegTsPlayer.TRACK_TYPE_VIDEO] = videoRenderer;
        renderers[MpegTsPlayer.TRACK_TYPE_AUDIO] = audioRenderer;
        renderers[MpegTsPlayer.TRACK_TYPE_TEXT] = textRenderer;
        callback.onRenderers(null, renderers);
    }
}
