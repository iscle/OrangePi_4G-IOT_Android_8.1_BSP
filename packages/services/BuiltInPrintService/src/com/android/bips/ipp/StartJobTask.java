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

package com.android.bips.ipp;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.print.PrintJobInfo;
import android.printservice.PrintJob;
import android.util.Log;
import android.view.Gravity;

import com.android.bips.jni.BackendConstants;
import com.android.bips.jni.LocalJobParams;
import com.android.bips.jni.LocalPrinterCapabilities;
import com.android.bips.jni.MediaSizes;
import com.android.bips.util.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A background task that starts sending a print job. The result of this task is an integer
 * defined by {@link Backend} ERROR_* codes or a non-negative code for success.
 */
class StartJobTask extends AsyncTask<Void, Void, Integer> {
    private static final String TAG = StartJobTask.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String MIME_TYPE_PDF = "application/pdf";

    private static final int MEDIA_TYPE_PLAIN = 0;
    // Unused but present
    //    private static final int MEDIA_TYPE_PHOTO = 1;
    //    private static final int MEDIA_TYPE_PHOTO_GLOSSY = 2;

    private static final int SIDES_SIMPLEX = 0;
    private static final int SIDES_DUPLEX_LONG_EDGE = 1;
    private static final int SIDES_DUPLEX_SHORT_EDGE = 2;

    private static final int RESOLUTION_300_DPI = 300;

    private static final int COLOR_SPACE_MONOCHROME = 0;
    private static final int COLOR_SPACE_COLOR = 1;

    private static final int BORDERLESS_OFF = 0;
    private static final int BORDERLESS_ON = 1;

    private final Context mContext;
    private final Backend mBackend;
    private final Uri mDestination;
    private final LocalPrinterCapabilities mCapabilities;
    private final LocalJobParams mJobParams;
    private final ParcelFileDescriptor mSourceFileDescriptor;
    private final String mJobId;
    private final PrintJobInfo mJobInfo;
    private final PrintDocumentInfo mDocInfo;
    private final MediaSizes mMediaSizes;

    public StartJobTask(Context context, Backend backend, Uri destination, PrintJob printJob,
            LocalPrinterCapabilities capabilities) {
        mContext = context;
        mBackend = backend;
        mDestination = destination;
        mCapabilities = capabilities;
        mJobParams = new LocalJobParams();
        mJobId = printJob.getId().toString();
        mJobInfo = printJob.getInfo();
        mDocInfo = printJob.getDocument().getInfo();
        mSourceFileDescriptor = printJob.getDocument().getData();
        mMediaSizes = MediaSizes.getInstance(mContext);
    }

    private void populateJobParams() {
        PrintAttributes.MediaSize mediaSize = mJobInfo.getAttributes().getMediaSize();

        mJobParams.borderless = isBorderless() ? BORDERLESS_ON : BORDERLESS_OFF;
        mJobParams.duplex = getSides();
        mJobParams.num_copies = mJobInfo.getCopies();
        mJobParams.pdf_render_resolution = RESOLUTION_300_DPI;
        mJobParams.fit_to_page = !getFillPage();
        mJobParams.fill_page = getFillPage();
        mJobParams.job_name = mJobInfo.getLabel();
        mJobParams.job_originating_user_name = Build.MODEL;
        mJobParams.auto_rotate = false;
        mJobParams.portrait_mode = mediaSize == null || mediaSize.isPortrait();
        mJobParams.landscape_mode = !mJobParams.portrait_mode;
        mJobParams.media_size = mMediaSizes.toMediaCode(mediaSize);
        mJobParams.media_type = getMediaType();
        mJobParams.color_space = getColorSpace();
        mJobParams.document_category = getDocumentCategory();

        mJobParams.job_margin_top = Math.max(mJobParams.job_margin_top, 0.0f);
        mJobParams.job_margin_left = Math.max(mJobParams.job_margin_left, 0.0f);
        mJobParams.job_margin_right = Math.max(mJobParams.job_margin_right, 0.0f);
        mJobParams.job_margin_bottom = Math.max(mJobParams.job_margin_bottom, 0.0f);

        mJobParams.alignment = Gravity.CENTER;
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        if (DEBUG) Log.d(TAG, "doInBackground() job=" + mJobParams + ", cap=" + mCapabilities);
        File tempFolder = new File(mContext.getFilesDir(), Backend.TEMP_JOB_FOLDER);
        if (!FileUtils.makeDirectory(tempFolder)) {
            Log.w(TAG, "makeDirectory failure");
            return Backend.ERROR_FILE;
        }

        File pdfFile = new File(tempFolder, mJobId + ".pdf");
        try {
            try {
                FileUtils.copy(new ParcelFileDescriptor.AutoCloseInputStream(mSourceFileDescriptor),
                        new BufferedOutputStream(new FileOutputStream(pdfFile)));
            } catch (IOException e) {
                Log.w(TAG, "Error while copying to " + pdfFile, e);
                return Backend.ERROR_FILE;
            }
            String files[] = new String[]{pdfFile.toString()};

            // Address, without port.
            String address = mDestination.getHost() + mDestination.getPath();

            if (isCancelled()) return Backend.ERROR_CANCEL;

            // Get default job parameters
            int result = mBackend.nativeGetDefaultJobParameters(mJobParams);
            if (result != 0) {
                if (DEBUG) Log.w(TAG, "nativeGetDefaultJobParameters failure: " + result);
                return Backend.ERROR_UNKNOWN;
            }

            if (isCancelled()) return Backend.ERROR_CANCEL;

            // Fill in job parameters from capabilities and print job info.
            populateJobParams();

            // Finalize job parameters
            mBackend.nativeGetFinalJobParameters(mJobParams, mCapabilities);

            if (isCancelled()) return Backend.ERROR_CANCEL;
            if (DEBUG) {
                Log.d(TAG, "nativeStartJob address=" + address +
                        " port=" + mDestination.getPort() + " mime=" + MIME_TYPE_PDF +
                        " files=" + files[0] + " job=" + mJobParams);
            }
            // Initiate job
            result = mBackend.nativeStartJob(Backend.getIp(address), mDestination.getPort(),
                    MIME_TYPE_PDF, mJobParams, mCapabilities, files, null, mDestination.getScheme());
            if (result < 0) {
                Log.w(TAG, "nativeStartJob failure: " + result);
                return Backend.ERROR_UNKNOWN;
            }

            pdfFile = null;
            return result;
        } finally {
            if (pdfFile != null) {
                pdfFile.delete();
            }
        }
    }

    private boolean isBorderless() {
        return mCapabilities.borderless &&
                mDocInfo.getContentType() == PrintDocumentInfo.CONTENT_TYPE_PHOTO;
    }

    private int getSides() {
        // Never duplex photo media; may damage printers
        if (mDocInfo.getContentType() == PrintDocumentInfo.CONTENT_TYPE_PHOTO) {
            return SIDES_SIMPLEX;
        }

        switch (mJobInfo.getAttributes().getDuplexMode()) {
            case PrintAttributes.DUPLEX_MODE_LONG_EDGE:
                return SIDES_DUPLEX_LONG_EDGE;
            case PrintAttributes.DUPLEX_MODE_SHORT_EDGE:
                return SIDES_DUPLEX_SHORT_EDGE;
            case PrintAttributes.DUPLEX_MODE_NONE:
            default:
                return SIDES_SIMPLEX;
        }
    }

    private boolean getFillPage() {
        switch (mDocInfo.getContentType()) {
            case PrintDocumentInfo.CONTENT_TYPE_PHOTO:
                return true;
            case PrintDocumentInfo.CONTENT_TYPE_UNKNOWN:
            case PrintDocumentInfo.CONTENT_TYPE_DOCUMENT:
            default:
                return false;
        }
    }

    private int getMediaType() {
        int mediaType = MEDIA_TYPE_PLAIN;

        if (mDocInfo.getContentType() == PrintDocumentInfo.CONTENT_TYPE_PHOTO) {
            // Select the best (highest #) supported type for photos
            for (int supportedType : mCapabilities.supportedMediaTypes) {
                if (supportedType > mediaType) {
                    mediaType = supportedType;
                }
            }
        }
        return mediaType;
    }

    private int getColorSpace() {
        switch (mJobInfo.getAttributes().getColorMode()) {
            case PrintAttributes.COLOR_MODE_COLOR:
                return COLOR_SPACE_COLOR;
            case PrintAttributes.COLOR_MODE_MONOCHROME:
            default:
                return COLOR_SPACE_MONOCHROME;
        }
    }

    private String getDocumentCategory() {
        switch (mDocInfo.getContentType()) {
            case PrintDocumentInfo.CONTENT_TYPE_PHOTO:
                return BackendConstants.PRINT_DOCUMENT_CATEGORY__PHOTO;

            case PrintDocumentInfo.CONTENT_TYPE_DOCUMENT:
            default:
                return BackendConstants.PRINT_DOCUMENT_CATEGORY__DOCUMENT;
        }
    }
}