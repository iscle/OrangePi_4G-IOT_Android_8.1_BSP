/*
 * Copyright (C) 2011 The Android Open Source Project
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Builds a multipart form and submits it. */
class MultipartForm {

    private static final String FORM_DATA_BOUNDARY = "C75I55u3R3p0r73r";

    /* package */ final String mServerUrl;
    /* package */ final Map<String, String> mFormValues = new HashMap<String, String>();
    /* package */ String mName;
    /* package */ String mFileName;
    /* package */ byte[] mData;

    /**
     * Creates a new multi-part form with the given serverUrl.
     */
    public MultipartForm(String serverUrl) {
        mServerUrl = serverUrl;
    }

    /**
     * Adds a key value attribute to the form.
     *
     * @param name the name of the attribute.
     * @param value the attribute's value.
     * @return the {@link MultipartForm} for easy chaining.
     */
    public MultipartForm addFormValue(String name, String value) {
        mFormValues.put(name, value);
        return this;
    }

    /**
     * Adds the file as the payload of the form.
     *
     * @param name The name of attribute
     * @param fileName The file's name
     * @param data The file's data
     * @return the {@link MultipartForm} for easy chaining.
     */
    public MultipartForm addFormFile(String name, String fileName, byte[] data) {
        mName = name;
        mFileName = fileName;
        mData = data;
        return this;
    }

    /**
     * Submits the form to the server url.
     *
     * This will handle a redirection from the server.
     *
     * @return response code
     * @throws IOException
     */
    public int submit() throws IOException {
        return submitForm(mServerUrl);
    }

    /**
     * @param serverUrl to post the data to
     * @return response code
     * @throws IOException
     */
    private int submitForm(String serverUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(serverUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + FORM_DATA_BOUNDARY);

            byte[] body = getContentBody();
            connection.setRequestProperty("Content-Length", Integer.toString(body.length));

            OutputStream output = connection.getOutputStream();
            try {
                output.write(body);
            } finally {
                output.close();
            }

            // Open the stream to get a response. Otherwise request will be cancelled.
            InputStream input = connection.getInputStream();
            input.close();

            int response = connection.getResponseCode();
            if (response == 302) {
                return submitForm(connection.getHeaderField("Location"));
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /* package */ byte[] getContentBody() throws IOException {
        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(byteOutput));
        writer.println();

        for (Map.Entry<String, String> formValue : mFormValues.entrySet()) {
            writeFormField(writer, formValue.getKey(), formValue.getValue());
        }

        if (mData != null) {
            writeFormFileHeader(writer, mName, mFileName);
            writer.flush(); // Must flush here before writing to the byte stream!
            byteOutput.write(mData);
            writer.println();
        }
        writer.append("--").append(FORM_DATA_BOUNDARY).println("--");
        writer.flush();
        writer.close();
        return byteOutput.toByteArray();
    }

    private void writeFormField(PrintWriter writer, String name, String value) {
        writer.append("--").println(FORM_DATA_BOUNDARY);
        writer.append("Content-Disposition: form-data; name=\"").append(name).println("\"");
        writer.println();
        writer.println(value);
    }

    private void writeFormFileHeader(PrintWriter writer, String name, String fileName) {
        writer.append("--").println(FORM_DATA_BOUNDARY);
        writer.append("Content-Disposition: form-data; name=\"").append(name);
        writer.append("\"; filename=\"").append(fileName).println("\"");
        writer.println("Content-Type: application/x-gzip");
        writer.println("Content-Transfer-Encoding: binary");
        writer.println();
    }
}
