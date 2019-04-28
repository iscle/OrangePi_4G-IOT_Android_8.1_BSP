/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.data;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.data.Program.CriticScore;
import com.android.tv.dvr.data.RecordedProgram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

/**
 * A utility class to parse and store data from the
 * {@link android.media.tv.TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA} field in the
 * {@link android.media.tv.TvContract.Programs}.
 */
public final class InternalDataUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = "InternalDataUtils";

    private InternalDataUtils() {
        //do nothing
    }

    /**
     * Deserializes a byte array into objects to be stored in the Program class.
     *
     * <p> Series ID and critic scores are loaded from the bytes.
     *
     * @param bytes the bytes to be deserialized
     * @param builder the builder for the Program class
     */
    public static void deserializeInternalProviderData(byte[] bytes, Program.Builder builder) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            builder.setSeriesId((String) in.readObject());
            builder.setCriticScores((List<CriticScore>) in.readObject());
        } catch (NullPointerException e) {
            Log.e(TAG, "no bytes to deserialize");
        } catch (IOException e) {
            Log.e(TAG, "Could not deserialize internal provider contents");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "class not found in internal provider contents");
        }
    }

    /**
     * Convenience method for converting relevant data in Program class to a serialized blob type
     * for storage in internal_provider_data field.
     * @param program the program which contains the objects to be serialized
     * @return serialized blob-type data
     */
    @Nullable
    public static byte[] serializeInternalProviderData(Program program) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            if (!TextUtils.isEmpty(program.getSeriesId()) || program.getCriticScores() != null) {
                out.writeObject(program.getSeriesId());
                out.writeObject(program.getCriticScores());
                return bos.toByteArray();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not serialize internal provider contents for program: "
                    + program.getTitle());
        }
        return null;
    }

    /**
     * Deserializes a byte array into objects to be stored in the RecordedProgram class.
     *
     * <p> Series ID is loaded from the bytes.
     *
     * @param bytes the bytes to be deserialized
     * @param builder the builder for the RecordedProgram class
     */
    public static void deserializeInternalProviderData(byte[] bytes,
            RecordedProgram.Builder builder) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            builder.setSeriesId((String) in.readObject());
        } catch (NullPointerException e) {
            Log.e(TAG, "no bytes to deserialize");
        } catch (IOException e) {
            Log.e(TAG, "Could not deserialize internal provider contents");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "class not found in internal provider contents");
        }
    }

    /**
     * Serializes relevant objects in {@link android.media.tv.TvContract.Programs} to byte array.
     * @return the serialized byte array
     */
    public static byte[] serializeInternalProviderData(RecordedProgram program) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            if (!TextUtils.isEmpty(program.getSeriesId())) {
                out.writeObject(program.getSeriesId());
                return bos.toByteArray();
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not serialize internal provider contents for program: "
                    + program.getTitle());
        }
        return null;
    }
}