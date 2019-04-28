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

package com.android.tv.tuner.ts;

import android.media.tv.TvContentRating;
import android.media.tv.TvContract.Programs.Genres;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.tv.tuner.data.PsiData.PatItem;
import com.android.tv.tuner.data.PsiData.PmtItem;
import com.android.tv.tuner.data.PsipData.Ac3AudioDescriptor;
import com.android.tv.tuner.data.PsipData.CaptionServiceDescriptor;
import com.android.tv.tuner.data.PsipData.ContentAdvisoryDescriptor;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.PsipData.EttItem;
import com.android.tv.tuner.data.PsipData.ExtendedChannelNameDescriptor;
import com.android.tv.tuner.data.PsipData.GenreDescriptor;
import com.android.tv.tuner.data.PsipData.Iso639LanguageDescriptor;
import com.android.tv.tuner.data.PsipData.MgtItem;
import com.android.tv.tuner.data.PsipData.ParentalRatingDescriptor;
import com.android.tv.tuner.data.PsipData.PsipSection;
import com.android.tv.tuner.data.PsipData.RatingRegion;
import com.android.tv.tuner.data.PsipData.RegionalRating;
import com.android.tv.tuner.data.PsipData.SdtItem;
import com.android.tv.tuner.data.PsipData.ServiceDescriptor;
import com.android.tv.tuner.data.PsipData.ShortEventDescriptor;
import com.android.tv.tuner.data.PsipData.TsDescriptor;
import com.android.tv.tuner.data.PsipData.VctItem;
import com.android.tv.tuner.data.nano.Channel;
import com.android.tv.tuner.data.nano.Track.AtscAudioTrack;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.util.ByteArrayBuffer;

import com.android.tv.tuner.util.ConvertUtils;
import com.ibm.icu.text.UnicodeDecompressor;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses ATSC PSIP sections.
 */
public class SectionParser {
    private static final String TAG = "SectionParser";
    private static final boolean DEBUG = false;

    private static final byte TABLE_ID_PAT = (byte) 0x00;
    private static final byte TABLE_ID_PMT = (byte) 0x02;
    private static final byte TABLE_ID_MGT = (byte) 0xc7;
    private static final byte TABLE_ID_TVCT = (byte) 0xc8;
    private static final byte TABLE_ID_CVCT = (byte) 0xc9;
    private static final byte TABLE_ID_EIT = (byte) 0xcb;
    private static final byte TABLE_ID_ETT = (byte) 0xcc;

    // Table id for DVB
    private static final byte TABLE_ID_SDT = (byte) 0x42;
    private static final byte TABLE_ID_DVB_ACTUAL_P_F_EIT = (byte) 0x4e;
    private static final byte TABLE_ID_DVB_OTHER_P_F_EIT = (byte) 0x4f;
    private static final byte TABLE_ID_DVB_ACTUAL_SCHEDULE_EIT = (byte) 0x50;
    private static final byte TABLE_ID_DVB_OTHER_SCHEDULE_EIT = (byte) 0x60;

    // For details of the structure for the tags of descriptors, see ATSC A/65 Table 6.25.
    public static final int DESCRIPTOR_TAG_ISO639LANGUAGE = 0x0a;
    public static final int DESCRIPTOR_TAG_CAPTION_SERVICE = 0x86;
    public static final int DESCRIPTOR_TAG_CONTENT_ADVISORY = 0x87;
    public static final int DESCRIPTOR_TAG_AC3_AUDIO_STREAM = 0x81;
    public static final int DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME = 0xa0;
    public static final int DESCRIPTOR_TAG_GENRE = 0xab;

    // For details of the structure for the tags of DVB descriptors, see DVB Document A038 Table 12.
    public static final int DVB_DESCRIPTOR_TAG_SERVICE = 0x48;
    public static final int DVB_DESCRIPTOR_TAG_SHORT_EVENT = 0X4d;
    public static final int DVB_DESCRIPTOR_TAG_CONTENT = 0x54;
    public static final int DVB_DESCRIPTOR_TAG_PARENTAL_RATING = 0x55;

    private static final byte COMPRESSION_TYPE_NO_COMPRESSION = (byte) 0x00;
    private static final byte MODE_SELECTED_UNICODE_RANGE_1 = (byte) 0x00;  // 0x0000 - 0x00ff
    private static final byte MODE_UTF16 = (byte) 0x3f;
    private static final byte MODE_SCSU = (byte) 0x3e;
    private static final int MAX_SHORT_NAME_BYTES = 14;

    // See ANSI/CEA-766-C.
    private static final int RATING_REGION_US_TV = 1;
    private static final int RATING_REGION_KR_TV = 4;

    // The following values are defined in the live channels app.
    // See https://developer.android.com/reference/android/media/tv/TvContentRating.html.
    private static final String RATING_DOMAIN = "com.android.tv";
    private static final String RATING_REGION_RATING_SYSTEM_US_TV = "US_TV";
    private static final String RATING_REGION_RATING_SYSTEM_US_MV = "US_MV";
    private static final String RATING_REGION_RATING_SYSTEM_KR_TV = "KR_TV";

    private static final String[] RATING_REGION_TABLE_US_TV = {
        "US_TV_Y", "US_TV_Y7", "US_TV_G", "US_TV_PG", "US_TV_14", "US_TV_MA"
    };

    private static final String[] RATING_REGION_TABLE_US_MV = {
        "US_MV_G", "US_MV_PG", "US_MV_PG13", "US_MV_R", "US_MV_NC17"
    };

    private static final String[] RATING_REGION_TABLE_KR_TV = {
        "KR_TV_ALL", "KR_TV_7", "KR_TV_12", "KR_TV_15", "KR_TV_19"
    };

    private static final String[] RATING_REGION_TABLE_US_TV_SUBRATING = {
        "US_TV_D", "US_TV_L", "US_TV_S", "US_TV_V", "US_TV_FV"
    };

    // According to ANSI-CEA-766-D
    private static final int VALUE_US_TV_Y = 1;
    private static final int VALUE_US_TV_Y7 = 2;
    private static final int VALUE_US_TV_NONE = 1;
    private static final int VALUE_US_TV_G = 2;
    private static final int VALUE_US_TV_PG = 3;
    private static final int VALUE_US_TV_14 = 4;
    private static final int VALUE_US_TV_MA = 5;

    private static final int DIMENSION_US_TV_RATING = 0;
    private static final int DIMENSION_US_TV_D = 1;
    private static final int DIMENSION_US_TV_L = 2;
    private static final int DIMENSION_US_TV_S = 3;
    private static final int DIMENSION_US_TV_V = 4;
    private static final int DIMENSION_US_TV_Y = 5;
    private static final int DIMENSION_US_TV_FV = 6;
    private static final int DIMENSION_US_MV_RATING = 7;

    private static final int VALUE_US_MV_G = 2;
    private static final int VALUE_US_MV_PG = 3;
    private static final int VALUE_US_MV_PG13 = 4;
    private static final int VALUE_US_MV_R = 5;
    private static final int VALUE_US_MV_NC17 = 6;
    private static final int VALUE_US_MV_X = 7;

    private static final String STRING_US_TV_Y = "US_TV_Y";
    private static final String STRING_US_TV_Y7 = "US_TV_Y7";
    private static final String STRING_US_TV_FV = "US_TV_FV";


    /*
     * The following CRC table is from the code generated by the following command.
     * $ python pycrc.py --model crc-32-mpeg --algorithm table-driven --generate c
     * To see the details of pycrc, visit http://www.tty1.net/pycrc/index_en.html
     */
    public static final int[] CRC_TABLE = {
        0x00000000, 0x04c11db7, 0x09823b6e, 0x0d4326d9,
        0x130476dc, 0x17c56b6b, 0x1a864db2, 0x1e475005,
        0x2608edb8, 0x22c9f00f, 0x2f8ad6d6, 0x2b4bcb61,
        0x350c9b64, 0x31cd86d3, 0x3c8ea00a, 0x384fbdbd,
        0x4c11db70, 0x48d0c6c7, 0x4593e01e, 0x4152fda9,
        0x5f15adac, 0x5bd4b01b, 0x569796c2, 0x52568b75,
        0x6a1936c8, 0x6ed82b7f, 0x639b0da6, 0x675a1011,
        0x791d4014, 0x7ddc5da3, 0x709f7b7a, 0x745e66cd,
        0x9823b6e0, 0x9ce2ab57, 0x91a18d8e, 0x95609039,
        0x8b27c03c, 0x8fe6dd8b, 0x82a5fb52, 0x8664e6e5,
        0xbe2b5b58, 0xbaea46ef, 0xb7a96036, 0xb3687d81,
        0xad2f2d84, 0xa9ee3033, 0xa4ad16ea, 0xa06c0b5d,
        0xd4326d90, 0xd0f37027, 0xddb056fe, 0xd9714b49,
        0xc7361b4c, 0xc3f706fb, 0xceb42022, 0xca753d95,
        0xf23a8028, 0xf6fb9d9f, 0xfbb8bb46, 0xff79a6f1,
        0xe13ef6f4, 0xe5ffeb43, 0xe8bccd9a, 0xec7dd02d,
        0x34867077, 0x30476dc0, 0x3d044b19, 0x39c556ae,
        0x278206ab, 0x23431b1c, 0x2e003dc5, 0x2ac12072,
        0x128e9dcf, 0x164f8078, 0x1b0ca6a1, 0x1fcdbb16,
        0x018aeb13, 0x054bf6a4, 0x0808d07d, 0x0cc9cdca,
        0x7897ab07, 0x7c56b6b0, 0x71159069, 0x75d48dde,
        0x6b93dddb, 0x6f52c06c, 0x6211e6b5, 0x66d0fb02,
        0x5e9f46bf, 0x5a5e5b08, 0x571d7dd1, 0x53dc6066,
        0x4d9b3063, 0x495a2dd4, 0x44190b0d, 0x40d816ba,
        0xaca5c697, 0xa864db20, 0xa527fdf9, 0xa1e6e04e,
        0xbfa1b04b, 0xbb60adfc, 0xb6238b25, 0xb2e29692,
        0x8aad2b2f, 0x8e6c3698, 0x832f1041, 0x87ee0df6,
        0x99a95df3, 0x9d684044, 0x902b669d, 0x94ea7b2a,
        0xe0b41de7, 0xe4750050, 0xe9362689, 0xedf73b3e,
        0xf3b06b3b, 0xf771768c, 0xfa325055, 0xfef34de2,
        0xc6bcf05f, 0xc27dede8, 0xcf3ecb31, 0xcbffd686,
        0xd5b88683, 0xd1799b34, 0xdc3abded, 0xd8fba05a,
        0x690ce0ee, 0x6dcdfd59, 0x608edb80, 0x644fc637,
        0x7a089632, 0x7ec98b85, 0x738aad5c, 0x774bb0eb,
        0x4f040d56, 0x4bc510e1, 0x46863638, 0x42472b8f,
        0x5c007b8a, 0x58c1663d, 0x558240e4, 0x51435d53,
        0x251d3b9e, 0x21dc2629, 0x2c9f00f0, 0x285e1d47,
        0x36194d42, 0x32d850f5, 0x3f9b762c, 0x3b5a6b9b,
        0x0315d626, 0x07d4cb91, 0x0a97ed48, 0x0e56f0ff,
        0x1011a0fa, 0x14d0bd4d, 0x19939b94, 0x1d528623,
        0xf12f560e, 0xf5ee4bb9, 0xf8ad6d60, 0xfc6c70d7,
        0xe22b20d2, 0xe6ea3d65, 0xeba91bbc, 0xef68060b,
        0xd727bbb6, 0xd3e6a601, 0xdea580d8, 0xda649d6f,
        0xc423cd6a, 0xc0e2d0dd, 0xcda1f604, 0xc960ebb3,
        0xbd3e8d7e, 0xb9ff90c9, 0xb4bcb610, 0xb07daba7,
        0xae3afba2, 0xaafbe615, 0xa7b8c0cc, 0xa379dd7b,
        0x9b3660c6, 0x9ff77d71, 0x92b45ba8, 0x9675461f,
        0x8832161a, 0x8cf30bad, 0x81b02d74, 0x857130c3,
        0x5d8a9099, 0x594b8d2e, 0x5408abf7, 0x50c9b640,
        0x4e8ee645, 0x4a4ffbf2, 0x470cdd2b, 0x43cdc09c,
        0x7b827d21, 0x7f436096, 0x7200464f, 0x76c15bf8,
        0x68860bfd, 0x6c47164a, 0x61043093, 0x65c52d24,
        0x119b4be9, 0x155a565e, 0x18197087, 0x1cd86d30,
        0x029f3d35, 0x065e2082, 0x0b1d065b, 0x0fdc1bec,
        0x3793a651, 0x3352bbe6, 0x3e119d3f, 0x3ad08088,
        0x2497d08d, 0x2056cd3a, 0x2d15ebe3, 0x29d4f654,
        0xc5a92679, 0xc1683bce, 0xcc2b1d17, 0xc8ea00a0,
        0xd6ad50a5, 0xd26c4d12, 0xdf2f6bcb, 0xdbee767c,
        0xe3a1cbc1, 0xe760d676, 0xea23f0af, 0xeee2ed18,
        0xf0a5bd1d, 0xf464a0aa, 0xf9278673, 0xfde69bc4,
        0x89b8fd09, 0x8d79e0be, 0x803ac667, 0x84fbdbd0,
        0x9abc8bd5, 0x9e7d9662, 0x933eb0bb, 0x97ffad0c,
        0xafb010b1, 0xab710d06, 0xa6322bdf, 0xa2f33668,
        0xbcb4666d, 0xb8757bda, 0xb5365d03, 0xb1f740b4
    };

    // A table which maps ATSC genres to TIF genres.
    // See ATSC/65 Table 6.20.
    private static final String[] CANONICAL_GENRES_TABLE = {
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        null, null, null, null,
        Genres.EDUCATION, Genres.ENTERTAINMENT, Genres.MOVIES, Genres.NEWS,
        Genres.LIFE_STYLE, Genres.SPORTS, null, Genres.MOVIES,
        null,
        Genres.FAMILY_KIDS, Genres.DRAMA, null, Genres.ENTERTAINMENT, Genres.SPORTS,
        Genres.SPORTS,
        null, null,
        Genres.MUSIC, Genres.EDUCATION,
        null,
        Genres.COMEDY,
        null,
        Genres.MUSIC,
        null, null,
        Genres.MOVIES, Genres.ENTERTAINMENT, Genres.NEWS, Genres.DRAMA,
        Genres.EDUCATION, Genres.MOVIES, Genres.SPORTS, Genres.MOVIES,
        null,
        Genres.LIFE_STYLE, Genres.ARTS, Genres.LIFE_STYLE, Genres.SPORTS,
        null, null,
        Genres.GAMING, Genres.LIFE_STYLE, Genres.SPORTS,
        null,
        Genres.LIFE_STYLE, Genres.EDUCATION, Genres.EDUCATION, Genres.LIFE_STYLE,
        Genres.SPORTS, Genres.LIFE_STYLE, Genres.MOVIES, Genres.NEWS,
        null, null, null,
        Genres.EDUCATION,
        null, null, null,
        Genres.EDUCATION,
        null, null, null,
        Genres.DRAMA, Genres.MUSIC, Genres.MOVIES,
        null,
        Genres.ANIMAL_WILDLIFE,
        null, null,
        Genres.PREMIER,
        null, null, null, null,
        Genres.SPORTS, Genres.ARTS,
        null, null, null,
        Genres.MOVIES, Genres.TECH_SCIENCE, Genres.DRAMA,
        null,
        Genres.SHOPPING, Genres.DRAMA,
        null,
        Genres.MOVIES, Genres.ENTERTAINMENT, Genres.TECH_SCIENCE, Genres.SPORTS,
        Genres.TRAVEL, Genres.ENTERTAINMENT, Genres.ARTS, Genres.NEWS,
        null,
        Genres.ARTS, Genres.SPORTS, Genres.SPORTS, Genres.NEWS,
        Genres.SPORTS, Genres.SPORTS, Genres.SPORTS, Genres.FAMILY_KIDS,
        Genres.FAMILY_KIDS, Genres.MOVIES,
        null,
        Genres.TECH_SCIENCE, Genres.MUSIC,
        null,
        Genres.SPORTS, Genres.FAMILY_KIDS, Genres.NEWS, Genres.SPORTS,
        Genres.NEWS, Genres.SPORTS, Genres.ANIMAL_WILDLIFE,
        null,
        Genres.MUSIC, Genres.NEWS, Genres.SPORTS,
        null,
        Genres.NEWS, Genres.NEWS, Genres.NEWS, Genres.NEWS,
        Genres.SPORTS, Genres.MOVIES, Genres.ARTS, Genres.ANIMAL_WILDLIFE,
        Genres.MUSIC, Genres.MUSIC, Genres.MOVIES, Genres.EDUCATION,
        Genres.DRAMA, Genres.SPORTS, Genres.SPORTS, Genres.SPORTS,
        Genres.SPORTS,
        null,
        Genres.SPORTS, Genres.SPORTS,
    };

    // A table which contains ATSC categorical genre code assignments.
    // See ATSC/65 Table 6.20.
    private static final String[] BROADCAST_GENRES_TABLE = new String[] {
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            null, null, null, null,
            "Education", "Entertainment", "Movie", "News",
            "Religious", "Sports", "Other", "Action",
            "Advertisement", "Animated", "Anthology", "Automobile",
            "Awards", "Baseball", "Basketball", "Bulletin",
            "Business", "Classical", "College", "Combat",
            "Comedy", "Commentary", "Concert", "Consumer",
            "Contemporary", "Crime", "Dance", "Documentary",
            "Drama", "Elementary", "Erotica", "Exercise",
            "Fantasy", "Farm", "Fashion", "Fiction",
            "Food", "Football", "Foreign", "Fund Raiser",
            "Game/Quiz", "Garden", "Golf", "Government",
            "Health", "High School", "History", "Hobby",
            "Hockey", "Home", "Horror", "Information",
            "Instruction", "International", "Interview", "Language",
            "Legal", "Live", "Local", "Math",
            "Medical", "Meeting", "Military", "Miniseries",
            "Music", "Mystery", "National", "Nature",
            "Police", "Politics", "Premier", "Prerecorded",
            "Product", "Professional", "Public", "Racing",
            "Reading", "Repair", "Repeat", "Review",
            "Romance", "Science", "Series", "Service",
            "Shopping", "Soap Opera", "Special", "Suspense",
            "Talk", "Technical", "Tennis", "Travel",
            "Variety", "Video", "Weather", "Western",
            "Art", "Auto Racing", "Aviation", "Biography",
            "Boating", "Bowling", "Boxing", "Cartoon",
            "Children", "Classic Film", "Community", "Computers",
            "Country Music", "Court", "Extreme Sports", "Family",
            "Financial", "Gymnastics", "Headlines", "Horse Racing",
            "Hunting/Fishing/Outdoors", "Independent", "Jazz", "Magazine",
            "Motorcycle Racing", "Music/Film/Books", "News-International", "News-Local",
            "News-National", "News-Regional", "Olympics", "Original",
            "Performing Arts", "Pets/Animals", "Pop", "Rock & Roll",
            "Sci-Fi", "Self Improvement", "Sitcom", "Skating",
            "Skiing", "Soccer", "Track/Field", "True",
            "Volleyball", "Wrestling",
    };

    // Audio language code map from ISO 639-2/B to 639-2/T, in order to show correct audio language.
    private static final HashMap<String, String> ISO_LANGUAGE_CODE_MAP;
    static {
        ISO_LANGUAGE_CODE_MAP = new HashMap<>();
        ISO_LANGUAGE_CODE_MAP.put("alb", "sqi");
        ISO_LANGUAGE_CODE_MAP.put("arm", "hye");
        ISO_LANGUAGE_CODE_MAP.put("baq", "eus");
        ISO_LANGUAGE_CODE_MAP.put("bur", "mya");
        ISO_LANGUAGE_CODE_MAP.put("chi", "zho");
        ISO_LANGUAGE_CODE_MAP.put("cze", "ces");
        ISO_LANGUAGE_CODE_MAP.put("dut", "nld");
        ISO_LANGUAGE_CODE_MAP.put("fre", "fra");
        ISO_LANGUAGE_CODE_MAP.put("geo", "kat");
        ISO_LANGUAGE_CODE_MAP.put("ger", "deu");
        ISO_LANGUAGE_CODE_MAP.put("gre", "ell");
        ISO_LANGUAGE_CODE_MAP.put("ice", "isl");
        ISO_LANGUAGE_CODE_MAP.put("mac", "mkd");
        ISO_LANGUAGE_CODE_MAP.put("mao", "mri");
        ISO_LANGUAGE_CODE_MAP.put("may", "msa");
        ISO_LANGUAGE_CODE_MAP.put("per", "fas");
        ISO_LANGUAGE_CODE_MAP.put("rum", "ron");
        ISO_LANGUAGE_CODE_MAP.put("slo", "slk");
        ISO_LANGUAGE_CODE_MAP.put("tib", "bod");
        ISO_LANGUAGE_CODE_MAP.put("wel", "cym");
        ISO_LANGUAGE_CODE_MAP.put("esl", "spa"); // Special entry for channel 9-1 KQED in bay area.
    }

    // Containers to store the last version numbers of the PSIP sections.
    private final HashMap<PsipSection, Integer> mSectionVersionMap = new HashMap<>();
    private final SparseArray<List<EttItem>> mParsedEttItems = new SparseArray<>();

    public interface OutputListener {
        void onPatParsed(List<PatItem> items);
        void onPmtParsed(int programNumber, List<PmtItem> items);
        void onMgtParsed(List<MgtItem> items);
        void onVctParsed(List<VctItem> items, int sectionNumber, int lastSectionNumber);
        void onEitParsed(int sourceId, List<EitItem> items);
        void onEttParsed(int sourceId, List<EttItem> descriptions);
        void onSdtParsed(List<SdtItem> items);
    }

    private final OutputListener mListener;

    public SectionParser(OutputListener listener) {
        mListener = listener;
    }

    public void parseSections(ByteArrayBuffer data) {
        int pos = 0;
        while (pos + 3 <= data.length()) {
            if ((data.byteAt(pos) & 0xff) == 0xff) {
                // Clear stuffing bytes according to H222.0 section 2.4.4.
                data.setLength(0);
                break;
            }
            int sectionLength =
                    (((data.byteAt(pos + 1) & 0x0f) << 8) | (data.byteAt(pos + 2) & 0xff)) + 3;
            if (pos + sectionLength > data.length()) {
                break;
            }
            if (DEBUG) {
                Log.d(TAG, "parseSections 0x" + Integer.toHexString(data.byteAt(pos) & 0xff));
            }
            parseSection(Arrays.copyOfRange(data.buffer(), pos, pos + sectionLength));
            pos += sectionLength;
        }
        if (mListener != null) {
            for (int i = 0; i < mParsedEttItems.size(); ++i) {
                int sourceId = mParsedEttItems.keyAt(i);
                List<EttItem> descriptions = mParsedEttItems.valueAt(i);
                mListener.onEttParsed(sourceId, descriptions);
            }
        }
        mParsedEttItems.clear();
    }

    public void resetVersionNumbers() {
        mSectionVersionMap.clear();
    }

    private void parseSection(byte[] data) {
        if (!checkSanity(data)) {
            Log.d(TAG, "Bad CRC!");
            return;
        }
        PsipSection section = PsipSection.create(data);
        if (section == null) {
            return;
        }

        // The currentNextIndicator indicates that the section sent is currently applicable.
        if (!section.getCurrentNextIndicator()) {
            return;
        }
        int versionNumber = (data[5] & 0x3e) >> 1;
        Integer oldVersionNumber = mSectionVersionMap.get(section);

        // The versionNumber shall be incremented when a change in the information carried within
        // the section occurs.
        if (oldVersionNumber != null && versionNumber == oldVersionNumber) {
            return;
        }
        boolean result = false;
        switch (data[0]) {
            case TABLE_ID_PAT:
                result = parsePAT(data);
                break;
            case TABLE_ID_PMT:
                result = parsePMT(data);
                break;
            case TABLE_ID_MGT:
                result = parseMGT(data);
                break;
            case TABLE_ID_TVCT:
            case TABLE_ID_CVCT:
                result = parseVCT(data);
                break;
            case TABLE_ID_EIT:
                result = parseEIT(data);
                break;
            case TABLE_ID_ETT:
                result = parseETT(data);
                break;
            case TABLE_ID_SDT:
                result = parseSDT(data);
                break;
            case TABLE_ID_DVB_ACTUAL_P_F_EIT:
            case TABLE_ID_DVB_ACTUAL_SCHEDULE_EIT:
                result = parseDVBEIT(data);
                break;
            default:
                break;
        }
        if (result) {
            mSectionVersionMap.put(section, versionNumber);
        }
    }

    private boolean parsePAT(byte[] data) {
        if (DEBUG) {
            Log.d(TAG, "PAT is discovered.");
        }
        int pos = 8;

        List<PatItem> results = new ArrayList<>();
        for (; pos < data.length - 4; pos = pos + 4) {
            if (pos > data.length - 4 - 4) {
                Log.e(TAG, "Broken PAT.");
                return false;
            }
            int programNo = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
            int pmtPid = ((data[pos + 2] & 0x1f) << 8) | (data[pos + 3] & 0xff);
            results.add(new PatItem(programNo, pmtPid));
        }
        if (mListener != null) {
            mListener.onPatParsed(results);
        }
        return true;
    }

    private boolean parsePMT(byte[] data) {
        int table_id_ext = ((data[3] & 0xff) << 8) | (data[4] & 0xff);
        if (DEBUG) {
            Log.d(TAG, "PMT is discovered. programNo = " + table_id_ext);
        }
        if (data.length <= 11) {
            Log.e(TAG, "Broken PMT.");
            return false;
        }
        int pcrPid = (data[8] & 0x1f) << 8 | data[9];
        int programInfoLen = (data[10] & 0x0f) << 8 | data[11];
        int pos = 12;
        List<TsDescriptor> descriptors = parseDescriptors(data, pos, pos + programInfoLen);
        pos += programInfoLen;
        if (DEBUG) {
            Log.d(TAG, "PMT descriptors size: " + descriptors.size());
        }
        List<PmtItem> results = new ArrayList<>();
        for (; pos < data.length - 4;) {
            if (pos < 0) {
                Log.e(TAG, "Broken PMT.");
                return false;
            }
            int streamType = data[pos] & 0xff;
            int esPid = (data[pos + 1] & 0x1f) << 8 | (data[pos + 2] & 0xff);
            int esInfoLen = (data[pos + 3] & 0xf) << 8 | (data[pos + 4] & 0xff);
            if (data.length < pos + esInfoLen + 5) {
                Log.e(TAG, "Broken PMT.");
                return false;
            }
            descriptors = parseDescriptors(data, pos + 5, pos + 5 + esInfoLen);
            List<AtscAudioTrack> audioTracks = generateAudioTracks(descriptors);
            List<AtscCaptionTrack> captionTracks = generateCaptionTracks(descriptors);
            PmtItem pmtItem = new PmtItem(streamType, esPid, audioTracks, captionTracks);
            if (DEBUG) {
                Log.d(TAG, "PMT " + pmtItem + " descriptors size: " + descriptors.size());
            }
            results.add(pmtItem);
            pos = pos + esInfoLen + 5;
        }
        results.add(new PmtItem(PmtItem.ES_PID_PCR, pcrPid, null, null));
        if (mListener != null) {
            mListener.onPmtParsed(table_id_ext, results);
        }
        return true;
    }

    private boolean parseMGT(byte[] data) {
        // For details of the structure for MGT, see ATSC A/65 Table 6.2.
        if (DEBUG) {
            Log.d(TAG, "MGT is discovered.");
        }
        if (data.length <= 10) {
            Log.e(TAG, "Broken MGT.");
            return false;
        }
        int tablesDefined = ((data[9] & 0xff) << 8) | (data[10] & 0xff);
        int pos = 11;
        List<MgtItem> results = new ArrayList<>();
        for (int i = 0; i < tablesDefined; ++i) {
            if (data.length <= pos + 10) {
                Log.e(TAG, "Broken MGT.");
                return false;
            }
            int tableType = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
            int tableTypePid = ((data[pos + 2] & 0x1f) << 8) | (data[pos + 3] & 0xff);
            int descriptorsLength = ((data[pos + 9] & 0x0f) << 8) | (data[pos + 10] & 0xff);
            pos += 11 + descriptorsLength;
            results.add(new MgtItem(tableType, tableTypePid));
        }
        // Skip the remaining descriptor part which we don't use.

        if (mListener != null) {
            mListener.onMgtParsed(results);
        }
        return true;
    }

    private boolean parseVCT(byte[] data) {
        // For details of the structure for VCT, see ATSC A/65 Table 6.4 and 6.8.
        if (DEBUG) {
            Log.d(TAG, "VCT is discovered.");
        }
        if (data.length <= 9) {
            Log.e(TAG, "Broken VCT.");
            return false;
        }
        int numChannelsInSection = (data[9] & 0xff);
        int sectionNumber = (data[6] & 0xff);
        int lastSectionNumber = (data[7] & 0xff);
        if (sectionNumber > lastSectionNumber) {
            // According to section 6.3.1 of the spec ATSC A/65,
            // last section number is the largest section number.
            Log.w(TAG, "Invalid VCT. Section Number " + sectionNumber + " > Last Section Number "
                    + lastSectionNumber);
            return false;
        }
        int pos = 10;
        List<VctItem> results = new ArrayList<>();
        for (int i = 0; i < numChannelsInSection; ++i) {
            if (data.length <= pos + 31) {
                Log.e(TAG, "Broken VCT.");
                return false;
            }
            String shortName = "";
            int shortNameSize = getShortNameSize(data, pos);
            try {
                shortName = new String(
                        Arrays.copyOfRange(data, pos, pos + shortNameSize), "UTF-16");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Broken VCT.", e);
                return false;
            }
            if ((data[pos + 14] & 0xf0) != 0xf0) {
                Log.e(TAG, "Broken VCT.");
                return false;
            }
            int majorNumber = ((data[pos + 14] & 0x0f) << 6) | ((data[pos + 15] & 0xff) >> 2);
            int minorNumber = ((data[pos + 15] & 0x03) << 8) | (data[pos + 16] & 0xff);
            if ((majorNumber & 0x3f0) == 0x3f0) {
                // If the six MSBs are 111111, these indicate that there is only one-part channel
                // number. To see details, refer A/65 Section 6.3.2.
                majorNumber = ((majorNumber & 0xf) << 10) + minorNumber;
                minorNumber = 0;
            }
            int channelTsid = ((data[pos + 22] & 0xff) << 8) | (data[pos + 23] & 0xff);
            int programNumber = ((data[pos + 24] & 0xff) << 8) | (data[pos + 25] & 0xff);
            boolean accessControlled = (data[pos + 26] & 0x20) != 0;
            boolean hidden = (data[pos + 26] & 0x10) != 0;
            int serviceType = (data[pos + 27] & 0x3f);
            int sourceId = ((data[pos + 28] & 0xff) << 8) | (data[pos + 29] & 0xff);
            int descriptorsPos = pos + 32;
            int descriptorsLength = ((data[pos + 30] & 0x03) << 8) | (data[pos + 31] & 0xff);
            pos += 32 + descriptorsLength;
            if (data.length < pos) {
                Log.e(TAG, "Broken VCT.");
                return false;
            }
            List<TsDescriptor> descriptors = parseDescriptors(
                    data, descriptorsPos, descriptorsPos + descriptorsLength);
            String longName = null;
            for (TsDescriptor descriptor : descriptors) {
                if (descriptor instanceof ExtendedChannelNameDescriptor) {
                    ExtendedChannelNameDescriptor extendedChannelNameDescriptor =
                            (ExtendedChannelNameDescriptor) descriptor;
                    longName = extendedChannelNameDescriptor.getLongChannelName();
                    break;
                }
            }
            if (DEBUG) {
                Log.d(TAG, String.format(
                        "Found channel [%s] %s - serviceType: %d tsid: 0x%x program: %d "
                                + "channel: %d-%d encrypted: %b hidden: %b, descriptors: %d",
                        shortName, longName, serviceType, channelTsid, programNumber, majorNumber,
                        minorNumber, accessControlled, hidden, descriptors.size()));
            }
            if (!accessControlled && !hidden && (serviceType == Channel.SERVICE_TYPE_ATSC_AUDIO ||
                    serviceType == Channel.SERVICE_TYPE_ATSC_DIGITAL_TELEVISION ||
                    serviceType == Channel.SERVICE_TYPE_UNASSOCIATED_SMALL_SCREEN_SERVICE)) {
                // Hide hidden, encrypted, or unsupported ATSC service type channels
                results.add(new VctItem(shortName, longName, serviceType, channelTsid,
                        programNumber, majorNumber, minorNumber, sourceId));
            }
        }
        // Skip the remaining descriptor part which we don't use.

        if (mListener != null) {
            mListener.onVctParsed(results, sectionNumber, lastSectionNumber);
        }
        return true;
    }

    private boolean parseEIT(byte[] data) {
        // For details of the structure for EIT, see ATSC A/65 Table 6.11.
        if (DEBUG) {
            Log.d(TAG, "EIT is discovered.");
        }
        if (data.length <= 9) {
            Log.e(TAG, "Broken EIT.");
            return false;
        }
        int sourceId = ((data[3] & 0xff) << 8) | (data[4] & 0xff);
        int numEventsInSection = (data[9] & 0xff);

        int pos = 10;
        List<EitItem> results = new ArrayList<>();
        for (int i = 0; i < numEventsInSection; ++i) {
            if (data.length <= pos + 9) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            if ((data[pos] & 0xc0) != 0xc0) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            int eventId = ((data[pos] & 0x3f) << 8) + (data[pos + 1] & 0xff);
            long startTime = ((data[pos + 2] & (long) 0xff) << 24) | ((data[pos + 3] & 0xff) << 16)
                    | ((data[pos + 4] & 0xff) << 8) | (data[pos + 5] & 0xff);
            int lengthInSecond = ((data[pos + 6] & 0x0f) << 16)
                    | ((data[pos + 7] & 0xff) << 8) | (data[pos + 8] & 0xff);
            int titleLength = (data[pos + 9] & 0xff);
            if (data.length <= pos + 10 + titleLength + 1) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            String titleText = "";
            if (titleLength > 0) {
                titleText = extractText(data, pos + 10);
            }
            if ((data[pos + 10 + titleLength] & 0xf0) != 0xf0) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            int descriptorsLength = ((data[pos + 10 + titleLength] & 0x0f) << 8)
                    | (data[pos + 10 + titleLength + 1] & 0xff);
            int descriptorsPos = pos + 10 + titleLength + 2;
            if (data.length < descriptorsPos + descriptorsLength) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            List<TsDescriptor> descriptors = parseDescriptors(
                    data, descriptorsPos, descriptorsPos + descriptorsLength);
            if (DEBUG) {
                Log.d(TAG, String.format("EIT descriptors size: %d", descriptors.size()));
            }
            String contentRating = generateContentRating(descriptors);
            String broadcastGenre = generateBroadcastGenre(descriptors);
            String canonicalGenre = generateCanonicalGenre(descriptors);
            List<AtscAudioTrack> audioTracks = generateAudioTracks(descriptors);
            List<AtscCaptionTrack> captionTracks = generateCaptionTracks(descriptors);
            pos += 10 + titleLength + 2 + descriptorsLength;
            results.add(new EitItem(EitItem.INVALID_PROGRAM_ID, eventId, titleText,
                    startTime, lengthInSecond, contentRating, audioTracks, captionTracks,
                    broadcastGenre, canonicalGenre, null));
        }
        if (mListener != null) {
            mListener.onEitParsed(sourceId, results);
        }
        return true;
    }

    private boolean parseETT(byte[] data) {
        // For details of the structure for ETT, see ATSC A/65 Table 6.13.
        if (DEBUG) {
            Log.d(TAG, "ETT is discovered.");
        }
        if (data.length <= 12) {
            Log.e(TAG, "Broken ETT.");
            return false;
        }
        int sourceId = ((data[9] & 0xff) << 8) | (data[10] & 0xff);
        int eventId = (((data[11] & 0xff) << 8) | (data[12] & 0xff)) >> 2;
        String text = extractText(data, 13);
        List<EttItem> ettItems = mParsedEttItems.get(sourceId);
        if (ettItems == null) {
            ettItems = new ArrayList<>();
            mParsedEttItems.put(sourceId, ettItems);
        }
        ettItems.add(new EttItem(eventId, text));
        return true;
    }

    private boolean parseSDT(byte[] data) {
        // For details of the structure for SDT, see DVB Document A038 Table 5.
        if (DEBUG) {
            Log.d(TAG, "SDT id discovered");
        }
        if (data.length <= 11) {
            Log.e(TAG, "Broken SDT.");
            return false;
        }
        if ((data[1] & 0x80) >> 7 != 1) {
            Log.e(TAG, "Broken SDT, section syntax indicator error.");
            return false;
        }
        int sectionLength = ((data[1] & 0x0f) << 8) | (data[2] & 0xff);
        int transportStreamId = ((data[3] & 0xff) << 8) | (data[4] & 0xff);
        int originalNetworkId = ((data[8] & 0xff) << 8) | (data[9] & 0xff);
        int pos = 11;
        if (sectionLength + 3 > data.length) {
            Log.e(TAG, "Broken SDT.");
        }
        List<SdtItem> sdtItems = new ArrayList<>();
        while (pos + 9 < data.length) {
            int serviceId = ((data[pos] & 0xff) << 8) | (data[pos + 1] & 0xff);
            int descriptorsLength = ((data[pos + 3] & 0x0f) << 8) | (data[pos + 4] & 0xff);
            pos += 5;
            List<TsDescriptor> descriptors = parseDescriptors(data, pos, pos + descriptorsLength);
            List<ServiceDescriptor> serviceDescriptors = generateServiceDescriptors(descriptors);
            String serviceName = "";
            String serviceProviderName = "";
            int serviceType = 0;
            for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
                serviceName = serviceDescriptor.getServiceName();
                serviceProviderName = serviceDescriptor.getServiceProviderName();
                serviceType = serviceDescriptor.getServiceType();
            }
            if (serviceDescriptors.size() > 0) {
                sdtItems.add(new SdtItem(serviceName, serviceProviderName, serviceType, serviceId,
                        originalNetworkId));
            }
            pos += descriptorsLength;
        }
        if (mListener != null) {
            mListener.onSdtParsed(sdtItems);
        }
        return true;
    }

    private boolean parseDVBEIT(byte[] data) {
        // For details of the structure for DVB ETT, see DVB Document A038 Table 7.
        if (DEBUG) {
            Log.d(TAG, "DVB EIT is discovered.");
        }
        if (data.length < 18) {
            Log.e(TAG, "Broken DVB EIT.");
            return false;
        }
        int sectionLength = ((data[1] & 0x0f) << 8) | (data[2] & 0xff);
        int sourceId = ((data[3] & 0xff) << 8) | (data[4] & 0xff);
        int transportStreamId = ((data[8] & 0xff) << 8) | (data[9] & 0xff);
        int originalNetworkId = ((data[10] & 0xff) << 8) | (data[11] & 0xff);

        int pos = 14;
        List<EitItem> results = new ArrayList<>();
        while (pos + 12 < data.length) {
            int eventId = ((data[pos] & 0xff) << 8) + (data[pos + 1] & 0xff);
            float modifiedJulianDate = ((data[pos + 2] &  0xff) << 8) | (data[pos + 3] & 0xff);
            int startYear = (int) ((modifiedJulianDate - 15078.2f) / 365.25f);
            int mjdMonth = (int) ((modifiedJulianDate - 14956.1f
                    - (int) (startYear * 365.25f)) / 30.6001f);
            int startDay = (int) modifiedJulianDate - 14956 - (int) (startYear * 365.25f)
                    - (int) (mjdMonth * 30.6001f);
            int startMonth = mjdMonth - 1;
            if (mjdMonth == 14 || mjdMonth == 15) {
                startYear += 1;
                startMonth -= 12;
            }
            int startHour = ((data[pos + 4] & 0xf0) >> 4) * 10 + (data[pos + 4] & 0x0f);
            int startMinute = ((data[pos + 5] & 0xf0) >> 4) * 10 + (data[pos + 5] & 0x0f);
            int startSecond = ((data[pos + 6] & 0xf0) >> 4) * 10 + (data[pos + 6] & 0x0f);
            Calendar calendar = Calendar.getInstance();
            startYear += 1900;
            calendar.set(startYear, startMonth, startDay, startHour, startMinute, startSecond);
            long startTime = ConvertUtils.convertUnixEpochToGPSTime(
                    calendar.getTimeInMillis() / 1000);
            int durationInSecond = (((data[pos + 7] & 0xf0) >> 4) * 10
                    + (data[pos + 7] & 0x0f)) * 3600
                    + (((data[pos + 8] & 0xf0) >> 4) * 10 + (data[pos + 8] & 0x0f)) * 60
                    + (((data[pos + 9] & 0xf0) >> 4) * 10 + (data[pos + 9] & 0x0f));
            int descriptorsLength = ((data[pos + 10] & 0x0f) << 8)
                    | (data[pos + 10 + 1] & 0xff);
            int descriptorsPos = pos + 10 + 2;
            if (data.length < descriptorsPos + descriptorsLength) {
                Log.e(TAG, "Broken EIT.");
                return false;
            }
            List<TsDescriptor> descriptors = parseDescriptors(
                    data, descriptorsPos, descriptorsPos + descriptorsLength);
            if (DEBUG) {
                Log.d(TAG, String.format("DVB EIT descriptors size: %d", descriptors.size()));
            }
            // TODO: Add logic to generating content rating for dvb. See DVB document 6.2.28 for
            // details. Content rating here will be null
            String contentRating = generateContentRating(descriptors);
            // TODO: Add logic for generating genre for dvb. See DVB document 6.2.9 for details.
            // Genre here will be null here.
            String broadcastGenre = generateBroadcastGenre(descriptors);
            String canonicalGenre = generateCanonicalGenre(descriptors);
            String titleText = generateShortEventName(descriptors);
            List<AtscAudioTrack> audioTracks = generateAudioTracks(descriptors);
            List<AtscCaptionTrack> captionTracks = generateCaptionTracks(descriptors);
            pos += 12 + descriptorsLength;
            results.add(new EitItem(EitItem.INVALID_PROGRAM_ID, eventId, titleText,
                    startTime, durationInSecond, contentRating, audioTracks, captionTracks,
                    broadcastGenre, canonicalGenre, null));
        }
        if (mListener != null) {
            mListener.onEitParsed(sourceId, results);
        }
        return true;
    }

    private static List<AtscAudioTrack> generateAudioTracks(List<TsDescriptor> descriptors) {
        // The list of audio tracks sent is located at both AC3 Audio descriptor and ISO 639
        // Language descriptor.
        List<AtscAudioTrack> ac3Tracks = new ArrayList<>();
        List<AtscAudioTrack> iso639LanguageTracks = new ArrayList<>();
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof Ac3AudioDescriptor) {
                Ac3AudioDescriptor audioDescriptor =
                        (Ac3AudioDescriptor) descriptor;
                AtscAudioTrack audioTrack = new AtscAudioTrack();
                if (audioDescriptor.getLanguage() != null) {
                    audioTrack.language = audioDescriptor.getLanguage();
                }
                if (audioTrack.language == null) {
                    audioTrack.language = "";
                }
                audioTrack.audioType = AtscAudioTrack.AUDIOTYPE_UNDEFINED;
                audioTrack.channelCount = audioDescriptor.getNumChannels();
                audioTrack.sampleRate = audioDescriptor.getSampleRate();
                ac3Tracks.add(audioTrack);
            }
        }
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof Iso639LanguageDescriptor) {
                Iso639LanguageDescriptor iso639LanguageDescriptor =
                        (Iso639LanguageDescriptor) descriptor;
                iso639LanguageTracks.addAll(iso639LanguageDescriptor.getAudioTracks());
            }
        }

        // An AC3 audio stream descriptor only has a audio channel count and a audio sample rate
        // while a ISO 639 Language descriptor only has a audio type, which describes a main use
        // case of its audio track.
        // Some channels contain only AC3 audio stream descriptors with valid language values.
        // Other channels contain both an AC3 audio stream descriptor and a ISO 639 Language
        // descriptor per audio track, and those AC3 audio stream descriptors often have a null
        // value of language field.
        // Combines two descriptors into one in order to gather more audio track specific
        // information as much as possible.
        List<AtscAudioTrack> tracks = new ArrayList<>();
        if (!ac3Tracks.isEmpty() && !iso639LanguageTracks.isEmpty()
                && ac3Tracks.size() != iso639LanguageTracks.size()) {
            // This shouldn't be happen. In here, it handles two cases. The first case is that the
            // only one type of descriptors arrives. The second case is that the two types of
            // descriptors have the same number of tracks.
            Log.e(TAG, "AC3 audio stream descriptors size != ISO 639 Language descriptors size");
            return tracks;
        }
        int size = Math.max(ac3Tracks.size(), iso639LanguageTracks.size());
        for (int i = 0; i < size; ++i) {
            AtscAudioTrack audioTrack = null;
            if (i < ac3Tracks.size()) {
                audioTrack = ac3Tracks.get(i);
            }
            if (i < iso639LanguageTracks.size()) {
                if (audioTrack == null) {
                    audioTrack = iso639LanguageTracks.get(i);
                } else {
                    AtscAudioTrack iso639LanguageTrack = iso639LanguageTracks.get(i);
                    if (audioTrack.language == null || TextUtils.equals(audioTrack.language, "")) {
                        audioTrack.language = iso639LanguageTrack.language;
                    }
                    audioTrack.audioType = iso639LanguageTrack.audioType;
                }
            }
            String language = ISO_LANGUAGE_CODE_MAP.get(audioTrack.language);
            if (language != null) {
                audioTrack.language = language;
            }
            tracks.add(audioTrack);
        }
        return tracks;
    }

    private static List<AtscCaptionTrack> generateCaptionTracks(List<TsDescriptor> descriptors) {
        List<AtscCaptionTrack> services = new ArrayList<>();
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof CaptionServiceDescriptor) {
                CaptionServiceDescriptor captionServiceDescriptor =
                        (CaptionServiceDescriptor) descriptor;
                services.addAll(captionServiceDescriptor.getCaptionTracks());
            }
        }
        return services;
    }

    @VisibleForTesting
    static String generateContentRating(List<TsDescriptor> descriptors) {
        Set<String> contentRatings = new ArraySet<>();
        List<RatingRegion> usRatingRegions = getRatingRegions(descriptors, RATING_REGION_US_TV);
        List<RatingRegion> krRatingRegions = getRatingRegions(descriptors, RATING_REGION_KR_TV);
        for (RatingRegion region : usRatingRegions) {
            String contentRating = getUsRating(region);
            if (contentRating != null) {
                contentRatings.add(contentRating);
            }
        }
        for (RatingRegion region : krRatingRegions) {
            String contentRating = getKrRating(region);
            if (contentRating != null) {
                contentRatings.add(contentRating);
            }
        }
        return TextUtils.join(",", contentRatings);
    }

    /**
     * Gets a list of {@link RatingRegion} in the specific region.
     *
     * @param descriptors {@link TsDescriptor} list which may contains rating information
     * @param region the specific region
     * @return a list of {@link RatingRegion} in the specific region
     */
    private static List<RatingRegion> getRatingRegions(List<TsDescriptor> descriptors, int region) {
        List<RatingRegion> ratingRegions = new ArrayList<>();
        for (TsDescriptor descriptor : descriptors) {
            if (!(descriptor instanceof ContentAdvisoryDescriptor)) {
                continue;
            }
            ContentAdvisoryDescriptor contentAdvisoryDescriptor =
                    (ContentAdvisoryDescriptor) descriptor;
            for (RatingRegion ratingRegion : contentAdvisoryDescriptor.getRatingRegions()) {
                if (ratingRegion.getName() == region) {
                    ratingRegions.add(ratingRegion);
                }
            }
        }
        return ratingRegions;
    }

    /**
     * Gets US content rating and subratings (if any).
     *
     * @param ratingRegion a {@link RatingRegion} instance which may contain rating information.
     * @return A string representing the US content rating and subratings. The format of the string
     *     is defined in {@link TvContentRating}. null, if no such a string exists.
     */
    private static String getUsRating(RatingRegion ratingRegion) {
        if (ratingRegion.getName() != RATING_REGION_US_TV) {
            return null;
        }
        List<RegionalRating> regionalRatings = ratingRegion.getRegionalRatings();
        String rating = null;
        int ratingIndex = VALUE_US_TV_NONE;
        List<String> subratings = new ArrayList<>();
        for (RegionalRating index : regionalRatings) {
            // See Table 3 of ANSI-CEA-766-D
            int dimension = index.getDimension();
            int value = index.getRating();
            switch (dimension) {
                    // According to Table 6.27 of ATSC A65,
                    // the dimensions shall be in increasing order.
                    // Therefore, rating and ratingIndex are assigned before any corresponding
                    // subrating.
                case DIMENSION_US_TV_RATING:
                    if (value >= VALUE_US_TV_G && value < RATING_REGION_TABLE_US_TV.length) {
                        rating = RATING_REGION_TABLE_US_TV[value];
                        ratingIndex = value;
                    }
                    break;
                case DIMENSION_US_TV_D:
                    if (value == 1
                            && (ratingIndex == VALUE_US_TV_PG || ratingIndex == VALUE_US_TV_14)) {
                        // US_TV_D is applicable to US_TV_PG and US_TV_14
                        subratings.add(RATING_REGION_TABLE_US_TV_SUBRATING[dimension - 1]);
                    }
                    break;
                case DIMENSION_US_TV_L:
                case DIMENSION_US_TV_S:
                case DIMENSION_US_TV_V:
                    if (value == 1
                            && ratingIndex >= VALUE_US_TV_PG
                            && ratingIndex <= VALUE_US_TV_MA) {
                        // US_TV_L, US_TV_S, and US_TV_V are applicable to
                        // US_TV_PG, US_TV_14 and US_TV_MA
                        subratings.add(RATING_REGION_TABLE_US_TV_SUBRATING[dimension - 1]);
                    }
                    break;
                case DIMENSION_US_TV_Y:
                    if (rating == null) {
                        if (value == VALUE_US_TV_Y) {
                            rating = STRING_US_TV_Y;
                        } else if (value == VALUE_US_TV_Y7) {
                            rating = STRING_US_TV_Y7;
                        }
                    }
                    break;
                case DIMENSION_US_TV_FV:
                    if (STRING_US_TV_Y7.equals(rating) && value == 1) {
                        // US_TV_FV is applicable to US_TV_Y7
                        subratings.add(STRING_US_TV_FV);
                    }
                    break;
                case DIMENSION_US_MV_RATING:
                    if (value >= VALUE_US_MV_G && value <= VALUE_US_MV_X) {
                        if (value == VALUE_US_MV_X) {
                            // US_MV_X was replaced by US_MV_NC17 in 1990,
                            // and it's not supported by TvContentRating
                            value = VALUE_US_MV_NC17;
                        }
                        if (rating != null) {
                            // According to Table 3 of ANSI-CEA-766-D,
                            // DIMENSION_US_TV_RATING and DIMENSION_US_MV_RATING shall not be
                            // present in the same descriptor.
                            Log.w(
                                    TAG,
                                    "DIMENSION_US_TV_RATING and DIMENSION_US_MV_RATING are "
                                            + "present in the same descriptor");
                        } else {
                            return TvContentRating.createRating(
                                            RATING_DOMAIN,
                                            RATING_REGION_RATING_SYSTEM_US_MV,
                                            RATING_REGION_TABLE_US_MV[value - 2])
                                    .flattenToString();
                        }
                    }
                    break;

                default:
                    break;
            }
        }
        if (rating == null) {
            return null;
        }

        String[] subratingArray = subratings.toArray(new String[subratings.size()]);
        return TvContentRating.createRating(
                        RATING_DOMAIN, RATING_REGION_RATING_SYSTEM_US_TV, rating, subratingArray)
                .flattenToString();
    }

    /**
     * Gets KR(South Korea) content rating.
     *
     * @param ratingRegion a {@link RatingRegion} instance which may contain rating information.
     * @return A string representing the KR content rating. The format of the string is defined in
     *     {@link TvContentRating}. null, if no such a string exists.
     */
    private static String getKrRating(RatingRegion ratingRegion) {
        if (ratingRegion.getName() != RATING_REGION_KR_TV) {
            return null;
        }
        List<RegionalRating> regionalRatings = ratingRegion.getRegionalRatings();
        String rating = null;
        for (RegionalRating index : regionalRatings) {
            if (index.getDimension() == 0
                    && index.getRating() >= 0
                    && index.getRating() < RATING_REGION_TABLE_KR_TV.length) {
                rating = RATING_REGION_TABLE_KR_TV[index.getRating()];
                break;
            }
        }
        if (rating == null) {
            return null;
        }
        return TvContentRating.createRating(
                        RATING_DOMAIN, RATING_REGION_RATING_SYSTEM_KR_TV, rating)
                .flattenToString();
    }

    private static String generateBroadcastGenre(List<TsDescriptor> descriptors) {
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof GenreDescriptor) {
                GenreDescriptor genreDescriptor =
                        (GenreDescriptor) descriptor;
                return TextUtils.join(",", genreDescriptor.getBroadcastGenres());
            }
        }
        return null;
    }

    private static String generateCanonicalGenre(List<TsDescriptor> descriptors) {
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof GenreDescriptor) {
                GenreDescriptor genreDescriptor =
                        (GenreDescriptor) descriptor;
                return Genres.encode(genreDescriptor.getCanonicalGenres());
            }
        }
        return null;
    }

    private static List<ServiceDescriptor> generateServiceDescriptors(
            List<TsDescriptor> descriptors) {
        List<ServiceDescriptor> serviceDescriptors = new ArrayList<>();
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof ServiceDescriptor) {
                ServiceDescriptor serviceDescriptor = (ServiceDescriptor) descriptor;
                serviceDescriptors.add(serviceDescriptor);
            }
        }
        return serviceDescriptors;
    }

    private static String generateShortEventName(List<TsDescriptor> descriptors) {
        for (TsDescriptor descriptor : descriptors) {
            if (descriptor instanceof ShortEventDescriptor) {
                ShortEventDescriptor shortEventDescriptor = (ShortEventDescriptor) descriptor;
                return shortEventDescriptor.getEventName();
            }
        }
        return "";
    }

    private static List<TsDescriptor> parseDescriptors(byte[] data, int offset, int limit) {
        // For details of the structure for descriptors, see ATSC A/65 Section 6.9.
        List<TsDescriptor> descriptors = new ArrayList<>();
        if (data.length < limit) {
            return descriptors;
        }
        int pos = offset;
        while (pos + 1 < limit) {
            int tag = data[pos] & 0xff;
            int length = data[pos + 1] & 0xff;
            if (length <= 0) {
                break;
            }
            if (limit < pos + length + 2) {
                break;
            }
            if (DEBUG) {
                Log.d(TAG, String.format("Descriptor tag: %02x", tag));
            }
            TsDescriptor descriptor = null;
            switch (tag) {
                case DESCRIPTOR_TAG_CONTENT_ADVISORY:
                    descriptor = parseContentAdvisory(data, pos, pos + length + 2);
                    break;

                case DESCRIPTOR_TAG_CAPTION_SERVICE:
                    descriptor = parseCaptionService(data, pos, pos + length + 2);
                    break;

                case DESCRIPTOR_TAG_EXTENDED_CHANNEL_NAME:
                    descriptor = parseLongChannelName(data, pos, pos + length + 2);
                    break;

                case DESCRIPTOR_TAG_GENRE:
                    descriptor = parseGenre(data, pos, pos + length + 2);
                    break;

                case DESCRIPTOR_TAG_AC3_AUDIO_STREAM:
                    descriptor = parseAc3AudioStream(data, pos, pos + length + 2);
                    break;

                case DESCRIPTOR_TAG_ISO639LANGUAGE:
                    descriptor = parseIso639Language(data, pos, pos + length + 2);
                    break;

                case DVB_DESCRIPTOR_TAG_SERVICE:
                    descriptor = parseDvbService(data, pos, pos + length + 2);
                    break;

                case DVB_DESCRIPTOR_TAG_SHORT_EVENT:
                    descriptor = parseDvbShortEvent(data, pos, pos + length + 2);
                    break;

                case DVB_DESCRIPTOR_TAG_CONTENT:
                    descriptor = parseDvbContent(data, pos, pos + length + 2);
                    break;

                case DVB_DESCRIPTOR_TAG_PARENTAL_RATING:
                    descriptor = parseDvbParentalRating(data, pos, pos + length + 2);
                    break;

                default:
            }
            if (descriptor != null) {
                if (DEBUG) {
                    Log.d(TAG, "Descriptor parsed: " + descriptor);
                }
                descriptors.add(descriptor);
            }
            pos += length + 2;
        }
        return descriptors;
    }

    private static Iso639LanguageDescriptor parseIso639Language(byte[] data, int pos, int limit) {
        // For the details of the structure of ISO 639 language descriptor,
        // see ISO13818-1 second edition Section 2.6.18.
        pos += 2;
        List<AtscAudioTrack> audioTracks = new ArrayList<>();
        while (pos + 4 <= limit) {
            if (limit <= pos + 3) {
                Log.e(TAG, "Broken Iso639Language.");
                return null;
            }
            String language = new String(data, pos, 3);
            int audioType = data[pos + 3] & 0xff;
            AtscAudioTrack audioTrack = new AtscAudioTrack();
            audioTrack.language = language;
            audioTrack.audioType = audioType;
            audioTracks.add(audioTrack);
            pos += 4;
        }
        return new Iso639LanguageDescriptor(audioTracks);
    }

    private static CaptionServiceDescriptor parseCaptionService(byte[] data, int pos, int limit) {
        // For the details of the structure of caption service descriptor,
        // see ATSC A/65 Section 6.9.2.
        if (limit <= pos + 2) {
            Log.e(TAG, "Broken CaptionServiceDescriptor.");
            return null;
        }
        List<AtscCaptionTrack> services = new ArrayList<>();
        pos += 2;
        int numberServices = data[pos] & 0x1f;
        ++pos;
        if (limit < pos + numberServices * 6) {
            Log.e(TAG, "Broken CaptionServiceDescriptor.");
            return null;
        }
        for (int i = 0; i < numberServices; ++i) {
            String language = new String(Arrays.copyOfRange(data, pos, pos + 3));
            pos += 3;
            boolean ccType = (data[pos] & 0x80) != 0;
            if (!ccType) {
                pos +=3;
                continue;
            }
            int captionServiceNumber = data[pos] & 0x3f;
            ++pos;
            boolean easyReader = (data[pos] & 0x80) != 0;
            boolean wideAspectRatio = (data[pos] & 0x40) != 0;
            byte[] reserved = new byte[2];
            reserved[0] = (byte) (data[pos] << 2);
            reserved[0] |= (byte) ((data[pos + 1] & 0xc0) >>> 6);
            reserved[1] = (byte) ((data[pos + 1] & 0x3f) << 2);
            pos += 2;
            AtscCaptionTrack captionTrack = new AtscCaptionTrack();
            captionTrack.language = language;
            captionTrack.serviceNumber = captionServiceNumber;
            captionTrack.easyReader = easyReader;
            captionTrack.wideAspectRatio = wideAspectRatio;
            services.add(captionTrack);
        }
        return new CaptionServiceDescriptor(services);
    }

    private static ContentAdvisoryDescriptor parseContentAdvisory(byte[] data, int pos, int limit) {
        // For details of the structure for content advisory descriptor, see A/65 Table 6.27.
        if (limit <= pos + 2) {
            Log.e(TAG, "Broken ContentAdvisory");
            return null;
        }
        int count = data[pos + 2] & 0x3f;
        pos += 3;
        List<RatingRegion> ratingRegions = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            if (limit <= pos + 1) {
                Log.e(TAG, "Broken ContentAdvisory");
                return null;
            }
            List<RegionalRating> indices = new ArrayList<>();
            int ratingRegion = data[pos] & 0xff;
            int dimensionCount = data[pos + 1] & 0xff;
            pos += 2;
            int previousDimension = -1;
            for (int j = 0; j < dimensionCount; ++j) {
                if (limit <= pos + 1) {
                    Log.e(TAG, "Broken ContentAdvisory");
                    return null;
                }
                int dimensionIndex = data[pos] & 0xff;
                int ratingValue = data[pos + 1] & 0x0f;
                if (dimensionIndex <= previousDimension) {
                    // According to Table 6.27 of ATSC A65,
                    // the indices shall be in increasing order.
                    Log.e(TAG, "Broken ContentAdvisory");
                    return null;
                }
                previousDimension = dimensionIndex;
                pos += 2;
                indices.add(new RegionalRating(dimensionIndex, ratingValue));
            }
            if (limit <= pos) {
                Log.e(TAG, "Broken ContentAdvisory");
                return null;
            }
            int ratingDescriptionLength = data[pos] & 0xff;
            ++pos;
            if (limit < pos + ratingDescriptionLength) {
                Log.e(TAG, "Broken ContentAdvisory");
                return null;
            }
            String ratingDescription = extractText(data, pos);
            pos += ratingDescriptionLength;
            ratingRegions.add(new RatingRegion(ratingRegion, ratingDescription, indices));
        }
        return new ContentAdvisoryDescriptor(ratingRegions);
    }

    private static ExtendedChannelNameDescriptor parseLongChannelName(byte[] data, int pos,
            int limit) {
        if (limit <= pos + 2) {
            Log.e(TAG, "Broken ExtendedChannelName.");
            return null;
        }
        pos += 2;
        String text = extractText(data, pos);
        if (text == null) {
            Log.e(TAG, "Broken ExtendedChannelName.");
            return null;
        }
        return new ExtendedChannelNameDescriptor(text);
    }

    private static GenreDescriptor parseGenre(byte[] data, int pos, int limit) {
        pos += 2;
        int attributeCount = data[pos] & 0x1f;
        if (limit <= pos + attributeCount) {
            Log.e(TAG, "Broken Genre.");
            return null;
        }
        HashSet<String> broadcastGenreSet = new HashSet<>();
        HashSet<String> canonicalGenreSet = new HashSet<>();
        for (int i = 0; i < attributeCount; ++i) {
            ++pos;
            int genreCode = data[pos] & 0xff;
            if (genreCode < BROADCAST_GENRES_TABLE.length) {
                String broadcastGenre = BROADCAST_GENRES_TABLE[genreCode];
                if (broadcastGenre != null && !broadcastGenreSet.contains(broadcastGenre)) {
                    broadcastGenreSet.add(broadcastGenre);
                }
            }
            if (genreCode < CANONICAL_GENRES_TABLE.length) {
                String canonicalGenre = CANONICAL_GENRES_TABLE[genreCode];
                if (canonicalGenre != null && !canonicalGenreSet.contains(canonicalGenre)) {
                    canonicalGenreSet.add(canonicalGenre);
                }
            }
        }
        return new GenreDescriptor(broadcastGenreSet.toArray(new String[broadcastGenreSet.size()]),
                canonicalGenreSet.toArray(new String[canonicalGenreSet.size()]));
    }

    private static TsDescriptor parseAc3AudioStream(byte[] data, int pos, int limit) {
        // For details of the AC3 audio stream descriptor, see A/52 Table A4.1.
        if (limit <= pos + 5) {
            Log.e(TAG, "Broken AC3 audio stream descriptor.");
            return null;
        }
        pos += 2;
        byte sampleRateCode = (byte) ((data[pos] & 0xe0) >> 5);
        byte bsid = (byte) (data[pos] & 0x1f);
        ++pos;
        byte bitRateCode = (byte) ((data[pos] & 0xfc) >> 2);
        byte surroundMode = (byte) (data[pos] & 0x03);
        ++pos;
        byte bsmod = (byte) ((data[pos] & 0xe0) >> 5);
        int numChannels = (data[pos] & 0x1e) >> 1;
        boolean fullSvc = (data[pos] & 0x01) != 0;
        ++pos;
        byte langCod = data[pos];
        byte langCod2 = 0;
        if (numChannels == 0) {
            if (limit <= pos) {
                Log.e(TAG, "Broken AC3 audio stream descriptor.");
                return null;
            }
            ++pos;
            langCod2 = data[pos];
        }
        if (limit <= pos + 1) {
            Log.e(TAG, "Broken AC3 audio stream descriptor.");
            return null;
        }
        byte mainId = 0;
        byte priority = 0;
        byte asvcflags = 0;
        ++pos;
        if (bsmod < 2) {
            mainId = (byte) ((data[pos] & 0xe0) >> 5);
            priority = (byte) ((data[pos] & 0x18) >> 3);
            if ((data[pos] & 0x07) != 0x07) {
                Log.e(TAG, "Broken AC3 audio stream descriptor reserved failed");
                return null;
            }
        } else {
            asvcflags = data[pos];
        }

        // See A/52B Table A3.6 num_channels.
        int numEncodedChannels;
        switch (numChannels) {
            case 1:
            case 8:
                numEncodedChannels = 1;
                break;
            case 2:
            case 9:
                numEncodedChannels = 2;
                break;
            case 3:
            case 4:
            case 10:
                numEncodedChannels = 3;
                break;
            case 5:
            case 6:
            case 11:
                numEncodedChannels = 4;
                break;
            case 7:
            case 12:
                numEncodedChannels = 5;
                break;
            case 13:
                numEncodedChannels = 6;
                break;
            default:
                numEncodedChannels = 0;
                break;
        }

        if (limit <= pos + 1) {
            Log.w(TAG, "Missing text and language fields on AC3 audio stream descriptor.");
            return new Ac3AudioDescriptor(sampleRateCode, bsid, bitRateCode, surroundMode, bsmod,
                    numEncodedChannels, fullSvc, langCod, langCod2, mainId, priority, asvcflags,
                    null, null, null);
        }
        ++pos;
        int textLen = (data[pos] & 0xfe) >> 1;
        boolean textCode = (data[pos] & 0x01) != 0;
        ++pos;
        String text = "";
        if (textLen > 0) {
            if (limit < pos + textLen) {
                Log.e(TAG, "Broken AC3 audio stream descriptor");
                return null;
            }
            if (textCode) {
                text = new String(data, pos, textLen);
            } else {
                text = new String(data, pos, textLen, Charset.forName("UTF-16"));
            }
            pos += textLen;
        }
        String language = null;
        String language2 = null;
        if (pos < limit) {
            // Many AC3 audio stream descriptors skip the language fields.
            boolean languageFlag1 = (data[pos] & 0x80) != 0;
            boolean languageFlag2 = (data[pos] & 0x40) != 0;
            if ((data[pos] & 0x3f) != 0x3f) {
                Log.e(TAG, "Broken AC3 audio stream descriptor");
                return null;
            }
            if (pos + (languageFlag1 ? 3 : 0) + (languageFlag2 ? 3 : 0) > limit) {
                Log.e(TAG, "Broken AC3 audio stream descriptor");
                return null;
            }
            ++pos;
            if (languageFlag1) {
                language = new String(data, pos, 3);
                pos += 3;
            }
            if (languageFlag2) {
                language2 = new String(data, pos, 3);
            }
        }

        return new Ac3AudioDescriptor(sampleRateCode, bsid, bitRateCode, surroundMode, bsmod,
                numEncodedChannels, fullSvc, langCod, langCod2, mainId, priority, asvcflags, text,
                language, language2);
    }

    private static TsDescriptor parseDvbService(byte[] data, int pos, int limit) {
        // For details of DVB service descriptors, see DVB Document A038 Table 86.
        if (limit < pos + 5) {
            Log.e(TAG, "Broken service descriptor.");
            return null;
        }
        pos += 2;
        int serviceType = data[pos] & 0xff;
        pos++;
        int serviceProviderNameLength = data[pos] & 0xff;
        pos++;
        String serviceProviderName = extractTextFromDvb(data, pos, serviceProviderNameLength);
        pos += serviceProviderNameLength;
        int serviceNameLength = data[pos] & 0xff;
        pos++;
        String serviceName = extractTextFromDvb(data, pos, serviceNameLength);
        return new ServiceDescriptor(serviceType, serviceProviderName, serviceName);
    }

    private static TsDescriptor parseDvbShortEvent(byte[] data, int pos, int limit) {
        // For details of DVB service descriptors, see DVB Document A038 Table 91.
        if (limit < pos + 7) {
            Log.e(TAG, "Broken short event descriptor.");
            return null;
        }
        pos += 2;
        String language = new String(data, pos, 3);
        int eventNameLength = data[pos + 3] & 0xff;
        pos += 4;
        if (pos + eventNameLength > limit) {
            Log.e(TAG, "Broken short event descriptor.");
            return null;
        }
        String eventName = new String(data, pos, eventNameLength);
        pos += eventNameLength;
        int textLength = data[pos] & 0xff;
        if (pos + textLength > limit) {
            Log.e(TAG, "Broken short event descriptor.");
            return null;
        }
        pos++;
        String text = new String(data, pos, textLength);
        return new ShortEventDescriptor(language, eventName, text);
    }

    private static TsDescriptor parseDvbContent(byte[] data, int pos, int limit) {
        // TODO: According to DVB Document A038 Table 27 to add a parser for content descriptor to
        // get content genre.
        return null;
    }

    private static TsDescriptor parseDvbParentalRating(byte[] data, int pos, int limit) {
        // For details of DVB service descriptors, see DVB Document A038 Table 81.
        HashMap<String, Integer> ratings = new HashMap<>();
        pos += 2;
        while (pos + 4 <= limit) {
            String countryCode = new String(data, pos, 3);
            int rating = data[pos + 3] & 0xff;
            pos += 4;
            if (rating > 15) {
                // Rating > 15 means that the ratings is defined by broadcaster.
                continue;
            }
            ratings.put(countryCode, rating + 3);
        }
        return new ParentalRatingDescriptor(ratings);
    }

    private static int getShortNameSize(byte[] data, int offset) {
        for (int i = 0; i < MAX_SHORT_NAME_BYTES; i += 2) {
            if (data[offset + i] == 0 && data[offset + i + 1] == 0) {
                return i;
            }
        }
        return MAX_SHORT_NAME_BYTES;
    }

    private static String extractText(byte[] data, int pos) {
        if (data.length < pos)  {
            return null;
        }
        int numStrings = data[pos] & 0xff;
        pos++;
        for (int i = 0; i < numStrings; ++i) {
            if (data.length <= pos + 3) {
                Log.e(TAG, "Broken text.");
                return null;
            }
            int numSegments = data[pos + 3] & 0xff;
            pos += 4;
            for (int j = 0; j < numSegments; ++j) {
                if (data.length <= pos + 2) {
                    Log.e(TAG, "Broken text.");
                    return null;
                }
                int compressionType = data[pos] & 0xff;
                int mode = data[pos + 1] & 0xff;
                int numBytes = data[pos + 2] & 0xff;
                if (data.length < pos + 3 + numBytes) {
                    Log.e(TAG, "Broken text.");
                    return null;
                }
                byte[] bytes = Arrays.copyOfRange(data, pos + 3, pos + 3 + numBytes);
                if (compressionType == COMPRESSION_TYPE_NO_COMPRESSION) {
                    try {
                        switch (mode) {
                            case MODE_SELECTED_UNICODE_RANGE_1:
                                return new String(bytes, "ISO-8859-1");
                            case MODE_SCSU:
                                return UnicodeDecompressor.decompress(bytes);
                            case MODE_UTF16:
                                return new String(bytes, "UTF-16");
                        }
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Unsupported text format.", e);
                    }
                }
                pos += 3 + numBytes;
            }
        }
        return null;
    }

    private static String extractTextFromDvb(byte[] data, int pos, int length) {
        // For details of DVB character set selection, see DVB Document A038 Annex A.
        if (data.length < pos + length) {
            return null;
        }
        try {
            String charsetPrefix = "ISO-8859-";
            switch (data[0]) {
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05:
                case 0x06:
                case 0x07:
                case 0x09:
                case 0x0A:
                case 0x0B:
                    String charset = charsetPrefix + String.valueOf(data[0] & 0xff + 4);
                    return new String(data, pos, length, charset);
                case 0x10:
                    if (length < 3) {
                        Log.e(TAG, "Broken DVB text");
                        return null;
                    }
                    int codeTable = data[pos + 2] & 0xff;
                    if (data[pos + 1] == 0 && codeTable > 0 && codeTable < 15) {
                        return new String(
                                data, pos, length, charsetPrefix + String.valueOf(codeTable));
                    } else {
                        return new String(data, pos, length, "ISO-8859-1");
                    }
                case 0x11:
                case 0x14:
                case 0x15:
                    return new String(data, pos, length, "UTF-16BE");
                case 0x12:
                    return new String(data, pos, length, "EUC-KR");
                case 0x13:
                    return new String(data, pos, length, "GB2312");
                default:
                    return new String(data, pos, length, "ISO-8859-1");
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unsupported text format.", e);
        }
        return new String(data, pos, length);
    }

    private static boolean checkSanity(byte[] data) {
        if (data.length <= 1) {
            return false;
        }
        boolean hasCRC = (data[1] & 0x80) != 0; // section_syntax_indicator
        if (hasCRC) {
            int crc = 0xffffffff;
            for(byte b : data) {
                int index = ((crc >> 24) ^ (b & 0xff)) & 0xff;
                crc = CRC_TABLE[index] ^ (crc << 8);
            }
            if(crc != 0){
                return false;
            }
        }
        return true;
    }
}
