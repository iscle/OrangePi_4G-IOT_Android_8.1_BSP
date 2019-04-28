/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.render;

import com.android.bips.jni.SizeD;

/**
 * Defines a simple PDF rendering service to protect the main process from
 * crashes or security issues caused by unexpected PDF data.
 */
interface IPdfRender {

    /**
     * Open a new document, returning the page count or 0 on error
     */
    int openDocument(in ParcelFileDescriptor file);

    /**
     * Return open document's page size in fractional points (1/72") or null on error.
     */
    SizeD getPageSize(int page);

    /**
     * Render a page from the open document as a bitmap.
     *
     * @param destFile File to receive a PNG compressed bitmap corresponding to the specified
     *                 portion of the page
     * @param y y-offset from the page in pixels at the specified zoom factor
     * @param width full-page width of bitmap to render
     * @param height height of strip to render
     * @return output receiver for bitmap output
     */
    ParcelFileDescriptor renderPageStripe(int page, int y, int width, int height,
        double zoomFactor);

    /**
     * Release all internal resources related to the open document
     */
    oneway void closeDocument();
}