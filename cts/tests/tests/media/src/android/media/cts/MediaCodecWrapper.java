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

package android.media.cts;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.Callback;
import android.media.MediaFormat;
import android.os.Bundle;
import java.nio.ByteBuffer;

/**
 * This interface exposes the minimum set of {@link MediaCodec} APIs tested in {@link EncodeDecodeTest}
 * and {@link VpxEncoderTest}.
 */
public interface MediaCodecWrapper {

  void release();

  void configure(MediaFormat format, int flags);

  void setInputSurface(InputSurfaceInterface inputSurface);

  InputSurfaceInterface createInputSurface();

  void start();

  void stop();

  int dequeueOutputBuffer(BufferInfo info, long timeoutUs);

  void releaseOutputBuffer(int index, boolean render);

  void signalEndOfInputStream();

  String getOutputFormatString();

  ByteBuffer getOutputBuffer(int index);

  ByteBuffer[] getOutputBuffers();

  ByteBuffer getInputBuffer(int index);

  ByteBuffer[] getInputBuffers();

  void queueInputBuffer(
          int index,
          int offset,
          int size,
          long presentationTimeUs,
          int flags);

  int dequeueInputBuffer(long timeoutUs);

  void setParameters(Bundle params);

  void setCallback(Callback mCallback);

}
