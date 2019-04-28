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
package com.android.tv.tuner.exoplayer;

import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.TimestampAdjuster;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Extractor factory, mainly aim at create TsExtractor with FLAG_ALLOW_NON_IDR_KEYFRAMES flags for
 * H.264 stream
 */
public final class ExoPlayerExtractorsFactory implements ExtractorsFactory {
    @Override
    public Extractor[] createExtractors() {
        // Only create TsExtractor since we only target MPEG2TS stream.
        Extractor[] extractors = {
                new TsExtractor(new TimestampAdjuster(0), new DefaultTsPayloadReaderFactory(
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES), false) };
        return extractors;
    }
}
