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
 * limitations under the License.
 */

package android.text.cts;

import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Paint;
import android.icu.lang.UCharacter;
import android.icu.lang.UCharacterCategory;
import android.icu.text.UnicodeSet;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Locale;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FontCoverageTest {
    // All characters in ASCII, Latin 1, Latin Extended A to D, and Currency Symbols block, as well
    // as all Latin, Greek and Cyrillic characters. Limited to Unicode 7.0.
    private static final UnicodeSet MIN_LGC = new UnicodeSet(
            "[[[:Block=ASCII:]"
            + "[:Block=Latin_1:]"
            + "[:Block=Latin_Extended_A:]"
            + "[:Block=Latin_Extended_B:]"
            + "[:Block=Latin_Extended_C:]"
            + "[:Block=Latin_Extended_D:]"
            + "[:Block=Currency_Symbols:]"
            + "[:Script=Latin:]"
            + "[:Script=Greek:]"
            + "[:Script=Cyrillic:]]"
            + "&[:Age=7.0:]"
            // Skip the blocks for ancient numbers and symbols.
            + "&[:^Block=Ancient_Greek_Numbers:]&[:^Block=Ancient_Symbols:]]");

    private static final UnicodeSet MIN_EXTRAS = new UnicodeSet(
            "[\u0300" // COMBINING GRAVE ACCENT
            + "\u0301" // COMBINING ACUTE ACCENT
            + "\u0307" // COMBINING DOT ABOVE
            + "\u0308" // COMBINING DIAERESIS
            + "\u0313" // COMBINING COMMA ABOVE
            + "\u0342" // COMBINING GREEK PERISPOMENI
            + "\u0345" // COMBINING GREEK YPOGEGRAMMENI
            + "\u2010" // HYPHEN
            + "\u2013" // EN DASH
            + "\u2014" // EM DASH
            + "\u2018" // LEFT SINGLE QUOTATION MARK
            + "\u2019" // RIGHT SINGLE QUOTATION MARK
            + "\u201A" // SINGLE LOW-9 QUOTATION MARK
            + "\u201C" // LEFT DOUBLE QUOTATION MARK
            + "\u201D" // RIGHT DOUBLE QUOTATION MARK
            + "\u201E" // DOUBLE LOW-9 QUOTATION MARK
            + "\u2020" // DAGGER
            + "\u2021" // DOUBLE DAGGER
            + "\u2026" // HORIZONTAL ELLIPSIS
            + "\u2032" // PRIME
            + "\u2033" // DOUBLE PRIME
            + "\u2212]"); // MINUS SIGN

    private static final UnicodeSet MIN_COVERAGE = new UnicodeSet(MIN_LGC).addAll(MIN_EXTRAS);

    // Characters outside of MIN_COVERAGE that are needed for some locales.
    private static final HashMap<String, UnicodeSet> EXEMPLAR_MAP = new HashMap();
    static {
        EXEMPLAR_MAP.put("agq", new UnicodeSet("[{\u0186\u0302}{\u0186\u0304}{\u0186\u030C}"
                + "{\u0190\u0302}{\u0190\u0304}{\u0190\u030C}{\u0197\u0302}{\u0197\u0304}"
                + "{\u0197\u030C}{\u0244\u0302}{\u0244\u0304}{\u0244\u030C}{\u0254\u0302}"
                + "{\u0254\u0304}{\u0254\u030C}{\u025B\u0302}{\u025B\u0304}{\u025B\u030C}"
                + "{\u0268\u0302}{\u0268\u0304}{\u0268\u030C}{\u0289\u0302}{\u0289\u0304}"
                + "{\u0289\u030C}]"));
        EXEMPLAR_MAP.put("am", new UnicodeSet("[\u1200-\u1206\u1208-\u1246\u1248\u124A-\u124D"
                + "\u1260-\u1286\u1288\u128A-\u128D\u1290-\u12AE\u12B0\u12B2-\u12B5\u12B8-\u12BE"
                + "\u12C8-\u12CE\u12D0-\u12D6\u12D8-\u12EE\u12F0-\u12F7\u1300-\u130E\u1310"
                + "\u1312-\u1315\u1320-\u1346\u1348-\u1357\u1361-\u1366\u2039\u203A]"));
        EXEMPLAR_MAP.put("ar", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u0660-\u066C\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5"
                + "\u06A7-\u06A9\u06AF\u06CC]"));
        EXEMPLAR_MAP.put("ar-DZ", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5\u06A7-\u06A9\u06AF"
                + "\u06CC]"));
        EXEMPLAR_MAP.put("ar-EH", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5\u06A7-\u06A9\u06AF"
                + "\u06CC]"));
        EXEMPLAR_MAP.put("ar-LY", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5\u06A7-\u06A9\u06AF"
                + "\u06CC]"));
        EXEMPLAR_MAP.put("ar-MA", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5\u06A7-\u06A9\u06AD\u06AF"
                + "\u06CC\u0763]"));
        EXEMPLAR_MAP.put("ar-TN", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u066F\u0670\u067E\u0686\u0698\u069C\u06A2\u06A4\u06A5\u06A7-\u06A9\u06AF"
                + "\u06CC]"));
        EXEMPLAR_MAP.put("as", new UnicodeSet("[\u0981-\u0983\u0985-\u098B\u098F\u0990"
                + "\u0993-\u09A8\u09AA-\u09AF\u09B2\u09B6-\u09B9\u09BC\u09BE-\u09C3\u09C7\u09C8"
                + "\u09CB-\u09CD\u09E6-\u09F2{\u0995\u09CD\u09B7}{\u09A1\u09BC}"
                + "{\u09A2\u09BC}{\u09AF\u09BC}]"));
        EXEMPLAR_MAP.put("bas", new UnicodeSet("[{A\u1DC6}{A\u1DC7}{E\u1DC6}{E\u1DC7}{I\u1DC6}"
                + "{I\u1DC7}{O\u1DC6}{O\u1DC7}{U\u1DC6}{U\u1DC7}{a\u1DC6}{a\u1DC7}{e\u1DC6}"
                + "{e\u1DC7}{i\u1DC6}{i\u1DC7}{o\u1DC6}{o\u1DC7}{u\u1DC6}{u\u1DC7}{\u0186\u0302}"
                + "{\u0186\u0304}{\u0186\u030C}{\u0186\u1DC6}{\u0186\u1DC7}{\u0190\u0302}"
                + "{\u0190\u0304}{\u0190\u030C}{\u0190\u1DC6}{\u0190\u1DC7}{\u0254\u0302}"
                + "{\u0254\u0304}{\u0254\u030C}{\u0254\u1DC6}{\u0254\u1DC7}{\u025B\u0302}"
                + "{\u025B\u0304}{\u025B\u030C}{\u025B\u1DC6}{\u025B\u1DC7}]"));
        EXEMPLAR_MAP.put("bg", new UnicodeSet("[\u2116]"));
        EXEMPLAR_MAP.put("bn", new UnicodeSet("[\u0981-\u0983\u0985-\u098C\u098F\u0990"
                + "\u0993-\u09A8\u09AA-\u09B0\u09B2\u09B6-\u09B9\u09BC-\u09C4\u09C7\u09C8"
                + "\u09CB-\u09CE\u09D7\u09E0-\u09E3\u09E6-\u09FA{\u0995\u09CD\u09B7}{\u09A1\u09BC}"
                + "{\u09A2\u09BC}{\u09AF\u09BC}]"));
        EXEMPLAR_MAP.put("bo", new UnicodeSet("[\u0F00\u0F40-\u0F42\u0F44-\u0F47\u0F49-\u0F4C"
                + "\u0F4E-\u0F51\u0F53-\u0F56\u0F58-\u0F5B\u0F5D-\u0F68\u0F6A\u0F72\u0F74\u0F77"
                + "\u0F79-\u0F80\u0F84\u0F90-\u0F92\u0F94-\u0F97\u0F99-\u0F9C\u0F9E-\u0FA1"
                + "\u0FA3-\u0FA6\u0FA8-\u0FAB\u0FAD-\u0FB8\u0FBA-\u0FBC{\u0F40\u0FB5}{\u0F42\u0FB7}"
                + "{\u0F4C\u0FB7}{\u0F51\u0FB7}{\u0F56\u0FB7}{\u0F5B\u0FB7}{\u0F71\u0F72}"
                + "{\u0F71\u0F74}{\u0F71\u0F80}{\u0F90\u0FB5}{\u0F92\u0FB7}{\u0F9C\u0FB7}"
                + "{\u0FA1\u0FB7}{\u0FA6\u0FB7}{\u0FAB\u0FB7}{\u0FB2\u0F80}{\u0FB3\u0F80}]"));
        EXEMPLAR_MAP.put("br", new UnicodeSet("[{C\u02BCH}{C\u02BCh}{c\u02BCh}]"));
        EXEMPLAR_MAP.put("brx", new UnicodeSet("[\u0901\u0902\u0905-\u090A\u090D\u090F-\u0911"
                + "\u0913-\u0918\u091A-\u0928\u092A-\u0930\u0932\u0933\u0935-\u0939\u093C"
                + "\u093E-\u0943\u0945\u0947-\u0949\u094B-\u094D{\u0921\u093C}]"));
        EXEMPLAR_MAP.put("chr", new UnicodeSet("[\u13A0-\u13F4\u13F8-\u13FC\uAB70-\uABBF]"));
        EXEMPLAR_MAP.put("dz", new UnicodeSet("[\u0F04-\u0F06\u0F08-\u0F0A\u0F0C-\u0F12\u0F14"
                + "\u0F20-\u0F29\u0F34\u0F36\u0F3C\u0F3D\u0F40-\u0F42\u0F44-\u0F47\u0F49-\u0F4C"
                + "\u0F4E-\u0F51\u0F53-\u0F56\u0F58-\u0F5B\u0F5D-\u0F68\u0F72\u0F74\u0F7A-\u0F7E"
                + "\u0F80\u0F84\u0F90-\u0F92\u0F94\u0F97\u0F99-\u0F9C\u0F9E-\u0FA1\u0FA3-\u0FA6"
                + "\u0FA8-\u0FAB\u0FAD\u0FB1-\u0FB3\u0FB5-\u0FB7\u0FBA-\u0FBC\u0FBE\u0FBF"
                + "\u0FD0-\u0FD4]"));
        EXEMPLAR_MAP.put("ee", new UnicodeSet("[{\u0186\u0303}{\u0190\u0303}{\u0254\u0303}"
                + "{\u025B\u0303}]"));
        EXEMPLAR_MAP.put("ewo", new UnicodeSet("[{\u0186\u0302}{\u0186\u030C}{\u018F\u0302}"
                + "{\u018F\u030C}{\u0190\u0302}{\u0190\u030C}{\u0254\u0302}{\u0254\u030C}"
                + "{\u0259\u0302}{\u0259\u030C}{\u025B\u0302}{\u025B\u030C}]"));
        EXEMPLAR_MAP.put("fa-AF", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u0654\u0656\u066A-\u066C\u0670\u067C\u067E\u0681\u0685\u0686\u0689\u0693\u0696"
                + "\u0698\u069A\u06A9\u06AB\u06AF\u06BC\u06CC\u06F0-\u06F9\u2039\u203A]"));
        EXEMPLAR_MAP.put("fa-IR", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u0654\u0656\u066A-\u066C\u0670\u067E\u0686\u0698\u06A9\u06AF\u06CC\u06F0-\u06F9"
                + "\u2039\u203A{\u200E\u066A}]"));
        EXEMPLAR_MAP.put("gu", new UnicodeSet("[\u0A81-\u0A83\u0A85-\u0A8B\u0A8D\u0A8F-\u0A91"
                + "\u0A93-\u0AA8\u0AAA-\u0AB0\u0AB2\u0AB3\u0AB5-\u0AB9\u0ABC-\u0AC5\u0AC7-\u0AC9"
                + "\u0ACB-\u0ACD\u0AD0\u0AE0\u0AF0{\u0A85\u0A82}{\u0A85\u0A83}"
                + "{\u0A95\u0ACD\u0AB7}{\u0A9C\u0ACD\u0A9E}{\u0AA4\u0ACD\u0AB0}]"));
        EXEMPLAR_MAP.put("ha", new UnicodeSet("[\u02BC{R\u0303}{r\u0303}{\u02BCY}{\u02BCy}]"));
        EXEMPLAR_MAP.put("haw", new UnicodeSet("[\u02BB]"));
        EXEMPLAR_MAP.put("he", new UnicodeSet("[\u05B0-\u05B9\u05BB-\u05BF\u05C1\u05C2\u05C4"
                + "\u05D0-\u05EA\u05F3\u05F4]"));
        EXEMPLAR_MAP.put("hi", new UnicodeSet("[\u0901-\u0903\u0905-\u090D\u090F-\u0911"
                + "\u0913-\u0928\u092A-\u0930\u0932\u0933\u0935-\u0939\u093C-\u0945\u0947-\u0949"
                + "\u094B-\u094D\u0950\u0970]"));
        EXEMPLAR_MAP.put("hu", new UnicodeSet("[\u2052\u27E8\u27E9]"));
        EXEMPLAR_MAP.put("hy", new UnicodeSet("[\u0531-\u0556\u055A-\u055F\u0561-\u0587\u058A]"));
        EXEMPLAR_MAP.put("ii", new UnicodeSet("[\uA000-\uA48C]"));
        EXEMPLAR_MAP.put("jgo", new UnicodeSet("[\u2039\u203A{M\u0304}{N\u0304}{m\u0304}{n\u0304}"
                + "{\u014A\u0304}{\u014B\u0304}{\u0186\u0302}{\u0186\u030C}{\u0190\u0302}"
                + "{\u0190\u0304}{\u0190\u030C}{\u0244\u0302}{\u0244\u030C}{\u0254\u0302}"
                + "{\u0254\u030C}{\u025B\u0302}{\u025B\u0304}{\u025B\u030C}{\u0289\u0302}"
                + "{\u0289\u030C}]"));
        EXEMPLAR_MAP.put("ja", new UnicodeSet("[\u2015\u2016\u2025\u2030\u203B\u203E\u3001-\u3003"
                + "\u3005\u3008-\u3011\u3014\u3015\u301C\u3041-\u3093\u309D\u309E\u30A1-\u30F6"
                + "\u30FB-\u30FE\u4E00\u4E01\u4E03\u4E07-\u4E0B\u4E0D\u4E0E\u4E11\u4E14\u4E16\u4E18"
                + "\u4E19\u4E21\u4E26\u4E2D\u4E38\u4E39\u4E3B\u4E45\u4E4F\u4E57"
                + "\u4E59\u4E5D\u4E71\u4E73\u4E7E\u4E80\u4E86\u4E88\u4E89\u4E8B"
                + "\u4E8C\u4E92\u4E94\u4E95\u4E9C\u4EA1\u4EA4\u4EA5\u4EA8\u4EAB-\u4EAD"
                + "\u4EBA\u4EC1\u4ECA\u4ECB\u4ECF\u4ED5\u4ED6\u4ED8\u4ED9\u4EE3-\u4EE5"
                + "\u4EEE\u4EF0\u4EF2\u4EF6\u4EFB\u4F01\u4F0A\u4F0F-\u4F11\u4F1A\u4F1D"
                + "\u4F2F\u4F34\u4F38\u4F3A\u4F3C\u4F46\u4F4D-\u4F50\u4F53\u4F55"
                + "\u4F59\u4F5C\u4F73\u4F75\u4F7F\u4F8B\u4F8D\u4F9B\u4F9D\u4FA1"
                + "\u4FAE\u4FAF\u4FB5\u4FBF\u4FC2\u4FC3\u4FCA\u4FD7\u4FDD\u4FE1"
                + "\u4FEE\u4FF3\u4FF5\u4FF8\u4FFA\u5009\u500B\u500D\u5012\u5019"
                + "\u501F\u5023\u5024\u502B\u5039\u5049\u504F\u505C\u5065\u5074-\u5076"
                + "\u507D\u508D\u5091\u5098\u5099\u50AC\u50B5\u50B7\u50BE\u50CD"
                + "\u50CF\u50D5\u50DA\u50E7\u5100\u5104\u5112\u511F\u512A\u5143-\u5146"
                + "\u5148\u5149\u514B-\u514E\u5150\u515A\u5165\u5168\u516B-\u516D"
                + "\u5171\u5175\u5177\u5178\u517C\u5185\u5186\u518A\u518D\u5192"
                + "\u5197\u5199\u51A0\u51AC\u51B7\u51C6\u51CD\u51DD\u51E1\u51E6"
                + "\u51F6\u51F8-\u51FA\u5200\u5203\u5206-\u5208\u520A\u5211\u5217\u521D"
                + "\u5224\u5225\u5229\u5230\u5236-\u5238\u523A\u523B\u5247\u524A"
                + "\u524D\u5256\u525B\u5263\u5264\u526F\u5270\u5272\u5275\u5287"
                + "\u529B\u529F\u52A0\u52A3\u52A9\u52AA\u52B1\u52B4\u52B9\u52BE"
                + "\u52C5\u52C7\u52C9\u52D5\u52D8\u52D9\u52DD\u52DF\u52E2\u52E4"
                + "\u52E7\u52F2\u52FA\u5301\u5305\u5316\u5317\u5320\u5339-\u533B"
                + "\u533F\u5341\u5343\u5347\u5348\u534A\u5351-\u5354\u5357\u5358"
                + "\u535A\u5360\u536F-\u5371\u5373-\u5375\u5378\u5384\u5398\u539A"
                + "\u539F\u53B3\u53BB\u53C2\u53C8\u53CA-\u53CE\u53D4\u53D6\u53D7\u53D9"
                + "\u53E3-\u53E5\u53EB\u53EC\u53EF\u53F0\u53F2\u53F3\u53F7\u53F8"
                + "\u5404\u5408\u5409\u540C-\u5411\u541B\u541F\u5426\u542B\u5438\u5439"
                + "\u5448-\u544A\u5468\u5473\u547C\u547D\u548C\u54B2\u54C0\u54C1"
                + "\u54E1\u54F2\u5506\u5507\u5510\u552F\u5531\u5546\u554F\u5553"
                + "\u5584\u559A\u559C\u559D\u55AA\u55AB\u55B6\u55E3\u5606\u5609"
                + "\u5631\u5668\u5674\u5687\u56DA\u56DB\u56DE\u56E0\u56E3\u56F0"
                + "\u56F2\u56F3\u56FA\u56FD\u570F\u5712\u571F\u5727\u5728\u5730"
                + "\u5742\u5747\u574A\u5751\u576A\u5782\u578B\u57A3\u57CB\u57CE"
                + "\u57DF\u57F7\u57F9\u57FA\u57FC\u5800\u5802\u5805\u5815\u5824"
                + "\u582A\u5831\u5834\u5840\u5841\u584A\u5851\u5854\u5857\u585A"
                + "\u5869\u587E\u5883\u5893\u5897\u589C\u58A8\u58B3\u58BE\u58C1"
                + "\u58C7\u58CA\u58CC\u58EB\u58EC\u58EE\u58F0-\u58F2\u5909\u590F"
                + "\u5915\u5916\u591A\u591C\u5922\u5927\u5929-\u592B\u592E\u5931"
                + "\u5947-\u5949\u594F\u5951\u5954\u5965\u5968\u596A\u596E\u5973"
                + "\u5974\u597D\u5982-\u5984\u598A\u5999\u59A5\u59A8\u59B9\u59BB"
                + "\u59C9\u59CB\u59D3\u59D4\u59EB\u59FB\u59FF\u5A01\u5A18\u5A20"
                + "\u5A2F\u5A46\u5A5A\u5A66\u5A7F\u5A92\u5AC1\u5ACC\u5AE1\u5B22"
                + "\u5B50\u5B54\u5B57\u5B58\u5B5D\u5B63\u5B64\u5B66\u5B6B\u5B85"
                + "\u5B87-\u5B89\u5B8C\u5B97-\u5B9A\u5B9C\u5B9D\u5B9F\u5BA2-\u5BA4"
                + "\u5BAE\u5BB0\u5BB3-\u5BB6\u5BB9\u5BBF\u5BC2\u5BC4-\u5BC6\u5BCC\u5BD2"
                + "\u5BDB\u5BDD\u5BDF\u5BE1\u5BE7\u5BE9\u5BEE\u5BF8\u5BFA\u5BFE"
                + "\u5BFF\u5C01\u5C02\u5C04\u5C06\u5C09-\u5C0B\u5C0E\u5C0F\u5C11\u5C1A"
                + "\u5C31\u5C3A\u5C3C-\u5C40\u5C45\u5C48\u5C4A\u5C4B\u5C55\u5C5E"
                + "\u5C64\u5C65\u5C6F\u5C71\u5C90\u5CA1\u5CA9\u5CAC\u5CB3\u5CB8"
                + "\u5CE0\u5CE1\u5CF0\u5CF6\u5D07\u5D0E\u5D29\u5DDD\u5DDE\u5DE1"
                + "\u5DE3\u5DE5-\u5DE8\u5DEE\u5DF1\u5DF3\u5DFB\u5E02\u5E03\u5E06\u5E0C"
                + "\u5E1D\u5E25\u5E2B\u5E2D\u5E2F\u5E30\u5E33\u5E38\u5E3D\u5E45"
                + "\u5E55\u5E63\u5E72-\u5E74\u5E78\u5E79\u5E7B-\u5E7E\u5E81\u5E83"
                + "\u5E8A\u5E8F\u5E95\u5E97\u5E9A\u5E9C\u5EA6\u5EA7\u5EAB\u5EAD"
                + "\u5EB6-\u5EB8\u5EC3\u5EC9\u5ECA\u5EF6\u5EF7\u5EFA\u5F01\u5F0A"
                + "\u5F0F\u5F10\u5F13-\u5F15\u5F18\u5F1F\u5F26\u5F27\u5F31\u5F35"
                + "\u5F37\u5F3E\u5F53\u5F62\u5F69\u5F6B\u5F70\u5F71\u5F79\u5F7C"
                + "\u5F80\u5F81\u5F84\u5F85\u5F8B\u5F8C\u5F90\u5F92\u5F93\u5F97"
                + "\u5FA1\u5FA9\u5FAA\u5FAE\u5FB3\u5FB4\u5FB9\u5FC3\u5FC5\u5FCC"
                + "\u5FCD\u5FD7-\u5FD9\u5FDC\u5FE0\u5FEB\u5FF5\u6012\u6016\u601D\u6020"
                + "\u6025\u6027\u602A\u604B\u6050\u6052\u6065\u6068\u6069\u606D"
                + "\u606F\u6075\u6094\u609F\u60A0\u60A3\u60A6\u60A9\u60AA\u60B2"
                + "\u60BC\u60C5\u60D1\u60DC\u60E8\u60F0\u60F3\u6101\u6109\u610F"
                + "\u611A\u611B\u611F\u6148\u614B\u614C\u614E\u6155\u6162\u6163"
                + "\u6168\u616E\u6170\u6176\u6182\u618E\u61A4\u61A9\u61B2\u61B6"
                + "\u61BE\u61C7\u61D0\u61F2\u61F8\u620A\u620C\u6210-\u6212\u6226\u622F"
                + "\u6238\u623B\u623F\u6240\u6247\u6249\u624B\u624D\u6253\u6255"
                + "\u6271\u6276\u6279\u627F\u6280\u6284\u628A\u6291\u6295\u6297"
                + "\u6298\u629C\u629E\u62AB\u62B1\u62B5\u62B9\u62BC\u62BD\u62C5"
                + "\u62CD\u62D0\u62D2\u62D3\u62D8\u62D9\u62DB\u62DD\u62E0\u62E1"
                + "\u62EC\u62F7\u62FC\u62FE\u6301\u6307\u6311\u6319\u631F\u632F"
                + "\u633F\u6355\u635C\u6368\u636E\u6383\u6388\u638C\u6392\u6398"
                + "\u639B\u63A1\u63A2\u63A5\u63A7\u63A8\u63AA\u63B2\u63CF\u63D0"
                + "\u63DA\u63DB\u63E1\u63EE\u63F4\u63FA\u640D\u642C\u642D\u643A"
                + "\u643E\u6442\u6458\u6469\u6483\u64A4\u64AE\u64B2\u64C1\u64CD"
                + "\u64E6\u64EC\u652F\u6539\u653B\u653E\u653F\u6545\u654F\u6551"
                + "\u6557\u6559\u6562\u6563\u656C\u6570\u6574\u6575\u6577\u6587"
                + "\u6589\u658E\u6597\u6599\u659C\u65A4\u65A5\u65AD\u65B0\u65B9"
                + "\u65BD\u65C5\u65CB\u65CF\u65D7\u65E2\u65E5\u65E7-\u65E9\u65EC\u6606"
                + "\u6607\u660C\u660E\u6613\u6614\u661F\u6620\u6625\u6628\u662D"
                + "\u662F\u663C\u6642\u6669\u666E\u666F\u6674\u6676\u6681\u6687"
                + "\u6691\u6696\u6697\u66A6\u66AB\u66AE\u66B4\u66C7\u66DC\u66F2"
                + "\u66F4\u66F8\u66F9\u66FF\u6700\u6708\u6709\u670D\u6715\u6717"
                + "\u671B\u671D\u671F\u6728\u672A-\u672D\u6731\u6734\u673A\u673D"
                + "\u6749\u6750\u6751\u675F\u6761\u6765\u676F\u6771\u677E\u677F"
                + "\u6790\u6797\u679A\u679C\u679D\u67A0\u67A2\u67AF\u67B6\u67C4"
                + "\u67D0\u67D3\u67D4\u67F1\u67F3\u67FB\u6804\u6813\u6821\u682A"
                + "\u6838\u6839\u683C\u683D\u6843\u6848\u6851\u685C\u685F\u6885"
                + "\u68B0\u68C4\u68CB\u68D2\u68DA\u68DF\u68EE\u68FA\u690D\u691C"
                + "\u6954\u696D\u6975\u697C\u697D\u6982\u69CB\u69D8\u69FD\u6A19"
                + "\u6A21\u6A29\u6A2A\u6A39\u6A4B\u6A5F\u6B04\u6B20\u6B21\u6B27"
                + "\u6B32\u6B3A\u6B3E\u6B4C\u6B53\u6B62\u6B63\u6B66\u6B69\u6B6F"
                + "\u6B73\u6B74\u6B7B\u6B89-\u6B8B\u6B96\u6BB4\u6BB5\u6BBA\u6BBB\u6BBF"
                + "\u6BCD\u6BCE\u6BD2\u6BD4\u6BDB\u6C0F\u6C11\u6C17\u6C34\u6C37"
                + "\u6C38\u6C41\u6C42\u6C4E\u6C57\u6C5A\u6C5F\u6C60\u6C7A\u6C7D"
                + "\u6C88\u6C96\u6CA1\u6CA2\u6CB3\u6CB8\u6CB9\u6CBB\u6CBC\u6CBF"
                + "\u6CC1\u6CC9\u6CCA\u6CCC\u6CD5\u6CE1-\u6CE3\u6CE5\u6CE8\u6CF0\u6CF3"
                + "\u6D0B\u6D17\u6D1E\u6D25\u6D2A\u6D3B\u6D3E\u6D41\u6D44\u6D45"
                + "\u6D5C\u6D66\u6D6A\u6D6E\u6D74\u6D77\u6D78\u6D88\u6D99\u6DAF"
                + "\u6DB2\u6DBC\u6DD1\u6DE1\u6DF1\u6DF7\u6DFB\u6E05\u6E07-\u6E09"
                + "\u6E0B\u6E13\u6E1B\u6E21\u6E26\u6E29\u6E2C\u6E2F\u6E56\u6E6F"
                + "\u6E7E-\u6E80\u6E90\u6E96\u6E9D\u6EB6\u6EC5\u6ECB\u6ED1\u6EDD"
                + "\u6EDE\u6EF4\u6F01\u6F02\u6F06\u6F0F\u6F14\u6F20\u6F22\u6F2B"
                + "\u6F2C\u6F38\u6F54\u6F5C\u6F5F\u6F64\u6F6E\u6F84\u6FC0\u6FC1"
                + "\u6FC3\u6FEB\u6FEF\u702C\u706B\u706F\u7070\u707D\u7089\u708A"
                + "\u708E\u70AD\u70B9\u70BA\u70C8\u7121\u7126\u7136\u713C\u7159"
                + "\u7167\u7169\u716E\u719F\u71B1\u71C3\u71E5\u7206\u7235\u7236"
                + "\u7247\u7248\u7259\u725B\u7267\u7269\u7272\u7279\u72A0\u72AC"
                + "\u72AF\u72B6\u72C2\u72E9\u72EC\u72ED\u731B\u731F\u732A\u732B"
                + "\u732E\u7336\u733F\u7344\u7363\u7372\u7384\u7387\u7389\u738B"
                + "\u73CD\u73E0\u73ED\u73FE\u7403\u7406\u7434\u74B0\u74BD\u74F6"
                + "\u7518\u751A\u751F\u7523\u7528\u7530-\u7533\u7537\u753A\u753B\u754C"
                + "\u7551\u7554\u7559\u755C\u755D\u7565\u756A\u7570\u7573\u758E"
                + "\u7591\u75AB\u75B2\u75BE\u75C5\u75C7\u75D8\u75DB\u75E2\u75F4"
                + "\u7642\u7652\u7656\u7678\u767A\u767B\u767D\u767E\u7684\u7686"
                + "\u7687\u76AE\u76BF\u76C6\u76CA\u76D7\u76DB\u76DF\u76E3\u76E4"
                + "\u76EE\u76F2\u76F4\u76F8\u76FE\u7701\u770B\u770C\u771F\u7720"
                + "\u773A\u773C\u7740\u7761\u7763\u77AC\u77DB\u77E2\u77E5\u77ED"
                + "\u77EF\u77F3\u7802\u7814\u7815\u7832\u7834\u785D\u786B\u786C"
                + "\u7881\u7891\u78BA\u78C1\u78E8\u7901\u790E\u793A\u793C\u793E"
                + "\u7948\u7949\u7956\u795A\u795D\u795E\u7965\u7968\u796D\u7981"
                + "\u7984\u7985\u798D-\u798F\u79C0\u79C1\u79CB\u79D1\u79D2\u79D8"
                + "\u79DF\u79E9\u79F0\u79FB\u7A0B\u7A0E\u7A1A\u7A2E\u7A32\u7A3C"
                + "\u7A3F\u7A40\u7A42\u7A4D\u7A4F\u7A6B\u7A74\u7A76\u7A7A\u7A81"
                + "\u7A83\u7A92\u7A93\u7AAE\u7AAF\u7ACB\u7ADC\u7AE0\u7AE5\u7AEF"
                + "\u7AF6\u7AF9\u7B11\u7B1B\u7B26\u7B2C\u7B46\u7B49\u7B4B\u7B52"
                + "\u7B54\u7B56\u7B87\u7B97\u7BA1\u7BB1\u7BC0\u7BC4\u7BC9\u7BE4"
                + "\u7C21\u7C3F\u7C4D\u7C73\u7C89\u7C8B\u7C92\u7C97\u7C98\u7C9B"
                + "\u7CA7\u7CBE\u7CD6\u7CE7\u7CF8\u7CFB\u7CFE\u7D00\u7D04\u7D05"
                + "\u7D0B\u7D0D\u7D14\u7D19-\u7D1B\u7D20-\u7D22\u7D2B\u7D2F\u7D30\u7D33"
                + "\u7D39\u7D3A\u7D42\u7D44\u7D4C\u7D50\u7D5E\u7D61\u7D66\u7D71"
                + "\u7D75\u7D76\u7D79\u7D99\u7D9A\u7DAD\u7DB1\u7DB2\u7DBF\u7DCA"
                + "\u7DCF\u7DD1\u7DD2\u7DDA\u7DE0\u7DE8\u7DE9\u7DEF\u7DF4\u7E01"
                + "\u7E04\u7E1B\u7E26\u7E2B\u7E2E\u7E3E\u7E41\u7E4A\u7E54\u7E55"
                + "\u7E6D\u7E70\u7F36\u7F6A\u7F6E\u7F70\u7F72\u7F77\u7F85\u7F8A"
                + "\u7F8E\u7FA4\u7FA9\u7FBD\u7FC1\u7FCC\u7FD2\u7FFB\u7FFC\u8001"
                + "\u8003\u8005\u8010\u8015\u8017\u8033\u8056\u805E\u8074\u8077"
                + "\u8089\u808C\u8096\u809D\u80A2\u80A5\u80A9\u80AA\u80AF\u80B2"
                + "\u80BA\u80C3\u80C6\u80CC\u80CE\u80DE\u80F4\u80F8\u80FD\u8102"
                + "\u8105\u8108\u811A\u8131\u8133\u8139\u8150\u8155\u8170\u8178"
                + "\u8179\u819A\u819C\u81A8\u81D3\u81E3\u81E8\u81EA\u81ED\u81F3"
                + "\u81F4\u8208\u820C\u820E\u8217\u821E\u821F\u822A\u822C\u8236"
                + "\u8239\u8247\u8266\u826F\u8272\u828B\u829D\u82B1\u82B3\u82B8"
                + "\u82BD\u82D7\u82E5\u82E6\u82F1\u8302\u830E\u8336\u8349\u8352"
                + "\u8358\u8377\u83CA\u83CC\u83D3\u83DC\u83EF\u843D\u8449\u8457"
                + "\u846C\u84B8\u84C4\u8535\u8584\u85A6\u85AA-\u85AC\u85E4\u85E9"
                + "\u85FB\u864E\u8650\u865A\u865C\u865E\u866B\u868A\u8695\u86C7"
                + "\u86CD\u86EE\u878D\u8840\u8846\u884C\u8853\u8857\u885B\u885D"
                + "\u8861\u8863\u8868\u8870\u8877\u888B\u88AB\u88C1\u88C2\u88C5"
                + "\u88CF\u88D5\u88DC\u88F8\u88FD\u8907\u8910\u8912\u895F\u8972"
                + "\u897F\u8981\u8986\u8987\u898B\u898F\u8996\u899A\u89A7\u89AA"
                + "\u89B3\u89D2\u89E3\u89E6\u8A00\u8A02\u8A08\u8A0E\u8A13\u8A17"
                + "\u8A18\u8A1F\u8A2A\u8A2D\u8A31\u8A33\u8A34\u8A3A\u8A3C\u8A50"
                + "\u8A54\u8A55\u8A5E\u8A60\u8A66\u8A69\u8A70-\u8A73\u8A87\u8A89"
                + "\u8A8C\u8A8D\u8A93\u8A95\u8A98\u8A9E\u8AA0\u8AA4\u8AAC\u8AAD"
                + "\u8AB0\u8AB2\u8ABF\u8AC7\u8ACB\u8AD6\u8AED\u8AEE\u8AF8\u8AFE"
                + "\u8B00\u8B01\u8B04\u8B19\u8B1B\u8B1D\u8B21\u8B39\u8B58\u8B5C"
                + "\u8B66\u8B70\u8B72\u8B77\u8C37\u8C46\u8C4A\u8C5A\u8C61\u8C6A"
                + "\u8C9D\u8C9E\u8CA0-\u8CA2\u8CA7-\u8CA9\u8CAB\u8CAC\u8CAF\u8CB4"
                + "\u8CB7\u8CB8\u8CBB\u8CBF\u8CC0\u8CC3\u8CC4\u8CC7\u8CCA\u8CD3"
                + "\u8CDB\u8CDC\u8CDE\u8CE0\u8CE2\u8CE6\u8CEA\u8CFC\u8D08\u8D64"
                + "\u8D66\u8D70\u8D74\u8D77\u8D85\u8D8A\u8DA3\u8DB3\u8DDD\u8DE1"
                + "\u8DEF\u8DF3\u8DF5\u8E0A\u8E0F\u8E8D\u8EAB\u8ECA\u8ECC\u8ECD"
                + "\u8ED2\u8EDF\u8EE2\u8EF8\u8EFD\u8F03\u8F09\u8F1D\u8F29\u8F2A"
                + "\u8F38\u8F44\u8F9B\u8F9E\u8FB0-\u8FB2\u8FBA\u8FBC\u8FC5\u8FCE"
                + "\u8FD1\u8FD4\u8FEB\u8FED\u8FF0\u8FF7\u8FFD\u9000\u9001\u9003"
                + "\u9006\u900F\u9010\u9013\u9014\u901A\u901D\u901F\u9020\u9023"
                + "\u902E\u9031\u9032\u9038\u9042\u9045\u9047\u904A\u904B\u904D"
                + "\u904E\u9053-\u9055\u9060\u9063\u9069\u906D\u906E\u9075\u9077\u9078"
                + "\u907A\u907F\u9084\u90A6\u90AA\u90B8\u90CA\u90CE\u90E1\u90E8"
                + "\u90ED\u90F5\u90F7\u90FD\u9149\u914C\u914D\u9152\u9154\u9162"
                + "\u916A\u916C\u9175\u9177\u9178\u919C\u91B8\u91C8\u91CC-\u91CF"
                + "\u91D1\u91DD\u91E3\u920D\u9234\u9244\u925B\u9262\u9271\u9280"
                + "\u9283\u9285\u9291\u9298\u92AD\u92ED\u92F3\u92FC\u9304\u9318"
                + "\u9320\u932C\u932F\u9332\u935B\u9396\u93AE\u93E1\u9418\u9451"
                + "\u9577\u9580\u9589\u958B\u958F\u9591\u9593\u95A2\u95A3\u95A5"
                + "\u95B2\u95D8\u962A\u9632\u963B\u9644\u964D\u9650\u965B\u9662-\u9665"
                + "\u966A\u9670\u9673\u9675\u9676\u9678\u967A\u967D\u9685\u9686"
                + "\u968A\u968E\u968F\u9694\u969B\u969C\u96A0\u96A3\u96B7\u96BB"
                + "\u96C4-\u96C7\u96C9\u96CC\u96D1\u96E2\u96E3\u96E8\u96EA\u96F0"
                + "\u96F2\u96F6\u96F7\u96FB\u9700\u9707\u970A\u971C\u9727\u9732"
                + "\u9752\u9759\u975E\u9762\u9769\u9774\u97D3\u97F3\u97FB\u97FF"
                + "\u9802\u9803\u9805\u9806\u9810-\u9812\u9818\u982D\u983B\u983C"
                + "\u984C\u984D\u9854\u9855\u9858\u985E\u9867\u98A8\u98DB\u98DF"
                + "\u98E2\u98EF\u98F2\u98FC-\u98FE\u990A\u9913\u9928\u9996\u9999\u99AC"
                + "\u99C4-\u99C6\u99D0\u9A0E\u9A12\u9A13\u9A30\u9A5A\u9AA8\u9AC4"
                + "\u9AD8\u9AEA\u9B3C\u9B42\u9B45\u9B54\u9B5A\u9BAE\u9BE8\u9CE5"
                + "\u9CEF\u9CF4\u9D8F\u9E7F\u9E97\u9EA6\u9EBB\u9EC4\u9ED2\u9ED9"
                + "\u9F13\u9F20\u9F3B\u9F62\uFF01-\uFF03\uFF05-\uFF0A\uFF0C-\uFF0F"
                + "\uFF1A\uFF1B\uFF1F\uFF20\uFF3B-\uFF3D\uFF3F\uFF5B\uFF5D\uFF61-\uFF65]"));
        EXEMPLAR_MAP.put("ka", new UnicodeSet("[\u10A0-\u10C5\u10D0-\u10FB\u2116\u2D00-\u2D25]"));
        EXEMPLAR_MAP.put("kkj", new UnicodeSet("[\u2039\u203A{A\u0327}{I\u0327}{U\u0327}{a\u0327}"
                + "{i\u0327}{u\u0327}{\u0186\u0302}{\u0186\u0327}{\u0190\u0302}{\u0190\u0327}"
                + "{\u0254\u0302}{\u0254\u0327}{\u025B\u0302}{\u025B\u0327}]"));
        EXEMPLAR_MAP.put("km", new UnicodeSet("[\u1780-\u17A2\u17A5-\u17A7\u17A9-\u17B3"
                + "\u17B6-\u17D2\u17D4-\u17D6\u17D9\u17DA\u200B{\u17A2\u17B6}{\u17A7\u1780}]"));
        EXEMPLAR_MAP.put("kn", new UnicodeSet("[\u0C82\u0C83\u0C85-\u0C8C\u0C8E-\u0C90"
                + "\u0C92-\u0CA8\u0CAA-\u0CB3\u0CB5-\u0CB9\u0CBC-\u0CC4\u0CC6-\u0CC8\u0CCA-\u0CCD"
                + "\u0CD5\u0CD6\u0CDE\u0CE0\u0CE1\u0CE6-\u0CEF]"));
        EXEMPLAR_MAP.put("ko", new UnicodeSet("[\u1100-\u1112\u1161-\u1175\u11A8-\u11C2\u2015"
                + "\u2025\u2030\u203B\u203E\u3001-\u3003\u3008-\u3011\u3014\u3015\u301C\u30FB\u3131"
                + "\u3134\u3137\u3139\u3141\u3142\u3145\u3147\u3148\u314A-\u314E\u4E18\u4E32"
                + "\u4E43\u4E45\u4E56\u4E5D\u4E5E\u4E6B\u4E7E\u4E82\u4E98\u4EA4"
                + "\u4EAC\u4EC7\u4ECA\u4ECB\u4EF6\u4EF7\u4F01\u4F0B\u4F0E\u4F3D"
                + "\u4F73\u4F76\u4F83\u4F86\u4F8A\u4F9B\u4FC2\u4FD3\u4FF1\u500B"
                + "\u501E\u5026\u5028\u5047\u5048\u5065\u5080\u5091\u50BE\u50C5"
                + "\u50D1\u50F9\u5106\u5109\u513A\u5149\u514B\u5162\u5167\u516C"
                + "\u5171\u5176\u5177\u517C\u5180\u51A0\u51F1\u520A\u522E\u5238"
                + "\u523B\u524B\u525B\u5287\u528D\u5292\u529F\u52A0\u52A4\u52AB"
                + "\u52C1\u52CD\u52D8\u52E4\u52F8\u52FB\u52FE\u5321\u5323\u5340"
                + "\u5357\u5366\u5374\u5375\u5377\u537F\u53A5\u53BB\u53CA\u53E3"
                + "\u53E5\u53E9\u53EB\u53EF\u5404\u5409\u541B\u544A\u5471\u5475"
                + "\u548E\u54AC\u54E5\u54ED\u5553\u5580\u5587\u559D\u55AB\u55AC"
                + "\u55DC\u5609\u5614\u5668\u56CA\u56F0\u56FA\u5708\u570B\u572D"
                + "\u573B\u5747\u574E\u5751\u5764\u5770\u5775\u57A2\u57FA\u57FC"
                + "\u5800\u5805\u5808\u582A\u583A\u584A\u584F\u5883\u58BE\u58D9"
                + "\u58DE\u5914\u5947\u5948\u594E\u5951\u5978\u5993\u5997\u59D1"
                + "\u59DC\u59E6\u5A18\u5A1C\u5AC1\u5B0C\u5B54\u5B63\u5B64\u5B8F"
                + "\u5B98\u5BA2\u5BAE\u5BB6\u5BC4\u5BC7\u5BE1\u5BEC\u5C3B\u5C40"
                + "\u5C45\u5C46\u5C48\u5C90\u5CA1\u5CAC\u5D0E\u5D11\u5D17\u5D4C"
                + "\u5D50\u5D87\u5DA0\u5DE5\u5DE7\u5DE8\u5DF1\u5DFE\u5E72\u5E79"
                + "\u5E7E\u5E9A\u5EAB\u5EB7\u5ECA\u5ED0\u5ED3\u5EE3\u5EFA\u5F13"
                + "\u5F3A\u5F4A\u5F91\u5FCC\u6025\u602A\u602F\u6050\u605D\u606A"
                + "\u606D\u60B8\u6106\u611F\u6127\u6137\u613E\u614A\u6163\u6164"
                + "\u6168\u6176\u6177\u61A9\u61AC\u61BE\u61C3\u61C7\u61E6\u61F6"
                + "\u61FC\u6208\u6212\u621F\u6221\u6271\u6280\u6289\u62C9\u62CF"
                + "\u62D0\u62D2\u62D8\u62EC\u62EE\u62F1\u62F3\u62F7\u62FF\u634F"
                + "\u636E\u6372\u637A\u6398\u639B\u63A7\u63C0\u63C6\u63ED\u64CA"
                + "\u64CE\u64D2\u64DA\u64E7\u652A\u6537\u6539\u653B\u6545\u654E"
                + "\u6551\u6562\u656C\u6572\u659B\u65A4\u65D7\u65E3\u6606\u6611"
                + "\u666F\u6677\u6687\u6696\u66A0\u66BB\u66E0\u66F2\u66F4\u66F7"
                + "\u6717\u671E\u671F\u673A\u6746\u675E\u6770\u678F\u679C\u67AF"
                + "\u67B6\u67B8\u67D1\u67E9\u67EC\u67EF\u6821\u6839\u683C\u6840"
                + "\u6842\u6854\u687F\u688F\u6897\u68B0\u68B1\u68C4\u68CB\u68CD"
                + "\u68D8\u68E8\u68FA\u6957\u6960\u6975\u69C1\u69CB\u69D0\u69E8"
                + "\u69EA\u69FB\u69FF\u6A02\u6A44\u6A4B\u6A58\u6A5F\u6A84\u6A8E"
                + "\u6AA2\u6AC3\u6B04\u6B0A\u6B3A\u6B3E\u6B4C\u6B50\u6B78\u6BBC"
                + "\u6BC6\u6BEC\u6C23\u6C42\u6C5F\u6C68\u6C72\u6C7A\u6C7D\u6C82"
                + "\u6CBD\u6D1B\u6D38\u6D6A\u6D87\u6DC3\u6DC7\u6E1B\u6E20\u6E34"
                + "\u6E73\u6E9D\u6EAA\u6ED1\u6EFE\u6F11\u6F54\u6F70\u6F97\u6FC0"
                + "\u6FEB\u704C\u7078\u7085\u709A\u70AC\u70D9\u70F1\u7156\u721B"
                + "\u727D\u72AC\u72C2\u72D7\u72E1\u72FC\u7357\u7396\u7398\u73C2"
                + "\u73CF\u73D6\u73D9\u73DE\u73EA\u7403\u7426\u7428\u742A\u742F"
                + "\u7434\u747E\u7482\u749F\u74A3\u74A5\u74CA\u74D8\u74DC\u7504"
                + "\u7518\u7532\u7537\u7547\u754C\u7578\u757A\u757F\u7586\u75A5"
                + "\u75B3\u75C2\u75D9\u75FC\u764E\u7669\u7678\u7686\u768E\u7690"
                + "\u76D6\u76E3\u770B\u7737\u777E\u77B0\u77BC\u77BF\u77DC\u77E9"
                + "\u77EF\u7845\u786C\u7881\u78A3\u78CE\u78EC\u78EF\u78F5\u7941"
                + "\u7947\u7948\u795B\u797A\u7981\u79BD\u79D1\u7A08\u7A3C\u7A3D"
                + "\u7A3F\u7A40\u7A76\u7A79\u7A7A\u7A98\u7A9F\u7AAE\u7ABA\u7AC5"
                + "\u7ADF\u7AED\u7AF6\u7AFF\u7B4B\u7B50\u7B60\u7B87\u7B95\u7B9D"
                + "\u7BA1\u7C21\u7CB3\u7CE0\u7CFB\u7CFE\u7D00\u7D0D\u7D18\u7D1A"
                + "\u7D3A\u7D45\u7D50\u7D5E\u7D66\u7D73\u7D79\u7D7F\u7D93\u7DB1"
                + "\u7DBA\u7DCA\u7E6B\u7E6D\u7E7C\u7F3A\u7F50\u7F6B\u7F85\u7F88"
                + "\u7F8C\u7F94\u7FA4\u7FB9\u7FF9\u8003\u8006\u8009\u8015\u802D"
                + "\u803F\u808C\u809D\u80A1\u80A9\u80AF\u80B1\u80DB\u80F1\u811A"
                + "\u811B\u8154\u8171\u8188\u818F\u81A0\u81D8\u81FC\u8205\u820A"
                + "\u8221\u826E\u8271\u828E\u82A5\u82A9\u82B9\u82DB\u82DF\u82E6"
                + "\u82FD\u8304\u8396\u83C5\u83CA\u83CC\u83D3\u83EB\u83F0\u843D"
                + "\u845B\u8475\u84CB\u854E\u8568\u8591\u85C1\u85CD\u85FF\u862D"
                + "\u863F\u8654\u86A3\u86DF\u874E\u87BA\u881F\u8831\u8857\u8862"
                + "\u8872\u887E\u887F\u8888\u889E\u88B4\u88D9\u88F8\u8910\u8941"
                + "\u895F\u8964\u898B\u898F\u89A1\u89B2\u89BA\u89C0\u89D2\u8A08"
                + "\u8A18\u8A23\u8A36\u8A6D\u8A87\u8AA1\u8AA5\u8AB2\u8AEB\u8AFE"
                + "\u8B19\u8B1B\u8B33\u8B39\u8B4F\u8B66\u8B74\u8C37\u8C3F\u8C48"
                + "\u8CA2\u8CAB\u8CB4\u8CC8\u8CFC\u8D73\u8D77\u8DCF\u8DDD\u8DE8"
                + "\u8E1E\u8E47\u8E76\u8EAC\u8EC0\u8ECA\u8ECC\u8ECD\u8EFB\u8F03"
                + "\u8F15\u8F4E\u8F5F\u8F9C\u8FD1\u8FE6\u8FF2\u9002\u9011\u9015"
                + "\u9035\u904E\u9063\u907D\u908F\u90A3\u90AF\u90B1\u90CA\u90CE"
                + "\u90E1\u90ED\u916A\u91B5\u91D1\u9210\u921E\u9240\u9245\u9257"
                + "\u9264\u92B6\u92F8\u92FC\u9321\u9324\u9326\u932E\u934B\u9375"
                + "\u938C\u93A7\u93E1\u9451\u9452\u945B\u958B\u9593\u9598\u95A3"
                + "\u95A8\u95D5\u95DC\u964D\u968E\u9694\u9699\u96C7\u96E3\u978F"
                + "\u97A0\u97A8\u97AB\u9803\u9838\u9846\u9867\u98E2\u9903\u9928"
                + "\u9949\u994B\u9951\u99D2\u99D5\u99F1\u9A0E\u9A0F\u9A2B\u9A45"
                + "\u9A55\u9A5A\u9A65\u9AA8\u9AD8\u9B3C\u9B41\u9BAB\u9BE4\u9BE8"
                + "\u9C47\u9CE9\u9D51\u9D60\u9DC4\u9DD7\u9E1E\u9E92\u9EB4\u9ED4"
                + "\u9F13\u9F95\u9F9C\uAC00-\uD7A3\uFF01-\uFF03\uFF05-\uFF0A\uFF0C-\uFF0F"
                + "\uFF1A\uFF1B\uFF1F\uFF20\uFF3B-\uFF3D\uFF3F\uFF5B\uFF5D]"));
        EXEMPLAR_MAP.put("kok", new UnicodeSet("[\u0901-\u0903\u0905-\u090D\u090F-\u0911"
                + "\u0913-\u0928\u092A-\u0930\u0932\u0933\u0935-\u0939\u093C-\u0945\u0947-\u0949"
                + "\u094B-\u094D\u0950\u0966-\u096F{\u0915\u093C}{\u0916\u093C}"
                + "{\u0917\u093C}{\u091C\u093C}{\u0921\u093C}{\u0922\u093C}{\u092B\u093C}"
                + "{\u092F\u093C}]"));
        EXEMPLAR_MAP.put("ksh", new UnicodeSet("[\u2E17]"));
        EXEMPLAR_MAP.put("lkt", new UnicodeSet("[\u02BC{k\u02BC}{p\u02BC}{s\u02BC}{t\u02BC}"
                + "{\u010D\u02BC}{\u0161\u02BC}{\u021F\u02BC}]"));
        EXEMPLAR_MAP.put("ln", new UnicodeSet("[{\u0186\u0302}{\u0186\u030C}{\u0190\u0302}"
                + "{\u0190\u030C}{\u0254\u0302}{\u0254\u030C}{\u025B\u0302}{\u025B\u030C}]"));
        EXEMPLAR_MAP.put("lo", new UnicodeSet("[\u0E81\u0E82\u0E84\u0E87\u0E88\u0E8A\u0E8D"
                + "\u0E94-\u0E97\u0E99-\u0E9F\u0EA1-\u0EA3\u0EA5\u0EA7\u0EAA\u0EAB\u0EAD-\u0EB9"
                + "\u0EBB-\u0EBD\u0EC0-\u0EC4\u0EC6\u0EC8-\u0ECD\u0ED0-\u0ED9\u0EDC\u0EDD\u200B"
                + "{\u0EAB\u0E87}{\u0EAB\u0E8D}{\u0EAB\u0E99}{\u0EAB\u0EA1}{\u0EAB\u0EA5}"
                + "{\u0EAB\u0EA7}]"));
        EXEMPLAR_MAP.put("lt", new UnicodeSet("[{I\u0303}{I\u0307\u0303}{J\u0303}{J\u0307\u0303}"
                + "{L\u0303}{M\u0303}{R\u0303}{i\u0307\u0303}{j\u0303}{j\u0307\u0303}{l\u0303}"
                + "{m\u0303}{r\u0303}{\u0104\u0303}{\u0105\u0303}{\u0116\u0303}{\u0117\u0303}"
                + "{\u0118\u0303}{\u0119\u0303}{\u012E\u0303}{\u012E\u0307\u0303}{\u012F\u0303}"
                + "{\u012F\u0307\u0303}{\u016A\u0303}{\u016B\u0303}{\u0172\u0303}{\u0173\u0303}]"));
        EXEMPLAR_MAP.put("mgo", new UnicodeSet("[\u02BC]"));
        EXEMPLAR_MAP.put("ml", new UnicodeSet("[\u0D02\u0D03\u0D05-\u0D0C\u0D0E-\u0D10"
                + "\u0D12-\u0D28\u0D2A-\u0D39\u0D3E-\u0D43\u0D46-\u0D48\u0D4A-\u0D4D\u0D57\u0D60"
                + "\u0D61\u0D7A-\u0D7F]"));
        EXEMPLAR_MAP.put("mr", new UnicodeSet("[\u0901-\u0903\u0905-\u090D\u090F-\u0911"
                + "\u0913-\u0928\u092A-\u0930\u0932\u0933\u0935-\u0939\u093C-\u0945\u0947-\u0949"
                + "\u094B-\u094D\u0950\u0966-\u096F]"));
        EXEMPLAR_MAP.put("my", new UnicodeSet("[\u1000-\u1032\u1036-\u104B\u1050-\u1059]"));
        EXEMPLAR_MAP.put("mzn", new UnicodeSet("[\u060C\u061B\u061F\u0621-\u063A\u0641-\u0652"
                + "\u0654\u0656\u066A-\u066C\u0670\u067E\u0686\u0698\u06A9\u06AF\u06CC\u06F0-\u06F9"
                + "\u2039\u203A]"));
        EXEMPLAR_MAP.put("ne", new UnicodeSet("[\u0901-\u0903\u0905-\u090D\u090F-\u0911"
                + "\u0913-\u0928\u092A-\u0930\u0932\u0933\u0935-\u0939\u093C-\u0945\u0947-\u0949"
                + "\u094B-\u094D\u0950\u0966-\u096F]"));
        EXEMPLAR_MAP.put("nmg", new UnicodeSet("[{\u0186\u0302}{\u0186\u0304}{\u0186\u030C}"
                + "{\u018E\u0302}{\u018E\u0304}{\u018E\u030C}{\u0190\u0302}{\u0190\u0304}"
                + "{\u0190\u030C}{\u01DD\u0302}{\u01DD\u0304}{\u01DD\u030C}{\u0254\u0302}"
                + "{\u0254\u0304}{\u0254\u030C}{\u025B\u0302}{\u025B\u0304}{\u025B\u030C}]"));
        EXEMPLAR_MAP.put("nnh", new UnicodeSet("[\u02BC{\u0186\u0302}{\u0186\u030C}{\u0190\u0302}"
                + "{\u0190\u030C}{\u0244\u0302}{\u0244\u030C}{\u0254\u0302}{\u0254\u030C}"
                + "{\u025B\u0302}{\u025B\u030C}{\u0289\u0302}{\u0289\u030C}]"));
        EXEMPLAR_MAP.put("nus", new UnicodeSet("[{A\u0331}{E\u0331}{I\u0331}{O\u0331}{a\u0331}"
                + "{e\u0331}{i\u0331}{o\u0331}{\u0186\u0331}{\u0190\u0331}{\u0190\u0331\u0308}"
                + "{\u0254\u0331}{\u025B\u0331}{\u025B\u0331\u0308}]"));
        EXEMPLAR_MAP.put("or", new UnicodeSet("[\u0B01-\u0B03\u0B05-\u0B0B\u0B0F\u0B10"
                + "\u0B13-\u0B28\u0B2A-\u0B30\u0B32\u0B33\u0B35-\u0B39\u0B3C\u0B3E-\u0B43\u0B47"
                + "\u0B48\u0B4B-\u0B4D\u0B5F\u0B71{\u0B15\u0B4D\u0B37}{\u0B21\u0B3C}"
                + "{\u0B22\u0B3C}]"));
        EXEMPLAR_MAP.put("pa-Arab", new UnicodeSet("[\u0621-\u0624\u0626-\u063A\u0641\u0642"
                + "\u0644-\u0648\u064F\u066A-\u066C\u0679-\u067E\u0686\u0688\u0691\u0698\u06A9"
                + "\u06AF\u06BA\u06BE\u06C1\u06CC\u06D2\u06F0-\u06F9]"));
        EXEMPLAR_MAP.put("pa-Guru", new UnicodeSet("[\u0A01-\u0A03\u0A05-\u0A0A\u0A0F\u0A10"
                + "\u0A13-\u0A28\u0A2A-\u0A30\u0A32\u0A35\u0A38\u0A39\u0A3C\u0A3E-\u0A42\u0A47"
                + "\u0A48\u0A4B-\u0A4D\u0A5C\u0A66-\u0A74{\u0A16\u0A3C}{\u0A17\u0A3C}"
                + "{\u0A1C\u0A3C}{\u0A2B\u0A3C}{\u0A32\u0A3C}{\u0A38\u0A3C}]"));
        EXEMPLAR_MAP.put("ps", new UnicodeSet("[\u0621-\u0624\u0626-\u063A\u0641\u0642"
                + "\u0644-\u0648\u064A-\u0652\u0654\u066A-\u066C\u0670\u067C\u067E\u0681\u0685"
                + "\u0686\u0689\u0693\u0696\u0698\u069A\u06A9\u06AB\u06AF\u06BC\u06CC\u06CD\u06D0"
                + "\u06F0-\u06F9]"));
        EXEMPLAR_MAP.put("qu", new UnicodeSet("[{CH\u02BC}{Ch\u02BC}{K\u02BC}{P\u02BC}{Q\u02BC}"
                + "{T\u02BC}{ch\u02BC}{k\u02BC}{p\u02BC}{q\u02BC}{t\u02BC}]"));
        EXEMPLAR_MAP.put("si", new UnicodeSet("[\u0D82\u0D83\u0D85-\u0D96\u0D9A-\u0DB1"
                + "\u0DB3-\u0DBB\u0DBD\u0DC0-\u0DC6\u0DCA\u0DCF-\u0DD4\u0DD6\u0DD8-\u0DDF\u0DF2"
                + "\u0DF3\u200B-\u200D]"));
        EXEMPLAR_MAP.put("ta", new UnicodeSet("[\u0B83\u0B85-\u0B8A\u0B8E-\u0B90\u0B92-\u0B95"
                + "\u0B99\u0B9A\u0B9C\u0B9E\u0B9F\u0BA3\u0BA4\u0BA8-\u0BAA\u0BAE-\u0BB9"
                + "\u0BBE-\u0BC2\u0BC6-\u0BC8\u0BCA-\u0BCD{\u0B95\u0BCD\u0BB7}]"));
        EXEMPLAR_MAP.put("te", new UnicodeSet("[\u0C01-\u0C03\u0C05-\u0C0C\u0C0E-\u0C10"
                + "\u0C12-\u0C28\u0C2A-\u0C33\u0C35-\u0C39\u0C3E-\u0C44\u0C46-\u0C48\u0C4A-\u0C4D"
                + "\u0C55\u0C56\u0C60\u0C61\u0C66-\u0C6F]"));
        EXEMPLAR_MAP.put("th", new UnicodeSet("[\u0E01-\u0E3A\u0E40-\u0E4E\u200B]"));
        EXEMPLAR_MAP.put("ti", new UnicodeSet("[\u1200-\u1248\u124A-\u124D\u1250-\u1256\u1258"
                + "\u125A-\u125D\u1260-\u1288\u128A-\u128D\u1290-\u12B0\u12B2-\u12B5\u12B8-\u12BE"
                + "\u12C0\u12C2-\u12C5\u12C8-\u12D6\u12D8-\u1310\u1312-\u1315\u1318-\u135A\u135F"
                + "\u1380-\u1399\u2D80-\u2D96\u2DA0-\u2DA6\u2DA8-\u2DAE\u2DB0-\u2DB6\u2DB8-\u2DBE"
                + "\u2DC0-\u2DC6\u2DC8-\u2DCE\u2DD0-\u2DD6\u2DD8-\u2DDE]"));
        EXEMPLAR_MAP.put("to", new UnicodeSet("[\u02BB]"));
        EXEMPLAR_MAP.put("ug", new UnicodeSet("[\u0626-\u0628\u062A\u062C\u062E\u062F"
                + "\u0631-\u0634\u063A\u0641-\u0646\u0648-\u064A\u067E\u0686\u0698\u06AD\u06AF"
                + "\u06BE\u06C6-\u06C8\u06CB\u06D0\u06D5]"));
        EXEMPLAR_MAP.put("uk", new UnicodeSet("[\u02BC\u2116]"));
        EXEMPLAR_MAP.put("ur-IN", new UnicodeSet("[\u0600-\u0603\u060C\u060D\u061B\u061F"
                + "\u0621-\u0624\u0626-\u063A\u0641\u0642\u0644-\u0648\u064A-\u0652\u0654"
                + "\u0656-\u0658\u066B\u066C\u0670\u0679-\u067E\u0686\u0688\u0691\u0698\u06A9\u06AF"
                + "\u06BA\u06BE\u06C1-\u06C3\u06CC\u06D2\u06D4\u06F0-\u06F9]"));
        EXEMPLAR_MAP.put("ur-PK", new UnicodeSet("[\u0600-\u0603\u060C\u060D\u061B\u061F"
                + "\u0621-\u0624\u0626-\u063A\u0641\u0642\u0644-\u0648\u064A-\u0652\u0654"
                + "\u0656-\u0658\u066B\u066C\u0670\u0679-\u067E\u0686\u0688\u0691\u0698\u06A9\u06AF"
                + "\u06BA\u06BE\u06C1-\u06C3\u06CC\u06D2\u06D4]"));
        EXEMPLAR_MAP.put("uz-Arab", new UnicodeSet("[\u0621-\u0624\u0626-\u063A\u0641\u0642"
                + "\u0644-\u0648\u064A-\u0652\u0654\u066A-\u066C\u0670\u067C\u067E\u0681\u0685"
                + "\u0686\u0689\u0693\u0696\u0698\u069A\u06A9\u06AB\u06AF\u06BC\u06C7\u06C9\u06CC"
                + "\u06CD\u06D0\u06F0-\u06F9]"));
        EXEMPLAR_MAP.put("uz-Latn", new UnicodeSet("[\u02BC{G\u02BB}{O\u02BB}{g\u02BB}{o\u02BB}]"));
        EXEMPLAR_MAP.put("yue", new UnicodeSet("[\u2025\u2027\u2030\u2035\u203B\u203E"
                + "\u3001-\u3003\u3008-\u3011\u3014\u3015\u301D\u301E\u4E00\u4E01\u4E03"
                + "\u4E08-\u4E0D\u4E11\u4E14\u4E16\u4E18\u4E19\u4E1E\u4E1F\u4E26\u4E2D\u4E32\u4E38"
                + "\u4E39\u4E3B\u4E43\u4E45\u4E48\u4E4B\u4E4D-\u4E4F\u4E56\u4E58\u4E59\u4E5D"
                + "\u4E5F\u4E7E\u4E82\u4E86\u4E88\u4E8B\u4E8C\u4E8E\u4E91\u4E92"
                + "\u4E94\u4E95\u4E9B\u4E9E\u4EA1\u4EA4-\u4EA6\u4EA8\u4EAB-\u4EAE"
                + "\u4EBA\u4EC0-\u4EC2\u4EC7\u4ECA\u4ECB\u4ECD\u4ED4\u4ED6\u4ED8\u4ED9"
                + "\u4EE3-\u4EE5\u4EF0\u4EF2\u4EF6\u4EFB\u4EFD\u4F01\u4F0A\u4F0D"
                + "\u4F0F-\u4F11\u4F19\u4F2F\u4F30\u4F34\u4F38\u4F3C\u4F3D\u4F46"
                + "\u4F48\u4F49\u4F4D-\u4F50\u4F54\u4F55\u4F59\u4F5B\u4F5C\u4F60"
                + "\u4F69\u4F73\u4F7F\u4F86\u4F8B\u4F9B\u4F9D\u4FAF\u4FB5\u4FB6"
                + "\u4FBF\u4FC2-\u4FC4\u4FCA\u4FD7\u4FDD\u4FE0\u4FE1\u4FEE\u4FF1\u4FFE"
                + "\u500B\u500D\u5011\u5012\u5019\u501A\u501F\u502B\u503C\u5047"
                + "\u5049\u504F\u505A\u505C\u5065\u5074-\u5077\u5080\u5091\u5099\u50A2"
                + "\u50A3\u50B2\u50B3\u50B7\u50BB\u50BE\u50C5\u50CE\u50CF\u50D1"
                + "\u50E7\u50F3\u50F5\u50F9\u5100\u5104\u5110\u5112\u5118\u511F"
                + "\u512A\u5133\u5137\u513B\u5141\u5143-\u5149\u514B-\u514D\u5152\u5154"
                + "\u5165\u5167-\u5169\u516B-\u516E\u5171\u5175-\u5179\u517C\u518A"
                + "\u518D\u5192\u51A0\u51AC\u51B0\u51B7\u51C6\u51CC\u51DD\u51E1"
                + "\u51F0\u51F1\u51FA\u51FD\u5200\u5206\u5207\u520A\u5217\u521D"
                + "\u5224\u5225\u5229-\u522B\u5230\u5236-\u5238\u523A\u523B\u5247\u524C"
                + "\u524D\u525B\u5269\u526A\u526F\u5272\u5275\u5283\u5287\u5289"
                + "\u528D\u529B\u529F\u52A0\u52A9-\u52AB\u52C1\u52C7\u52C9\u52D2"
                + "\u52D5\u52D9\u52DD\u52DE\u52E2\u52E4\u52F3\u52F5\u52F8\u52FF"
                + "\u5305\u5308\u5316\u5317\u5339\u5340\u5341\u5343\u5347\u5348"
                + "\u534A\u5351-\u5354\u5357\u535A\u535C\u535E\u5360\u5361\u536F-\u5371"
                + "\u5373\u5377\u537B\u5384\u5398\u539A\u539F\u53AD\u53B2\u53BB"
                + "\u53C3\u53C8\u53CA\u53CB\u53CD\u53D4\u53D6\u53D7\u53E2-\u53E6"
                + "\u53EA-\u53ED\u53EF\u53F0\u53F2\u53F3\u53F6\u53F8\u5403\u5404"
                + "\u5408-\u540A\u540C-\u540E\u5410-\u5412\u541B\u541D-\u5420\u5426\u5427"
                + "\u542B\u5433\u5435\u5438\u5439\u543E\u5440\u5442\u5446\u544A"
                + "\u5462\u5468\u5473\u5475\u547C\u547D\u548C\u5496\u54A6\u54A7"
                + "\u54AA\u54AC\u54B1\u54C0\u54C1\u54C7-\u54C9\u54CE\u54E1\u54E5\u54E6"
                + "\u54E9\u54EA\u54ED\u54F2\u5509\u5510\u5514\u552C\u552E\u552F"
                + "\u5531\u5537\u5538\u5546\u554A\u554F\u555F\u5561\u5565\u5566"
                + "\u556A\u5580\u5582\u5584\u5587\u558A\u5594\u559C\u559D\u55AC"
                + "\u55AE\u55B5\u55CE\u55DA\u55E8\u55EF\u5606\u5609\u5617\u561B"
                + "\u5634\u563B\u563F\u5668\u5674\u5687\u56B4\u56C9\u56CC\u56D1"
                + "\u56DB\u56DE\u56E0\u56F0\u56FA\u5708\u570B\u570D\u5712\u5713"
                + "\u5716\u5718\u571C\u571F\u5728\u572D\u5730\u573E\u5740\u5747"
                + "\u574E\u5750\u5761\u5764\u5766\u576A\u5782\u5783\u578B\u57C3"
                + "\u57CE\u57D4\u57DF\u57F7\u57F9\u57FA\u5802\u5805\u5806\u5821"
                + "\u5824\u582A\u5831\u5834\u584A\u5854\u5857\u585E\u586B\u5875"
                + "\u5883\u588E\u589E\u58A8\u58AE\u58C1\u58C7\u58D3\u58D8\u58DE"
                + "\u58E2\u58E4\u58EB\u58EC\u58EF\u58FD\u590F\u5915\u5916\u591A"
                + "\u591C\u5920\u5922\u5925\u5927\u5929-\u592B\u592E\u5931\u5937\u5938"
                + "\u593E\u5947-\u5949\u594E\u594F\u5951\u5954\u5957\u5965\u5967\u596A"
                + "\u596E\u5973\u5974\u5976\u5979\u597D\u5982\u5999\u599D\u59A5"
                + "\u59A8\u59AE\u59B3\u59B9\u59BB\u59C6\u59CA\u59CB\u59D0\u59D1"
                + "\u59D3\u59D4\u59FF\u5A01\u5A03\u5A18\u5A1B\u5A41\u5A46\u5A5A"
                + "\u5A66\u5A92\u5ABD\u5ACC\u5AE9\u5B50\u5B54\u5B57\u5B58\u5B5C"
                + "\u5B5D\u5B5F\u5B63\u5B64\u5B69\u5B6B\u5B78\u5B83\u5B85\u5B87-\u5B89"
                + "\u5B8B\u5B8C\u5B8F\u5B97-\u5B9C\u5BA2-\u5BA4\u5BAE\u5BB3\u5BB6\u5BB9"
                + "\u5BBF\u5BC2\u5BC4-\u5BC6\u5BCC\u5BD2\u5BDE\u5BDF\u5BE2\u5BE6-\u5BE9"
                + "\u5BEB\u5BEC\u5BEE\u5BF5\u5BF6\u5C01\u5C04\u5C07\u5C08\u5C0A"
                + "\u5C0B\u5C0D-\u5C0F\u5C11\u5C16\u5C1A\u5C24\u5C31\u5C3A\u5C3C\u5C3E"
                + "\u5C40\u5C41\u5C45\u5C46\u5C4B\u5C4F\u5C55\u5C60\u5C64\u5C6C"
                + "\u5C71\u5CA1\u5CA9\u5CB8\u5CC7\u5CF0\u5CF6\u5CFD\u5D07\u5D19"
                + "\u5D34\u5D50\u5DBA\u5DBC\u5DDD\u5DDE\u5DE1\u5DE5-\u5DE8\u5DEB\u5DEE"
                + "\u5DF1-\u5DF4\u5DF7\u5DFD\u5E02\u5E03\u5E0C\u5E15\u5E16\u5E1B"
                + "\u5E1D\u5E25\u5E2B\u5E2D\u5E33\u5E36\u5E38\u5E3D\u5E45\u5E55"
                + "\u5E63\u5E6B\u5E72-\u5E74\u5E78\u5E79\u5E7B-\u5E7E\u5E87\u5E8A"
                + "\u5E8F\u5E95\u5E97\u5E9A\u5E9C\u5EA6\u5EA7\u5EAB\u5EAD\u5EB7"
                + "\u5EB8\u5EC9\u5ED6\u5EE0\u5EE2\u5EE3\u5EF3\u5EF6\u5EF7\u5EFA"
                + "\u5F04\u5F0F\u5F15\u5F17\u5F18\u5F1F\u5F26\u5F31\u5F35\u5F37"
                + "\u5F48\u5F4A\u5F4C\u5F4E\u5F5D\u5F5E\u5F62\u5F65\u5F69\u5F6C"
                + "\u5F6D\u5F70\u5F71\u5F79\u5F7C\u5F80\u5F81\u5F85\u5F88\u5F8B"
                + "\u5F8C\u5F90-\u5F92\u5F97\u5F9E\u5FA9\u5FAE\u5FB5\u5FB7\u5FB9\u5FC3"
                + "\u5FC5\u5FCC\u5FCD\u5FD7-\u5FD9\u5FE0\u5FEB\u5FF5\u5FFD\u600E\u6012"
                + "\u6015\u6016\u601D\u6021\u6025\u6027\u6028\u602A\u6046\u6050"
                + "\u6062\u6065\u6068\u6069\u606D\u606F\u6070\u6085\u6089\u6094"
                + "\u609F\u60A0\u60A8\u60B2\u60B6\u60C5\u60D1\u60DC\u60E0\u60E1"
                + "\u60F1\u60F3\u60F9\u6101\u6108\u6109\u610F\u611A\u611B\u611F"
                + "\u6148\u614B\u6155\u6158\u6162\u6163\u6167\u616E\u6170\u6176"
                + "\u617E\u6182\u6190\u6191\u61B2\u61B6\u61BE\u61C2\u61C9\u61F6"
                + "\u61F7\u61FC\u6200\u6208\u620A\u620C\u6210-\u6212\u6216\u622A"
                + "\u6230\u6232\u6234\u6236\u623F-\u6241\u6247\u624B\u624D\u624E"
                + "\u6253\u6258\u6263\u6265\u626D\u626F\u6279\u627E-\u6280\u6284\u628A"
                + "\u6293\u6295\u6297\u6298\u62AB\u62AC\u62B1\u62B5\u62B9\u62BD"
                + "\u62C6\u62C9\u62CB\u62CD\u62CF\u62D2\u62D4\u62D6\u62DB\u62DC"
                + "\u62EC\u62F3\u62FC\u62FE\u62FF\u6301\u6307\u6309\u6311\u6316"
                + "\u632A\u632F\u633A\u6350\u6355\u6368\u6372\u6377\u6383\u6388"
                + "\u6389\u638C\u6392\u639B\u63A1\u63A2\u63A5\u63A7\u63A8\u63AA"
                + "\u63CF\u63D0\u63D2\u63DA\u63DB\u63E1\u63EE\u63F4\u640D\u6416"
                + "\u641C\u641E\u642C\u642D\u6436\u6458\u6469\u6478\u6490\u6492"
                + "\u649E\u64A3\u64A5\u64AD\u64BE\u64BF\u64C1\u64C7\u64CA\u64CB"
                + "\u64CD\u64CE\u64D4\u64DA\u64E0\u64E6\u64EC\u64F4\u64FA\u64FE"
                + "\u651D\u652F\u6536\u6539\u653B\u653E\u653F\u6545\u6548\u654D"
                + "\u654F\u6551\u6557-\u6559\u655D\u6562\u6563\u6566\u656C\u6574"
                + "\u6575\u6578\u6587\u6590\u6597\u6599\u65AF\u65B0\u65B7\u65B9"
                + "\u65BC\u65BD\u65C1\u65C5\u65CB\u65CF\u65D7\u65E2\u65E5\u65E6"
                + "\u65E9\u65ED\u65FA\u6602\u6606\u6607\u660C\u660E\u660F\u6613"
                + "\u661F\u6620\u6625\u6628\u662D\u662F\u6642\u6649\u6652\u665A"
                + "\u6668\u666E\u666F\u6674\u6676\u667A\u6691\u6696\u6697\u66AB"
                + "\u66B4\u66C6\u66C9\u66F0\u66F2\u66F4\u66F8\u66FC\u66FE-\u6700"
                + "\u6703\u6708\u6709\u670B\u670D\u6717\u671B\u671D\u671F\u6728"
                + "\u672A-\u672D\u6731\u6735\u6749\u674E\u6750\u6751\u675C\u675F"
                + "\u676F-\u6771\u677E\u677F\u6790\u6797\u679C\u679D\u67B6\u67CF"
                + "\u67D0\u67D3\u67D4\u67E5\u67EC\u67EF\u67F3\u67F4\u6817\u6821"
                + "\u6838\u6839\u683C\u6843\u6848\u684C\u6851\u6881\u6885\u689D"
                + "\u68A8\u68AF\u68B0\u68B5\u68C4\u68C9\u68CB\u68D2\u68DA\u68EE"
                + "\u6905\u690D\u6930\u694A\u6953\u6954\u695A\u696D\u6975\u6982"
                + "\u699C\u69AE\u69CB\u69CD\u6A02\u6A13\u6A19\u6A1E\u6A21\u6A23"
                + "\u6A39\u6A4B\u6A5F\u6A6B\u6A80\u6A94\u6AA2\u6B04\u6B0A\u6B21"
                + "\u6B23\u6B32\u6B3A\u6B3D\u6B3E\u6B49\u6B4C\u6B50\u6B61-\u6B66"
                + "\u6B72\u6B77\u6B78\u6B7B\u6B8A\u6B98\u6BB5\u6BBA\u6BBC\u6BC0"
                + "\u6BC5\u6BCD\u6BCF\u6BD2\u6BD4\u6BDB\u6BEB\u6C0F\u6C11\u6C23"
                + "\u6C34\u6C38\u6C42\u6C57\u6C5D\u6C5F-\u6C61\u6C6A\u6C76\u6C7A\u6C7D"
                + "\u6C83\u6C88\u6C89\u6C92\u6C96\u6C99\u6CB3\u6CB9\u6CBB\u6CBF"
                + "\u6CC1\u6CC9\u6CCA\u6CD5\u6CE1\u6CE2\u6CE5\u6CE8\u6CF0\u6CF3"
                + "\u6D0B\u6D17\u6D1B\u6D1E\u6D29\u6D2A\u6D32\u6D3B\u6D3D\u6D3E"
                + "\u6D41\u6D66\u6D69\u6D6A\u6D6E\u6D77\u6D85\u6D87-\u6D89\u6DAF\u6DB2"
                + "\u6DB5\u6DBC\u6DD1\u6DDA\u6DE1\u6DE8\u6DF1\u6DF7\u6DFA\u6E05"
                + "\u6E1B\u6E21\u6E2C\u6E2F\u6E38\u6E3E\u6E56\u6E6F\u6E90\u6E96"
                + "\u6E9D\u6EAA\u6EAB\u6EC4\u6EC5\u6ECB\u6ED1\u6EF4\u6EFE\u6EFF"
                + "\u6F02\u6F0F\u6F14\u6F20\u6F22\u6F2B\u6F32\u6F38\u6F54\u6F58"
                + "\u6F5B\u6F6E\u6F8E\u6FA4\u6FB3\u6FC0\u6FC3\u6FDF\u6FE4\u6FEB"
                + "\u6FF1\u700F\u704C\u7063\u706B\u7070\u707D\u708E\u70AE\u70B8"
                + "\u70BA\u70C8\u70CF\u70E4\u7121\u7126\u7136\u7159\u715E\u7167"
                + "\u7169\u718A\u719F\u71B1\u71C3\u71C8\u71D2\u71DF\u71E6\u7206"
                + "\u7210\u721B\u722A\u722C\u722D\u7235\u7236\u7238\u723A\u723D"
                + "\u723E\u7246-\u7248\u724C\u7259\u725B\u7260\u7267\u7269\u7272\u7279"
                + "\u727D\u72A7\u72AF\u72C0\u72C2\u72C4\u72D0\u72D7\u72E0\u72FC"
                + "\u731B\u731C\u7334\u7336\u7344\u7345\u734E\u7368\u7372\u7378"
                + "\u737B\u7384\u7387\u7389\u738B\u73A9\u73AB\u73B2\u73BB\u73CA"
                + "\u73CD\u73E0\u73E5\u73ED\u73FE\u7403\u7406\u7409\u742A\u7433"
                + "\u7434\u7459\u745A\u745C\u745E\u745F\u7464\u746A\u7470\u74B0"
                + "\u74DC\u74E6\u74F6\u7518\u751A\u751C\u751F\u7522\u7528\u752B"
                + "\u7530-\u7533\u7537\u7538\u754C\u7559\u7562\u7565\u756A\u756B"
                + "\u7570\u7576\u7586\u758F\u7591\u75BC\u75C5\u75D5\u75DB\u75F4"
                + "\u760B\u7642\u7661\u7678\u767B-\u767E\u7684\u7686\u7687\u76AE"
                + "\u76C3\u76CA\u76DB\u76DC\u76DF\u76E1\u76E3\u76E4\u76E7\u76EE"
                + "\u76F2\u76F4\u76F8\u76FC\u76FE\u7701\u7709\u770B\u771F\u7720"
                + "\u773C\u773E\u775B\u7761\u7763\u77A7\u77AD\u77DB\u77E3\u77E5"
                + "\u77ED\u77F3\u7802\u780D\u7814\u7832\u7834\u786C\u788E\u7891"
                + "\u7897\u789F\u78A7\u78A9\u78B0\u78BA\u78BC\u78C1\u78E8\u78EF"
                + "\u7901\u790E\u7919\u793A\u793E\u7955\u7956\u795A\u795B\u795D"
                + "\u795E\u7965\u7968\u797F\u7981\u798D-\u798F\u79AA\u79AE\u79C0\u79C1"
                + "\u79CB\u79D1\u79D2\u79D8\u79DF\u79E4\u79E6\u79FB\u7A05\u7A0B"
                + "\u7A0D\u7A2E\u7A31\u7A3F\u7A46\u7A4C\u7A4D\u7A69\u7A76\u7A79"
                + "\u7A7A\u7A7F\u7A81\u7A97\u7AA9\u7AAE\u7AB6\u7ACB\u7AD9\u7ADF"
                + "\u7AE0\u7AE5\u7AEF\u7AF6\u7AF9\u7B11\u7B1B\u7B26\u7B28\u7B2C"
                + "\u7B46\u7B49\u7B4B\u7B54\u7B56\u7B80\u7B97\u7BA1\u7BAD\u7BB1"
                + "\u7BC0\u7BC4\u7BC7\u7BC9\u7C21\u7C2B\u7C3D\u7C3F\u7C43\u7C4C"
                + "\u7C4D\u7C64\u7C73\u7C89\u7C97\u7CB5\u7CBE\u7CCA\u7CD5\u7CDF"
                + "\u7CFB\u7CFE\u7D00\u7D04\u7D05\u7D0D\u7D10\u7D14\u7D19-\u7D1B"
                + "\u7D20\u7D22\u7D2B\u7D2F\u7D30\u7D39\u7D42\u7D44\u7D50\u7D55"
                + "\u7D61\u7D66\u7D71\u7D72\u7D93\u7D9C\u7DA0\u7DAD\u7DB1\u7DB2"
                + "\u7DCA\u7DD2\u7DDA\u7DE3\u7DE8\u7DE9\u7DEC\u7DEF\u7DF4\u7E1B"
                + "\u7E23\u7E2E\u7E31\u7E3D\u7E3E\u7E41\u7E46\u7E54\u7E5E\u7E6A"
                + "\u7E73\u7E7C\u7E8C\u7F38\u7F3A\u7F55\u7F6A\u7F6E\u7F70\u7F72"
                + "\u7F75\u7F77\u7F85\u7F8A\u7F8E\u7F9E\u7FA4\u7FA9\u7FBD\u7FC1"
                + "\u7FD2\u7FD4\u7FF0\u7FF9\u7FFB\u7FFC\u8000\u8001\u8003\u8005"
                + "\u800C\u800D\u8010\u8017\u8033\u8036\u804A\u8056\u805A\u805E"
                + "\u806F\u8070\u8072\u8077\u807D\u8089\u809A\u80A1\u80A5\u80A9"
                + "\u80AF\u80B2\u80CC\u80CE\u80D6\u80DE\u80E1\u80F8\u80FD\u8106"
                + "\u812B\u8153\u8154\u8166\u8170\u8173\u817F\u81BD\u81C9\u81D8"
                + "\u81E3\u81E5\u81E8\u81EA\u81ED\u81F3\u81F4\u81FA\u8207-\u820A"
                + "\u820C\u820D\u8212\u821E\u821F\u822A\u822C\u8239\u8266\u826F"
                + "\u8272\u827E\u8292\u829D\u82AC\u82B1\u82B3\u82D7\u82E5\u82E6"
                + "\u82F1\u8305\u8328\u832B\u8332\u8336\u8349\u8352\u8377\u837C"
                + "\u8389\u838A\u838E\u83AB\u83DC\u83E9\u83EF\u83F2\u8404\u840A"
                + "\u842C\u843D\u8449\u8457\u845B\u8461\u8482\u8499\u84B2\u84BC"
                + "\u84CB\u84EC\u84EE\u8515\u8521\u8523\u856D\u8584\u85A6\u85A9"
                + "\u85AA\u85C9\u85CD\u85CF\u85DD\u85E4\u85E5\u8606\u8607\u862D"
                + "\u864E\u8655\u865B\u865F\u8667\u86A9\u86C7\u86CB\u86D9\u8700"
                + "\u8702\u871C\u8776\u878D\u87A2\u87F2\u87F9\u880D\u883B\u8840"
                + "\u884C\u8853\u8857\u885B\u885D\u8861\u8863\u8868\u888B\u88AB"
                + "\u88C1\u88C2\u88D5\u88D8\u88DC\u88DD\u88E1\u88FD\u8907\u8932"
                + "\u897F\u8981\u8986\u898B\u898F\u8996\u89AA\u89BA\u89BD\u89C0"
                + "\u89D2\u89E3\u89F8\u8A00\u8A02\u8A08\u8A0A\u8A0E\u8A13\u8A17"
                + "\u8A18\u8A25\u8A2A\u8A2D\u8A31\u8A34\u8A3B\u8A3C\u8A55\u8A5E"
                + "\u8A62\u8A66\u8A69\u8A71-\u8A73\u8A87\u8A8C\u8A8D\u8A93\u8A95\u8A9E"
                + "\u8AA0\u8AA4\u8AAA\u8AB0\u8AB2\u8ABC\u8ABF\u8AC7\u8ACB\u8AD2"
                + "\u8AD6\u8AF8\u8AFA\u8AFE\u8B00\u8B02\u8B1B\u8B1D\u8B2C\u8B49"
                + "\u8B58\u8B5C\u8B66\u8B6F\u8B70\u8B77\u8B7D\u8B80\u8B8A\u8B93"
                + "\u8B9A\u8C37\u8C46\u8C48\u8C50\u8C61\u8C6A\u8C6C\u8C8C\u8C93"
                + "\u8C9D\u8C9E\u8CA0-\u8CA2\u8CA8\u8CAA-\u8CAC\u8CB4\u8CB7\u8CBB\u8CBC"
                + "\u8CC0\u8CC7\u8CC8\u8CD3\u8CDC\u8CDE\u8CE2-\u8CE4\u8CE6\u8CEA"
                + "\u8CED\u8CF4\u8CFA\u8CFC\u8CFD\u8D08\u8D0A\u8D0F\u8D64\u8D6B"
                + "\u8D70\u8D77\u8D85\u8D8A\u8D95\u8D99\u8DA3\u8DA8\u8DB3\u8DCC"
                + "\u8DCE\u8DD1\u8DDD\u8DDF\u8DE1\u8DEF\u8DF3\u8E0F\u8E22\u8E5F"
                + "\u8E64\u8E8D\u8EAB\u8EB2\u8ECA\u8ECC\u8ECD\u8ED2\u8EDF\u8F03"
                + "\u8F09\u8F14\u8F15\u8F1B\u8F1D\u8F29\u8F2A\u8F2F\u8F38\u8F49"
                + "\u8F5F\u8F9B\u8FA6\u8FA8\u8FAD\u8FAF-\u8FB2\u8FC5\u8FCE\u8FD1\u8FD4"
                + "\u8FE6\u8FEA\u8FEB\u8FF0\u8FF4\u8FF7\u8FFD\u9000\u9001\u9003"
                + "\u9006\u900F\u9010\u9014\u9019-\u901B\u901D\u901F\u9020\u9022"
                + "\u9023\u9031\u9032\u9038\u903C\u9047\u904A\u904B\u904D\u904E"
                + "\u9053-\u9055\u9059\u905C\u9060\u9069\u906D\u906E\u9072\u9077"
                + "\u9078\u907A\u907F-\u9081\u9084\u908A\u908F\u90A3\u90A6\u90AA"
                + "\u90B1\u90CE\u90E8\u90ED\u90F5\u90FD\u9102\u9109\u912D\u9130"
                + "\u9149\u914B\u914D\u9152\u9177\u9178\u9189\u9192\u919C\u91AB"
                + "\u91C7\u91CB-\u91CF\u91D1\u91DD\u91E3\u9234\u9262\u9280\u9285\u9296"
                + "\u9298\u92B3\u92B7\u92D2\u92FC\u9304\u9322\u9326\u932B\u932F"
                + "\u934B\u9375\u937E\u938A\u9396\u93AE\u93E1\u9418\u9435\u9451"
                + "\u9577\u9580\u9583\u9589\u958B\u958F\u9592\u9593\u95A3\u95B1"
                + "\u95C6\u95CA\u95CD\u95D0\u95DC\u95E1\u9632\u963B\u963F\u9640"
                + "\u9644\u964D\u9650\u9662-\u9664\u966A\u9670\u9673\u9675-\u9678"
                + "\u967D\u9686\u968A\u968E\u9694\u969B\u969C\u96A8\u96AA\u96B1"
                + "\u96B4\u96BB\u96C4-\u96C6\u96C9\u96D6\u96D9\u96DC\u96DE\u96E2"
                + "\u96E3\u96E8\u96EA\u96F2\u96F6\u96F7\u96FB\u9700\u9707\u970D"
                + "\u9727\u9732\u9738\u9739\u9742\u9748\u9752\u9756\u975C\u975E"
                + "\u9760\u9762\u9769\u977C\u978B\u97C3\u97CB\u97D3\u97F3\u97FB"
                + "\u97FF\u9801\u9802\u9805\u9806\u9808\u9810\u9811\u9813\u9817"
                + "\u9818\u981E\u982D\u983B\u9846\u984C\u984D\u984F\u9858\u985E"
                + "\u9867\u986F\u98A8\u98C4\u98DB\u98DF\u98EF\u98F2\u98FD\u98FE"
                + "\u9905\u990A\u9910\u9918\u9928\u9996\u9999\u99AC\u99D0\u99D5"
                + "\u99DB\u9A0E\u9A19\u9A37\u9A45\u9A57\u9A5A\u9AA8\u9AD4\u9AD8"
                + "\u9AEE\u9B06\u9B25\u9B27\u9B31\u9B3C\u9B41\u9B42\u9B45\u9B54"
                + "\u9B5A\u9B6F\u9BAE\u9CE5\u9CF3\u9CF4\u9D3B\u9D5D\u9DF9\u9E7F"
                + "\u9E97\u9EA5\u9EB5\u9EBB\u9EBC\u9EC3\u9ECE\u9ED1\u9ED8\u9EDE"
                + "\u9EE8\u9F13\u9F20\u9F3B\u9F4A\u9F4B\u9F52\u9F61\u9F8D\u9F9C"
                + "\uFE30-\uFE44\uFE49-\uFE52\uFE54-\uFE61\uFE63\uFE68\uFE6A\uFE6B"
                + "\uFF01-\uFF03\uFF05-\uFF0A\uFF0C-\uFF0F\uFF1A\uFF1B\uFF1F\uFF20"
                + "\uFF3B-\uFF3D\uFF3F\uFF5B\uFF5D]"));
        EXEMPLAR_MAP.put("zgh", new UnicodeSet("[\u2D30\u2D31\u2D33\u2D37\u2D39\u2D3B-\u2D3D"
                + "\u2D40\u2D43-\u2D45\u2D47\u2D49\u2D4A\u2D4D-\u2D4F\u2D53-\u2D56\u2D59-\u2D5C"
                + "\u2D5F\u2D61-\u2D63\u2D65{\u2D33\u2D6F}{\u2D3D\u2D6F}]"));
        EXEMPLAR_MAP.put("zh-Hans", new UnicodeSet("[\u2015\u2016\u2025\u2030\u2035\u203B"
                + "\u3001-\u3003\u3008-\u3011\u3014-\u3017\u301D\u301E\u4E00\u4E01\u4E03"
                + "\u4E07-\u4E0E\u4E11\u4E13\u4E14\u4E16\u4E18-\u4E1A\u4E1C\u4E1D\u4E22\u4E24\u4E25"
                + "\u4E27\u4E2A\u4E2D\u4E30\u4E32\u4E34\u4E38-\u4E3B\u4E3D\u4E3E\u4E43\u4E45"
                + "\u4E48\u4E49\u4E4B-\u4E50\u4E54\u4E56\u4E58\u4E59\u4E5D\u4E5F-\u4E61"
                + "\u4E66\u4E70\u4E71\u4E7E\u4E86\u4E88\u4E89\u4E8B\u4E8C\u4E8E"
                + "\u4E8F\u4E91\u4E92\u4E94\u4E95\u4E9A\u4E9B\u4EA1\u4EA4-\u4EA8"
                + "\u4EAB\u4EAC\u4EAE\u4EB2\u4EBA\u4EBF-\u4EC2\u4EC5\u4EC7\u4ECA\u4ECB"
                + "\u4ECD\u4ECE\u4ED4\u4ED6\u4ED8\u4ED9\u4EE3-\u4EE5\u4EEA\u4EEC"
                + "\u4EF0\u4EF2\u4EF6\u4EF7\u4EFB\u4EFD\u4EFF\u4F01\u4F0A\u4F0D"
                + "\u4F0F-\u4F11\u4F17-\u4F1A\u4F1F\u4F20\u4F24\u4F26\u4F2F\u4F30"
                + "\u4F34\u4F38\u4F3C\u4F3D\u4F46\u4F4D-\u4F51\u4F53\u4F55\u4F59\u4F5B"
                + "\u4F5C\u4F60\u4F64\u4F69\u4F73\u4F7F\u4F8B\u4F9B\u4F9D\u4FA0"
                + "\u4FA3\u4FA6-\u4FA8\u4FAC\u4FAF\u4FB5\u4FBF\u4FC3\u4FC4\u4FCA\u4FD7"
                + "\u4FDD\u4FE1\u4FE9\u4FEE\u4FF1\u4FFE\u500D\u5012\u5019\u501A"
                + "\u501F\u5026\u503C\u503E\u5047\u504C\u504F\u505A\u505C\u5065"
                + "\u5076\u5077\u5088\u50A3\u50A8\u50AC\u50B2\u50BB\u50CF\u50E7"
                + "\u50F3\u5112\u513F\u5141\u5143-\u5146\u5148\u5149\u514B\u514D"
                + "\u5151\u5154\u515A\u5165\u5168\u516B-\u516E\u5170\u5171\u5173-\u5179"
                + "\u517B-\u517D\u5185\u5188\u518C\u518D\u5192\u5199\u519B\u519C"
                + "\u51A0\u51AC\u51B0\u51B2\u51B3\u51B5\u51B7\u51C6\u51CC\u51CF"
                + "\u51DD\u51E0\u51E1\u51E4\u51ED\u51EF\u51F0\u51FA\u51FB\u51FD"
                + "\u5200\u5206\u5207\u520A\u5211\u5212\u5217-\u521B\u521D\u5224"
                + "\u5229\u522B\u5230\u5236-\u5238\u523A\u523B\u5242\u524D\u5251\u5267"
                + "\u5269\u526A\u526F\u5272\u529B\u529D-\u52A1\u52A3\u52A8-\u52AB"
                + "\u52B1-\u52B3\u52BF\u52C7\u52C9\u52CB\u52D2\u52E4\u52FE\u52FF"
                + "\u5305\u5306\u5308\u5316\u5317\u5319\u5339-\u533B\u5341\u5343"
                + "\u5347\u5348\u534A\u534E\u534F\u5351-\u5353\u5355-\u5357\u535A\u535E"
                + "\u5360-\u5362\u536B\u536F-\u5371\u5373\u5374\u5377\u5382\u5384-\u5386"
                + "\u5389\u538B-\u538D\u5398\u539A\u539F\u53BB\u53BF\u53C2\u53C8-\u53CD"
                + "\u53D1\u53D4\u53D6-\u53D9\u53E3-\u53E6\u53EA-\u53ED\u53EF\u53F0"
                + "\u53F2\u53F3\u53F6-\u53F9\u5403\u5404\u5408-\u540A\u540C-\u540E"
                + "\u5410\u5411\u5413\u5415\u5417\u541B\u541D\u541F\u5426\u5427"
                + "\u542B\u542C\u542F\u5435\u5438\u5439\u543B\u543E\u5440\u5446"
                + "\u5448\u544A\u5450\u5458\u545C\u5462\u5466\u5468\u5473\u5475"
                + "\u547C\u547D\u548C\u5496\u54A6-\u54A8\u54AA\u54AC\u54AF\u54B1"
                + "\u54C0\u54C1\u54C7-\u54C9\u54CD\u54CE\u54DF\u54E5\u54E6\u54E9"
                + "\u54EA\u54ED\u54F2\u5509\u5510\u5524\u552C\u552E\u552F\u5531"
                + "\u5537\u5546\u554A\u5561\u5565\u5566\u556A\u5580\u5582\u5584"
                + "\u5587\u558A\u558F\u5594\u559C\u559D\u55B5\u55B7\u55BB\u55D2"
                + "\u55E8\u55EF\u5609\u561B\u5634\u563B\u563F\u5668\u56DB\u56DE"
                + "\u56E0\u56E2\u56ED\u56F0\u56F4\u56FA\u56FD\u56FE\u5706\u5708"
                + "\u571F\u5723\u5728\u572D\u5730\u5733\u573A\u573E\u5740\u5747"
                + "\u574E\u5750\u5751\u5757\u575A-\u575D\u5761\u5764\u5766\u576A"
                + "\u5782\u5783\u578B\u5792\u57C3\u57CB\u57CE\u57D4\u57DF\u57F9"
                + "\u57FA\u5802\u5806\u5815\u5821\u5824\u582A\u5851\u5854\u585E"
                + "\u586B\u5883\u589E\u58A8\u58C1\u58E4\u58EB\u58EC\u58EE\u58F0"
                + "\u5904\u5907\u590D\u590F\u5915\u5916\u591A\u591C\u591F\u5925"
                + "\u5927\u5929-\u592B\u592E\u5931\u5934\u5937-\u593A\u5947-\u5949"
                + "\u594B\u594E\u594F\u5951\u5954\u5956\u5957\u5965\u5973\u5974"
                + "\u5976\u5979\u597D\u5982\u5987\u5988\u5996\u5999\u59A5\u59A8"
                + "\u59AE\u59B9\u59BB\u59C6\u59CA\u59CB\u59D0\u59D1\u59D3\u59D4"
                + "\u59FF\u5A01\u5A03\u5A04\u5A18\u5A1C\u5A1F\u5A31\u5A46\u5A5A"
                + "\u5A92\u5AC1\u5ACC\u5AE9\u5B50\u5B54\u5B55\u5B57-\u5B59\u5B5C\u5B5D"
                + "\u5B5F\u5B63\u5B64\u5B66\u5B69\u5B81\u5B83\u5B87-\u5B89\u5B8B\u5B8C"
                + "\u5B8F\u5B97-\u5B9E\u5BA1-\u5BA4\u5BAA\u5BB3\u5BB4\u5BB6"
                + "\u5BB9\u5BBD-\u5BBF\u5BC2\u5BC4-\u5BC7\u5BCC\u5BD2\u5BDD-\u5BDF"
                + "\u5BE1\u5BE8\u5BF8\u5BF9\u5BFB\u5BFC\u5BFF\u5C01\u5C04\u5C06"
                + "\u5C0A\u5C0F\u5C11\u5C14\u5C16\u5C18\u5C1A\u5C1D\u5C24\u5C31"
                + "\u5C3A\u5C3C-\u5C3E\u5C40-\u5C42\u5C45\u5C4B\u5C4F\u5C55\u5C5E\u5C60"
                + "\u5C71\u5C7F\u5C81\u5C82\u5C97\u5C98\u5C9A\u5C9B\u5CB3\u5CB8"
                + "\u5CE1\u5CF0\u5D07\u5D29\u5D34\u5DDD\u5DDE\u5DE1\u5DE5-\u5DE8"
                + "\u5DEB\u5DEE\u5DF1-\u5DF4\u5DF7\u5DFD\u5E01-\u5E03\u5E05\u5E08"
                + "\u5E0C\u5E10\u5E15\u5E16\u5E1D\u5E26\u5E2D\u5E2E\u5E38\u5E3D"
                + "\u5E45\u5E55\u5E72-\u5E74\u5E76\u5E78\u5E7B-\u5E7D\u5E7F\u5E86"
                + "\u5E8A\u5E8F\u5E93-\u5E95\u5E97\u5E99\u5E9A\u5E9C\u5E9E\u5E9F"
                + "\u5EA6\u5EA7\u5EAD\u5EB7\u5EB8\u5EC9\u5ED6\u5EF6\u5EF7\u5EFA"
                + "\u5F00\u5F02-\u5F04\u5F0A\u5F0F\u5F15\u5F17\u5F18\u5F1F\u5F20\u5F25"
                + "\u5F26\u5F2F\u5F31\u5F39\u5F3A\u5F52\u5F53\u5F55\u5F5D\u5F62"
                + "\u5F69\u5F6C\u5F6D\u5F70\u5F71\u5F77\u5F79\u5F7B\u5F7C\u5F80"
                + "\u5F81\u5F84\u5F85\u5F88\u5F8B\u5F8C\u5F90\u5F92\u5F97\u5FAA"
                + "\u5FAE\u5FB5\u5FB7\u5FC3\u5FC5\u5FC6\u5FCC\u5FCD\u5FD7-\u5FD9"
                + "\u5FE0\u5FE7\u5FEB\u5FF5\u5FFD\u6000\u6001\u600E\u6012\u6015"
                + "\u6016\u601D\u6021\u6025\u6027\u6028\u602A\u603B\u604B\u6050"
                + "\u6062\u6068\u6069\u606D\u606F\u6070\u6076\u607C\u6084\u6089"
                + "\u6094\u609F\u60A0\u60A3\u60A8\u60B2\u60C5\u60D1\u60DC\u60E0"
                + "\u60E7\u60E8\u60EF\u60F3\u60F9\u6101\u6108\u6109\u610F\u611A"
                + "\u611F\u6127\u6148\u614E\u6155\u6162\u6167\u6170\u61BE\u61C2"
                + "\u61D2\u6208\u620A\u620C\u620F-\u6212\u6216\u6218\u622A\u6234"
                + "\u6237\u623F-\u6241\u6247\u624B\u624D\u624E\u6251\u6253\u6258\u6263"
                + "\u6267\u6269\u626B-\u626F\u6279\u627E-\u6280\u6284\u628A\u6291\u6293"
                + "\u6295\u6297\u6298\u62A2\u62A4\u62A5\u62AB\u62AC\u62B1\u62B5"
                + "\u62B9\u62BD\u62C5\u62C6\u62C9\u62CD\u62D2\u62D4\u62D6\u62D8"
                + "\u62DB\u62DC\u62DF\u62E5\u62E6\u62E8\u62E9\u62EC\u62F3\u62F7"
                + "\u62FC\u62FE\u62FF\u6301\u6307\u6309\u6311\u6316\u631D\u6321"
                + "\u6324\u6325\u632A\u632F\u633A\u6349\u6350\u6355\u635F\u6361"
                + "\u6362\u636E\u6377\u6388\u6389\u638C\u6392\u63A2\u63A5\u63A7-\u63AA"
                + "\u63B8\u63CF\u63D0\u63D2\u63E1\u63F4\u641C\u641E\u642C\u642D"
                + "\u6444\u6446\u644A\u6454\u6458\u6469\u6478\u6492\u649E\u64A4"
                + "\u64AD\u64CD\u64CE\u64E6\u652F\u6536\u6539\u653B\u653E\u653F"
                + "\u6545\u6548\u654C\u654F\u6551\u6559\u655D\u6562\u6563\u6566"
                + "\u656C\u6570\u6572\u6574\u6587\u658B\u6590\u6597\u6599\u659C"
                + "\u65A5\u65AD\u65AF\u65B0\u65B9\u65BC\u65BD\u65C1\u65C5\u65CB"
                + "\u65CF\u65D7\u65E0\u65E2\u65E5-\u65E9\u65ED\u65F6\u65FA\u6602"
                + "\u6606\u660C\u660E\u660F\u6613\u661F\u6620\u6625\u6628\u662D"
                + "\u662F\u663E\u6643\u664B\u6652\u6653\u665A\u6668\u666E\u666F"
                + "\u6674\u6676\u667A\u6682\u6691\u6696\u6697\u66AE\u66B4\u66F0"
                + "\u66F2\u66F4\u66F9\u66FC\u66FE-\u6700\u6708\u6709\u670B\u670D"
                + "\u6717\u671B\u671D\u671F\u6728\u672A-\u672D\u672F\u6731\u6735\u673A"
                + "\u6740\u6742\u6743\u6749\u674E\u6750\u6751\u675C\u675F\u6761"
                + "\u6765\u6768\u676F\u6770\u677E\u677F\u6781\u6784\u6790\u6797"
                + "\u679C\u679D\u67A2\u67AA\u67AB\u67B6\u67CF\u67D0\u67D3\u67D4"
                + "\u67E5\u67EC\u67EF\u67F3\u67F4\u6807\u680B\u680F\u6811\u6821"
                + "\u6837-\u6839\u683C\u6843\u6846\u6848\u684C\u6851\u6863\u6865"
                + "\u6881\u6885\u68A6\u68AF\u68B0\u68B5\u68C0\u68C9\u68CB\u68D2"
                + "\u68DA\u68EE\u6905\u690D\u6930\u6954\u695A\u6960\u697C\u6982"
                + "\u699C\u6A21\u6A31\u6A80\u6B20-\u6B23\u6B27\u6B32\u6B3A\u6B3E"
                + "\u6B49\u6B4C\u6B62-\u6B66\u6B6A\u6B7B\u6B8A\u6B8B\u6BB5\u6BC5"
                + "\u6BCD\u6BCF\u6BD2\u6BD4\u6BD5\u6BDB\u6BEB\u6C0F\u6C11\u6C14"
                + "\u6C1B\u6C34\u6C38\u6C42\u6C47\u6C49\u6C57\u6C5D\u6C5F-\u6C61"
                + "\u6C64\u6C6A\u6C76\u6C7D\u6C83\u6C88\u6C89\u6C99\u6C9F\u6CA1"
                + "\u6CA7\u6CB3\u6CB9\u6CBB\u6CBF\u6CC9\u6CCA\u6CD5\u6CDB\u6CE1-\u6CE3"
                + "\u6CE5\u6CE8\u6CF0\u6CF3\u6CFD\u6D0B\u6D17\u6D1B\u6D1E\u6D25"
                + "\u6D2A\u6D32\u6D3B\u6D3D\u6D3E\u6D41\u6D45\u6D4B\u6D4E\u6D4F"
                + "\u6D51\u6D53\u6D59\u6D66\u6D69\u6D6A\u6D6E\u6D74\u6D77\u6D85"
                + "\u6D88\u6D89\u6D9B\u6DA8\u6DAF\u6DB2\u6DB5\u6DCB\u6DD1\u6DD8"
                + "\u6DE1\u6DF1\u6DF7\u6DFB\u6E05\u6E10\u6E21\u6E23\u6E29\u6E2F"
                + "\u6E34\u6E38\u6E56\u6E7E\u6E90\u6E9C\u6EAA\u6ECB\u6ED1\u6ED5"
                + "\u6EE1\u6EE5\u6EE8\u6EF4\u6F02\u6F0F\u6F14\u6F20\u6F2B\u6F58"
                + "\u6F5C\u6F6E\u6F8E\u6FB3\u6FC0\u704C\u706B\u706D\u706F\u7070"
                + "\u7075\u707F\u7089\u708E\u70AE\u70B8\u70B9\u70C2\u70C8\u70E4"
                + "\u70E6\u70E7\u70ED\u7126\u7136\u714C\u715E\u7167\u716E\u718A"
                + "\u719F\u71C3\u71D5\u7206\u722A\u722C\u7231\u7235-\u7238\u723D\u7247"
                + "\u7248\u724C\u7259\u725B\u7261\u7262\u7267\u7269\u7272\u7275"
                + "\u7279\u727A\u72AF\u72B6\u72B9\u72C2\u72D0\u72D7\u72E0\u72EC"
                + "\u72EE\u72F1\u72FC\u731B\u731C\u732A\u732E\u7334\u7384\u7387"
                + "\u7389\u738B\u739B\u73A9\u73AB\u73AF\u73B0\u73B2\u73BB\u73C0"
                + "\u73CA\u73CD\u73E0\u73ED\u7403\u7406\u740A\u742A\u7433\u7434"
                + "\u743C\u7459\u745A\u745C\u745E\u745F\u7470\u7476\u7483\u74DC"
                + "\u74E6\u74F6\u7518\u751A\u751C\u751F\u7528\u752B\u7530-\u7533"
                + "\u7535\u7537\u7538\u753B\u7545\u754C\u7559\u7565\u756A\u7586"
                + "\u758F\u7591\u7597\u75AF\u75B2\u75BC\u75BE\u75C5\u75D5\u75DB"
                + "\u75F4\u7678\u767B\u767D\u767E\u7684\u7686\u7687\u76AE\u76C8"
                + "\u76CA\u76D1\u76D2\u76D6\u76D8\u76DB\u76DF\u76EE\u76F2\u76F4"
                + "\u76F8\u76FC\u76FE\u7701\u7709\u770B\u771F\u7720\u773C\u7740"
                + "\u775B\u7761\u7763\u77A7\u77DB\u77E3\u77E5\u77ED\u77F3\u77F6"
                + "\u7801\u7802\u780D\u7814\u7834\u7840\u7855\u786C\u786E\u788D"
                + "\u788E\u7891\u7897\u789F\u78A7\u78B0\u78C1\u78C5\u78E8\u793A"
                + "\u793C\u793E\u7956\u795A\u795D\u795E\u7965\u7968\u796F\u7978"
                + "\u7981\u7984\u7985\u798F\u79BB\u79C0\u79C1\u79CB\u79CD\u79D1"
                + "\u79D2\u79D8\u79DF\u79E4\u79E6\u79E9\u79EF\u79F0\u79FB\u7A00"
                + "\u7A0B\u7A0D\u7A0E\u7A23\u7A33\u7A3F\u7A46\u7A76\u7A77\u7A79"
                + "\u7A7A\u7A7F\u7A81\u7A97\u7A9D\u7ACB\u7AD9\u7ADE-\u7AE0\u7AE5\u7AEF"
                + "\u7AF9\u7B11\u7B14\u7B1B\u7B26\u7B28\u7B2C\u7B49\u7B4B\u7B51"
                + "\u7B54\u7B56\u7B79\u7B7E\u7B80\u7B97\u7BA1\u7BAD\u7BB1\u7BC7"
                + "\u7BEE\u7C3F\u7C4D\u7C73\u7C7B\u7C89\u7C92\u7C97\u7C9F\u7CA4"
                + "\u7CB9\u7CBE\u7CCA\u7CD5\u7CD6\u7CDF\u7CFB\u7D20\u7D22\u7D27"
                + "\u7D2B\u7D2F\u7E41\u7EA2\u7EA6\u7EA7\u7EAA\u7EAF\u7EB2\u7EB3"
                + "\u7EB5\u7EB7\u7EB8\u7EBD\u7EBF\u7EC3\u7EC4\u7EC6-\u7EC8\u7ECD\u7ECF"
                + "\u7ED3\u7ED5\u7ED8\u7ED9\u7EDC\u7EDD\u7EDF\u7EE7\u7EE9\u7EEA"
                + "\u7EED\u7EF4\u7EF5\u7EFC\u7EFF\u7F05\u7F13\u7F16\u7F18\u7F20"
                + "\u7F29\u7F34\u7F36\u7F38\u7F3A\u7F50\u7F51\u7F55\u7F57\u7F5A"
                + "\u7F62\u7F6A\u7F6E\u7F72\u7F8A\u7F8E\u7F9E\u7FA4\u7FAF\u7FBD"
                + "\u7FC1\u7FC5\u7FD4\u7FD8\u7FE0\u7FF0\u7FFB\u7FFC\u8000\u8001"
                + "\u8003\u8005\u800C\u800D\u8010\u8017\u8033\u8036\u804A\u804C"
                + "\u8054\u8058\u805A\u806A\u8089\u8096\u809A\u80A1\u80A4\u80A5"
                + "\u80A9\u80AF\u80B2\u80C1\u80C6\u80CC\u80CE\u80D6\u80DC\u80DE"
                + "\u80E1\u80F6\u80F8\u80FD\u8106\u8111\u811A\u8131\u8138\u814A"
                + "\u8150\u8153\u8170\u8179\u817E\u817F\u81C2\u81E3\u81EA\u81ED"
                + "\u81F3\u81F4\u820C\u820D\u8212\u821E\u821F\u822A\u822C\u8230"
                + "\u8239\u826E\u826F\u8272\u827A\u827E\u8282\u8292\u829D\u82A6"
                + "\u82AC\u82AD\u82B1\u82B3\u82CD\u82CF\u82D7\u82E5\u82E6\u82F1"
                + "\u8302\u8303\u8328\u832B\u8336\u8349\u8350\u8352\u8363\u836F"
                + "\u8377\u8389\u838E\u83AA\u83AB\u83B1\u83B2\u83B7\u83DC\u83E9"
                + "\u83F2\u8404\u840D\u8424\u8425\u8427\u8428\u843D\u8457\u845B"
                + "\u8461\u8482\u848B\u8499\u84C9\u84DD\u84EC\u8511\u8521\u8584"
                + "\u85AA\u85C9\u85CF\u85E4\u864E\u8651\u866B\u8679\u867D\u867E"
                + "\u8681\u86C7\u86CB\u86D9\u86EE\u8702\u871C\u8776\u878D\u87F9"
                + "\u8822\u8840\u884C\u8857\u8861\u8863\u8865\u8868\u888B\u88AB"
                + "\u88AD\u88C1\u88C2\u88C5\u88D5\u88E4\u897F\u8981\u8986\u89C1"
                + "\u89C2\u89C4\u89C6\u89C8\u89C9\u89D2\u89E3\u8A00\u8A89\u8A93"
                + "\u8B66\u8BA1\u8BA2\u8BA4\u8BA8\u8BA9\u8BAD-\u8BB0\u8BB2\u8BB7"
                + "\u8BB8\u8BBA\u8BBE\u8BBF\u8BC1\u8BC4\u8BC6\u8BC9\u8BCD\u8BD1"
                + "\u8BD5\u8BD7\u8BDA\u8BDD\u8BDE\u8BE2\u8BE5\u8BE6\u8BED\u8BEF"
                + "\u8BF4\u8BF7\u8BF8\u8BFA\u8BFB\u8BFE\u8C01\u8C03\u8C05\u8C08"
                + "\u8C0A\u8C0B\u8C13\u8C1C\u8C22\u8C28\u8C2C\u8C31\u8C37\u8C46"
                + "\u8C61\u8C6A\u8C8C\u8D1D-\u8D1F\u8D21-\u8D25\u8D27-\u8D2A\u8D2D\u8D2F"
                + "\u8D31\u8D34\u8D35\u8D38-\u8D3A\u8D3C\u8D3E\u8D44\u8D4B\u8D4C\u8D4F"
                + "\u8D50\u8D54\u8D56\u8D5A\u8D5B\u8D5E\u8D60\u8D62\u8D64\u8D6B"
                + "\u8D70\u8D75\u8D77\u8D81\u8D85\u8D8A\u8D8B\u8DA3\u8DB3\u8DC3"
                + "\u8DCC\u8DD1\u8DDD\u8DDF\u8DEF\u8DF3\u8E0F\u8E22\u8E29\u8EAB"
                + "\u8EB2\u8F66\u8F68\u8F69\u8F6C\u8F6E-\u8F70\u8F7B\u8F7D\u8F83\u8F85"
                + "\u8F86\u8F88\u8F89\u8F91\u8F93\u8F9B\u8F9E\u8FA8\u8FA9\u8FB0"
                + "\u8FB1\u8FB9\u8FBE\u8FC1\u8FC5\u8FC7\u8FC8\u8FCE\u8FD0\u8FD1"
                + "\u8FD4\u8FD8\u8FD9\u8FDB-\u8FDF\u8FE6\u8FEA\u8FEB\u8FF0\u8FF7\u8FFD"
                + "\u9000-\u9003\u9006\u9009\u900A\u900F\u9010\u9012\u9014\u901A"
                + "\u901B\u901D\u901F\u9020\u9022\u9038\u903B\u903C\u9047\u904D"
                + "\u9053\u9057\u906D\u906E\u9075\u907F\u9080\u9093\u90A3\u90A6"
                + "\u90AA\u90AE\u90B1\u90BB\u90CE\u90D1\u90E8\u90ED\u90FD\u9102"
                + "\u9149\u914B\u914D\u9152\u9177\u9178\u9189\u9192\u91C7\u91CA"
                + "\u91CC-\u91CF\u91D1\u9488\u9493\u949F\u94A2\u94A6\u94AF\u94B1"
                + "\u94BB\u94C1-\u94C3\u94DC\u94E2\u94ED\u94F6\u94FA\u94FE\u9500\u9501"
                + "\u9505\u950B\u9511\u9519\u9521\u9526\u952E\u953A\u9547\u9551"
                + "\u955C\u956D\u957F\u95E8\u95EA\u95ED\u95EE\u95F0\u95F2\u95F4"
                + "\u95F7\u95F9\u95FB\u9601\u9605\u9610\u9614\u961F\u962E\u9632-\u9636"
                + "\u963B\u963F\u9640\u9644-\u9646\u9648\u964D\u9650\u9662\u9664\u9669"
                + "\u966A\u9675-\u9677\u9686\u968F\u9690\u9694\u969C\u96BE\u96C4-\u96C6"
                + "\u96C9\u96E8\u96EA\u96EF\u96F3\u96F6\u96F7\u96FE\u9700\u9707"
                + "\u970D\u9716\u9732\u9738\u9739\u9752\u9756\u9759\u975E\u9760"
                + "\u9762\u9769\u977C\u978B\u9791\u97E6\u97E9\u97F3\u9875\u9876"
                + "\u9879-\u987B\u987D-\u987F\u9884\u9886\u9887\u9891\u9897\u9898"
                + "\u989D\u98CE\u98D8\u98D9\u98DE\u98DF\u9910\u996D\u996E\u9970"
                + "\u9971\u997C\u9986\u9996\u9999\u99A8\u9A6C\u9A71\u9A76\u9A7B"
                + "\u9A7E\u9A8C\u9A91\u9A97\u9A9A\u9AA4\u9AA8\u9AD8\u9B3C\u9B41"
                + "\u9B42\u9B45\u9B54\u9C7C\u9C81\u9C9C\u9E1F\u9E21\u9E23\u9E2D"
                + "\u9E3F\u9E45\u9E64\u9E70\u9E7F\u9EA6\u9EBB\u9EC4\u9ECE\u9ED1"
                + "\u9ED8\u9F13\u9F20\u9F3B\u9F50\u9F7F\u9F84\u9F99\u9F9F\uFE30"
                + "\uFE31\uFE33-\uFE44\uFE49-\uFE52\uFE54-\uFE57\uFE59-\uFE61\uFE63\uFE68"
                + "\uFE6A\uFE6B\uFF01-\uFF03\uFF05-\uFF0A\uFF0C-\uFF0F\uFF1A\uFF1B"
                + "\uFF1F\uFF20\uFF3B-\uFF3D\uFF3F\uFF5B\uFF5D]"));
        EXEMPLAR_MAP.put("zh-Hant", new UnicodeSet("[\u2025\u2027\u2030\u2035\u203B\u203E"
                + "\u3001-\u3003\u3008-\u3011\u3014\u3015\u301D\u301E\u4E00\u4E01\u4E03"
                + "\u4E08-\u4E0D\u4E11\u4E14\u4E16\u4E18\u4E19\u4E1E\u4E1F\u4E26\u4E2D\u4E32\u4E38"
                + "\u4E39\u4E3B\u4E43\u4E45\u4E48\u4E4B\u4E4D-\u4E4F\u4E56\u4E58\u4E59\u4E5D"
                + "\u4E5F\u4E7E\u4E82\u4E86\u4E88\u4E8B\u4E8C\u4E8E\u4E91\u4E92"
                + "\u4E94\u4E95\u4E9B\u4E9E\u4EA1\u4EA4-\u4EA6\u4EA8\u4EAB-\u4EAE"
                + "\u4EBA\u4EC0-\u4EC2\u4EC7\u4ECA\u4ECB\u4ECD\u4ED4\u4ED6\u4ED8\u4ED9"
                + "\u4EE3-\u4EE5\u4EF0\u4EF2\u4EF6\u4EFB\u4EFD\u4F01\u4F0A\u4F0D"
                + "\u4F0F-\u4F11\u4F19\u4F2F\u4F30\u4F34\u4F38\u4F3C\u4F3D\u4F46"
                + "\u4F48\u4F49\u4F4D-\u4F50\u4F54\u4F55\u4F59\u4F5B\u4F5C\u4F60"
                + "\u4F69\u4F73\u4F7F\u4F86\u4F8B\u4F9B\u4F9D\u4FAF\u4FB5\u4FB6"
                + "\u4FBF\u4FC2-\u4FC4\u4FCA\u4FD7\u4FDD\u4FE0\u4FE1\u4FEE\u4FF1\u4FFE"
                + "\u500B\u500D\u5011\u5012\u5019\u501A\u501F\u502B\u503C\u5047"
                + "\u5049\u504F\u505A\u505C\u5065\u5074-\u5077\u5080\u5091\u5099\u50A2"
                + "\u50A3\u50B2\u50B3\u50B7\u50BB\u50BE\u50C5\u50CE\u50CF\u50D1"
                + "\u50E7\u50F3\u50F5\u50F9\u5100\u5104\u5110\u5112\u5118\u511F"
                + "\u512A\u5133\u5137\u513B\u5141\u5143-\u5149\u514B-\u514D\u5152\u5154"
                + "\u5165\u5167-\u5169\u516B-\u516E\u5171\u5175-\u5179\u517C\u518A"
                + "\u518D\u5192\u51A0\u51AC\u51B0\u51B7\u51C6\u51CC\u51DD\u51E1"
                + "\u51F0\u51F1\u51FA\u51FD\u5200\u5206\u5207\u520A\u5217\u521D"
                + "\u5224\u5225\u5229-\u522B\u5230\u5236-\u5238\u523A\u523B\u5247\u524C"
                + "\u524D\u525B\u5269\u526A\u526F\u5272\u5275\u5283\u5287\u5289"
                + "\u528D\u529B\u529F\u52A0\u52A9-\u52AB\u52C1\u52C7\u52C9\u52D2"
                + "\u52D5\u52D9\u52DD\u52DE\u52E2\u52E4\u52F3\u52F5\u52F8\u52FF"
                + "\u5305\u5308\u5316\u5317\u5339\u5340\u5341\u5343\u5347\u5348"
                + "\u534A\u5351-\u5354\u5357\u535A\u535C\u535E\u5360\u5361\u536F-\u5371"
                + "\u5373\u5377\u537B\u5384\u5398\u539A\u539F\u53AD\u53B2\u53BB"
                + "\u53C3\u53C8\u53CA\u53CB\u53CD\u53D4\u53D6\u53D7\u53E2-\u53E6"
                + "\u53EA-\u53ED\u53EF\u53F0\u53F2\u53F3\u53F6\u53F8\u5403\u5404"
                + "\u5408-\u540A\u540C-\u540E\u5410-\u5412\u541B\u541D-\u5420\u5426\u5427"
                + "\u542B\u5433\u5435\u5438\u5439\u543E\u5440\u5442\u5446\u544A"
                + "\u5462\u5468\u5473\u5475\u547C\u547D\u548C\u5496\u54A6\u54A7"
                + "\u54AA\u54AC\u54B1\u54C0\u54C1\u54C7-\u54C9\u54CE\u54E1\u54E5\u54E6"
                + "\u54E9\u54EA\u54ED\u54F2\u5509\u5510\u5514\u552C\u552E\u552F"
                + "\u5531\u5537\u5538\u5546\u554A\u554F\u555F\u5561\u5565\u5566"
                + "\u556A\u5580\u5582\u5584\u5587\u558A\u5594\u559C\u559D\u55AC"
                + "\u55AE\u55B5\u55CE\u55DA\u55E8\u55EF\u5606\u5609\u5617\u561B"
                + "\u5634\u563B\u563F\u5668\u5674\u5687\u56B4\u56C9\u56CC\u56D1"
                + "\u56DB\u56DE\u56E0\u56F0\u56FA\u5708\u570B\u570D\u5712\u5713"
                + "\u5716\u5718\u571C\u571F\u5728\u572D\u5730\u573E\u5740\u5747"
                + "\u574E\u5750\u5761\u5764\u5766\u576A\u5782\u5783\u578B\u57C3"
                + "\u57CE\u57D4\u57DF\u57F7\u57F9\u57FA\u5802\u5805\u5806\u5821"
                + "\u5824\u582A\u5831\u5834\u584A\u5854\u5857\u585E\u586B\u5875"
                + "\u5883\u588E\u589E\u58A8\u58AE\u58C1\u58C7\u58D3\u58D8\u58DE"
                + "\u58E2\u58E4\u58EB\u58EC\u58EF\u58FD\u590F\u5915\u5916\u591A"
                + "\u591C\u5920\u5922\u5925\u5927\u5929-\u592B\u592E\u5931\u5937\u5938"
                + "\u593E\u5947-\u5949\u594E\u594F\u5951\u5954\u5957\u5965\u5967\u596A"
                + "\u596E\u5973\u5974\u5976\u5979\u597D\u5982\u5999\u599D\u59A5"
                + "\u59A8\u59AE\u59B3\u59B9\u59BB\u59C6\u59CA\u59CB\u59D0\u59D1"
                + "\u59D3\u59D4\u59FF\u5A01\u5A03\u5A18\u5A1B\u5A41\u5A46\u5A5A"
                + "\u5A66\u5A92\u5ABD\u5ACC\u5AE9\u5B50\u5B54\u5B57\u5B58\u5B5C"
                + "\u5B5D\u5B5F\u5B63\u5B64\u5B69\u5B6B\u5B78\u5B83\u5B85\u5B87-\u5B89"
                + "\u5B8B\u5B8C\u5B8F\u5B97-\u5B9C\u5BA2-\u5BA4\u5BAE\u5BB3\u5BB6\u5BB9"
                + "\u5BBF\u5BC2\u5BC4-\u5BC6\u5BCC\u5BD2\u5BDE\u5BDF\u5BE2\u5BE6-\u5BE9"
                + "\u5BEB\u5BEC\u5BEE\u5BF5\u5BF6\u5C01\u5C04\u5C07\u5C08\u5C0A"
                + "\u5C0B\u5C0D-\u5C0F\u5C11\u5C16\u5C1A\u5C24\u5C31\u5C3A\u5C3C\u5C3E"
                + "\u5C40\u5C41\u5C45\u5C46\u5C4B\u5C4F\u5C55\u5C60\u5C64\u5C6C"
                + "\u5C71\u5CA1\u5CA9\u5CB8\u5CC7\u5CF0\u5CF6\u5CFD\u5D07\u5D19"
                + "\u5D34\u5D50\u5DBA\u5DBC\u5DDD\u5DDE\u5DE1\u5DE5-\u5DE8\u5DEB\u5DEE"
                + "\u5DF1-\u5DF4\u5DF7\u5DFD\u5E02\u5E03\u5E0C\u5E15\u5E16\u5E1B"
                + "\u5E1D\u5E25\u5E2B\u5E2D\u5E33\u5E36\u5E38\u5E3D\u5E45\u5E55"
                + "\u5E63\u5E6B\u5E72-\u5E74\u5E78\u5E79\u5E7B-\u5E7E\u5E87\u5E8A"
                + "\u5E8F\u5E95\u5E97\u5E9A\u5E9C\u5EA6\u5EA7\u5EAB\u5EAD\u5EB7"
                + "\u5EB8\u5EC9\u5ED6\u5EE0\u5EE2\u5EE3\u5EF3\u5EF6\u5EF7\u5EFA"
                + "\u5F04\u5F0F\u5F15\u5F17\u5F18\u5F1F\u5F26\u5F31\u5F35\u5F37"
                + "\u5F48\u5F4A\u5F4C\u5F4E\u5F5D\u5F5E\u5F62\u5F65\u5F69\u5F6C"
                + "\u5F6D\u5F70\u5F71\u5F79\u5F7C\u5F80\u5F81\u5F85\u5F88\u5F8B"
                + "\u5F8C\u5F90-\u5F92\u5F97\u5F9E\u5FA9\u5FAE\u5FB5\u5FB7\u5FB9\u5FC3"
                + "\u5FC5\u5FCC\u5FCD\u5FD7-\u5FD9\u5FE0\u5FEB\u5FF5\u5FFD\u600E\u6012"
                + "\u6015\u6016\u601D\u6021\u6025\u6027\u6028\u602A\u6046\u6050"
                + "\u6062\u6065\u6068\u6069\u606D\u606F\u6070\u6085\u6089\u6094"
                + "\u609F\u60A0\u60A8\u60B2\u60B6\u60C5\u60D1\u60DC\u60E0\u60E1"
                + "\u60F1\u60F3\u60F9\u6101\u6108\u6109\u610F\u611A\u611B\u611F"
                + "\u6148\u614B\u6155\u6158\u6162\u6163\u6167\u616E\u6170\u6176"
                + "\u617E\u6182\u6190\u6191\u61B2\u61B6\u61BE\u61C2\u61C9\u61F6"
                + "\u61F7\u61FC\u6200\u6208\u620A\u620C\u6210-\u6212\u6216\u622A"
                + "\u6230\u6232\u6234\u6236\u623F-\u6241\u6247\u624B\u624D\u624E"
                + "\u6253\u6258\u6263\u6265\u626D\u626F\u6279\u627E-\u6280\u6284\u628A"
                + "\u6293\u6295\u6297\u6298\u62AB\u62AC\u62B1\u62B5\u62B9\u62BD"
                + "\u62C6\u62C9\u62CB\u62CD\u62CF\u62D2\u62D4\u62D6\u62DB\u62DC"
                + "\u62EC\u62F3\u62FC\u62FE\u62FF\u6301\u6307\u6309\u6311\u6316"
                + "\u632A\u632F\u633A\u6350\u6355\u6368\u6372\u6377\u6383\u6388"
                + "\u6389\u638C\u6392\u639B\u63A1\u63A2\u63A5\u63A7\u63A8\u63AA"
                + "\u63CF\u63D0\u63D2\u63DA\u63DB\u63E1\u63EE\u63F4\u640D\u6416"
                + "\u641C\u641E\u642C\u642D\u6436\u6458\u6469\u6478\u6490\u6492"
                + "\u649E\u64A3\u64A5\u64AD\u64BE\u64BF\u64C1\u64C7\u64CA\u64CB"
                + "\u64CD\u64CE\u64D4\u64DA\u64E0\u64E6\u64EC\u64F4\u64FA\u64FE"
                + "\u651D\u652F\u6536\u6539\u653B\u653E\u653F\u6545\u6548\u654D"
                + "\u654F\u6551\u6557-\u6559\u655D\u6562\u6563\u6566\u656C\u6574"
                + "\u6575\u6578\u6587\u6590\u6597\u6599\u65AF\u65B0\u65B7\u65B9"
                + "\u65BC\u65BD\u65C1\u65C5\u65CB\u65CF\u65D7\u65E2\u65E5\u65E6"
                + "\u65E9\u65ED\u65FA\u6602\u6606\u6607\u660C\u660E\u660F\u6613"
                + "\u661F\u6620\u6625\u6628\u662D\u662F\u6642\u6649\u6652\u665A"
                + "\u6668\u666E\u666F\u6674\u6676\u667A\u6691\u6696\u6697\u66AB"
                + "\u66B4\u66C6\u66C9\u66F0\u66F2\u66F4\u66F8\u66FC\u66FE-\u6700"
                + "\u6703\u6708\u6709\u670B\u670D\u6717\u671B\u671D\u671F\u6728"
                + "\u672A-\u672D\u6731\u6735\u6749\u674E\u6750\u6751\u675C\u675F"
                + "\u676F-\u6771\u677E\u677F\u6790\u6797\u679C\u679D\u67B6\u67CF"
                + "\u67D0\u67D3\u67D4\u67E5\u67EC\u67EF\u67F3\u67F4\u6817\u6821"
                + "\u6838\u6839\u683C\u6843\u6848\u684C\u6851\u6881\u6885\u689D"
                + "\u68A8\u68AF\u68B0\u68B5\u68C4\u68C9\u68CB\u68D2\u68DA\u68EE"
                + "\u6905\u690D\u6930\u694A\u6953\u6954\u695A\u696D\u6975\u6982"
                + "\u699C\u69AE\u69CB\u69CD\u6A02\u6A13\u6A19\u6A1E\u6A21\u6A23"
                + "\u6A39\u6A4B\u6A5F\u6A6B\u6A80\u6A94\u6AA2\u6B04\u6B0A\u6B21"
                + "\u6B23\u6B32\u6B3A\u6B3D\u6B3E\u6B49\u6B4C\u6B50\u6B61-\u6B66"
                + "\u6B72\u6B77\u6B78\u6B7B\u6B8A\u6B98\u6BB5\u6BBA\u6BBC\u6BC0"
                + "\u6BC5\u6BCD\u6BCF\u6BD2\u6BD4\u6BDB\u6BEB\u6C0F\u6C11\u6C23"
                + "\u6C34\u6C38\u6C42\u6C57\u6C5D\u6C5F-\u6C61\u6C6A\u6C76\u6C7A\u6C7D"
                + "\u6C83\u6C88\u6C89\u6C92\u6C96\u6C99\u6CB3\u6CB9\u6CBB\u6CBF"
                + "\u6CC1\u6CC9\u6CCA\u6CD5\u6CE1\u6CE2\u6CE5\u6CE8\u6CF0\u6CF3"
                + "\u6D0B\u6D17\u6D1B\u6D1E\u6D29\u6D2A\u6D32\u6D3B\u6D3D\u6D3E"
                + "\u6D41\u6D66\u6D69\u6D6A\u6D6E\u6D77\u6D85\u6D87-\u6D89\u6DAF\u6DB2"
                + "\u6DB5\u6DBC\u6DD1\u6DDA\u6DE1\u6DE8\u6DF1\u6DF7\u6DFA\u6E05"
                + "\u6E1B\u6E21\u6E2C\u6E2F\u6E38\u6E3E\u6E56\u6E6F\u6E90\u6E96"
                + "\u6E9D\u6EAA\u6EAB\u6EC4\u6EC5\u6ECB\u6ED1\u6EF4\u6EFE\u6EFF"
                + "\u6F02\u6F0F\u6F14\u6F20\u6F22\u6F2B\u6F32\u6F38\u6F54\u6F58"
                + "\u6F5B\u6F6E\u6F8E\u6FA4\u6FB3\u6FC0\u6FC3\u6FDF\u6FE4\u6FEB"
                + "\u6FF1\u700F\u704C\u7063\u706B\u7070\u707D\u708E\u70AE\u70B8"
                + "\u70BA\u70C8\u70CF\u70E4\u7121\u7126\u7136\u7159\u715E\u7167"
                + "\u7169\u718A\u719F\u71B1\u71C3\u71C8\u71D2\u71DF\u71E6\u7206"
                + "\u7210\u721B\u722A\u722C\u722D\u7235\u7236\u7238\u723A\u723D"
                + "\u723E\u7246-\u7248\u724C\u7259\u725B\u7260\u7267\u7269\u7272\u7279"
                + "\u727D\u72A7\u72AF\u72C0\u72C2\u72C4\u72D0\u72D7\u72E0\u72FC"
                + "\u731B\u731C\u7334\u7336\u7344\u7345\u734E\u7368\u7372\u7378"
                + "\u737B\u7384\u7387\u7389\u738B\u73A9\u73AB\u73B2\u73BB\u73CA"
                + "\u73CD\u73E0\u73E5\u73ED\u73FE\u7403\u7406\u7409\u742A\u7433"
                + "\u7434\u7459\u745A\u745C\u745E\u745F\u7464\u746A\u7470\u74B0"
                + "\u74DC\u74E6\u74F6\u7518\u751A\u751C\u751F\u7522\u7528\u752B"
                + "\u7530-\u7533\u7537\u7538\u754C\u7559\u7562\u7565\u756A\u756B"
                + "\u7570\u7576\u7586\u758F\u7591\u75BC\u75C5\u75D5\u75DB\u75F4"
                + "\u760B\u7642\u7661\u7678\u767B-\u767E\u7684\u7686\u7687\u76AE"
                + "\u76C3\u76CA\u76DB\u76DC\u76DF\u76E1\u76E3\u76E4\u76E7\u76EE"
                + "\u76F2\u76F4\u76F8\u76FC\u76FE\u7701\u7709\u770B\u771F\u7720"
                + "\u773C\u773E\u775B\u7761\u7763\u77A7\u77AD\u77DB\u77E3\u77E5"
                + "\u77ED\u77F3\u7802\u780D\u7814\u7832\u7834\u786C\u788E\u7891"
                + "\u7897\u789F\u78A7\u78A9\u78B0\u78BA\u78BC\u78C1\u78E8\u78EF"
                + "\u7901\u790E\u7919\u793A\u793E\u7955\u7956\u795A\u795B\u795D"
                + "\u795E\u7965\u7968\u797F\u7981\u798D-\u798F\u79AA\u79AE\u79C0\u79C1"
                + "\u79CB\u79D1\u79D2\u79D8\u79DF\u79E4\u79E6\u79FB\u7A05\u7A0B"
                + "\u7A0D\u7A2E\u7A31\u7A3F\u7A46\u7A4C\u7A4D\u7A69\u7A76\u7A79"
                + "\u7A7A\u7A7F\u7A81\u7A97\u7AA9\u7AAE\u7AB6\u7ACB\u7AD9\u7ADF"
                + "\u7AE0\u7AE5\u7AEF\u7AF6\u7AF9\u7B11\u7B1B\u7B26\u7B28\u7B2C"
                + "\u7B46\u7B49\u7B4B\u7B54\u7B56\u7B80\u7B97\u7BA1\u7BAD\u7BB1"
                + "\u7BC0\u7BC4\u7BC7\u7BC9\u7C21\u7C2B\u7C3D\u7C3F\u7C43\u7C4C"
                + "\u7C4D\u7C64\u7C73\u7C89\u7C97\u7CB5\u7CBE\u7CCA\u7CD5\u7CDF"
                + "\u7CFB\u7CFE\u7D00\u7D04\u7D05\u7D0D\u7D10\u7D14\u7D19-\u7D1B"
                + "\u7D20\u7D22\u7D2B\u7D2F\u7D30\u7D39\u7D42\u7D44\u7D50\u7D55"
                + "\u7D61\u7D66\u7D71\u7D72\u7D93\u7D9C\u7DA0\u7DAD\u7DB1\u7DB2"
                + "\u7DCA\u7DD2\u7DDA\u7DE3\u7DE8\u7DE9\u7DEC\u7DEF\u7DF4\u7E1B"
                + "\u7E23\u7E2E\u7E31\u7E3D\u7E3E\u7E41\u7E46\u7E54\u7E5E\u7E6A"
                + "\u7E73\u7E7C\u7E8C\u7F38\u7F3A\u7F55\u7F6A\u7F6E\u7F70\u7F72"
                + "\u7F75\u7F77\u7F85\u7F8A\u7F8E\u7F9E\u7FA4\u7FA9\u7FBD\u7FC1"
                + "\u7FD2\u7FD4\u7FF0\u7FF9\u7FFB\u7FFC\u8000\u8001\u8003\u8005"
                + "\u800C\u800D\u8010\u8017\u8033\u8036\u804A\u8056\u805A\u805E"
                + "\u806F\u8070\u8072\u8077\u807D\u8089\u809A\u80A1\u80A5\u80A9"
                + "\u80AF\u80B2\u80CC\u80CE\u80D6\u80DE\u80E1\u80F8\u80FD\u8106"
                + "\u812B\u8153\u8154\u8166\u8170\u8173\u817F\u81BD\u81C9\u81D8"
                + "\u81E3\u81E5\u81E8\u81EA\u81ED\u81F3\u81F4\u81FA\u8207-\u820A"
                + "\u820C\u820D\u8212\u821E\u821F\u822A\u822C\u8239\u8266\u826F"
                + "\u8272\u827E\u8292\u829D\u82AC\u82B1\u82B3\u82D7\u82E5\u82E6"
                + "\u82F1\u8305\u8328\u832B\u8332\u8336\u8349\u8352\u8377\u837C"
                + "\u8389\u838A\u838E\u83AB\u83DC\u83E9\u83EF\u83F2\u8404\u840A"
                + "\u842C\u843D\u8449\u8457\u845B\u8461\u8482\u8499\u84B2\u84BC"
                + "\u84CB\u84EC\u84EE\u8515\u8521\u8523\u856D\u8584\u85A6\u85A9"
                + "\u85AA\u85C9\u85CD\u85CF\u85DD\u85E4\u85E5\u8606\u8607\u862D"
                + "\u864E\u8655\u865B\u865F\u8667\u86A9\u86C7\u86CB\u86D9\u8700"
                + "\u8702\u871C\u8776\u878D\u87A2\u87F2\u87F9\u880D\u883B\u8840"
                + "\u884C\u8853\u8857\u885B\u885D\u8861\u8863\u8868\u888B\u88AB"
                + "\u88C1\u88C2\u88D5\u88D8\u88DC\u88DD\u88E1\u88FD\u8907\u8932"
                + "\u897F\u8981\u8986\u898B\u898F\u8996\u89AA\u89BA\u89BD\u89C0"
                + "\u89D2\u89E3\u89F8\u8A00\u8A02\u8A08\u8A0A\u8A0E\u8A13\u8A17"
                + "\u8A18\u8A25\u8A2A\u8A2D\u8A31\u8A34\u8A3B\u8A3C\u8A55\u8A5E"
                + "\u8A62\u8A66\u8A69\u8A71-\u8A73\u8A87\u8A8C\u8A8D\u8A93\u8A95\u8A9E"
                + "\u8AA0\u8AA4\u8AAA\u8AB0\u8AB2\u8ABC\u8ABF\u8AC7\u8ACB\u8AD2"
                + "\u8AD6\u8AF8\u8AFA\u8AFE\u8B00\u8B02\u8B1B\u8B1D\u8B2C\u8B49"
                + "\u8B58\u8B5C\u8B66\u8B6F\u8B70\u8B77\u8B7D\u8B80\u8B8A\u8B93"
                + "\u8B9A\u8C37\u8C46\u8C48\u8C50\u8C61\u8C6A\u8C6C\u8C8C\u8C93"
                + "\u8C9D\u8C9E\u8CA0-\u8CA2\u8CA8\u8CAA-\u8CAC\u8CB4\u8CB7\u8CBB\u8CBC"
                + "\u8CC0\u8CC7\u8CC8\u8CD3\u8CDC\u8CDE\u8CE2-\u8CE4\u8CE6\u8CEA"
                + "\u8CED\u8CF4\u8CFA\u8CFC\u8CFD\u8D08\u8D0A\u8D0F\u8D64\u8D6B"
                + "\u8D70\u8D77\u8D85\u8D8A\u8D95\u8D99\u8DA3\u8DA8\u8DB3\u8DCC"
                + "\u8DCE\u8DD1\u8DDD\u8DDF\u8DE1\u8DEF\u8DF3\u8E0F\u8E22\u8E5F"
                + "\u8E64\u8E8D\u8EAB\u8EB2\u8ECA\u8ECC\u8ECD\u8ED2\u8EDF\u8F03"
                + "\u8F09\u8F14\u8F15\u8F1B\u8F1D\u8F29\u8F2A\u8F2F\u8F38\u8F49"
                + "\u8F5F\u8F9B\u8FA6\u8FA8\u8FAD\u8FAF-\u8FB2\u8FC5\u8FCE\u8FD1\u8FD4"
                + "\u8FE6\u8FEA\u8FEB\u8FF0\u8FF4\u8FF7\u8FFD\u9000\u9001\u9003"
                + "\u9006\u900F\u9010\u9014\u9019-\u901B\u901D\u901F\u9020\u9022"
                + "\u9023\u9031\u9032\u9038\u903C\u9047\u904A\u904B\u904D\u904E"
                + "\u9053-\u9055\u9059\u905C\u9060\u9069\u906D\u906E\u9072\u9077"
                + "\u9078\u907A\u907F-\u9081\u9084\u908A\u908F\u90A3\u90A6\u90AA"
                + "\u90B1\u90CE\u90E8\u90ED\u90F5\u90FD\u9102\u9109\u912D\u9130"
                + "\u9149\u914B\u914D\u9152\u9177\u9178\u9189\u9192\u919C\u91AB"
                + "\u91C7\u91CB-\u91CF\u91D1\u91DD\u91E3\u9234\u9262\u9280\u9285\u9296"
                + "\u9298\u92B3\u92B7\u92D2\u92FC\u9304\u9322\u9326\u932B\u932F"
                + "\u934B\u9375\u937E\u938A\u9396\u93AE\u93E1\u9418\u9435\u9451"
                + "\u9577\u9580\u9583\u9589\u958B\u958F\u9592\u9593\u95A3\u95B1"
                + "\u95C6\u95CA\u95CD\u95D0\u95DC\u95E1\u9632\u963B\u963F\u9640"
                + "\u9644\u964D\u9650\u9662-\u9664\u966A\u9670\u9673\u9675-\u9678"
                + "\u967D\u9686\u968A\u968E\u9694\u969B\u969C\u96A8\u96AA\u96B1"
                + "\u96B4\u96BB\u96C4-\u96C6\u96C9\u96D6\u96D9\u96DC\u96DE\u96E2"
                + "\u96E3\u96E8\u96EA\u96F2\u96F6\u96F7\u96FB\u9700\u9707\u970D"
                + "\u9727\u9732\u9738\u9739\u9742\u9748\u9752\u9756\u975C\u975E"
                + "\u9760\u9762\u9769\u977C\u978B\u97C3\u97CB\u97D3\u97F3\u97FB"
                + "\u97FF\u9801\u9802\u9805\u9806\u9808\u9810\u9811\u9813\u9817"
                + "\u9818\u981E\u982D\u983B\u9846\u984C\u984D\u984F\u9858\u985E"
                + "\u9867\u986F\u98A8\u98C4\u98DB\u98DF\u98EF\u98F2\u98FD\u98FE"
                + "\u9905\u990A\u9910\u9918\u9928\u9996\u9999\u99AC\u99D0\u99D5"
                + "\u99DB\u9A0E\u9A19\u9A37\u9A45\u9A57\u9A5A\u9AA8\u9AD4\u9AD8"
                + "\u9AEE\u9B06\u9B25\u9B27\u9B31\u9B3C\u9B41\u9B42\u9B45\u9B54"
                + "\u9B5A\u9B6F\u9BAE\u9CE5\u9CF3\u9CF4\u9D3B\u9D5D\u9DF9\u9E7F"
                + "\u9E97\u9EA5\u9EB5\u9EBB\u9EBC\u9EC3\u9ECE\u9ED1\u9ED8\u9EDE"
                + "\u9EE8\u9F13\u9F20\u9F3B\u9F4A\u9F4B\u9F52\u9F61\u9F8D\u9F9C"
                + "\uFE30-\uFE44\uFE49-\uFE52\uFE54-\uFE61\uFE63\uFE68\uFE6A\uFE6B"
                + "\uFF01-\uFF03\uFF05-\uFF0A\uFF0C-\uFF0F\uFF1A\uFF1B\uFF1F\uFF20"
                + "\uFF3B-\uFF3D\uFF3F\uFF5B\uFF5D]"));
    }

    private String dropRegion(String localeName) {
        return new Locale.Builder()
                         .setLanguageTag(localeName)
                         .setRegion("")
                         .build()
                         .toLanguageTag();
    }

    private UnicodeSet requiredExtraChars(String localeName) {
        if (EXEMPLAR_MAP.containsKey(localeName)) {
            return EXEMPLAR_MAP.get(localeName);
        }
        // Drop the region code.
        final String parentLocale = dropRegion(localeName);
        if (EXEMPLAR_MAP.containsKey(parentLocale)) {
            return EXEMPLAR_MAP.get(parentLocale);
        }

        // Unknown locale. Return an empty set.
        return UnicodeSet.EMPTY;
    }

    @Test
    public void testLanguageCoverage() {
        final Paint paint = new Paint();
        final String[] localeNames = Resources.getSystem().getStringArray(
                Resources.getSystem().getIdentifier("supported_locales", "array", "android"));

        final UnicodeSet allRequired = new UnicodeSet(MIN_COVERAGE);
        // Add all characters needed for supported locales.
        for (String localeName : localeNames) {
            allRequired.addAll(requiredExtraChars(localeName));
        }
        for (String str : allRequired) {
            str.codePoints().filter(cp -> {
                // No need to check for format or control characters. They are handled by
                // HarfBuzz and other parts of the rendering system.
                final int gc = UCharacter.getType(cp);
                return (gc != UCharacterCategory.CONTROL && gc != UCharacter.FORMAT);
            }).forEach(cp -> {
                final String characterAsString = new String(Character.toChars(cp));
                assertTrue(
                        String.format("No glyph for U+%04X", cp),
                        paint.hasGlyph(characterAsString));
            });
        }
    }

    // All emoji characters in Unicode 10.0/Emoji 5.0
    private static final int[] ALL_EMOJI = {
        0x0023, // NUMBER SIGN
        0x002A, // ASTERISK
        0x0030, // DIGIT ZERO
        0x0031, // DIGIT ONE
        0x0032, // DIGIT TWO
        0x0033, // DIGIT THREE
        0x0034, // DIGIT FOUR
        0x0035, // DIGIT FIVE
        0x0036, // DIGIT SIX
        0x0037, // DIGIT SEVEN
        0x0038, // DIGIT EIGHT
        0x0039, // DIGIT NINE
        0x00A9, // COPYRIGHT SIGN
        0x00AE, // REGISTERED SIGN
        0x203C, // DOUBLE EXCLAMATION MARK
        0x2049, // EXCLAMATION QUESTION MARK
        0x2122, // TRADE MARK SIGN
        0x2139, // INFORMATION SOURCE
        0x2194, // LEFT RIGHT ARROW
        0x2195, // UP DOWN ARROW
        0x2196, // NORTH WEST ARROW
        0x2197, // NORTH EAST ARROW
        0x2198, // SOUTH EAST ARROW
        0x2199, // SOUTH WEST ARROW
        0x21A9, // LEFTWARDS ARROW WITH HOOK
        0x21AA, // RIGHTWARDS ARROW WITH HOOK
        0x231A, // WATCH
        0x231B, // HOURGLASS
        0x2328, // KEYBOARD
        0x23CF, // EJECT SYMBOL
        0x23E9, // BLACK RIGHT-POINTING DOUBLE TRIANGLE
        0x23EA, // BLACK LEFT-POINTING DOUBLE TRIANGLE
        0x23EB, // BLACK UP-POINTING DOUBLE TRIANGLE
        0x23EC, // BLACK DOWN-POINTING DOUBLE TRIANGLE
        0x23ED, // BLACK RIGHT-POINTING DOUBLE TRIANGLE WITH VERTICAL BAR
        0x23EE, // BLACK LEFT-POINTING DOUBLE TRIANGLE WITH VERTICAL BAR
        0x23EF, // BLACK RIGHT-POINTING TRIANGLE WITH DOUBLE VERTICAL BAR
        0x23F0, // ALARM CLOCK
        0x23F1, // STOPWATCH
        0x23F2, // TIMER CLOCK
        0x23F3, // HOURGLASS WITH FLOWING SAND
        0x23F8, // DOUBLE VERTICAL BAR
        0x23F9, // BLACK SQUARE FOR STOP
        0x23FA, // BLACK CIRCLE FOR RECORD
        0x24C2, // CIRCLED LATIN CAPITAL LETTER M
        0x25AA, // BLACK SMALL SQUARE
        0x25AB, // WHITE SMALL SQUARE
        0x25B6, // BLACK RIGHT-POINTING TRIANGLE
        0x25C0, // BLACK LEFT-POINTING TRIANGLE
        0x25FB, // WHITE MEDIUM SQUARE
        0x25FC, // BLACK MEDIUM SQUARE
        0x25FD, // WHITE MEDIUM SMALL SQUARE
        0x25FE, // BLACK MEDIUM SMALL SQUARE
        0x2600, // BLACK SUN WITH RAYS
        0x2601, // CLOUD
        0x2602, // UMBRELLA
        0x2603, // SNOWMAN
        0x2604, // COMET
        0x260E, // BLACK TELEPHONE
        0x2611, // BALLOT BOX WITH CHECK
        0x2614, // UMBRELLA WITH RAIN DROPS
        0x2615, // HOT BEVERAGE
        0x2618, // SHAMROCK
        0x261D, // WHITE UP POINTING INDEX
        0x2620, // SKULL AND CROSSBONES
        0x2622, // RADIOACTIVE SIGN
        0x2623, // BIOHAZARD SIGN
        0x2626, // ORTHODOX CROSS
        0x262A, // STAR AND CRESCENT
        0x262E, // PEACE SYMBOL
        0x262F, // YIN YANG
        0x2638, // WHEEL OF DHARMA
        0x2639, // WHITE FROWNING FACE
        0x263A, // WHITE SMILING FACE
        0x2640, // FEMALE SIGN
        0x2642, // MALE SIGN
        0x2648, // ARIES
        0x2649, // TAURUS
        0x264A, // GEMINI
        0x264B, // CANCER
        0x264C, // LEO
        0x264D, // VIRGO
        0x264E, // LIBRA
        0x264F, // SCORPIUS
        0x2650, // SAGITTARIUS
        0x2651, // CAPRICORN
        0x2652, // AQUARIUS
        0x2653, // PISCES
        0x2660, // BLACK SPADE SUIT
        0x2663, // BLACK CLUB SUIT
        0x2665, // BLACK HEART SUIT
        0x2666, // BLACK DIAMOND SUIT
        0x2668, // HOT SPRINGS
        0x267B, // BLACK UNIVERSAL RECYCLING SYMBOL
        0x267F, // WHEELCHAIR SYMBOL
        0x2692, // HAMMER AND PICK
        0x2693, // ANCHOR
        0x2694, // CROSSED SWORDS
        0x2695, // STAFF OF AESCULAPIUS
        0x2696, // SCALES
        0x2697, // ALEMBIC
        0x2699, // GEAR
        0x269B, // ATOM SYMBOL
        0x269C, // FLEUR-DE-LIS
        0x26A0, // WARNING SIGN
        0x26A1, // HIGH VOLTAGE SIGN
        0x26AA, // MEDIUM WHITE CIRCLE
        0x26AB, // MEDIUM BLACK CIRCLE
        0x26B0, // COFFIN
        0x26B1, // FUNERAL URN
        0x26BD, // SOCCER BALL
        0x26BE, // BASEBALL
        0x26C4, // SNOWMAN WITHOUT SNOW
        0x26C5, // SUN BEHIND CLOUD
        0x26C8, // THUNDER CLOUD AND RAIN
        0x26CE, // OPHIUCHUS
        0x26CF, // PICK
        0x26D1, // HELMET WITH WHITE CROSS
        0x26D3, // CHAINS
        0x26D4, // NO ENTRY
        0x26E9, // SHINTO SHRINE
        0x26EA, // CHURCH
        0x26F0, // MOUNTAIN
        0x26F1, // UMBRELLA ON GROUND
        0x26F2, // FOUNTAIN
        0x26F3, // FLAG IN HOLE
        0x26F4, // FERRY
        0x26F5, // SAILBOAT
        0x26F7, // SKIER
        0x26F8, // ICE SKATE
        0x26F9, // PERSON WITH BALL
        0x26FA, // TENT
        0x26FD, // FUEL PUMP
        0x2702, // BLACK SCISSORS
        0x2705, // WHITE HEAVY CHECK MARK
        0x2708, // AIRPLANE
        0x2709, // ENVELOPE
        0x270A, // RAISED FIST
        0x270B, // RAISED HAND
        0x270C, // VICTORY HAND
        0x270D, // WRITING HAND
        0x270F, // PENCIL
        0x2712, // BLACK NIB
        0x2714, // HEAVY CHECK MARK
        0x2716, // HEAVY MULTIPLICATION X
        0x271D, // LATIN CROSS
        0x2721, // STAR OF DAVID
        0x2728, // SPARKLES
        0x2733, // EIGHT SPOKED ASTERISK
        0x2734, // EIGHT POINTED BLACK STAR
        0x2744, // SNOWFLAKE
        0x2747, // SPARKLE
        0x274C, // CROSS MARK
        0x274E, // NEGATIVE SQUARED CROSS MARK
        0x2753, // BLACK QUESTION MARK ORNAMENT
        0x2754, // WHITE QUESTION MARK ORNAMENT
        0x2755, // WHITE EXCLAMATION MARK ORNAMENT
        0x2757, // HEAVY EXCLAMATION MARK SYMBOL
        0x2763, // HEAVY HEART EXCLAMATION MARK ORNAMENT
        0x2764, // HEAVY BLACK HEART
        0x2795, // HEAVY PLUS SIGN
        0x2796, // HEAVY MINUS SIGN
        0x2797, // HEAVY DIVISION SIGN
        0x27A1, // BLACK RIGHTWARDS ARROW
        0x27B0, // CURLY LOOP
        0x27BF, // DOUBLE CURLY LOOP
        0x2934, // ARROW POINTING RIGHTWARDS THEN CURVING UPWARDS
        0x2935, // ARROW POINTING RIGHTWARDS THEN CURVING DOWNWARDS
        0x2B05, // LEFTWARDS BLACK ARROW
        0x2B06, // UPWARDS BLACK ARROW
        0x2B07, // DOWNWARDS BLACK ARROW
        0x2B1B, // BLACK LARGE SQUARE
        0x2B1C, // WHITE LARGE SQUARE
        0x2B50, // WHITE MEDIUM STAR
        0x2B55, // HEAVY LARGE CIRCLE
        0x3030, // WAVY DASH
        0x303D, // PART ALTERNATION MARK
        0x3297, // CIRCLED IDEOGRAPH CONGRATULATION
        0x3299, // CIRCLED IDEOGRAPH SECRET
        0x1F004, // MAHJONG TILE RED DRAGON
        0x1F0CF, // PLAYING CARD BLACK JOKER
        0x1F170, // NEGATIVE SQUARED LATIN CAPITAL LETTER A
        0x1F171, // NEGATIVE SQUARED LATIN CAPITAL LETTER B
        0x1F17E, // NEGATIVE SQUARED LATIN CAPITAL LETTER O
        0x1F17F, // NEGATIVE SQUARED LATIN CAPITAL LETTER P
        0x1F18E, // NEGATIVE SQUARED AB
        0x1F191, // SQUARED CL
        0x1F192, // SQUARED COOL
        0x1F193, // SQUARED FREE
        0x1F194, // SQUARED ID
        0x1F195, // SQUARED NEW
        0x1F196, // SQUARED NG
        0x1F197, // SQUARED OK
        0x1F198, // SQUARED SOS
        0x1F199, // SQUARED UP WITH EXCLAMATION MARK
        0x1F19A, // SQUARED VS
        0x1F1E6, // REGIONAL INDICATOR SYMBOL LETTER A
        0x1F1E7, // REGIONAL INDICATOR SYMBOL LETTER B
        0x1F1E8, // REGIONAL INDICATOR SYMBOL LETTER C
        0x1F1E9, // REGIONAL INDICATOR SYMBOL LETTER D
        0x1F1EA, // REGIONAL INDICATOR SYMBOL LETTER E
        0x1F1EB, // REGIONAL INDICATOR SYMBOL LETTER F
        0x1F1EC, // REGIONAL INDICATOR SYMBOL LETTER G
        0x1F1ED, // REGIONAL INDICATOR SYMBOL LETTER H
        0x1F1EE, // REGIONAL INDICATOR SYMBOL LETTER I
        0x1F1EF, // REGIONAL INDICATOR SYMBOL LETTER J
        0x1F1F0, // REGIONAL INDICATOR SYMBOL LETTER K
        0x1F1F1, // REGIONAL INDICATOR SYMBOL LETTER L
        0x1F1F2, // REGIONAL INDICATOR SYMBOL LETTER M
        0x1F1F3, // REGIONAL INDICATOR SYMBOL LETTER N
        0x1F1F4, // REGIONAL INDICATOR SYMBOL LETTER O
        0x1F1F5, // REGIONAL INDICATOR SYMBOL LETTER P
        0x1F1F6, // REGIONAL INDICATOR SYMBOL LETTER Q
        0x1F1F7, // REGIONAL INDICATOR SYMBOL LETTER R
        0x1F1F8, // REGIONAL INDICATOR SYMBOL LETTER S
        0x1F1F9, // REGIONAL INDICATOR SYMBOL LETTER T
        0x1F1FA, // REGIONAL INDICATOR SYMBOL LETTER U
        0x1F1FB, // REGIONAL INDICATOR SYMBOL LETTER V
        0x1F1FC, // REGIONAL INDICATOR SYMBOL LETTER W
        0x1F1FD, // REGIONAL INDICATOR SYMBOL LETTER X
        0x1F1FE, // REGIONAL INDICATOR SYMBOL LETTER Y
        0x1F1FF, // REGIONAL INDICATOR SYMBOL LETTER Z
        0x1F201, // SQUARED KATAKANA KOKO
        0x1F202, // SQUARED KATAKANA SA
        0x1F21A, // SQUARED CJK UNIFIED IDEOGRAPH-7121
        0x1F22F, // SQUARED CJK UNIFIED IDEOGRAPH-6307
        0x1F232, // SQUARED CJK UNIFIED IDEOGRAPH-7981
        0x1F233, // SQUARED CJK UNIFIED IDEOGRAPH-7A7A
        0x1F234, // SQUARED CJK UNIFIED IDEOGRAPH-5408
        0x1F235, // SQUARED CJK UNIFIED IDEOGRAPH-6E80
        0x1F236, // SQUARED CJK UNIFIED IDEOGRAPH-6709
        0x1F237, // SQUARED CJK UNIFIED IDEOGRAPH-6708
        0x1F238, // SQUARED CJK UNIFIED IDEOGRAPH-7533
        0x1F239, // SQUARED CJK UNIFIED IDEOGRAPH-5272
        0x1F23A, // SQUARED CJK UNIFIED IDEOGRAPH-55B6
        0x1F250, // CIRCLED IDEOGRAPH ADVANTAGE
        0x1F251, // CIRCLED IDEOGRAPH ACCEPT
        0x1F300, // CYCLONE
        0x1F301, // FOGGY
        0x1F302, // CLOSED UMBRELLA
        0x1F303, // NIGHT WITH STARS
        0x1F304, // SUNRISE OVER MOUNTAINS
        0x1F305, // SUNRISE
        0x1F306, // CITYSCAPE AT DUSK
        0x1F307, // SUNSET OVER BUILDINGS
        0x1F308, // RAINBOW
        0x1F309, // BRIDGE AT NIGHT
        0x1F30A, // WATER WAVE
        0x1F30B, // VOLCANO
        0x1F30C, // MILKY WAY
        0x1F30D, // EARTH GLOBE EUROPE-AFRICA
        0x1F30E, // EARTH GLOBE AMERICAS
        0x1F30F, // EARTH GLOBE ASIA-AUSTRALIA
        0x1F310, // GLOBE WITH MERIDIANS
        0x1F311, // NEW MOON SYMBOL
        0x1F312, // WAXING CRESCENT MOON SYMBOL
        0x1F313, // FIRST QUARTER MOON SYMBOL
        0x1F314, // WAXING GIBBOUS MOON SYMBOL
        0x1F315, // FULL MOON SYMBOL
        0x1F316, // WANING GIBBOUS MOON SYMBOL
        0x1F317, // LAST QUARTER MOON SYMBOL
        0x1F318, // WANING CRESCENT MOON SYMBOL
        0x1F319, // CRESCENT MOON
        0x1F31A, // NEW MOON WITH FACE
        0x1F31B, // FIRST QUARTER MOON WITH FACE
        0x1F31C, // LAST QUARTER MOON WITH FACE
        0x1F31D, // FULL MOON WITH FACE
        0x1F31E, // SUN WITH FACE
        0x1F31F, // GLOWING STAR
        0x1F320, // SHOOTING STAR
        0x1F321, // THERMOMETER
        0x1F324, // WHITE SUN WITH SMALL CLOUD
        0x1F325, // WHITE SUN BEHIND CLOUD
        0x1F326, // WHITE SUN BEHIND CLOUD WITH RAIN
        0x1F327, // CLOUD WITH RAIN
        0x1F328, // CLOUD WITH SNOW
        0x1F329, // CLOUD WITH LIGHTNING
        0x1F32A, // CLOUD WITH TORNADO
        0x1F32B, // FOG
        0x1F32C, // WIND BLOWING FACE
        0x1F32D, // HOT DOG
        0x1F32E, // TACO
        0x1F32F, // BURRITO
        0x1F330, // CHESTNUT
        0x1F331, // SEEDLING
        0x1F332, // EVERGREEN TREE
        0x1F333, // DECIDUOUS TREE
        0x1F334, // PALM TREE
        0x1F335, // CACTUS
        0x1F336, // HOT PEPPER
        0x1F337, // TULIP
        0x1F338, // CHERRY BLOSSOM
        0x1F339, // ROSE
        0x1F33A, // HIBISCUS
        0x1F33B, // SUNFLOWER
        0x1F33C, // BLOSSOM
        0x1F33D, // EAR OF MAIZE
        0x1F33E, // EAR OF RICE
        0x1F33F, // HERB
        0x1F340, // FOUR LEAF CLOVER
        0x1F341, // MAPLE LEAF
        0x1F342, // FALLEN LEAF
        0x1F343, // LEAF FLUTTERING IN WIND
        0x1F344, // MUSHROOM
        0x1F345, // TOMATO
        0x1F346, // AUBERGINE
        0x1F347, // GRAPES
        0x1F348, // MELON
        0x1F349, // WATERMELON
        0x1F34A, // TANGERINE
        0x1F34B, // LEMON
        0x1F34C, // BANANA
        0x1F34D, // PINEAPPLE
        0x1F34E, // RED APPLE
        0x1F34F, // GREEN APPLE
        0x1F350, // PEAR
        0x1F351, // PEACH
        0x1F352, // CHERRIES
        0x1F353, // STRAWBERRY
        0x1F354, // HAMBURGER
        0x1F355, // SLICE OF PIZZA
        0x1F356, // MEAT ON BONE
        0x1F357, // POULTRY LEG
        0x1F358, // RICE CRACKER
        0x1F359, // RICE BALL
        0x1F35A, // COOKED RICE
        0x1F35B, // CURRY AND RICE
        0x1F35C, // STEAMING BOWL
        0x1F35D, // SPAGHETTI
        0x1F35E, // BREAD
        0x1F35F, // FRENCH FRIES
        0x1F360, // ROASTED SWEET POTATO
        0x1F361, // DANGO
        0x1F362, // ODEN
        0x1F363, // SUSHI
        0x1F364, // FRIED SHRIMP
        0x1F365, // FISH CAKE WITH SWIRL DESIGN
        0x1F366, // SOFT ICE CREAM
        0x1F367, // SHAVED ICE
        0x1F368, // ICE CREAM
        0x1F369, // DOUGHNUT
        0x1F36A, // COOKIE
        0x1F36B, // CHOCOLATE BAR
        0x1F36C, // CANDY
        0x1F36D, // LOLLIPOP
        0x1F36E, // CUSTARD
        0x1F36F, // HONEY POT
        0x1F370, // SHORTCAKE
        0x1F371, // BENTO BOX
        0x1F372, // POT OF FOOD
        0x1F373, // COOKING
        0x1F374, // FORK AND KNIFE
        0x1F375, // TEACUP WITHOUT HANDLE
        0x1F376, // SAKE BOTTLE AND CUP
        0x1F377, // WINE GLASS
        0x1F378, // COCKTAIL GLASS
        0x1F379, // TROPICAL DRINK
        0x1F37A, // BEER MUG
        0x1F37B, // CLINKING BEER MUGS
        0x1F37C, // BABY BOTTLE
        0x1F37D, // FORK AND KNIFE WITH PLATE
        0x1F37E, // BOTTLE WITH POPPING CORK
        0x1F37F, // POPCORN
        0x1F380, // RIBBON
        0x1F381, // WRAPPED PRESENT
        0x1F382, // BIRTHDAY CAKE
        0x1F383, // JACK-O-LANTERN
        0x1F384, // CHRISTMAS TREE
        0x1F385, // FATHER CHRISTMAS
        0x1F386, // FIREWORKS
        0x1F387, // FIREWORK SPARKLER
        0x1F388, // BALLOON
        0x1F389, // PARTY POPPER
        0x1F38A, // CONFETTI BALL
        0x1F38B, // TANABATA TREE
        0x1F38C, // CROSSED FLAGS
        0x1F38D, // PINE DECORATION
        0x1F38E, // JAPANESE DOLLS
        0x1F38F, // CARP STREAMER
        0x1F390, // WIND CHIME
        0x1F391, // MOON VIEWING CEREMONY
        0x1F392, // SCHOOL SATCHEL
        0x1F393, // GRADUATION CAP
        0x1F396, // MILITARY MEDAL
        0x1F397, // REMINDER RIBBON
        0x1F399, // STUDIO MICROPHONE
        0x1F39A, // LEVEL SLIDER
        0x1F39B, // CONTROL KNOBS
        0x1F39E, // FILM FRAMES
        0x1F39F, // ADMISSION TICKETS
        0x1F3A0, // CAROUSEL HORSE
        0x1F3A1, // FERRIS WHEEL
        0x1F3A2, // ROLLER COASTER
        0x1F3A3, // FISHING POLE AND FISH
        0x1F3A4, // MICROPHONE
        0x1F3A5, // MOVIE CAMERA
        0x1F3A6, // CINEMA
        0x1F3A7, // HEADPHONE
        0x1F3A8, // ARTIST PALETTE
        0x1F3A9, // TOP HAT
        0x1F3AA, // CIRCUS TENT
        0x1F3AB, // TICKET
        0x1F3AC, // CLAPPER BOARD
        0x1F3AD, // PERFORMING ARTS
        0x1F3AE, // VIDEO GAME
        0x1F3AF, // DIRECT HIT
        0x1F3B0, // SLOT MACHINE
        0x1F3B1, // BILLIARDS
        0x1F3B2, // GAME DIE
        0x1F3B3, // BOWLING
        0x1F3B4, // FLOWER PLAYING CARDS
        0x1F3B5, // MUSICAL NOTE
        0x1F3B6, // MULTIPLE MUSICAL NOTES
        0x1F3B7, // SAXOPHONE
        0x1F3B8, // GUITAR
        0x1F3B9, // MUSICAL KEYBOARD
        0x1F3BA, // TRUMPET
        0x1F3BB, // VIOLIN
        0x1F3BC, // MUSICAL SCORE
        0x1F3BD, // RUNNING SHIRT WITH SASH
        0x1F3BE, // TENNIS RACQUET AND BALL
        0x1F3BF, // SKI AND SKI BOOT
        0x1F3C0, // BASKETBALL AND HOOP
        0x1F3C1, // CHEQUERED FLAG
        0x1F3C2, // SNOWBOARDER
        0x1F3C3, // RUNNER
        0x1F3C4, // SURFER
        0x1F3C5, // SPORTS MEDAL
        0x1F3C6, // TROPHY
        0x1F3C7, // HORSE RACING
        0x1F3C8, // AMERICAN FOOTBALL
        0x1F3C9, // RUGBY FOOTBALL
        0x1F3CA, // SWIMMER
        0x1F3CB, // WEIGHT LIFTER
        0x1F3CC, // GOLFER
        0x1F3CD, // RACING MOTORCYCLE
        0x1F3CE, // RACING CAR
        0x1F3CF, // CRICKET BAT AND BALL
        0x1F3D0, // VOLLEYBALL
        0x1F3D1, // FIELD HOCKEY STICK AND BALL
        0x1F3D2, // ICE HOCKEY STICK AND PUCK
        0x1F3D3, // TABLE TENNIS PADDLE AND BALL
        0x1F3D4, // SNOW CAPPED MOUNTAIN
        0x1F3D5, // CAMPING
        0x1F3D6, // BEACH WITH UMBRELLA
        0x1F3D7, // BUILDING CONSTRUCTION
        0x1F3D8, // HOUSE BUILDINGS
        0x1F3D9, // CITYSCAPE
        0x1F3DA, // DERELICT HOUSE BUILDING
        0x1F3DB, // CLASSICAL BUILDING
        0x1F3DC, // DESERT
        0x1F3DD, // DESERT ISLAND
        0x1F3DE, // NATIONAL PARK
        0x1F3DF, // STADIUM
        0x1F3E0, // HOUSE BUILDING
        0x1F3E1, // HOUSE WITH GARDEN
        0x1F3E2, // OFFICE BUILDING
        0x1F3E3, // JAPANESE POST OFFICE
        0x1F3E4, // EUROPEAN POST OFFICE
        0x1F3E5, // HOSPITAL
        0x1F3E6, // BANK
        0x1F3E7, // AUTOMATED TELLER MACHINE
        0x1F3E8, // HOTEL
        0x1F3E9, // LOVE HOTEL
        0x1F3EA, // CONVENIENCE STORE
        0x1F3EB, // SCHOOL
        0x1F3EC, // DEPARTMENT STORE
        0x1F3ED, // FACTORY
        0x1F3EE, // IZAKAYA LANTERN
        0x1F3EF, // JAPANESE CASTLE
        0x1F3F0, // EUROPEAN CASTLE
        0x1F3F3, // WAVING WHITE FLAG
        0x1F3F4, // WAVING BLACK FLAG
        0x1F3F5, // ROSETTE
        0x1F3F7, // LABEL
        0x1F3F8, // BADMINTON RACQUET AND SHUTTLECOCK
        0x1F3F9, // BOW AND ARROW
        0x1F3FA, // AMPHORA
        0x1F3FB, // EMOJI MODIFIER FITZPATRICK TYPE-1-2
        0x1F3FC, // EMOJI MODIFIER FITZPATRICK TYPE-3
        0x1F3FD, // EMOJI MODIFIER FITZPATRICK TYPE-4
        0x1F3FE, // EMOJI MODIFIER FITZPATRICK TYPE-5
        0x1F3FF, // EMOJI MODIFIER FITZPATRICK TYPE-6
        0x1F400, // RAT
        0x1F401, // MOUSE
        0x1F402, // OX
        0x1F403, // WATER BUFFALO
        0x1F404, // COW
        0x1F405, // TIGER
        0x1F406, // LEOPARD
        0x1F407, // RABBIT
        0x1F408, // CAT
        0x1F409, // DRAGON
        0x1F40A, // CROCODILE
        0x1F40B, // WHALE
        0x1F40C, // SNAIL
        0x1F40D, // SNAKE
        0x1F40E, // HORSE
        0x1F40F, // RAM
        0x1F410, // GOAT
        0x1F411, // SHEEP
        0x1F412, // MONKEY
        0x1F413, // ROOSTER
        0x1F414, // CHICKEN
        0x1F415, // DOG
        0x1F416, // PIG
        0x1F417, // BOAR
        0x1F418, // ELEPHANT
        0x1F419, // OCTOPUS
        0x1F41A, // SPIRAL SHELL
        0x1F41B, // BUG
        0x1F41C, // ANT
        0x1F41D, // HONEYBEE
        0x1F41E, // LADY BEETLE
        0x1F41F, // FISH
        0x1F420, // TROPICAL FISH
        0x1F421, // BLOWFISH
        0x1F422, // TURTLE
        0x1F423, // HATCHING CHICK
        0x1F424, // BABY CHICK
        0x1F425, // FRONT-FACING BABY CHICK
        0x1F426, // BIRD
        0x1F427, // PENGUIN
        0x1F428, // KOALA
        0x1F429, // POODLE
        0x1F42A, // DROMEDARY CAMEL
        0x1F42B, // BACTRIAN CAMEL
        0x1F42C, // DOLPHIN
        0x1F42D, // MOUSE FACE
        0x1F42E, // COW FACE
        0x1F42F, // TIGER FACE
        0x1F430, // RABBIT FACE
        0x1F431, // CAT FACE
        0x1F432, // DRAGON FACE
        0x1F433, // SPOUTING WHALE
        0x1F434, // HORSE FACE
        0x1F435, // MONKEY FACE
        0x1F436, // DOG FACE
        0x1F437, // PIG FACE
        0x1F438, // FROG FACE
        0x1F439, // HAMSTER FACE
        0x1F43A, // WOLF FACE
        0x1F43B, // BEAR FACE
        0x1F43C, // PANDA FACE
        0x1F43D, // PIG NOSE
        0x1F43E, // PAW PRINTS
        0x1F43F, // CHIPMUNK
        0x1F440, // EYES
        0x1F441, // EYE
        0x1F442, // EAR
        0x1F443, // NOSE
        0x1F444, // MOUTH
        0x1F445, // TONGUE
        0x1F446, // WHITE UP POINTING BACKHAND INDEX
        0x1F447, // WHITE DOWN POINTING BACKHAND INDEX
        0x1F448, // WHITE LEFT POINTING BACKHAND INDEX
        0x1F449, // WHITE RIGHT POINTING BACKHAND INDEX
        0x1F44A, // FISTED HAND SIGN
        0x1F44B, // WAVING HAND SIGN
        0x1F44C, // OK HAND SIGN
        0x1F44D, // THUMBS UP SIGN
        0x1F44E, // THUMBS DOWN SIGN
        0x1F44F, // CLAPPING HANDS SIGN
        0x1F450, // OPEN HANDS SIGN
        0x1F451, // CROWN
        0x1F452, // WOMANS HAT
        0x1F453, // EYEGLASSES
        0x1F454, // NECKTIE
        0x1F455, // T-SHIRT
        0x1F456, // JEANS
        0x1F457, // DRESS
        0x1F458, // KIMONO
        0x1F459, // BIKINI
        0x1F45A, // WOMANS CLOTHES
        0x1F45B, // PURSE
        0x1F45C, // HANDBAG
        0x1F45D, // POUCH
        0x1F45E, // MANS SHOE
        0x1F45F, // ATHLETIC SHOE
        0x1F460, // HIGH-HEELED SHOE
        0x1F461, // WOMANS SANDAL
        0x1F462, // WOMANS BOOTS
        0x1F463, // FOOTPRINTS
        0x1F464, // BUST IN SILHOUETTE
        0x1F465, // BUSTS IN SILHOUETTE
        0x1F466, // BOY
        0x1F467, // GIRL
        0x1F468, // MAN
        0x1F469, // WOMAN
        0x1F46A, // FAMILY
        0x1F46B, // MAN AND WOMAN HOLDING HANDS
        0x1F46C, // TWO MEN HOLDING HANDS
        0x1F46D, // TWO WOMEN HOLDING HANDS
        0x1F46E, // POLICE OFFICER
        0x1F46F, // WOMAN WITH BUNNY EARS
        0x1F470, // BRIDE WITH VEIL
        0x1F471, // PERSON WITH BLOND HAIR
        0x1F472, // MAN WITH GUA PI MAO
        0x1F473, // MAN WITH TURBAN
        0x1F474, // OLDER MAN
        0x1F475, // OLDER WOMAN
        0x1F476, // BABY
        0x1F477, // CONSTRUCTION WORKER
        0x1F478, // PRINCESS
        0x1F479, // JAPANESE OGRE
        0x1F47A, // JAPANESE GOBLIN
        0x1F47B, // GHOST
        0x1F47C, // BABY ANGEL
        0x1F47D, // EXTRATERRESTRIAL ALIEN
        0x1F47E, // ALIEN MONSTER
        0x1F47F, // IMP
        0x1F480, // SKULL
        0x1F481, // INFORMATION DESK PERSON
        0x1F482, // GUARDSMAN
        0x1F483, // DANCER
        0x1F484, // LIPSTICK
        0x1F485, // NAIL POLISH
        0x1F486, // FACE MASSAGE
        0x1F487, // HAIRCUT
        0x1F488, // BARBER POLE
        0x1F489, // SYRINGE
        0x1F48A, // PILL
        0x1F48B, // KISS MARK
        0x1F48C, // LOVE LETTER
        0x1F48D, // RING
        0x1F48E, // GEM STONE
        0x1F48F, // KISS
        0x1F490, // BOUQUET
        0x1F491, // COUPLE WITH HEART
        0x1F492, // WEDDING
        0x1F493, // BEATING HEART
        0x1F494, // BROKEN HEART
        0x1F495, // TWO HEARTS
        0x1F496, // SPARKLING HEART
        0x1F497, // GROWING HEART
        0x1F498, // HEART WITH ARROW
        0x1F499, // BLUE HEART
        0x1F49A, // GREEN HEART
        0x1F49B, // YELLOW HEART
        0x1F49C, // PURPLE HEART
        0x1F49D, // HEART WITH RIBBON
        0x1F49E, // REVOLVING HEARTS
        0x1F49F, // HEART DECORATION
        0x1F4A0, // DIAMOND SHAPE WITH A DOT INSIDE
        0x1F4A1, // ELECTRIC LIGHT BULB
        0x1F4A2, // ANGER SYMBOL
        0x1F4A3, // BOMB
        0x1F4A4, // SLEEPING SYMBOL
        0x1F4A5, // COLLISION SYMBOL
        0x1F4A6, // SPLASHING SWEAT SYMBOL
        0x1F4A7, // DROPLET
        0x1F4A8, // DASH SYMBOL
        0x1F4A9, // PILE OF POO
        0x1F4AA, // FLEXED BICEPS
        0x1F4AB, // DIZZY SYMBOL
        0x1F4AC, // SPEECH BALLOON
        0x1F4AD, // THOUGHT BALLOON
        0x1F4AE, // WHITE FLOWER
        0x1F4AF, // HUNDRED POINTS SYMBOL
        0x1F4B0, // MONEY BAG
        0x1F4B1, // CURRENCY EXCHANGE
        0x1F4B2, // HEAVY DOLLAR SIGN
        0x1F4B3, // CREDIT CARD
        0x1F4B4, // BANKNOTE WITH YEN SIGN
        0x1F4B5, // BANKNOTE WITH DOLLAR SIGN
        0x1F4B6, // BANKNOTE WITH EURO SIGN
        0x1F4B7, // BANKNOTE WITH POUND SIGN
        0x1F4B8, // MONEY WITH WINGS
        0x1F4B9, // CHART WITH UPWARDS TREND AND YEN SIGN
        0x1F4BA, // SEAT
        0x1F4BB, // PERSONAL COMPUTER
        0x1F4BC, // BRIEFCASE
        0x1F4BD, // MINIDISC
        0x1F4BE, // FLOPPY DISK
        0x1F4BF, // OPTICAL DISC
        0x1F4C0, // DVD
        0x1F4C1, // FILE FOLDER
        0x1F4C2, // OPEN FILE FOLDER
        0x1F4C3, // PAGE WITH CURL
        0x1F4C4, // PAGE FACING UP
        0x1F4C5, // CALENDAR
        0x1F4C6, // TEAR-OFF CALENDAR
        0x1F4C7, // CARD INDEX
        0x1F4C8, // CHART WITH UPWARDS TREND
        0x1F4C9, // CHART WITH DOWNWARDS TREND
        0x1F4CA, // BAR CHART
        0x1F4CB, // CLIPBOARD
        0x1F4CC, // PUSHPIN
        0x1F4CD, // ROUND PUSHPIN
        0x1F4CE, // PAPERCLIP
        0x1F4CF, // STRAIGHT RULER
        0x1F4D0, // TRIANGULAR RULER
        0x1F4D1, // BOOKMARK TABS
        0x1F4D2, // LEDGER
        0x1F4D3, // NOTEBOOK
        0x1F4D4, // NOTEBOOK WITH DECORATIVE COVER
        0x1F4D5, // CLOSED BOOK
        0x1F4D6, // OPEN BOOK
        0x1F4D7, // GREEN BOOK
        0x1F4D8, // BLUE BOOK
        0x1F4D9, // ORANGE BOOK
        0x1F4DA, // BOOKS
        0x1F4DB, // NAME BADGE
        0x1F4DC, // SCROLL
        0x1F4DD, // MEMO
        0x1F4DE, // TELEPHONE RECEIVER
        0x1F4DF, // PAGER
        0x1F4E0, // FAX MACHINE
        0x1F4E1, // SATELLITE ANTENNA
        0x1F4E2, // PUBLIC ADDRESS LOUDSPEAKER
        0x1F4E3, // CHEERING MEGAPHONE
        0x1F4E4, // OUTBOX TRAY
        0x1F4E5, // INBOX TRAY
        0x1F4E6, // PACKAGE
        0x1F4E7, // E-MAIL SYMBOL
        0x1F4E8, // INCOMING ENVELOPE
        0x1F4E9, // ENVELOPE WITH DOWNWARDS ARROW ABOVE
        0x1F4EA, // CLOSED MAILBOX WITH LOWERED FLAG
        0x1F4EB, // CLOSED MAILBOX WITH RAISED FLAG
        0x1F4EC, // OPEN MAILBOX WITH RAISED FLAG
        0x1F4ED, // OPEN MAILBOX WITH LOWERED FLAG
        0x1F4EE, // POSTBOX
        0x1F4EF, // POSTAL HORN
        0x1F4F0, // NEWSPAPER
        0x1F4F1, // MOBILE PHONE
        0x1F4F2, // MOBILE PHONE WITH RIGHTWARDS ARROW AT LEFT
        0x1F4F3, // VIBRATION MODE
        0x1F4F4, // MOBILE PHONE OFF
        0x1F4F5, // NO MOBILE PHONES
        0x1F4F6, // ANTENNA WITH BARS
        0x1F4F7, // CAMERA
        0x1F4F8, // CAMERA WITH FLASH
        0x1F4F9, // VIDEO CAMERA
        0x1F4FA, // TELEVISION
        0x1F4FB, // RADIO
        0x1F4FC, // VIDEOCASSETTE
        0x1F4FD, // FILM PROJECTOR
        0x1F4FF, // PRAYER BEADS
        0x1F500, // TWISTED RIGHTWARDS ARROWS
        0x1F501, // CLOCKWISE RIGHTWARDS AND LEFTWARDS OPEN CIRCLE ARROWS
        0x1F502, // CLOCKWISE RIGHTWARDS AND LEFTWARDS OPEN CIRCLE ARROWS WITH CIRCLED ONE OVERLAY
        0x1F503, // CLOCKWISE DOWNWARDS AND UPWARDS OPEN CIRCLE ARROWS
        0x1F504, // ANTICLOCKWISE DOWNWARDS AND UPWARDS OPEN CIRCLE ARROWS
        0x1F505, // LOW BRIGHTNESS SYMBOL
        0x1F506, // HIGH BRIGHTNESS SYMBOL
        0x1F507, // SPEAKER WITH CANCELLATION STROKE
        0x1F508, // SPEAKER
        0x1F509, // SPEAKER WITH ONE SOUND WAVE
        0x1F50A, // SPEAKER WITH THREE SOUND WAVES
        0x1F50B, // BATTERY
        0x1F50C, // ELECTRIC PLUG
        0x1F50D, // LEFT-POINTING MAGNIFYING GLASS
        0x1F50E, // RIGHT-POINTING MAGNIFYING GLASS
        0x1F50F, // LOCK WITH INK PEN
        0x1F510, // CLOSED LOCK WITH KEY
        0x1F511, // KEY
        0x1F512, // LOCK
        0x1F513, // OPEN LOCK
        0x1F514, // BELL
        0x1F515, // BELL WITH CANCELLATION STROKE
        0x1F516, // BOOKMARK
        0x1F517, // LINK SYMBOL
        0x1F518, // RADIO BUTTON
        0x1F519, // BACK WITH LEFTWARDS ARROW ABOVE
        0x1F51A, // END WITH LEFTWARDS ARROW ABOVE
        0x1F51B, // ON WITH EXCLAMATION MARK WITH LEFT RIGHT ARROW ABOVE
        0x1F51C, // SOON WITH RIGHTWARDS ARROW ABOVE
        0x1F51D, // TOP WITH UPWARDS ARROW ABOVE
        0x1F51E, // NO ONE UNDER EIGHTEEN SYMBOL
        0x1F51F, // KEYCAP TEN
        0x1F520, // INPUT SYMBOL FOR LATIN CAPITAL LETTERS
        0x1F521, // INPUT SYMBOL FOR LATIN SMALL LETTERS
        0x1F522, // INPUT SYMBOL FOR NUMBERS
        0x1F523, // INPUT SYMBOL FOR SYMBOLS
        0x1F524, // INPUT SYMBOL FOR LATIN LETTERS
        0x1F525, // FIRE
        0x1F526, // ELECTRIC TORCH
        0x1F527, // WRENCH
        0x1F528, // HAMMER
        0x1F529, // NUT AND BOLT
        0x1F52A, // HOCHO
        0x1F52B, // PISTOL
        0x1F52C, // MICROSCOPE
        0x1F52D, // TELESCOPE
        0x1F52E, // CRYSTAL BALL
        0x1F52F, // SIX POINTED STAR WITH MIDDLE DOT
        0x1F530, // JAPANESE SYMBOL FOR BEGINNER
        0x1F531, // TRIDENT EMBLEM
        0x1F532, // BLACK SQUARE BUTTON
        0x1F533, // WHITE SQUARE BUTTON
        0x1F534, // LARGE RED CIRCLE
        0x1F535, // LARGE BLUE CIRCLE
        0x1F536, // LARGE ORANGE DIAMOND
        0x1F537, // LARGE BLUE DIAMOND
        0x1F538, // SMALL ORANGE DIAMOND
        0x1F539, // SMALL BLUE DIAMOND
        0x1F53A, // UP-POINTING RED TRIANGLE
        0x1F53B, // DOWN-POINTING RED TRIANGLE
        0x1F53C, // UP-POINTING SMALL RED TRIANGLE
        0x1F53D, // DOWN-POINTING SMALL RED TRIANGLE
        0x1F549, // OM SYMBOL
        0x1F54A, // DOVE OF PEACE
        0x1F54B, // KAABA
        0x1F54C, // MOSQUE
        0x1F54D, // SYNAGOGUE
        0x1F54E, // MENORAH WITH NINE BRANCHES
        0x1F550, // CLOCK FACE ONE OCLOCK
        0x1F551, // CLOCK FACE TWO OCLOCK
        0x1F552, // CLOCK FACE THREE OCLOCK
        0x1F553, // CLOCK FACE FOUR OCLOCK
        0x1F554, // CLOCK FACE FIVE OCLOCK
        0x1F555, // CLOCK FACE SIX OCLOCK
        0x1F556, // CLOCK FACE SEVEN OCLOCK
        0x1F557, // CLOCK FACE EIGHT OCLOCK
        0x1F558, // CLOCK FACE NINE OCLOCK
        0x1F559, // CLOCK FACE TEN OCLOCK
        0x1F55A, // CLOCK FACE ELEVEN OCLOCK
        0x1F55B, // CLOCK FACE TWELVE OCLOCK
        0x1F55C, // CLOCK FACE ONE-THIRTY
        0x1F55D, // CLOCK FACE TWO-THIRTY
        0x1F55E, // CLOCK FACE THREE-THIRTY
        0x1F55F, // CLOCK FACE FOUR-THIRTY
        0x1F560, // CLOCK FACE FIVE-THIRTY
        0x1F561, // CLOCK FACE SIX-THIRTY
        0x1F562, // CLOCK FACE SEVEN-THIRTY
        0x1F563, // CLOCK FACE EIGHT-THIRTY
        0x1F564, // CLOCK FACE NINE-THIRTY
        0x1F565, // CLOCK FACE TEN-THIRTY
        0x1F566, // CLOCK FACE ELEVEN-THIRTY
        0x1F567, // CLOCK FACE TWELVE-THIRTY
        0x1F56F, // CANDLE
        0x1F570, // MANTELPIECE CLOCK
        0x1F573, // HOLE
        0x1F574, // MAN IN BUSINESS SUIT LEVITATING
        0x1F575, // SLEUTH OR SPY
        0x1F576, // DARK SUNGLASSES
        0x1F577, // SPIDER
        0x1F578, // SPIDER WEB
        0x1F579, // JOYSTICK
        0x1F57A, // MAN DANCING
        0x1F587, // LINKED PAPERCLIPS
        0x1F58A, // LOWER LEFT BALLPOINT PEN
        0x1F58B, // LOWER LEFT FOUNTAIN PEN
        0x1F58C, // LOWER LEFT PAINTBRUSH
        0x1F58D, // LOWER LEFT CRAYON
        0x1F590, // RAISED HAND WITH FINGERS SPLAYED
        0x1F595, // REVERSED HAND WITH MIDDLE FINGER EXTENDED
        0x1F596, // RAISED HAND WITH PART BETWEEN MIDDLE AND RING FINGERS
        0x1F5A4, // BLACK HEART
        0x1F5A5, // DESKTOP COMPUTER
        0x1F5A8, // PRINTER
        0x1F5B1, // THREE BUTTON MOUSE
        0x1F5B2, // TRACKBALL
        0x1F5BC, // FRAME WITH PICTURE
        0x1F5C2, // CARD INDEX DIVIDERS
        0x1F5C3, // CARD FILE BOX
        0x1F5C4, // FILE CABINET
        0x1F5D1, // WASTEBASKET
        0x1F5D2, // SPIRAL NOTE PAD
        0x1F5D3, // SPIRAL CALENDAR PAD
        0x1F5DC, // COMPRESSION
        0x1F5DD, // OLD KEY
        0x1F5DE, // ROLLED-UP NEWSPAPER
        0x1F5E1, // DAGGER KNIFE
        0x1F5E3, // SPEAKING HEAD IN SILHOUETTE
        0x1F5E8, // LEFT SPEECH BUBBLE
        0x1F5EF, // RIGHT ANGER BUBBLE
        0x1F5F3, // BALLOT BOX WITH BALLOT
        0x1F5FA, // WORLD MAP
        0x1F5FB, // MOUNT FUJI
        0x1F5FC, // TOKYO TOWER
        0x1F5FD, // STATUE OF LIBERTY
        0x1F5FE, // SILHOUETTE OF JAPAN
        0x1F5FF, // MOYAI
        0x1F600, // GRINNING FACE
        0x1F601, // GRINNING FACE WITH SMILING EYES
        0x1F602, // FACE WITH TEARS OF JOY
        0x1F603, // SMILING FACE WITH OPEN MOUTH
        0x1F604, // SMILING FACE WITH OPEN MOUTH AND SMILING EYES
        0x1F605, // SMILING FACE WITH OPEN MOUTH AND COLD SWEAT
        0x1F606, // SMILING FACE WITH OPEN MOUTH AND TIGHTLY-CLOSED EYES
        0x1F607, // SMILING FACE WITH HALO
        0x1F608, // SMILING FACE WITH HORNS
        0x1F609, // WINKING FACE
        0x1F60A, // SMILING FACE WITH SMILING EYES
        0x1F60B, // FACE SAVOURING DELICIOUS FOOD
        0x1F60C, // RELIEVED FACE
        0x1F60D, // SMILING FACE WITH HEART-SHAPED EYES
        0x1F60E, // SMILING FACE WITH SUNGLASSES
        0x1F60F, // SMIRKING FACE
        0x1F610, // NEUTRAL FACE
        0x1F611, // EXPRESSIONLESS FACE
        0x1F612, // UNAMUSED FACE
        0x1F613, // FACE WITH COLD SWEAT
        0x1F614, // PENSIVE FACE
        0x1F615, // CONFUSED FACE
        0x1F616, // CONFOUNDED FACE
        0x1F617, // KISSING FACE
        0x1F618, // FACE THROWING A KISS
        0x1F619, // KISSING FACE WITH SMILING EYES
        0x1F61A, // KISSING FACE WITH CLOSED EYES
        0x1F61B, // FACE WITH STUCK-OUT TONGUE
        0x1F61C, // FACE WITH STUCK-OUT TONGUE AND WINKING EYE
        0x1F61D, // FACE WITH STUCK-OUT TONGUE AND TIGHTLY-CLOSED EYES
        0x1F61E, // DISAPPOINTED FACE
        0x1F61F, // WORRIED FACE
        0x1F620, // ANGRY FACE
        0x1F621, // POUTING FACE
        0x1F622, // CRYING FACE
        0x1F623, // PERSEVERING FACE
        0x1F624, // FACE WITH LOOK OF TRIUMPH
        0x1F625, // DISAPPOINTED BUT RELIEVED FACE
        0x1F626, // FROWNING FACE WITH OPEN MOUTH
        0x1F627, // ANGUISHED FACE
        0x1F628, // FEARFUL FACE
        0x1F629, // WEARY FACE
        0x1F62A, // SLEEPY FACE
        0x1F62B, // TIRED FACE
        0x1F62C, // GRIMACING FACE
        0x1F62D, // LOUDLY CRYING FACE
        0x1F62E, // FACE WITH OPEN MOUTH
        0x1F62F, // HUSHED FACE
        0x1F630, // FACE WITH OPEN MOUTH AND COLD SWEAT
        0x1F631, // FACE SCREAMING IN FEAR
        0x1F632, // ASTONISHED FACE
        0x1F633, // FLUSHED FACE
        0x1F634, // SLEEPING FACE
        0x1F635, // DIZZY FACE
        0x1F636, // FACE WITHOUT MOUTH
        0x1F637, // FACE WITH MEDICAL MASK
        0x1F638, // GRINNING CAT FACE WITH SMILING EYES
        0x1F639, // CAT FACE WITH TEARS OF JOY
        0x1F63A, // SMILING CAT FACE WITH OPEN MOUTH
        0x1F63B, // SMILING CAT FACE WITH HEART-SHAPED EYES
        0x1F63C, // CAT FACE WITH WRY SMILE
        0x1F63D, // KISSING CAT FACE WITH CLOSED EYES
        0x1F63E, // POUTING CAT FACE
        0x1F63F, // CRYING CAT FACE
        0x1F640, // WEARY CAT FACE
        0x1F641, // SLIGHTLY FROWNING FACE
        0x1F642, // SLIGHTLY SMILING FACE
        0x1F643, // UPSIDE-DOWN FACE
        0x1F644, // FACE WITH ROLLING EYES
        0x1F645, // FACE WITH NO GOOD GESTURE
        0x1F646, // FACE WITH OK GESTURE
        0x1F647, // PERSON BOWING DEEPLY
        0x1F648, // SEE-NO-EVIL MONKEY
        0x1F649, // HEAR-NO-EVIL MONKEY
        0x1F64A, // SPEAK-NO-EVIL MONKEY
        0x1F64B, // HAPPY PERSON RAISING ONE HAND
        0x1F64C, // PERSON RAISING BOTH HANDS IN CELEBRATION
        0x1F64D, // PERSON FROWNING
        0x1F64E, // PERSON WITH POUTING FACE
        0x1F64F, // PERSON WITH FOLDED HANDS
        0x1F680, // ROCKET
        0x1F681, // HELICOPTER
        0x1F682, // STEAM LOCOMOTIVE
        0x1F683, // RAILWAY CAR
        0x1F684, // HIGH-SPEED TRAIN
        0x1F685, // HIGH-SPEED TRAIN WITH BULLET NOSE
        0x1F686, // TRAIN
        0x1F687, // METRO
        0x1F688, // LIGHT RAIL
        0x1F689, // STATION
        0x1F68A, // TRAM
        0x1F68B, // TRAM CAR
        0x1F68C, // BUS
        0x1F68D, // ONCOMING BUS
        0x1F68E, // TROLLEYBUS
        0x1F68F, // BUS STOP
        0x1F690, // MINIBUS
        0x1F691, // AMBULANCE
        0x1F692, // FIRE ENGINE
        0x1F693, // POLICE CAR
        0x1F694, // ONCOMING POLICE CAR
        0x1F695, // TAXI
        0x1F696, // ONCOMING TAXI
        0x1F697, // AUTOMOBILE
        0x1F698, // ONCOMING AUTOMOBILE
        0x1F699, // RECREATIONAL VEHICLE
        0x1F69A, // DELIVERY TRUCK
        0x1F69B, // ARTICULATED LORRY
        0x1F69C, // TRACTOR
        0x1F69D, // MONORAIL
        0x1F69E, // MOUNTAIN RAILWAY
        0x1F69F, // SUSPENSION RAILWAY
        0x1F6A0, // MOUNTAIN CABLEWAY
        0x1F6A1, // AERIAL TRAMWAY
        0x1F6A2, // SHIP
        0x1F6A3, // ROWBOAT
        0x1F6A4, // SPEEDBOAT
        0x1F6A5, // HORIZONTAL TRAFFIC LIGHT
        0x1F6A6, // VERTICAL TRAFFIC LIGHT
        0x1F6A7, // CONSTRUCTION SIGN
        0x1F6A8, // POLICE CARS REVOLVING LIGHT
        0x1F6A9, // TRIANGULAR FLAG ON POST
        0x1F6AA, // DOOR
        0x1F6AB, // NO ENTRY SIGN
        0x1F6AC, // SMOKING SYMBOL
        0x1F6AD, // NO SMOKING SYMBOL
        0x1F6AE, // PUT LITTER IN ITS PLACE SYMBOL
        0x1F6AF, // DO NOT LITTER SYMBOL
        0x1F6B0, // POTABLE WATER SYMBOL
        0x1F6B1, // NON-POTABLE WATER SYMBOL
        0x1F6B2, // BICYCLE
        0x1F6B3, // NO BICYCLES
        0x1F6B4, // BICYCLIST
        0x1F6B5, // MOUNTAIN BICYCLIST
        0x1F6B6, // PEDESTRIAN
        0x1F6B7, // NO PEDESTRIANS
        0x1F6B8, // CHILDREN CROSSING
        0x1F6B9, // MENS SYMBOL
        0x1F6BA, // WOMENS SYMBOL
        0x1F6BB, // RESTROOM
        0x1F6BC, // BABY SYMBOL
        0x1F6BD, // TOILET
        0x1F6BE, // WATER CLOSET
        0x1F6BF, // SHOWER
        0x1F6C0, // BATH
        0x1F6C1, // BATHTUB
        0x1F6C2, // PASSPORT CONTROL
        0x1F6C3, // CUSTOMS
        0x1F6C4, // BAGGAGE CLAIM
        0x1F6C5, // LEFT LUGGAGE
        0x1F6CB, // COUCH AND LAMP
        0x1F6CC, // SLEEPING ACCOMMODATION
        0x1F6CD, // SHOPPING BAGS
        0x1F6CE, // BELLHOP BELL
        0x1F6CF, // BED
        0x1F6D0, // PLACE OF WORSHIP
        0x1F6D1, // OCTAGONAL SIGN
        0x1F6D2, // SHOPPING TROLLEY
        0x1F6E0, // HAMMER AND WRENCH
        0x1F6E1, // SHIELD
        0x1F6E2, // OIL DRUM
        0x1F6E3, // MOTORWAY
        0x1F6E4, // RAILWAY TRACK
        0x1F6E5, // MOTOR BOAT
        0x1F6E9, // SMALL AIRPLANE
        0x1F6EB, // AIRPLANE DEPARTURE
        0x1F6EC, // AIRPLANE ARRIVING
        0x1F6F0, // SATELLITE
        0x1F6F3, // PASSENGER SHIP
        0x1F6F4, // SCOOTER
        0x1F6F5, // MOTOR SCOOTER
        0x1F6F6, // CANOE
        0x1F6F7, // SLED
        0x1F6F8, // FLYING SAUCER
        0x1F910, // ZIPPER-MOUTH FACE
        0x1F911, // MONEY-MOUTH FACE
        0x1F912, // FACE WITH THERMOMETER
        0x1F913, // NERD FACE
        0x1F914, // THINKING FACE
        0x1F915, // FACE WITH HEAD-BANDAGE
        0x1F916, // ROBOT FACE
        0x1F917, // HUGGING FACE
        0x1F918, // SIGN OF THE HORNS
        0x1F919, // CALL ME HAND
        0x1F91A, // RAISED BACK OF HAND
        0x1F91B, // LEFT-FACING FIST
        0x1F91C, // RIGHT-FACING FIST
        0x1F91D, // HANDSHAKE
        0x1F91E, // HAND WITH INDEX AND MIDDLE FINGERS CROSSED
        0x1F91F, // LOVE-YOU GESTURE
        0x1F920, // FACE WITH COWBOY HAT
        0x1F921, // CLOWN FACE
        0x1F922, // NAUSEATED FACE
        0x1F923, // ROLLING ON THE FLOOR LAUGHING
        0x1F924, // DROOLING FACE
        0x1F925, // LYING FACE
        0x1F926, // FACE PALM
        0x1F927, // SNEEZING FACE
        0x1F928, // FACE WITH RAISED EYEBROW
        0x1F929, // STAR-STRUCK
        0x1F92A, // CRAZY FACE
        0x1F92B, // SHUSHING FACE
        0x1F92C, // FACE WITH SYMBOLS OVER MOUTH
        0x1F92D, // FACE WITH HAND OVER MOUTH
        0x1F92E, // FACE VOMITING
        0x1F92F, // EXPLODING HEAD
        0x1F930, // PREGNANT WOMAN
        0x1F931, // BREAST-FEEDING
        0x1F932, // PALMS UP TOGETHER
        0x1F933, // SELFIE
        0x1F934, // PRINCE
        0x1F935, // MAN IN TUXEDO
        0x1F936, // MOTHER CHRISTMAS
        0x1F937, // SHRUG
        0x1F938, // PERSON DOING CARTWHEEL
        0x1F939, // JUGGLING
        0x1F93A, // FENCER
        0x1F93C, // WRESTLERS
        0x1F93D, // WATER POLO
        0x1F93E, // HANDBALL
        0x1F940, // WILTED FLOWER
        0x1F941, // DRUM WITH DRUMSTICKS
        0x1F942, // CLINKING GLASSES
        0x1F943, // TUMBLER GLASS
        0x1F944, // SPOON
        0x1F945, // GOAL NET
        0x1F947, // FIRST PLACE MEDAL
        0x1F948, // SECOND PLACE MEDAL
        0x1F949, // THIRD PLACE MEDAL
        0x1F94A, // BOXING GLOVE
        0x1F94B, // MARTIAL ARTS UNIFORM
        0x1F94C, // CURLING STONE
        0x1F950, // CROISSANT
        0x1F951, // AVOCADO
        0x1F952, // CUCUMBER
        0x1F953, // BACON
        0x1F954, // POTATO
        0x1F955, // CARROT
        0x1F956, // BAGUETTE BREAD
        0x1F957, // GREEN SALAD
        0x1F958, // SHALLOW PAN OF FOOD
        0x1F959, // STUFFED FLATBREAD
        0x1F95A, // EGG
        0x1F95B, // GLASS OF MILK
        0x1F95C, // PEANUTS
        0x1F95D, // KIWIFRUIT
        0x1F95E, // PANCAKES
        0x1F95F, // DUMPLING
        0x1F960, // FORTUNE COOKIE
        0x1F961, // TAKEOUT BOX
        0x1F962, // CHOPSTICKS
        0x1F963, // BOWL WITH SPOON
        0x1F964, // CUP WITH STRAW
        0x1F965, // COCONUT
        0x1F966, // BROCCOLI
        0x1F967, // PIE
        0x1F968, // PRETZEL
        0x1F969, // CUT OF MEAT
        0x1F96A, // SANDWICH
        0x1F96B, // CANNED FOOD
        0x1F980, // CRAB
        0x1F981, // LION FACE
        0x1F982, // SCORPION
        0x1F983, // TURKEY
        0x1F984, // UNICORN FACE
        0x1F985, // EAGLE
        0x1F986, // DUCK
        0x1F987, // BAT
        0x1F988, // SHARK
        0x1F989, // OWL
        0x1F98A, // FOX FACE
        0x1F98B, // BUTTERFLY
        0x1F98C, // DEER
        0x1F98D, // GORILLA
        0x1F98E, // LIZARD
        0x1F98F, // RHINOCEROS
        0x1F990, // SHRIMP
        0x1F991, // SQUID
        0x1F992, // GIRAFFE
        0x1F993, // ZEBRA
        0x1F994, // HEDGEHOG
        0x1F995, // SAUROPOD
        0x1F996, // T-REX
        0x1F997, // CRICKET
        0x1F9C0, // CHEESE WEDGE
        0x1F9D0, // FACE WITH MONOCLE
        0x1F9D1, // ADULT
        0x1F9D2, // CHILD
        0x1F9D3, // OLDER ADULT
        0x1F9D4, // BEARDED PERSON
        0x1F9D5, // WOMAN WITH HEADSCARF
        0x1F9D6, // PERSON IN STEAMY ROOM
        0x1F9D7, // PERSON CLIMBING
        0x1F9D8, // PERSON IN LOTUS POSITION
        0x1F9D9, // MAGE
        0x1F9DA, // FAIRY
        0x1F9DB, // VAMPIRE
        0x1F9DC, // MERPERSON
        0x1F9DD, // ELF
        0x1F9DE, // GENIE
        0x1F9DF, // ZOMBIE
        0x1F9E0, // BRAIN
        0x1F9E1, // ORANGE HEART
        0x1F9E2, // BILLED CAP
        0x1F9E3, // SCARF
        0x1F9E4, // GLOVES
        0x1F9E5, // COAT
        0x1F9E6, // SOCKS
    };

    @Test
    public void testEmojiCoverage() {
        final Paint paint = new Paint();
        for (int cp : ALL_EMOJI) {
            final String characterAsString = new String(Character.toChars(cp));
            assertTrue(String.format("No glyph for U+%04X", cp), paint.hasGlyph(characterAsString));
        }
    }
}
