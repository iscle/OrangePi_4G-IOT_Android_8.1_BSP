/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.cts.compilation;

import android.app.Activity;
import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A simple activity which can be subjected to (dex to native) compilation.
 *
 * If you change this code, you need to regenerate APK and profile using the
 * the new code - see instructions in {@code assets/README.txt}.
 */
public class CompilationTargetActivity extends Activity {

    private AsyncTask<Integer, String, Void> mTask;

    @Override
    protected void onResume() {
        super.onResume();
        setTitle("Starting...");
        mTask = new AsyncTask<Integer, String, Void>() {
            @Override
            protected Void doInBackground(Integer... params) {
                int numValues = params[0];
                int numIter = params[1];
                for (int i = 0; i < numIter; i++) {
                    if (Thread.interrupted()) {
                        break;
                    }
                    publishProgress("Step " + (i+1) + " of " + numIter);
                    List<Integer> values = makeValues(numValues);
                    Collections.shuffle(values);
                    Collections.sort(values);
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                setTitle(values[0]);
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                setTitle("Done");
            }
        };
        mTask.execute(1024, 100 * 1000);
    }

    @Override
    protected void onPause() {
        mTask.cancel(/* mayInterruptIfRunning */ true);
        mTask = null;
        super.onPause();
    }

    private List<Integer> makeValues(int numValues) {
        List<Integer> result = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numValues; i++) {
            int v = dispatch(random.nextInt());
            result.add(v);
        }
        return result;
    }

    /**
     * Dispatches to a bunch of simple methods because JIT profiles are only generated for
     * apps with enough methods (10, as of May 2016).
     */
    private int dispatch(int i) {
        int v = Math.abs(i % 100);
        switch (v) {
            case 0: return m0();
            case 1: return m1();
            case 2: return m2();
            case 3: return m3();
            case 4: return m4();
            case 5: return m5();
            case 6: return m6();
            case 7: return m7();
            case 8: return m8();
            case 9: return m9();
            case 10: return m10();
            case 11: return m11();
            case 12: return m12();
            case 13: return m13();
            case 14: return m14();
            case 15: return m15();
            case 16: return m16();
            case 17: return m17();
            case 18: return m18();
            case 19: return m19();
            case 20: return m20();
            case 21: return m21();
            case 22: return m22();
            case 23: return m23();
            case 24: return m24();
            case 25: return m25();
            case 26: return m26();
            case 27: return m27();
            case 28: return m28();
            case 29: return m29();
            case 30: return m30();
            case 31: return m31();
            case 32: return m32();
            case 33: return m33();
            case 34: return m34();
            case 35: return m35();
            case 36: return m36();
            case 37: return m37();
            case 38: return m38();
            case 39: return m39();
            case 40: return m40();
            case 41: return m41();
            case 42: return m42();
            case 43: return m43();
            case 44: return m44();
            case 45: return m45();
            case 46: return m46();
            case 47: return m47();
            case 48: return m48();
            case 49: return m49();
            case 50: return m50();
            case 51: return m51();
            case 52: return m52();
            case 53: return m53();
            case 54: return m54();
            case 55: return m55();
            case 56: return m56();
            case 57: return m57();
            case 58: return m58();
            case 59: return m59();
            case 60: return m60();
            case 61: return m61();
            case 62: return m62();
            case 63: return m63();
            case 64: return m64();
            case 65: return m65();
            case 66: return m66();
            case 67: return m67();
            case 68: return m68();
            case 69: return m69();
            case 70: return m70();
            case 71: return m71();
            case 72: return m72();
            case 73: return m73();
            case 74: return m74();
            case 75: return m75();
            case 76: return m76();
            case 77: return m77();
            case 78: return m78();
            case 79: return m79();
            case 80: return m80();
            case 81: return m81();
            case 82: return m82();
            case 83: return m83();
            case 84: return m84();
            case 85: return m85();
            case 86: return m86();
            case 87: return m87();
            case 88: return m88();
            case 89: return m89();
            case 90: return m90();
            case 91: return m91();
            case 92: return m92();
            case 93: return m93();
            case 94: return m94();
            case 95: return m95();
            case 96: return m96();
            case 97: return m97();
            case 98: return m98();
            case 99: return m99();
            case 100: return m100();
            case 101: return m101();
            case 102: return m102();
            case 103: return m103();
            case 104: return m104();
            case 105: return m105();
            case 106: return m106();
            case 107: return m107();
            case 108: return m108();
            case 109: return m109();
            case 110: return m110();
            case 111: return m111();
            case 112: return m112();
            case 113: return m113();
            case 114: return m114();
            case 115: return m115();
            case 116: return m116();
            case 117: return m117();
            case 118: return m118();
            case 119: return m119();
            case 120: return m120();
            case 121: return m121();
            case 122: return m122();
            case 123: return m123();
            case 124: return m124();
            case 125: return m125();
            case 126: return m126();
            case 127: return m127();
            case 128: return m128();
            case 129: return m129();
            case 130: return m130();
            case 131: return m131();
            case 132: return m132();
            case 133: return m133();
            case 134: return m134();
            case 135: return m135();
            case 136: return m136();
            case 137: return m137();
            case 138: return m138();
            case 139: return m139();
            case 140: return m140();
            case 141: return m141();
            case 142: return m142();
            case 143: return m143();
            case 144: return m144();
            case 145: return m145();
            case 146: return m146();
            case 147: return m147();
            case 148: return m148();
            case 149: return m149();
            case 150: return m150();
            case 151: return m151();
            case 152: return m152();
            case 153: return m153();
            case 154: return m154();
            case 155: return m155();
            case 156: return m156();
            case 157: return m157();
            case 158: return m158();
            case 159: return m159();
            case 160: return m160();
            case 161: return m161();
            case 162: return m162();
            case 163: return m163();
            case 164: return m164();
            case 165: return m165();
            case 166: return m166();
            case 167: return m167();
            case 168: return m168();
            case 169: return m169();
            case 170: return m170();
            case 171: return m171();
            case 172: return m172();
            case 173: return m173();
            case 174: return m174();
            case 175: return m175();
            case 176: return m176();
            case 177: return m177();
            case 178: return m178();
            case 179: return m179();
            case 180: return m180();
            case 181: return m181();
            case 182: return m182();
            case 183: return m183();
            case 184: return m184();
            case 185: return m185();
            case 186: return m186();
            case 187: return m187();
            case 188: return m188();
            case 189: return m189();
            case 190: return m190();
            case 191: return m191();
            case 192: return m192();
            case 193: return m193();
            case 194: return m194();
            case 195: return m195();
            case 196: return m196();
            case 197: return m197();
            case 198: return m198();
            case 199: return m199();
            default: throw new AssertionError(v + " out of bounds");
        }
    }

    public int m0() { return new Random(0).nextInt(); }
    public int m1() { return new Random(1).nextInt(); }
    public int m2() { return new Random(2).nextInt(); }
    public int m3() { return new Random(3).nextInt(); }
    public int m4() { return new Random(4).nextInt(); }
    public int m5() { return new Random(5).nextInt(); }
    public int m6() { return new Random(6).nextInt(); }
    public int m7() { return new Random(7).nextInt(); }
    public int m8() { return new Random(8).nextInt(); }
    public int m9() { return new Random(9).nextInt(); }
    public int m10() { return new Random(10).nextInt(); }
    public int m11() { return new Random(11).nextInt(); }
    public int m12() { return new Random(12).nextInt(); }
    public int m13() { return new Random(13).nextInt(); }
    public int m14() { return new Random(14).nextInt(); }
    public int m15() { return new Random(15).nextInt(); }
    public int m16() { return new Random(16).nextInt(); }
    public int m17() { return new Random(17).nextInt(); }
    public int m18() { return new Random(18).nextInt(); }
    public int m19() { return new Random(19).nextInt(); }
    public int m20() { return new Random(20).nextInt(); }
    public int m21() { return new Random(21).nextInt(); }
    public int m22() { return new Random(22).nextInt(); }
    public int m23() { return new Random(23).nextInt(); }
    public int m24() { return new Random(24).nextInt(); }
    public int m25() { return new Random(25).nextInt(); }
    public int m26() { return new Random(26).nextInt(); }
    public int m27() { return new Random(27).nextInt(); }
    public int m28() { return new Random(28).nextInt(); }
    public int m29() { return new Random(29).nextInt(); }
    public int m30() { return new Random(30).nextInt(); }
    public int m31() { return new Random(31).nextInt(); }
    public int m32() { return new Random(32).nextInt(); }
    public int m33() { return new Random(33).nextInt(); }
    public int m34() { return new Random(34).nextInt(); }
    public int m35() { return new Random(35).nextInt(); }
    public int m36() { return new Random(36).nextInt(); }
    public int m37() { return new Random(37).nextInt(); }
    public int m38() { return new Random(38).nextInt(); }
    public int m39() { return new Random(39).nextInt(); }
    public int m40() { return new Random(40).nextInt(); }
    public int m41() { return new Random(41).nextInt(); }
    public int m42() { return new Random(42).nextInt(); }
    public int m43() { return new Random(43).nextInt(); }
    public int m44() { return new Random(44).nextInt(); }
    public int m45() { return new Random(45).nextInt(); }
    public int m46() { return new Random(46).nextInt(); }
    public int m47() { return new Random(47).nextInt(); }
    public int m48() { return new Random(48).nextInt(); }
    public int m49() { return new Random(49).nextInt(); }
    public int m50() { return new Random(50).nextInt(); }
    public int m51() { return new Random(51).nextInt(); }
    public int m52() { return new Random(52).nextInt(); }
    public int m53() { return new Random(53).nextInt(); }
    public int m54() { return new Random(54).nextInt(); }
    public int m55() { return new Random(55).nextInt(); }
    public int m56() { return new Random(56).nextInt(); }
    public int m57() { return new Random(57).nextInt(); }
    public int m58() { return new Random(58).nextInt(); }
    public int m59() { return new Random(59).nextInt(); }
    public int m60() { return new Random(60).nextInt(); }
    public int m61() { return new Random(61).nextInt(); }
    public int m62() { return new Random(62).nextInt(); }
    public int m63() { return new Random(63).nextInt(); }
    public int m64() { return new Random(64).nextInt(); }
    public int m65() { return new Random(65).nextInt(); }
    public int m66() { return new Random(66).nextInt(); }
    public int m67() { return new Random(67).nextInt(); }
    public int m68() { return new Random(68).nextInt(); }
    public int m69() { return new Random(69).nextInt(); }
    public int m70() { return new Random(70).nextInt(); }
    public int m71() { return new Random(71).nextInt(); }
    public int m72() { return new Random(72).nextInt(); }
    public int m73() { return new Random(73).nextInt(); }
    public int m74() { return new Random(74).nextInt(); }
    public int m75() { return new Random(75).nextInt(); }
    public int m76() { return new Random(76).nextInt(); }
    public int m77() { return new Random(77).nextInt(); }
    public int m78() { return new Random(78).nextInt(); }
    public int m79() { return new Random(79).nextInt(); }
    public int m80() { return new Random(80).nextInt(); }
    public int m81() { return new Random(81).nextInt(); }
    public int m82() { return new Random(82).nextInt(); }
    public int m83() { return new Random(83).nextInt(); }
    public int m84() { return new Random(84).nextInt(); }
    public int m85() { return new Random(85).nextInt(); }
    public int m86() { return new Random(86).nextInt(); }
    public int m87() { return new Random(87).nextInt(); }
    public int m88() { return new Random(88).nextInt(); }
    public int m89() { return new Random(89).nextInt(); }
    public int m90() { return new Random(90).nextInt(); }
    public int m91() { return new Random(91).nextInt(); }
    public int m92() { return new Random(92).nextInt(); }
    public int m93() { return new Random(93).nextInt(); }
    public int m94() { return new Random(94).nextInt(); }
    public int m95() { return new Random(95).nextInt(); }
    public int m96() { return new Random(96).nextInt(); }
    public int m97() { return new Random(97).nextInt(); }
    public int m98() { return new Random(98).nextInt(); }
    public int m99() { return new Random(99).nextInt(); }
    public int m100() { return new Random(100).nextInt(); }
    public int m101() { return new Random(101).nextInt(); }
    public int m102() { return new Random(102).nextInt(); }
    public int m103() { return new Random(103).nextInt(); }
    public int m104() { return new Random(104).nextInt(); }
    public int m105() { return new Random(105).nextInt(); }
    public int m106() { return new Random(106).nextInt(); }
    public int m107() { return new Random(107).nextInt(); }
    public int m108() { return new Random(108).nextInt(); }
    public int m109() { return new Random(109).nextInt(); }
    public int m110() { return new Random(110).nextInt(); }
    public int m111() { return new Random(111).nextInt(); }
    public int m112() { return new Random(112).nextInt(); }
    public int m113() { return new Random(113).nextInt(); }
    public int m114() { return new Random(114).nextInt(); }
    public int m115() { return new Random(115).nextInt(); }
    public int m116() { return new Random(116).nextInt(); }
    public int m117() { return new Random(117).nextInt(); }
    public int m118() { return new Random(118).nextInt(); }
    public int m119() { return new Random(119).nextInt(); }
    public int m120() { return new Random(120).nextInt(); }
    public int m121() { return new Random(121).nextInt(); }
    public int m122() { return new Random(122).nextInt(); }
    public int m123() { return new Random(123).nextInt(); }
    public int m124() { return new Random(124).nextInt(); }
    public int m125() { return new Random(125).nextInt(); }
    public int m126() { return new Random(126).nextInt(); }
    public int m127() { return new Random(127).nextInt(); }
    public int m128() { return new Random(128).nextInt(); }
    public int m129() { return new Random(129).nextInt(); }
    public int m130() { return new Random(130).nextInt(); }
    public int m131() { return new Random(131).nextInt(); }
    public int m132() { return new Random(132).nextInt(); }
    public int m133() { return new Random(133).nextInt(); }
    public int m134() { return new Random(134).nextInt(); }
    public int m135() { return new Random(135).nextInt(); }
    public int m136() { return new Random(136).nextInt(); }
    public int m137() { return new Random(137).nextInt(); }
    public int m138() { return new Random(138).nextInt(); }
    public int m139() { return new Random(139).nextInt(); }
    public int m140() { return new Random(140).nextInt(); }
    public int m141() { return new Random(141).nextInt(); }
    public int m142() { return new Random(142).nextInt(); }
    public int m143() { return new Random(143).nextInt(); }
    public int m144() { return new Random(144).nextInt(); }
    public int m145() { return new Random(145).nextInt(); }
    public int m146() { return new Random(146).nextInt(); }
    public int m147() { return new Random(147).nextInt(); }
    public int m148() { return new Random(148).nextInt(); }
    public int m149() { return new Random(149).nextInt(); }
    public int m150() { return new Random(150).nextInt(); }
    public int m151() { return new Random(151).nextInt(); }
    public int m152() { return new Random(152).nextInt(); }
    public int m153() { return new Random(153).nextInt(); }
    public int m154() { return new Random(154).nextInt(); }
    public int m155() { return new Random(155).nextInt(); }
    public int m156() { return new Random(156).nextInt(); }
    public int m157() { return new Random(157).nextInt(); }
    public int m158() { return new Random(158).nextInt(); }
    public int m159() { return new Random(159).nextInt(); }
    public int m160() { return new Random(160).nextInt(); }
    public int m161() { return new Random(161).nextInt(); }
    public int m162() { return new Random(162).nextInt(); }
    public int m163() { return new Random(163).nextInt(); }
    public int m164() { return new Random(164).nextInt(); }
    public int m165() { return new Random(165).nextInt(); }
    public int m166() { return new Random(166).nextInt(); }
    public int m167() { return new Random(167).nextInt(); }
    public int m168() { return new Random(168).nextInt(); }
    public int m169() { return new Random(169).nextInt(); }
    public int m170() { return new Random(170).nextInt(); }
    public int m171() { return new Random(171).nextInt(); }
    public int m172() { return new Random(172).nextInt(); }
    public int m173() { return new Random(173).nextInt(); }
    public int m174() { return new Random(174).nextInt(); }
    public int m175() { return new Random(175).nextInt(); }
    public int m176() { return new Random(176).nextInt(); }
    public int m177() { return new Random(177).nextInt(); }
    public int m178() { return new Random(178).nextInt(); }
    public int m179() { return new Random(179).nextInt(); }
    public int m180() { return new Random(180).nextInt(); }
    public int m181() { return new Random(181).nextInt(); }
    public int m182() { return new Random(182).nextInt(); }
    public int m183() { return new Random(183).nextInt(); }
    public int m184() { return new Random(184).nextInt(); }
    public int m185() { return new Random(185).nextInt(); }
    public int m186() { return new Random(186).nextInt(); }
    public int m187() { return new Random(187).nextInt(); }
    public int m188() { return new Random(188).nextInt(); }
    public int m189() { return new Random(189).nextInt(); }
    public int m190() { return new Random(190).nextInt(); }
    public int m191() { return new Random(191).nextInt(); }
    public int m192() { return new Random(192).nextInt(); }
    public int m193() { return new Random(193).nextInt(); }
    public int m194() { return new Random(194).nextInt(); }
    public int m195() { return new Random(195).nextInt(); }
    public int m196() { return new Random(196).nextInt(); }
    public int m197() { return new Random(197).nextInt(); }
    public int m198() { return new Random(198).nextInt(); }
    public int m199() { return new Random(199).nextInt(); }
}
