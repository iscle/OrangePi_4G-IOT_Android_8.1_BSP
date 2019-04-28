/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

package com.android.bips.jni;

import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.text.TextUtils;

import com.android.bips.BuiltInPrintService;
import com.android.bips.R;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class LocalPrinterCapabilities {
    public String path;
    public String name;
    public String uuid;
    public String location;

    public boolean duplex;
    public boolean borderless;
    public boolean color;

    /** Reported MIME types include at least one that the lower layer supports */
    public boolean isSupported;

    public String mediaDefault;
    public int[] supportedMediaTypes;
    public int[] supportedMediaSizes;

    public InetAddress inetAddress;

    /** Bears the underlying native C structure (printer_capabilities_t) or null if not present */
    public byte[] nativeData;

    public void buildCapabilities(BuiltInPrintService service,
            PrinterCapabilitiesInfo.Builder builder) {
        builder.setColorModes(
                PrintAttributes.COLOR_MODE_MONOCHROME |
                        (color ? PrintAttributes.COLOR_MODE_COLOR : 0),
                (color ? PrintAttributes.COLOR_MODE_COLOR : PrintAttributes.COLOR_MODE_MONOCHROME));

        MediaSizes mediaSizes = MediaSizes.getInstance(service);

        String defaultMediaName = mediaDefault;
        if (TextUtils.isEmpty(defaultMediaName) ||
                null == mediaSizes.toMediaSize(defaultMediaName)) {
            defaultMediaName = MediaSizes.DEFAULT_MEDIA_NAME;
        }

        List<String> mediaNames = new ArrayList<>();
        for (int supportedMediaSize : supportedMediaSizes) {
            String mediaName = MediaSizes.toMediaName(supportedMediaSize);
            if (mediaName != null) {
                mediaNames.add(mediaName);
            }
        }

        if (mediaNames.isEmpty()) {
            mediaNames.addAll(MediaSizes.DEFAULT_MEDIA_NAMES);
        }

        if (!mediaNames.contains(defaultMediaName)) {
            defaultMediaName = mediaNames.get(0);
        }

        // Add media sizes without duplicates
        for (String mediaName : new HashSet<>(mediaNames)) {
            builder.addMediaSize(mediaSizes.toMediaSize(mediaName),
                    Objects.equals(mediaName, defaultMediaName));
        }

        builder.addResolution(new PrintAttributes.Resolution(
                BackendConstants.RESOLUTION_300_DPI,
                service.getString(R.string.resolution_300_dpi), 300, 300), true);

        if (duplex) {
            builder.setDuplexModes(
                    PrintAttributes.DUPLEX_MODE_NONE | PrintAttributes.DUPLEX_MODE_LONG_EDGE |
                            PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
                    PrintAttributes.DUPLEX_MODE_NONE);
        }

        if (borderless) {
            builder.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0));
        }
    }

    @Override
    public String toString() {
        return "LocalPrinterCapabilities{" +
                "path=" + path +
                " name=" + name +
                " uuid=" + uuid +
                " location=" + location +
                " duplex=" + duplex +
                " borderless=" + borderless +
                " color=" + color +
                " isSupported=" + isSupported +
                " mediaDefault=" + mediaDefault +
                " supportedMediaTypes=" + Arrays.toString(supportedMediaTypes) +
                " supportedMediaSizes=" + Arrays.toString(supportedMediaSizes) +
                " inetAddress=" + inetAddress +
                "}";
    }
}