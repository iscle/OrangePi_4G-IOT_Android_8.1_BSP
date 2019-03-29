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
package android.media.cts;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioAttributes;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Class for playing audio by using audio track.
 * {@link #write(byte[], int, int)} and {@link #write(short[], int, int)} methods will
 * block until all data has been written to system. In order to avoid blocking, this class
 * caculates available buffer size first then writes to audio sink.
 */
public class NonBlockingAudioTrack {
    private static final String TAG = NonBlockingAudioTrack.class.getSimpleName();

    class QueueElement {
        ByteBuffer data;
        int size;
        long pts;
    }

    private AudioTrack mAudioTrack;
    private int mSampleRate;
    private int mNumBytesQueued = 0;
    private LinkedList<QueueElement> mQueue = new LinkedList<QueueElement>();
    private boolean mStopped;

    public NonBlockingAudioTrack(int sampleRate, int channelCount, boolean hwAvSync,
                    int audioSessionId) {
        int channelConfig;
        switch (channelCount) {
            case 1:
                channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            default:
                throw new IllegalArgumentException();
        }

        int minBufferSize =
            AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);

        int bufferSize = 2 * minBufferSize;

        if (!hwAvSync) {
            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM);
        }
        else {
            // build AudioTrack using Audio Attributes and FLAG_HW_AV_SYNC
            AudioAttributes audioAttributes = (new AudioAttributes.Builder())
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .setFlags(AudioAttributes.FLAG_HW_AV_SYNC)
                            .build();
            AudioFormat audioFormat = (new AudioFormat.Builder())
                            .setChannelMask(channelConfig)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .build();
             mAudioTrack = new AudioTrack(audioAttributes, audioFormat, bufferSize,
                                    AudioTrack.MODE_STREAM, audioSessionId);
        }

        mSampleRate = sampleRate;
    }

    public long getAudioTimeUs() {
        int numFramesPlayed = mAudioTrack.getPlaybackHeadPosition();

        return (numFramesPlayed * 1000000L) / mSampleRate;
    }

    public int getNumBytesQueued() {
        return mNumBytesQueued;
    }

    public void play() {
        mStopped = false;
        mAudioTrack.play();
    }

    public void stop() {
        if (mQueue.isEmpty()) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
        } else {
            mStopped = true;
        }
    }

    public void pause() {
        mAudioTrack.pause();
    }

    public void flush() {
        if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        mAudioTrack.flush();
        mQueue.clear();
        mNumBytesQueued = 0;
        mStopped = false;
    }

    public void release() {
        mQueue.clear();
        mNumBytesQueued = 0;
        mAudioTrack.release();
        mAudioTrack = null;
        mStopped = false;
    }

    public void process() {
        while (!mQueue.isEmpty()) {
            QueueElement element = mQueue.peekFirst();
            int written = mAudioTrack.write(element.data, element.size,
                                            AudioTrack.WRITE_NON_BLOCKING, element.pts);
            if (written < 0) {
                throw new RuntimeException("Audiotrack.write() failed.");
            }

            mNumBytesQueued -= written;
            element.size -= written;
            if (element.size != 0) {
                break;
            }
            mQueue.removeFirst();
        }
        if (mStopped) {
            mAudioTrack.stop();
            mNumBytesQueued = 0;
            mStopped = false;
        }
    }

    public int getPlayState() {
        return mAudioTrack.getPlayState();
    }

    public void write(ByteBuffer data, int size, long pts) {
        QueueElement element = new QueueElement();
        element.data = data;
        element.size = size;
        element.pts  = pts;

        // accumulate size written to queue
        mNumBytesQueued += size;
        mQueue.add(element);
    }
}

