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

package com.android.compatibility.common.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Uploads a result through a HTTP POST multipart/form-data request containing
 * the test result XML.
 */
public class ResultUploader {

    private static final int RESULT_XML_BYTES = 500 * 1024;

    /* package */ MultipartForm mMultipartForm;

    public ResultUploader(String serverUrl, String suiteName) {
        mMultipartForm = new MultipartForm(serverUrl).addFormValue("suite", suiteName);
    }

    /**
     * Uploads the given file to the server.
     *
     * @param reportFile The file to upload.
     * @param referenceUrl A reference url to use.
     * @throws IOException
     */
    public int uploadResult(File reportFile, String referenceUrl) throws IOException {
        InputStream input = new FileInputStream(reportFile);
        try {
            byte[] data = getBytes(input);
            mMultipartForm.addFormFile("resultXml", "test-result.xml.gz", data);
            if (referenceUrl != null && !referenceUrl.trim().isEmpty()) {
                mMultipartForm.addFormValue("referenceUrl", referenceUrl);
            }
            return mMultipartForm.submit();
        } finally {
            input.close();
        }
    }

    private static byte[] getBytes(InputStream input) throws IOException {
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream(RESULT_XML_BYTES);
        GZIPOutputStream gzipOutput = new GZIPOutputStream(byteOutput);
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) > 0) {
            gzipOutput.write(buffer, 0, count);
        }
        gzipOutput.close();
        return byteOutput.toByteArray();
    }

}
