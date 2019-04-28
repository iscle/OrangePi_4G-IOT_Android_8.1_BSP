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

package com.android.documentsui.clipping;

import android.net.Uri;

import java.io.File;
import java.io.IOException;

/**
 * Interface for clip data URI storage.
 */
public interface ClipStore {

    /**
     * Gets a {@link File} instance given a tag.
     *
     * This method creates a symbolic link in the slot folder to the data file as a reference
     * counting method. When someone is done using this symlink, it's responsible to delete it.
     * Therefore we can have a neat way to track how many things are still using this slot.
     */
    File getFile(int tag) throws IOException;

    /**
     * Returns a Reader. Callers must close the reader when finished.
     */
    ClipStorageReader createReader(File file) throws IOException;

    /**
     * Writes the uris to the next available slot, returning the tag for that slot.
     *
     * @return int the tag used to store the URIs.
     */
    int persistUris(Iterable<Uri> uris);
}
