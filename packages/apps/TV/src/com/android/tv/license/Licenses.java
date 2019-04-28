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
 * limitations under the License
 */

package com.android.tv.license;

import android.content.Context;
import android.support.annotation.RawRes;

import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A helper for extracting licenses embedded using
 * third_party_licenses.build:third_party_licenses().
 */
public final class Licenses {

    public static final String TAG = "Licenses";
    public static boolean hasLicenses(Context context) {
        return !getTextFromResource(
                        context.getApplicationContext(), R.raw.third_party_license_metadata, 0, -1)
                .isEmpty();
    }

    /** Return the licenses bundled into this app. */
    public static ArrayList<License> getLicenses(Context context) {
        return getLicenseListFromMetadata(
                getTextFromResource(
                        context.getApplicationContext(), R.raw.third_party_license_metadata, 0, -1),
                "");
    }

    /**
     * Returns a list of {@link License}s parsed from a license metadata file.
     *
     * @param metadata a {@code String} containing the contents of a license metadata file.
     * @param filePath a path to a package archive with licenses or empty string for the app package
     */
    private static ArrayList<License> getLicenseListFromMetadata(String metadata, String filePath) {
        String[] entries = metadata.split("\n");
        ArrayList<License> licenses = new ArrayList<License>(entries.length);
        for (String entry : entries) {
            int delimiter = entry.indexOf(' ');
            String[] licenseLocation = entry.substring(0, delimiter).split(":");
            SoftPreconditions.checkState(
                    licenseLocation.length == 2 && delimiter > 0,
                    TAG,
                    "Invalid license meta-data line:\n" + entry);
            long licenseOffset = Long.parseLong(licenseLocation[0]);
            int licenseLength = Integer.parseInt(licenseLocation[1]);
            licenses.add(
                    License.create(
                            entry.substring(delimiter + 1),
                            licenseOffset,
                            licenseLength,
                            filePath));
        }
        Collections.sort(licenses);
        return licenses;
    }

    /** Return the text of a bundled license file. */
    public static String getLicenseText(Context context, License license) {
        long offset = license.getLicenseOffset();
        int length = license.getLicenseLength();
        return getTextFromResource(context, R.raw.third_party_licenses, offset, length);
    }

    private static String getTextFromResource(
            Context context, @RawRes int resourcesIdentifier, long offset, int length) {
        InputStream stream =
                context.getApplicationContext().getResources().openRawResource(resourcesIdentifier);
        return getTextFromInputStream(stream, offset, length);
    }

    private static String getTextFromInputStream(InputStream stream, long offset, int length) {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream textArray = new ByteArrayOutputStream();

        try {
            stream.skip(offset);
            int bytesRemaining = length > 0 ? length : Integer.MAX_VALUE;
            int bytes = 0;

            while (bytesRemaining > 0
                    && (bytes = stream.read(buffer, 0, Math.min(bytesRemaining, buffer.length)))
                            != -1) {
                textArray.write(buffer, 0, bytes);
                bytesRemaining -= bytes;
            }
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read license or metadata text.", e);
        }
        try {
            return textArray.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Unsupported encoding UTF8. This should always be supported.", e);
        }
    }
}
