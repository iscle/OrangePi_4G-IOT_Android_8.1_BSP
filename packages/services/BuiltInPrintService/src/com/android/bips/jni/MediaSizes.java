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

package com.android.bips.jni;

import android.annotation.SuppressLint;
import android.content.Context;
import android.print.PrintAttributes;

import com.android.bips.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Supported media sizes */
public class MediaSizes {
    private static final String ISO_A4 = "iso_a4_210x297mm";
    private static final String LETTER = "na_letter_8.5x11in";
    private static final String LEGAL = "na_legal_8.5x14in";
    private static final String PHOTO_4x6in = "na_index-4x6_4x6in";
    private static final String PHOTO_5x7 = "na_5x7_5x7in";
    private static final String NA_LEDGER_11X17 = "na_ledger_11x17in";
    private static final String ISO_A3 = "iso_a3_297x420mm";
    private static final String ISO_A5 = "iso_a5_148x210mm";
    private static final String JPN_HAGAKI = "jpn_hagaki_100x148mm";
    private static final String OM_DSC_PHOTO = "om_dsc-photo_89x119mm";
    private static final String OM_CARD = "om_card_54x86mm";
    private static final String JIS_B4 = "jis_b4_257x364mm";
    private static final String JIS_B5 = "jis_b5_182x257mm";
    private static final String OE_PHOTO_L = "oe_photo-l_3.5x5in";
    private static final String NA_GOVT_LETTER = "na_govt-letter_8x10in";

    /** The backend string name for the default media size */
    static final String DEFAULT_MEDIA_NAME = ISO_A4;

    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, String> sCodeToStringMap = new HashMap<>();

    /** The default string names for default media sizes */
    static final Collection<String> DEFAULT_MEDIA_NAMES = new ArrayList<>();
    private static MediaSizes sInstance;

    static {
        DEFAULT_MEDIA_NAMES.add(ISO_A4);
        DEFAULT_MEDIA_NAMES.add(LETTER);
        DEFAULT_MEDIA_NAMES.add(PHOTO_4x6in);
        DEFAULT_MEDIA_NAMES.add(PHOTO_5x7);
    }

    static {
        sCodeToStringMap.put(2, LETTER);
        sCodeToStringMap.put(3, LEGAL);
        sCodeToStringMap.put(7, NA_GOVT_LETTER);
        sCodeToStringMap.put(11, NA_LEDGER_11X17);
        sCodeToStringMap.put(25, ISO_A5);
        sCodeToStringMap.put(26, ISO_A4);
        sCodeToStringMap.put(27, ISO_A3);
        sCodeToStringMap.put(45, JIS_B5);
        sCodeToStringMap.put(46, JIS_B4);
        sCodeToStringMap.put(71, JPN_HAGAKI);
        sCodeToStringMap.put(74, PHOTO_4x6in);
        sCodeToStringMap.put(122, PHOTO_5x7);
        sCodeToStringMap.put(302, OM_DSC_PHOTO);
        sCodeToStringMap.put(303, OM_CARD);
        sCodeToStringMap.put(304, OE_PHOTO_L);
    }

    private final Map<String, PrintAttributes.MediaSize> mNameToSizeMap = new HashMap<>();

    private MediaSizes(Context context) {
        mNameToSizeMap.put(LETTER, PrintAttributes.MediaSize.NA_LETTER);
        mNameToSizeMap.put(LEGAL, PrintAttributes.MediaSize.NA_LEGAL);
        mNameToSizeMap.put(ISO_A3, PrintAttributes.MediaSize.ISO_A3);
        mNameToSizeMap.put(ISO_A4, PrintAttributes.MediaSize.ISO_A4);
        mNameToSizeMap.put(ISO_A5, PrintAttributes.MediaSize.ISO_A5);
        mNameToSizeMap.put(JPN_HAGAKI, PrintAttributes.MediaSize.JPN_HAGAKI);
        mNameToSizeMap.put(JIS_B4, PrintAttributes.MediaSize.JIS_B4);
        mNameToSizeMap.put(JIS_B5, PrintAttributes.MediaSize.JIS_B5);
        mNameToSizeMap.put(NA_LEDGER_11X17, PrintAttributes.MediaSize.NA_TABLOID);

        // Custom media sizes
        mNameToSizeMap.put(PHOTO_4x6in, new PrintAttributes.MediaSize(
                PHOTO_4x6in,
                context.getString(R.string.media_size_4x6in), 4000, 6000));
        mNameToSizeMap.put(NA_GOVT_LETTER, new PrintAttributes.MediaSize(
                NA_GOVT_LETTER,
                context.getString(R.string.media_size_8x10in), 8000, 10000));
        mNameToSizeMap.put(PHOTO_5x7, new PrintAttributes.MediaSize(
                PHOTO_5x7,
                context.getString(R.string.media_size_5x7in), 5000, 7000));
        mNameToSizeMap.put(OM_DSC_PHOTO, new PrintAttributes.MediaSize(
                OM_DSC_PHOTO,
                context.getString(R.string.media_size_89x119mm), 3504, 4685));
        mNameToSizeMap.put(OM_CARD, new PrintAttributes.MediaSize(
                OM_CARD,
                context.getString(R.string.media_size_54x86mm), 2126, 3386));
        mNameToSizeMap.put(OE_PHOTO_L, new PrintAttributes.MediaSize(
                OE_PHOTO_L,
                context.getString(R.string.media_size_l), 3500, 5000));
    }

    public static MediaSizes getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MediaSizes(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * Return the backend code for a backend string name.
     */
    private static int toMediaCode(String name) {
        for (Map.Entry<Integer, String> entry : sCodeToStringMap.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return 0;
    }

    /**
     * Return the backend string for a backend code name.
     */
    static String toMediaName(int code) {
        return sCodeToStringMap.get(code);
    }

    /**
     * Return the {@link PrintAttributes.MediaSize} object corresponding to the backend
     * string name.
     */
    PrintAttributes.MediaSize toMediaSize(String name) {
        return mNameToSizeMap.get(name);
    }

    /**
     * Return backend code for a {@link PrintAttributes.MediaSize}.
     */
    public int toMediaCode(PrintAttributes.MediaSize mediaSize) {
        for (Map.Entry<String, PrintAttributes.MediaSize> entry : mNameToSizeMap.entrySet()) {
            // Note: media size may be oriented and not match. So check by ID.
            if (entry.getValue().getId().equals(mediaSize.getId())) {
                return toMediaCode(entry.getKey());
            }
        }
        return 0;
    }
}