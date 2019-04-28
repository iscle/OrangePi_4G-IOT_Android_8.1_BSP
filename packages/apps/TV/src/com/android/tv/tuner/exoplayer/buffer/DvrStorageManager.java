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

package com.android.tv.tuner.exoplayer.buffer;

import android.media.MediaFormat;
import android.util.Log;
import android.util.Pair;

import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.google.protobuf.nano.MessageNano;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Manages DVR storage.
 */
public class DvrStorageManager implements BufferManager.StorageManager {
    private static final String TAG = "DvrStorageManager";

    // TODO: make serializable classes and use protobuf after internal data structure is finalized.
    private static final String KEY_PIXEL_WIDTH_HEIGHT_RATIO =
            "com.google.android.videos.pixelWidthHeightRatio";
    private static final String META_FILE_TYPE_AUDIO = "audio";
    private static final String META_FILE_TYPE_VIDEO = "video";
    private static final String META_FILE_TYPE_CAPTION = "caption";
    private static final String META_FILE_SUFFIX = ".meta";
    private static final String IDX_FILE_SUFFIX = ".idx";
    private static final String IDX_FILE_SUFFIX_V2 = IDX_FILE_SUFFIX + "2";

    // Size of minimum reserved storage buffer which will be used to save meta files
    // and index files after actual recording finished.
    private static final long MIN_BUFFER_BYTES = 256L * 1024 * 1024;
    private static final int NO_VALUE = -1;
    private static final long NO_VALUE_LONG = -1L;

    private final File mBufferDir;

    // {@code true} when this is for recording, {@code false} when this is for replaying.
    private final boolean mIsRecording;

    public DvrStorageManager(File file, boolean isRecording) {
        mBufferDir = file;
        mBufferDir.mkdirs();
        mIsRecording = isRecording;
    }

    @Override
    public File getBufferDir() {
        return mBufferDir;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean reachedStorageMax(long bufferSize, long pendingDelete) {
        return false;
    }

    @Override
    public boolean hasEnoughBuffer(long pendingDelete) {
        return !mIsRecording || mBufferDir.getUsableSpace() >= MIN_BUFFER_BYTES;
    }

    private void readFormatInt(DataInputStream in, MediaFormat format, String key)
            throws IOException {
        int val = in.readInt();
        if (val != NO_VALUE) {
            format.setInteger(key, val);
        }
    }

    private void readFormatLong(DataInputStream in, MediaFormat format, String key)
            throws IOException {
        long val = in.readLong();
        if (val != NO_VALUE_LONG) {
            format.setLong(key, val);
        }
    }

    private void readFormatFloat(DataInputStream in, MediaFormat format, String key)
            throws IOException {
        float val = in.readFloat();
        if (val != NO_VALUE) {
            format.setFloat(key, val);
        }
    }

    private String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0) {
            return null;
        }
        byte [] strBytes = new byte[len];
        in.readFully(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }

    private void readFormatString(DataInputStream in, MediaFormat format, String key)
            throws IOException {
        String str = readString(in);
        if (str != null) {
            format.setString(key, str);
        }
    }

    private void readFormatStringOptional(DataInputStream in, MediaFormat format, String key) {
        try {
            String str = readString(in);
            if (str != null) {
                format.setString(key, str);
            }
        } catch (IOException e) {
            // Since we are reading optional field, ignore the exception.
        }
    }

    private ByteBuffer readByteBuffer(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0) {
            return null;
        }
        byte [] bytes = new byte[len];
        in.readFully(bytes);
        ByteBuffer buffer = ByteBuffer.allocate(len);
        buffer.put(bytes);
        buffer.flip();

        return buffer;
    }

    private void readFormatByteBuffer(DataInputStream in, MediaFormat format, String key)
            throws IOException {
        ByteBuffer buffer = readByteBuffer(in);
        if (buffer != null) {
            format.setByteBuffer(key, buffer);
        }
    }

    @Override
    public List<BufferManager.TrackFormat> readTrackInfoFiles(boolean isAudio) {
        List<BufferManager.TrackFormat> trackFormatList = new ArrayList<>();
        int index = 0;
        boolean trackNotFound = false;
        do {
            String fileName = (isAudio ? META_FILE_TYPE_AUDIO : META_FILE_TYPE_VIDEO)
                    + ((index == 0) ? META_FILE_SUFFIX : (index + META_FILE_SUFFIX));
            File file = new File(getBufferDir(), fileName);
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                String name = readString(in);
                MediaFormat format = new MediaFormat();
                readFormatString(in, format, MediaFormat.KEY_MIME);
                readFormatInt(in, format, MediaFormat.KEY_MAX_INPUT_SIZE);
                readFormatInt(in, format, MediaFormat.KEY_WIDTH);
                readFormatInt(in, format, MediaFormat.KEY_HEIGHT);
                readFormatInt(in, format, MediaFormat.KEY_CHANNEL_COUNT);
                readFormatInt(in, format, MediaFormat.KEY_SAMPLE_RATE);
                readFormatFloat(in, format, KEY_PIXEL_WIDTH_HEIGHT_RATIO);
                for (int i = 0; i < 3; ++i) {
                    readFormatByteBuffer(in, format, "csd-" + i);
                }
                readFormatLong(in, format, MediaFormat.KEY_DURATION);

                // This is optional since language field is added later.
                readFormatStringOptional(in, format, MediaFormat.KEY_LANGUAGE);
                trackFormatList.add(new BufferManager.TrackFormat(name, format));
            } catch (IOException e) {
                trackNotFound = true;
            }
            index++;
        } while(!trackNotFound);
        return trackFormatList;
    }

    /**
     * Reads caption information from files.
     *
     * @return a list of {@link AtscCaptionTrack} objects which store caption information.
     */
    public List<AtscCaptionTrack> readCaptionInfoFiles() {
        List<AtscCaptionTrack> tracks = new ArrayList<>();
        int index = 0;
        boolean trackNotFound = false;
        do {
            String fileName = META_FILE_TYPE_CAPTION +
                    ((index == 0) ? META_FILE_SUFFIX : (index + META_FILE_SUFFIX));
            File file = new File(getBufferDir(), fileName);
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                byte[] data = new byte[(int) file.length()];
                in.read(data);
                tracks.add(AtscCaptionTrack.parseFrom(data));
            } catch (IOException e) {
                trackNotFound = true;
            }
            index++;
        } while(!trackNotFound);
        return tracks;
    }

    private ArrayList<BufferManager.PositionHolder> readOldIndexFile(File indexFile)
            throws IOException {
        ArrayList<BufferManager.PositionHolder> indices = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new FileInputStream(indexFile))) {
            long count = in.readLong();
            for (long i = 0; i < count; ++i) {
                long positionUs = in.readLong();
                indices.add(new BufferManager.PositionHolder(positionUs, positionUs, 0));
            }
            return indices;
        }
    }

    private ArrayList<BufferManager.PositionHolder> readNewIndexFile(File indexFile)
            throws IOException {
        ArrayList<BufferManager.PositionHolder> indices = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new FileInputStream(indexFile))) {
            long count = in.readLong();
            for (long i = 0; i < count; ++i) {
                long positionUs = in.readLong();
                long basePositionUs = in.readLong();
                int offset = in.readInt();
                indices.add(new BufferManager.PositionHolder(positionUs, basePositionUs, offset));
            }
            return indices;
        }
    }

    @Override
    public ArrayList<BufferManager.PositionHolder> readIndexFile(String trackId)
            throws IOException {
        File file = new File(getBufferDir(), trackId + IDX_FILE_SUFFIX_V2);
        if (file.exists()) {
            return readNewIndexFile(file);
        } else {
            return readOldIndexFile(new File(getBufferDir(),trackId + IDX_FILE_SUFFIX));
        }
    }

    private void writeFormatInt(DataOutputStream out, MediaFormat format, String key)
            throws IOException {
        if (format.containsKey(key)) {
            out.writeInt(format.getInteger(key));
        } else {
            out.writeInt(NO_VALUE);
        }
    }

    private void writeFormatLong(DataOutputStream out, MediaFormat format, String key)
            throws IOException {
        if (format.containsKey(key)) {
            out.writeLong(format.getLong(key));
        } else {
            out.writeLong(NO_VALUE_LONG);
        }
    }

    private void writeFormatFloat(DataOutputStream out, MediaFormat format, String key)
            throws IOException {
        if (format.containsKey(key)) {
            out.writeFloat(format.getFloat(key));
        } else {
            out.writeFloat(NO_VALUE);
        }
    }

    private void writeString(DataOutputStream out, String str) throws IOException {
        byte [] data = str.getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        if (data.length > 0) {
            out.write(data);
        }
    }

    private void writeFormatString(DataOutputStream out, MediaFormat format, String key)
            throws IOException {
        if (format.containsKey(key)) {
            writeString(out, format.getString(key));
        } else {
            out.writeInt(0);
        }
    }

    private void writeByteBuffer(DataOutputStream out, ByteBuffer buffer) throws IOException {
        byte [] data = new byte[buffer.limit()];
        buffer.get(data);
        buffer.flip();
        out.writeInt(data.length);
        if (data.length > 0) {
            out.write(data);
        } else {
            out.writeInt(0);
        }
    }

    private void writeFormatByteBuffer(DataOutputStream out, MediaFormat format, String key)
            throws IOException {
        if (format.containsKey(key)) {
            writeByteBuffer(out, format.getByteBuffer(key));
        } else {
            out.writeInt(0);
        }
    }

    @Override
    public void writeTrackInfoFiles(List<BufferManager.TrackFormat> formatList, boolean isAudio)
            throws IOException {
        for (int i = 0; i < formatList.size() ; ++i) {
            BufferManager.TrackFormat trackFormat = formatList.get(i);
            String fileName = (isAudio ? META_FILE_TYPE_AUDIO : META_FILE_TYPE_VIDEO)
                    + ((i == 0) ? META_FILE_SUFFIX : (i + META_FILE_SUFFIX));
            File file = new File(getBufferDir(), fileName);
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
                writeString(out, trackFormat.trackId);
                writeFormatString(out, trackFormat.format, MediaFormat.KEY_MIME);
                writeFormatInt(out, trackFormat.format, MediaFormat.KEY_MAX_INPUT_SIZE);
                writeFormatInt(out, trackFormat.format, MediaFormat.KEY_WIDTH);
                writeFormatInt(out, trackFormat.format, MediaFormat.KEY_HEIGHT);
                writeFormatInt(out, trackFormat.format, MediaFormat.KEY_CHANNEL_COUNT);
                writeFormatInt(out, trackFormat.format, MediaFormat.KEY_SAMPLE_RATE);
                writeFormatFloat(out, trackFormat.format, KEY_PIXEL_WIDTH_HEIGHT_RATIO);
                for (int j = 0; j < 3; ++j) {
                    writeFormatByteBuffer(out, trackFormat.format, "csd-" + j);
                }
                writeFormatLong(out, trackFormat.format, MediaFormat.KEY_DURATION);
                writeFormatString(out, trackFormat.format, MediaFormat.KEY_LANGUAGE);
            }
        }
    }

    /**
     * Writes caption information to files.
     *
     * @param tracks a list of {@link AtscCaptionTrack} objects which store caption information.
     */
    public void writeCaptionInfoFiles(List<AtscCaptionTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            AtscCaptionTrack track = tracks.get(i);
            String fileName = META_FILE_TYPE_CAPTION +
                    ((i == 0) ? META_FILE_SUFFIX : (i + META_FILE_SUFFIX));
            File file = new File(getBufferDir(), fileName);
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
                out.write(MessageNano.toByteArray(track));
            } catch (Exception e) {
                Log.e(TAG, "Fail to write caption info to files", e);
            }
        }
    }

    @Override
    public void writeIndexFile(String trackName, SortedMap<Long, Pair<SampleChunk, Integer>> index)
            throws IOException {
        File indexFile  = new File(getBufferDir(), trackName + IDX_FILE_SUFFIX_V2);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(indexFile))) {
            out.writeLong(index.size());
            for (Map.Entry<Long, Pair<SampleChunk, Integer>> entry : index.entrySet()) {
                out.writeLong(entry.getKey());
                out.writeLong(entry.getValue().first.getStartPositionUs());
                out.writeInt(entry.getValue().second);
            }
        }
    }
}
