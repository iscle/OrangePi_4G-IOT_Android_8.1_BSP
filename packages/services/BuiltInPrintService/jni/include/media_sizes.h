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
#ifndef __MEDIA_SIZES_H__
#define __MEDIA_SIZES_H__

#include <stdint.h>

/*
 * Enumeration of the different media sizes known by the printing system. The numeration of the
 * media ID corresponds to the PCL numeration of the media ID. There is also a numeration for
 * custom size (101). This enum contains all the values that are currently defined for media types.
 * A product may choose to support any *subset* of these defined media types.
 */
typedef enum {
    US_EXECUTIVE = 1,
    US_LETTER = 2,
    US_LEGAL = 3,
    US_EDP = 4,
    EUROPEAN_EDP = 5,
    B_TABLOID = 6,
    US_GOVERNMENT_LETTER = 7,
    US_GOVERNMENT_LEGAL = 8,
    FOLIO = 9,
    FOOLSCAP = 10,
    LEDGER = 11,
    C_SIZE = 12,
    D_SIZE = 13,
    E_SIZE = 14,
    MINI = 15,
    SUPER_B = 16,
    ROC16K = 17,
    ROC8K = 19,
    ISO_AND_JIS_A10 = 20,
    ISO_AND_JIS_A9 = 21,
    ISO_AND_JIS_A8 = 22,
    ISO_AND_JIS_A7 = 23,
    ISO_AND_JIS_A6 = 24,
    ISO_AND_JIS_A5 = 25,
    ISO_A5 = 25,
    ISO_AND_JIS_A4 = 26,
    ISO_A4 = 26,
    ISO_AND_JIS_A3 = 27,
    ISO_A3 = 27,
    ISO_AND_JIS_A2 = 28,
    ISO_AND_JIS_A1 = 29,
    ISO_AND_JIS_A0 = 30,
    ISO_AND_JIS_2A0 = 31,
    ISO_AND_JIS_4A0 = 32,
    K8_270X390MM = 33,
    K16_195X270MM = 34,
    K8_260X368MM = 35,
    RA4 = 36,
    SRA4 = 37,
    SRA3 = 38,
    RA3 = 39,
    JIS_B10 = 40,
    JIS_B9 = 41,
    JIS_B8 = 42,
    JIS_B7 = 43,
    JIS_B6 = 44,
    JIS_B5 = 45,
    JIS_B4 = 46,
    JIS_B3 = 47,
    JIS_B2 = 48,
    JIS_B1 = 49,
    JIS_B0 = 50,
    ISO_B10 = 60,
    ISO_B9 = 61,
    ISO_B8 = 62,
    ISO_B7 = 63,
    ISO_B6 = 64,
    ISO_B5 = 65,
    ISO_B4 = 66,
    ISO_B3 = 67,
    ISO_B2 = 68,
    ISO_B1 = 69,
    ISO_B0 = 70,
    JAPANESE_POSTCARD_SINGLE = 71,
    JPN_HAGAKI_PC = 71,
    JAPANESE_POSTCARD_DOUBLE = 72,
    JPN_OUFUKU_PC = 72,
    ISO_A6_POSTCARD = 73,
    ISO_A6_CARD = 73,
    INDEX_CARD_4X6 = 74,
    US_SMALL_IDX = 74,
    INDEX_CARD_5X8 = 75,
    US_LARGE_IDX = 75,
    PHOTO_4X6 = 76,
    JAPANESE_POSTCARD_WITH_TAB = 77,
    INDEX_CARD_3X5 = 78,
    MONARCH = 80,
    COMMERCIAL_10 = 81,
    NO_10_ENVELOPE = 81,
    CATALOG_1 = 82,
    ENVELOPE_NO_6_75 = 83,
    K16_184X260MM = 89,
    INTERNATIONAL_DL = 90,
    INT_DL_ENVELOPE = 90,
    INTERNATIONAL_C5 = 91,
    INT_C6_ENVELOPE = 92,
    INTERNATIONAL_C6 = 92,
    INTERNATIONAL_C4 = 93,
    PRINTABLE_CD_3_5_INCH = 98,
    PRINTABLE_CD_5_INCH = 99,
    INTERNATIONAL_B5 = 100,
    CUSTOM = 101,
    COMMERCIAL_9 = 102,
    CUSTOM_CARD = 108,
    US_ENVELOPE_A2 = 109,
    A2_ENVELOPE = 109,
    JAPANESE_ENV_LONG_3 = 110,
    NEC_L3_ENVELOPE = 110,
    JAPANESE_ENV_LONG_4 = 111,
    NEC_L4_ENVELOPE = 111,
    JAPANESE_ENV_2 = 112,
    HP_GREETING_CARD_ENVELOPE = 114,
    US_PHOTO_9X12 = 116,
    US_PHOTO_ALBUM_12X12 = 117,
    PHOTO_10X15 = 118,
    PHOTO_CABINET = 119,
    SUPER_B_PAPER = 120,
    PHOTO_L_SIZE_CARD = 121,
    LSIZE_CARD = 121,
    INDEX_CARD_5X7 = 122,
    PHOTO_E_SIZE_CARD = 123,
    PHOTO_KG_SIZE_CARD = 124,
    PHOTO_2E_SIZE_CARD = 125,
    PHOTO_2L_SIZE_CARD = 126,

    /* Rotated Media (add 256 to the unrotated value) */
    US_EXECUTIVE_ROTATED = 257,
    US_LETTER_ROTATED = 258,
    ISO_AND_JIS_A5_ROTATED = 281,
    ISO_AND_JIS_A4_ROTATED = 282,
    JIS_B5_ROTATED = 301,
    PHOTO_89X119 = 302,
    CARD_54X86 = 303,
    OE_PHOTO_L = 304,

    /*
     * Need a media size for products that want to reject any media that doesn't have an exact
     * match. UNKNOWN_MEDIA_SIZE can't be used because it is used on other (most) products that
     * don't want this behavior.
     */
    UNDEFINED_MEDIA_SIZE = 29999,

    /* Special photo sizes */
    PHOTO_4X12 = 30000,
    PHOTO_4X8 = 30001,
    PHOTO_5X7_MAIN_TRAY = 30002,

    /* Other Media */
    CUSTOM_ROLL = 32766,
    UNKNOWN_MEDIA_SIZE = 32767,
} media_size_t;

#endif // __MEDIA_SIZES_H__