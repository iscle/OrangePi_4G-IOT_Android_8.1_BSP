/*
 * Copyright 2015 The Android Open Source Project
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

package android.keystore.cts;

import android.keystore.cts.R;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import android.content.Context;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

/**
 * Tests for algorithm-agnostic functionality of {@code Signature} implementations backed by Android
 * Keystore.
 */
public class SignatureTest extends AndroidTestCase {

    static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME;

    static final String[] EXPECTED_SIGNATURE_ALGORITHMS = {
        "NONEwithRSA",
        "MD5withRSA",
        "SHA1withRSA",
        "SHA224withRSA",
        "SHA256withRSA",
        "SHA384withRSA",
        "SHA512withRSA",
        "SHA1withRSA/PSS",
        "SHA224withRSA/PSS",
        "SHA256withRSA/PSS",
        "SHA384withRSA/PSS",
        "SHA512withRSA/PSS",
        "NONEwithECDSA",
        "SHA1withECDSA",
        "SHA224withECDSA",
        "SHA256withECDSA",
        "SHA384withECDSA",
        "SHA512withECDSA"
    };

    private static final Map<String, String> SIG_ALG_TO_CANONICAL_NAME_CASE_INSENSITIVE =
            new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
    static {
        // For an unknown legacy reason, libcore's ProviderTest#test_Provider_getServices insists
        // that a Service with algorithm "ECDSA" be exposed, despite the RI not exposing any such
        // services. Thus, our provider has to expose the "ECDSA" service whose actual proper
        // name is SHA1withECDSA.
        SIG_ALG_TO_CANONICAL_NAME_CASE_INSENSITIVE.put("ECDSA", "SHA1withECDSA");
    }

    private static final byte[] SHORT_MSG_KAT_MESSAGE =
            HexEncoding.decode("ec174729c4f5c570ba0de4c424cdcbf0362a7718039464");
    private static final byte[] LONG_MSG_KAT_SEED = SHORT_MSG_KAT_MESSAGE;
    private static final int LONG_MSG_KAT_SIZE_BYTES = 3 * 1024 * 1024 + 123;

    private static final Map<String, byte[]> SHORT_MSG_KAT_SIGNATURES =
            new TreeMap<String, byte[]>(String.CASE_INSENSITIVE_ORDER);
    static {
        // From RI
        SHORT_MSG_KAT_SIGNATURES.put("NONEwithECDSA", HexEncoding.decode(
                "304402201ea57c2fb571991639d103bfec658ee7f359b60664e400a5834cfc20d28b588902202433f5"
                + "eb07d2b03bf8d238ea256ea399d0913a6cfcae2c3b00e2efd50aebc967"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA1withECDSA", HexEncoding.decode(
                "30440220742d71a013564ab196789322b9231ac5ff26460c2d6b1ab8ccb45eec254cc8ba0220780a86"
                + "5ddc2334fae23d563e3142b04660c2ab1b875c4ff8c557a1d1accc43e1"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA224withECDSA", HexEncoding.decode(
                "304502200f74966078b34317daa69e487c3163dbb4e0391cd74191cc3e95b33fc60966e3022100ebdc"
                + "be19c516d550609f73fb37557a406e397bc1725a1baba50cdfc3537bd377"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA256withECDSA", HexEncoding.decode(
                "304402204443b560d888beeae729155b0d9410fef2ec78607d9166af6144346fba8ce45d02205b0727"
                + "bfa630050f1395c8bcf46c614c14eb15f2a6abbd3bc7c0e83b41819281"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA384withECDSA", HexEncoding.decode(
                "3045022025ade03446ce95aa525a51aedd16baf12a2b8b9c1f4c87224c38e48c84cbbbf8022100ad21"
                + "8424c3671bc1513e1da7e7186dbc6bf67bec434c95863a48e79f53684971"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA512withECDSA", HexEncoding.decode(
                "3045022100969e8fed2dc4ddcdf341368e057efe4e3a00eda66bbb127dec31bb0144c5334602201087"
                + "2b7f9ab9c06a07053e0641e6adc18a87a1d7807550a19e872e78e5c7f0dd"));

        // From RI
        SHORT_MSG_KAT_SIGNATURES.put("NONEwithRSA", HexEncoding.decode(
                "257d0704e514ead29a5c45576adb2d5a7d7738e6a83b5d6463a5306788015d14580fee340e78e00d39"
                + "f56ae616083ac929e5daf9eeab40b908eb05d0cd1036d9e92799587df0d4c5304c9b27f913e1c891"
                + "919eff0df3b5d9c0d8cc4cd843795840799cc0353192c3868b3f8cad96d04bb566ca53e1146aa2a3"
                + "4b890ce917680bbdea1dee417a89630224d2ee79d66d38c7c77e50b45e1dd1b8b63eb98ecd60426b"
                + "c9fb30917e78ae4dd7cbfa9475f9be53bf45e7032add52681553679252f4f74de77831c95ea69f30"
                + "2f0fd28867de058728455e3537680c86a001236e70c7680c78b4dc98942d233b968635a0debccb41"
                + "fbc17ece7172631f5ab6d578d70eb0"));
        SHORT_MSG_KAT_SIGNATURES.put("MD5withRSA", HexEncoding.decode(
                "1e1edefc9a6a4e61fcef0d4b202cc2b53ab9043b1a0b21117d122d4c5399182998ec66608e1ab13513"
                + "08fbb23f92d5d970f7fb1a0691f8d1e682ff4f5e394ef2dfcbdc2de5c2c33372aec9a0c7fba982c5"
                + "c0f1a5f65677d9294d54a2e613cc0e5feb919135e883827da0e1c222bf31336afa63a837c57c0b70"
                + "70ceb8a24492a42afa6750cc9fe3a9586aa15507a65410db973a4b26734624d323dc700928717789"
                + "fb1f970d57326eef49e012fcebcfbbfd18fb4d6feff03587d0f2b0a556fe467437a44a2283ea5027"
                + "6efda4fd427365687960e0c289c5853a7b300ff081511a2f52899e6f6a569b30e07adfd52e9d9d7e"
                + "ab33999da0da1dd16d4e88bd980fcd"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA1withRSA", HexEncoding.decode(
                "280977b4ee18cbe27d3e452c9b90174f5d81dd518018ce52ff1cd1e8d4d0626afca85be14a43fa3b76"
                + "a80e818b4bc10abc62180fa15619d78be98ccd8fa642ea05355aa84f2924e041c2b594b1cf31d42b"
                + "f11c78dd3cbb6cc2cbfe151792985e6e5cf73c2e600a38f31e26e84c3e4a434f67a625fefe712d17"
                + "b34125ea91d333cfe1c4ac914b6c411b08e64700885325e07510c4f49ef3648252736f17d3ae7705"
                + "0054ceb07ab04b5ecaa5fc4556328bad4f97eba37f9bf079506e0eb136c9eadf9e466ccb18d65b4d"
                + "ef2ba3ca5c2f33354a2040dfb646423f96ba43d1d8f3dcf91c0c2c14e7958159d2ac3ebc0b6b87e6"
                + "6efbdda046ce8a766fecc3f905d6ff"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA224withRSA", HexEncoding.decode(
                "490af9e685ef44da9528d9271d00e09a3e688012bf3f63fd924a06cb4db747a28cdf924c2d51620165"
                + "33985abf4b91d64c17ff7e2b4f0de5a28375dddf556cd9e5dcebd112f766f07cb867e8d5710ce79a"
                + "1c3d5244cbd16618b0fedc2b9015d51a98d453747fb320b97995ea9579adbc8bf6042b2f4252cef1"
                + "787207fefaf4b9c7212fe0ff8b22ae12ffc888f0a1e6923455577e82b58608dabc2acba05be693ff"
                + "ae7da263d6c83cb13d59a083f177578d11030f8974bdb301f6135ecd5ec18dd68dc453c5963e94b6"
                + "2d89bcda0ff63ac7394030f79b59139e1d51573f0d4a1e85d22502c9b0d29412f7eb277fb42fa4db"
                + "18875baffa7719b700e4830edbcd6f"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA256withRSA", HexEncoding.decode(
                "1f1adcd6edbf4c50777c932db6e99a577853fbc9c71c692e921291c5aa73eb0155e30c8d4f3aff828f"
                + "2040c84e10b1ba729ccc23899650451022fcd3574df5454b01112adec5f01565b578bbc7c32810c9"
                + "407106054ad8f4f640b589ddef264d028ad906536d61c8053ef0dba8e10ca2e30a9dd6ccc9a9ec3e"
                + "76c10d36029820865b2d01095987af4a29369ffc6f70fa7e1de2b8e28f41894df4225cf966454096"
                + "7fb7ecff443948c8a0ee6a1be51e0f8e8887ff512dbdc4fc81636e69ae698000ce3899c2ec999b68"
                + "691adfb53092380264b27d91bd64561fee9d2e622919cf6b472fd764dc2065ae6a67df2c7b5ec855"
                + "099bdb6bb104ee41fa129c9da99745"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA384withRSA", HexEncoding.decode(
                "3b5e8baa62803569642fa8c3255249709c9f1d69bd31f7b531d5071c07cd9bac29273097666d96b2e3"
                + "2db13529b6414be5aee0c8a90c3f3b2a5c815f37fac16a3527fa45903f847416ed218eee2fef5b87"
                + "5f0c97576f58b3467e83497c1cdeea44d0ea151e9c4d27b85eef75d612b1fc16731859738e95bdf1"
                + "2f2098ebd501d8493c66585e8545fb13d736f7dbbb530fb06f6f157cd10c332ca498b379336efdaf"
                + "a8f940552da2dbb047c33e87f699068eaadd6d47c92a299f35483ba3ae09f7e52a205f202c1af997"
                + "c9bdc40f423b3767292c7fcea3eaf338a76f9db07a53d7f8d084bf1ceac38ec0509e442b1283cd8f"
                + "013c4c7a9189fe4ef9ab00c80cb470"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA512withRSA", HexEncoding.decode(
                "23eb7577d2ffefed10780c2a26f79f64abe08203e900db2333413f30bbc81f800857857c8b0e02c3e8"
                + "8fe3cf5514130d6216ef7c4a86b1012594c7cb07a293159b92bf40291224386a84e607e0a8389c8a"
                + "a0c45cc553037517c52f61fe0ea51dba184e890db7d9517760724c038018330c0a9450c280430e6f"
                + "9e4cdd4545c3f6684485cd6e27203735ff4be76420071920b18c54d98c0e3eb7ae7d1f01f5171ace"
                + "87885c6185f66d947d51a441a756bc953458f7d3a1714226899562478ebf91ab18d8e7556a966661"
                + "31de37bc2e399b366877f53c1d88f93c989aeb64a43f0f6cbc2a29587230f7e3e90ea18868d79584"
                + "3e62b49f5df78e355b437ec2f882f9"));

        // From Bouncy Castle
        SHORT_MSG_KAT_SIGNATURES.put("SHA1withRSA/PSS", HexEncoding.decode(
                "17e483781695067a25bc7cb204429a8754af36032038460e1938c28cd058025b14d2cffe5d3da39e76"
                + "6542014e5419f1d4c4d7d8e3ebcd2221dde04d24bbbad657f6782b7a0fada3c3ea595bc21054b0ab"
                + "d1eb1ada86276ed31dbcce58be7407cbbb924d595fbf44f2bb6e3eab92296076e291439107e67912"
                + "b4fac3a27ff84af7cd2db1385a8340b2e49c7c2ec96a6b657a1641da80799cb88734cca35a2b3a2c"
                + "4af832a34ac8d3134ccc8b61150dc1b64391888a3a84bdb5184b48e8509e8ba726ba8847e4ca0640"
                + "ce615e3adf5248ce08adb6484f6f29caf6c65308ec6351d97369ae005a7c762f76f0ddc0becc3e45"
                + "529aa9c8391473e392c9a60c2d0834"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA224withRSA/PSS", HexEncoding.decode(
                "3b7641a49e7766ed879f2b0e33ceb3d935678a7deffbd855a97abf00a65c981814ac54a71150b2ffea"
                + "d5db83aa96d0939267b3c5e2fcf958e9c6fdf7d90908e6139f7f330a16dc625d8268ffd324659f6c"
                + "e36798ef3b71a92f1d2237e3ce1e281aacc1d5370ae63c9b75e7134ad15cca1410746bf259ac4519"
                + "c407877503900ec8f3b71edce727e9d0275c9cd48385f89ce76ed17a2bf246578f183bb6577d6942"
                + "2056c7d9145528fc8ca036926a9fafe819f37c1a4a0a69b17f3d4b0a116106f94a5d2a5f8ce6981c"
                + "d6e5c2f858dcb0823e725fffe6be14ca882c81fa993bebda549fcf983eb7f8a87eccd545951dcdc9"
                + "d8055ae4f4067de997cfd89952c905"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA256withRSA/PSS", HexEncoding.decode(
                "6f0f744fa8e813b4c7caa0c395c1aa8aee0d61e621b4daae305c759b5b5972311ad691f8867821efba"
                + "d57995cc8ff38f33393293e94e1c484e94de4816b0fd986f5710a02d80e62461cc6f87f1f3742268"
                + "c28a54870f290d136aa629cbe00a1bf243fab1674c04cd5910a786b2ac5e71d9c6f4c41daa4c584d"
                + "46ba7ee768d2d2559be587a7b2009f3b7497d556a0da8a8ae80ce91152c81ffba62720d36b699d1f"
                + "157137ff7ee7239fc4baf611d01582346e201900f7a4f2617cdf574653e124fb895c6cb76d4ed5a8"
                + "aca97d1e408e8011eba649d5617bae8b27c1b946dcff7b29151d8632ad128f22907e8b83b9149e16"
                + "fbb9e9b87600a2f90c1fd6dc164c52"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA384withRSA/PSS", HexEncoding.decode(
                "8e57992362ad4b0487a707b2f8811d953f5aaf800978859981e7dcddad6f9f411fb162859115577c53"
                + "7a3524e26bf069508185848d6e29e7da1f9660a49771533e43853e02232314afd2928a1ff1824345"
                + "a5a90309a59d213ff6a4d04520f95a976342e6ac529ec6a6821157f4fee3bdae30d836d3ab44386d"
                + "3914e6aacd6a6a63e1d63b4d9bfb93b343b6c1f28d60042ffbe1e46fb692a381456e84b3328dbcae"
                + "ed6fc577cb1c5f86a38c5c34d439eeee7e798edc9f2bcd4fc217b1630e45b8df67def2c2cdb9fea0"
                + "5d67aa6cce6e9a72e9a114e2e620a54c05755e32685ffc7e50487c3cd00888c09492fad8c461c338"
                + "e7d099b275deaf184b7d6689385f7c"));
        SHORT_MSG_KAT_SIGNATURES.put("SHA512withRSA/PSS", HexEncoding.decode(
                "7a40f9f2797beda0702df0520c7138269295a0f0328aab4eba123ebf178ea4abc745ed42d3b175dc70"
                + "c8dcc98f46f2234b392dbb3e939f30888715c4fbb47fbb5bb7c0557c140c579f48226710e5b3da0d"
                + "9511337cde5626df586b4004100dd45490e5f8ae23307b5d1054c97e9ef58f9c385ca55b6db4f58d"
                + "2e19bc8ca9d8c2b4922fb3325b6fb61fc40a359e9196aa9388845b136d2790d71410e20371dcf0a7"
                + "0425ee1854c5c3d7de976b28de0ee9b1048ed99b2a957edc97466cc4c87e36224fd323605228f61a"
                + "1aad30253b0698f9a358491138027d325d46bdfdf72171c57a2dab0a9cddaad8e170b8275c172e42"
                + "33b29ed81c0f4de9fe9f0670106aad"));
    }

    private static final Map<String, byte[]> LONG_MSG_KAT_SIGNATURES =
            new TreeMap<String, byte[]>(String.CASE_INSENSITIVE_ORDER);
    static {
        // From RI
        LONG_MSG_KAT_SIGNATURES.put("NONEwithECDSA", HexEncoding.decode(
                "304502206e4039608a66ce118821eeca3e2af7f530f51d1ce8089685a13f49010e3cd58b02210083a5"
                + "fe62a171f1b1d775fad712128a223d6b63336e0248783652474221cb3193"));
        LONG_MSG_KAT_SIGNATURES.put("SHA1withECDSA", HexEncoding.decode(
                "3044022075f09bb5c87d883c088ca2ad263bbe1754ab614f727465bc43695d3521eaccf80220460e4e"
                + "32421e6f4398cd9b7fbb31a1d1f2961f26b9783620f6413f0e6f7efb84"));
        LONG_MSG_KAT_SIGNATURES.put("SHA224withECDSA", HexEncoding.decode(
                "3045022100d6b24250b7d3cbd329913705f4990cfd1000f338f7332a44f07d7731bd8e1ff602200565"
                + "0951e14d0d21c4344a449843ef65ac3a3f831dc7f304c0fa068c996f7d34"));
        LONG_MSG_KAT_SIGNATURES.put("SHA256withECDSA", HexEncoding.decode(
                "30440220501946a2c373e8da19b36e3c7718e3f2f2f16395d5026ac4fbbc7b2d53f9f21a0220347d7a"
                + "46685282f308bacd5fb25ae92b351228ea39082784789696580f27eed1"));
        LONG_MSG_KAT_SIGNATURES.put("SHA384withECDSA", HexEncoding.decode(
                "30450220576836de4ab94a869e867b2360a71dc5a0b3351ea1c896b163206db7c3507dc2022100c1a6"
                + "719052a175e023bca7f3b9bb7a379fc6b51864cb28a195076d2f3c79ed2e"));
        LONG_MSG_KAT_SIGNATURES.put("SHA512withECDSA", HexEncoding.decode(
                "304402204ca46bac4e43e8694d1af38854c96024a4e9bcc55c6904c1f8fea0d1927f69f7022054662e"
                + "84b4d16b9f7e8164f4896212dec3c7c1e7fd108f69b0dff5bc15399eeb"));

        // From RI
        LONG_MSG_KAT_SIGNATURES.put("MD5withRSA", HexEncoding.decode(
                "7040f3e0d95f4d22719d26e5e684dbcd5ed52ab4a7c5aa51b938b2c060c79eb600f9c9771c2fcda7e3"
                + "55e7c7b5e2ba9fe9a2a3621881c0fe51702781ffcde6ce7013218c04bb05988346c2bed99afb97a8"
                + "113fb50697adf93791c9129e938040f91178e35d6f323cfa515ea6d2112e8cce1302201b51333794"
                + "4a5c425cecc8181842ace89163d84784599ea688060ad0d61ac92b673feabe01ae5e6b85d8b5e8f0"
                + "519aea3c29781e82df9153404d027d75df8370658898ed348acf4e13fd8f79c8a545881fbbf585e1"
                + "c666be3805e808819e2cc730379f35a207f9e0e646c7ab6d598c75b1901f0c5ca7099e34f7f01579"
                + "3b57dfb5c2a32e8423bfed6215f9d0"));
        LONG_MSG_KAT_SIGNATURES.put("SHA1withRSA", HexEncoding.decode(
                "187d7689206c9dd03861009c6cb62c7752fd2bbc354f0bea4e76059fe582744c80027175112a3df4b6"
                + "3b4a5626ed3051192e3c9b6d906497472f6df81171064b59114ff5d7c60f66943549634461cfadd8"
                + "a033cba2b8781fb7936ea1ca0043da119856a21e533afa999f095cf87604bb33a14e8f82fab01998"
                + "9ef3133e8069708670645ddd5cdc86bbe19fbf672b409fb6d7cae2f913814cd3dc8d5ae8e4037ccf"
                + "4a3ef97db8c8a08516716258c4b767607c51dfb289d90af014d3cfc64dbadb2135ed59728b78fda0"
                + "823fe7e68e84280c283d21ab660364a9bf035afa9a7262bade87057a63aa1d7e2c09bb9dd037bcbd"
                + "7b98356793bc32be81623833c6ab62"));
        LONG_MSG_KAT_SIGNATURES.put("SHA224withRSA", HexEncoding.decode(
                "31ff68ddfafcf3ff6e651c93649bf9cc06f4138493317606d8676a8676f9d9f3a1d5e418358f79d143"
                + "a922a3cfc5e1ad6765dc829b556c9019a6d9389144cc6a7571011c024c0514891970508dac5f0d26"
                + "f26b536cf3e4511b5e72cd9f60590b387d8a351a9f28839a1c5be5272cb75a9062aa313f3d095074"
                + "6d0a21d4f8c9a94d3bb4715c3ef0207cf1335653161a8f78972329f3ec2fa5cfe05318221cb1f535"
                + "8151dde5410f6c36f32287a2d5e76bf36134d7103fc6810a1bb8627de37d6b9efde347242d08b0b6"
                + "2b1d73bacd243ccc8546536080b42a82b7162afeb4151315746a14b64e45226c9f5b35cf1577fc6b"
                + "f5c882b71deb7f0e375db5c0196446"));
        LONG_MSG_KAT_SIGNATURES.put("SHA256withRSA", HexEncoding.decode(
                "529c70877dedf3eb1abda98a2f2b0fc899e1edece70da79f8f8bbceb98de5c85263bef2ef8a7322624"
                + "5ed2767045ea6965f35cb53e6ac1d6c62e8007a79962507d3e01c77d4e96674344438519adae67d9"
                + "6357da5c4527969c939fd86f3b8685338c2be4bf6f1f85527b11fcaa4708f925e8bb9b877bda179b"
                + "d1b45153ef22834cf593ecc5b6eca3deddbe5d05894e4e5707d71bc35ea879ccb6e8ffc32e0cdc5e"
                + "88a30eef7a608d9ea80b5cefec2aa493a3b1354ad20e88ab1f8bfda3bd9961e10f0736d1bc090d57"
                + "b93fbce3e6e2fc99e67c7b466188d1615b4150c206472e48a9253b7549cebf6c7cbb558b54e10b73"
                + "c8b1747c18d1890a24d0a835ee710a"));
        LONG_MSG_KAT_SIGNATURES.put("SHA384withRSA", HexEncoding.decode(
                "5dd3553bc594c541937dac9a8ac119407712da7564816bcdc0ca4e14bc6059b9f9bd72e99be8a3df3e"
                + "0a3c4e8ed643db9ed528b43a396dba470ad3307815bd7c75fa5b08775a378cc4203341379087dcb3"
                + "62a5e9f5c979744e4498a6aafd1b1a8069caf4ef437f2743754861fcc96d67a0f1dd3397bb65ede3"
                + "18d2b3628eb2c3ec5db8a3e21fbbe2629f1030641e420963abc4da99e24dd497337c8149a52d97da"
                + "7176c0767d72e18f8c9a49e6808509837f719fd16ba27b19a2b32bd19b9b14818e0b9be81062be77"
                + "4fb1b3105a1528170822391915a5cd12b8e79aaab7943c34094da4c4f8d56f52177db953d3bf7846"
                + "f0e5f22f2311054a1daba4fec6b589"));
        LONG_MSG_KAT_SIGNATURES.put("SHA512withRSA", HexEncoding.decode(
                "971d6350337866fbcb48c49446c50cac1995b822cfec8f2a3e2c8206158a2ddfc8fc7b38f5174b3288"
                + "91489e7b379829bac5e48cd41e9713ea7e2dc9c61cf90d255387d31818d2f161ec5c3a977b4ce121"
                + "62fb11ede30d1e63c0fbba8a4094e6ad39e64176a033e7130bbed71a67ff1713b45f0bedeb1ee532"
                + "15690f169452c061cd7d15e71cc754a2f233f5647af8373d2b583e98e4242c0a0581e0ce2b22e15a"
                + "443b0ff23d516ed39664f8b8ab5ca98a44af500407941fae97f37cb1becbcbff453608cb94a176d9"
                + "e702947fff80bc8d1e9bcdef2b7bbe681e15327cee50a72649aed0d730df7b3c9c31b165416d9c9f"
                + "1fcb04edbf96514f5758b9e90ebc0e"));

        // From Bouncy Castle
        LONG_MSG_KAT_SIGNATURES.put("SHA1withRSA/PSS", HexEncoding.decode(
                "54a2050b22f6182b65d790da80ea16bfbc34b0c7e564d1a3ce4450e9b7785d9eaa14814dee8699977a"
                + "e8da5cfb3c55c9a623ca55abcc0ef4b3b515ce31d49a78db442f9db270d35a179baf71057fe8d6d2"
                + "d3f7e4fd5f5c80e11dc059c72a1a0373f527d88089c230525f895ee19e45f5547572083418c9e542"
                + "5ff44e407500d1c49159484f38e4b00523c2fa45b7b9b38a2c1ad676b36f02a06db6fca52bd79ba1"
                + "94d5062f5035a12a1f026ac216789844a5da0caa4d481386a12ca635c06b877515ce3782d9189d87"
                + "d1ff5ec6ec7c39437071c8db7d1c2702205da4cfb01805ca7fec5595dba2234602ca5347d30538cb"
                + "4b5286c151609afcca890a6276d5e8"));
        LONG_MSG_KAT_SIGNATURES.put("SHA224withRSA/PSS", HexEncoding.decode(
                "7e95c1e4f700ceaf9ce72fd3f9f245ba80f2e1341341c49521779c8a79004f9c534297441330b9df36"
                + "bb23467eb560e5e5538612cecc27953336a0d4d8044d5a80f6bcef5299830215258c574d271ea6cd"
                + "7117c2723189385435b0f06951ff3d6a700b23bc7ed3298cfb6aa65e8f540717d57f8b55290a4862"
                + "034d9f73e8d9cb6ae7fa55a8b4c127535b5690122d6405cb0c9a313808327cfd4fb763eae875acd1"
                + "b60e1920ecf1116102cc5f7d776ed88e666962f759258d6f5454c29cb99b8f9ccad07d209671b607"
                + "014d19009e392bfb08247acf7f354458dc51196d84b492798dd829b7300c7591d42c58f21bd2c3d1"
                + "e5ce0a0d3e0aa8aa4b090b6a619fc6"));
        LONG_MSG_KAT_SIGNATURES.put("SHA256withRSA/PSS", HexEncoding.decode(
                "5a8c0ae593a6714207b3ad83398b38b93da18cfda73139ea9f02c88a989368ae6901357194a873dd2e"
                + "8cd1bb86d1f81fbc8bf725538dc2ad60759f8caab6a98a6baa6014874a92d4b92ed72e73f2721ba2"
                + "86e545924860d27210b53f9308c4fec622fdfca7dd6d51a5b092184114e9dcf57636cdabaca17b49"
                + "70cd5e93ce12c30af6d09d6964c5ad173095ea000529620d94a25b4cc977deefd25cc810a7b11cd5"
                + "e5b71c9276b0bd33c53db01304b359a4a88f3fe8bc3335669f7609b0f6da17e49ad87f38468fa2c6"
                + "8134ba6df407207559355b6e486a745009931796ab0567c9bd61788073aa00113b324fa25bd32b4d"
                + "3521e98e0b4905c6dce30d70387a2e"));
        LONG_MSG_KAT_SIGNATURES.put("SHA384withRSA/PSS", HexEncoding.decode(
                "7913f13dc399adb07cd96c1bb484f999d047efcd96501c92477d2234a1da94db9c6fd65a8031bd3040"
                + "82d90ef4a4f388e670795d144ef72a160583d4a2c805415542fa16ffd8760d2f28bdc82f63db0900"
                + "a3554bc9175dafa1899249abd49591216ba2965a4862d0f59d8b8c8d1042ed7ac43a3a15650d578a"
                + "2ea53696e462f757b326b7f0f7610fb9934aee7d954a45ca03ef66464a5611433e1224d05f783cd1"
                + "935eff90015140cb35e15f2bbf491a0d6342ccef57e453f3462412c5ff4dfdc44527ea76c6b05b1d"
                + "1330869aec1b2f41e7d975eba6b056e7c2f75dd73b1eff6d853b9507f410279b02f9244b656a1aca"
                + "befcc5e1167df3a49c4a7d8479c30f"));
        LONG_MSG_KAT_SIGNATURES.put("SHA512withRSA/PSS", HexEncoding.decode(
                "43ffefe9c96014312679a2e3803eb7c58a2a4ab8bb659c12fec7fb574c82aed673e21ed86ac309cf6c"
                + "e567e47b7c6c83dcd72e3ee946067c2004689420528174d028e3d32b2b306bcbcb6a9c8e8b83918f"
                + "7415d792f9d6417769def3316ed61898443d3ffa4dc160e5b5ecf4a11a9dfed6b4a7aa65f0f2c653"
                + "4f7e514aed73be441609ffca29207b4ced249058543fd6e13a02ef42babe2cdf4aaba66b42e9d47f"
                + "c79b4ed54fbc28d9d732f2e468d43f0ca1de6fd5312fad2c4e3eaf3e9586bca6a8bab24b4dfab8b3"
                + "9a4057c8ed27024b61b425036bea5e23689cd9db2450be47ec2c30bb6707740c70a53b3e7a1c7ecf"
                + "f04e3de1460e60e9be7a42b1ddff0c"));
    }

    private static final long DAY_IN_MILLIS = TestUtils.DAY_IN_MILLIS;

    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected signature algorithms.
        // We don't care whether the algorithms are exposed via aliases, as long as the canonical
        // names of algorithms are accepted.
        // If the Provider exposes extraneous algorithms, it'll be caught because it'll have to
        // expose at least one Service for such an algorithm, and this Service's algorithm will
        // not be in the expected set.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        Set<Service> services = provider.getServices();
        Set<String> actualSigAlgsLowerCase = new HashSet<String>();
        Set<String> expectedSigAlgsLowerCase = new HashSet<String>(
                Arrays.asList(TestUtils.toLowerCase(EXPECTED_SIGNATURE_ALGORITHMS)));
        for (Service service : services) {
            if ("Signature".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                if (!expectedSigAlgsLowerCase.contains(algLowerCase)) {
                    // Unexpected algorithm -- check whether it's an alias for an expected one
                    String canonicalAlgorithm =
                            SIG_ALG_TO_CANONICAL_NAME_CASE_INSENSITIVE.get(algLowerCase);
                    if (canonicalAlgorithm != null) {
                        // Use the canonical name instead
                        algLowerCase = canonicalAlgorithm.toLowerCase();
                    }
                }
                actualSigAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualSigAlgsLowerCase,
                expectedSigAlgsLowerCase.toArray(new String[0]));
    }

    public void testAndroidKeyStoreKeysHandledByAndroidKeyStoreProviderWhenSigning()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyPair keyPair = importDefaultKatKeyPair(sigAlgorithm).getKeystoreBackedKeyPair();
                Signature signature = Signature.getInstance(sigAlgorithm);
                signature.initSign(keyPair.getPrivate());
                assertSame(provider, signature.getProvider());
            } catch (Throwable e) {
                throw new RuntimeException(sigAlgorithm + " failed", e);
            }
        }
    }

    public void testAndroidKeyStorePublicKeysAcceptedByHighestPriorityProviderWhenVerifying()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyPair keyPair = importDefaultKatKeyPair(sigAlgorithm).getKeystoreBackedKeyPair();
                Signature signature = Signature.getInstance(sigAlgorithm);
                signature.initVerify(keyPair.getPublic());
            } catch (Throwable e) {
                throw new RuntimeException(sigAlgorithm + " failed", e);
            }
        }
    }

    public void testValidSignatureGeneratedForEmptyMessage()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                try {
                    KeyPair keyPair = key.getKeystoreBackedKeyPair();

                    // Generate a signature
                    Signature signature = Signature.getInstance(sigAlgorithm, provider);
                    signature.initSign(keyPair.getPrivate());
                    byte[] sigBytes = signature.sign();

                    // Assert that it verifies using our own Provider
                    signature.initVerify(keyPair.getPublic());
                    assertTrue(signature.verify(sigBytes));
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias(), e);
                }
            }
        }
    }

    public void testEmptySignatureDoesNotVerify()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                try {
                    KeyPair keyPair = key.getKeystoreBackedKeyPair();
                    Signature signature = Signature.getInstance(sigAlgorithm, provider);
                    signature.initVerify(keyPair.getPublic());
                    assertFalse(signature.verify(EmptyArray.BYTE));
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias(), e);
                }
            }
        }
    }

    public void testSignatureGeneratedByAndroidKeyStoreVerifiesByAndroidKeyStore()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                try {
                    KeyPair keyPair = key.getKeystoreBackedKeyPair();

                    // Generate a signature
                    Signature signature = Signature.getInstance(sigAlgorithm, provider);
                    signature.initSign(keyPair.getPrivate());
                    byte[] message = "This is a test".getBytes("UTF-8");
                    signature.update(message);
                    byte[] sigBytes = signature.sign();

                    // Assert that it verifies using our own Provider
                    assertSignatureVerifiesOneShot(
                            sigAlgorithm, provider, keyPair.getPublic(), message, sigBytes);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias(), e);
                }
            }
        }
    }

    public void testSignatureGeneratedByAndroidKeyStoreVerifiesByHighestPriorityProvider()
            throws Exception {
        Provider keystoreProvider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(keystoreProvider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                Provider verificationProvider = null;
                try {
                    PrivateKey keystorePrivateKey = key.getKeystoreBackedKeyPair().getPrivate();

                    // Generate a signature
                    Signature signature = Signature.getInstance(sigAlgorithm, keystoreProvider);
                    signature.initSign(keystorePrivateKey);
                    byte[] message = "This is a test".getBytes("UTF-8");
                    signature.update(message);
                    byte[] sigBytes = signature.sign();

                    // Assert that it verifies using whatever Provider is chosen by JCA by default
                    // for this signature algorithm and public key.
                    PublicKey publicKey = key.getOriginalKeyPair().getPublic();
                    try {
                        signature = Signature.getInstance(sigAlgorithm);
                        signature.initVerify(publicKey);
                        verificationProvider = signature.getProvider();
                    } catch (InvalidKeyException e) {
                        // No providers support verifying signatures using this algorithm and key.
                        continue;
                    }
                    assertSignatureVerifiesOneShot(
                            sigAlgorithm, verificationProvider, publicKey, message, sigBytes);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias()
                                    + ", verification provider: " + verificationProvider,
                            e);
                }
            }
        }
    }

    public void testSignatureGeneratedByHighestPriorityProviderVerifiesByAndroidKeyStore()
            throws Exception {

        Provider keystoreProvider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(keystoreProvider);
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                Provider signingProvider = null;
                try {
                    PrivateKey privateKey = key.getOriginalKeyPair().getPrivate();

                    // Generate a signature
                    Signature signature;
                    try {
                        signature = Signature.getInstance(sigAlgorithm);
                        signature.initSign(privateKey);
                        signingProvider = signature.getProvider();
                    } catch (InvalidKeyException e) {
                        // No providers support signing using this algorithm and key.
                        continue;
                    }
                    byte[] message = "This is a test".getBytes("UTF-8");
                    signature.update(message);
                    byte[] sigBytes = signature.sign();

                    // Assert that the signature verifies using the Android Keystore provider.
                    PublicKey keystorePublicKey = key.getKeystoreBackedKeyPair().getPublic();
                    assertSignatureVerifiesOneShot(
                            sigAlgorithm, keystoreProvider, keystorePublicKey, message, sigBytes);
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias()
                                    + ", signing provider: " + signingProvider,
                            e);
                }
            }
        }
    }

    public void testEntropyConsumption() throws Exception {
        // Assert that signature generation consumes the correct amount of entropy from the provided
        // SecureRandom. There is no need to check that Signature.verify does not consume entropy
        // because Signature.initVerify does not take a SecureRandom.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        CountingSecureRandom rng = new CountingSecureRandom();
        for (String sigAlgorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            for (ImportedKey key : importKatKeyPairsForSigning(getContext(), sigAlgorithm)) {
                if (!TestUtils.isKeyLongEnoughForSignatureAlgorithm(
                        sigAlgorithm, key.getOriginalSigningKey())) {
                    continue;
                }
                try {
                    KeyPair keyPair = key.getKeystoreBackedKeyPair();
                    PrivateKey privateKey = keyPair.getPrivate();
                    Signature signature = Signature.getInstance(sigAlgorithm, provider);

                    // Signature.initSign should not consume entropy.
                    rng.resetCounters();
                    signature.initSign(privateKey, rng);
                    assertEquals(0, rng.getOutputSizeBytes());

                    // Signature.update should not consume entropy.
                    byte[] message = "This is a test message".getBytes("UTF-8");
                    rng.resetCounters();
                    signature.update(message);
                    assertEquals(0, rng.getOutputSizeBytes());

                    // Signature.sign may consume entropy.
                    rng.resetCounters();
                    signature.sign();
                    int expectedEntropyBytesConsumed;
                    String algorithmUpperCase = sigAlgorithm.toUpperCase(Locale.US);
                    if (algorithmUpperCase.endsWith("WITHECDSA")) {
                        expectedEntropyBytesConsumed =
                                (TestUtils.getKeySizeBits(privateKey) + 7) / 8;
                    } else if (algorithmUpperCase.endsWith("WITHRSA")) {
                        expectedEntropyBytesConsumed = 0;
                    } else if (algorithmUpperCase.endsWith("WITHRSA/PSS")) {
                        expectedEntropyBytesConsumed = 20; // salt length
                    } else {
                        throw new RuntimeException("Unsupported algorithm: " + sigAlgorithm);
                    }
                    assertEquals(expectedEntropyBytesConsumed, rng.getOutputSizeBytes());
                } catch (Throwable e) {
                    throw new RuntimeException(
                            "Failed for " + sigAlgorithm + " with key " + key.getAlias(), e);
                }
            }
        }
    }

    public void testSmallMsgKat() throws Exception {
        byte[] message = SHORT_MSG_KAT_MESSAGE;

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                byte[] goodSigBytes = SHORT_MSG_KAT_SIGNATURES.get(algorithm);
                assertNotNull(goodSigBytes);
                KeyPair keyPair = importDefaultKatKeyPair(algorithm).getKeystoreBackedKeyPair();
                // Assert that AndroidKeyStore provider can verify the known good signature.
                assertSignatureVerifiesOneShot(
                        algorithm, provider, keyPair.getPublic(), message, goodSigBytes);
                assertSignatureVerifiesFedOneByteAtATime(
                        algorithm, provider, keyPair.getPublic(), message, goodSigBytes);
                assertSignatureVerifiesFedUsingFixedSizeChunks(
                        algorithm, provider, keyPair.getPublic(), message, goodSigBytes, 3);

                byte[] messageWithBitFlip = message.clone();
                messageWithBitFlip[messageWithBitFlip.length / 2] ^= 1;
                assertSignatureDoesNotVerifyOneShot(
                        algorithm, provider, keyPair.getPublic(), messageWithBitFlip, goodSigBytes);

                byte[] goodSigWithBitFlip = goodSigBytes.clone();
                goodSigWithBitFlip[goodSigWithBitFlip.length / 2] ^= 1;
                assertSignatureDoesNotVerifyOneShot(
                        algorithm, provider, keyPair.getPublic(), message, goodSigWithBitFlip);

                // Sign the message in one go
                Signature signature = Signature.getInstance(algorithm, provider);
                signature.initSign(keyPair.getPrivate());
                signature.update(message);
                byte[] generatedSigBytes = signature.sign();
                boolean deterministicSignatureScheme =
                        algorithm.toLowerCase().endsWith("withrsa");
                if (deterministicSignatureScheme) {
                    MoreAsserts.assertEquals(goodSigBytes, generatedSigBytes);
                } else {
                    if (Math.abs(goodSigBytes.length - generatedSigBytes.length) > 2) {
                        fail("Generated signature expected to be between "
                                + (goodSigBytes.length - 2) + " and "
                                + (goodSigBytes.length + 2) + " bytes long, but was: "
                                + generatedSigBytes.length + " bytes: "
                                + HexEncoding.encode(generatedSigBytes));
                    }

                    // Assert that the signature verifies using our own Provider
                    assertSignatureVerifiesOneShot(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedOneByteAtATime(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedUsingFixedSizeChunks(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes,
                            3);

                    // Assert that the signature verifies using whatever Provider is chosen by JCA
                    // by default for this signature algorithm and public key.
                    assertSignatureVerifiesOneShot(
                            algorithm, keyPair.getPublic(), message, generatedSigBytes);
                }

                // Sign the message by feeding it into the Signature one byte at a time
                signature = Signature.getInstance(signature.getAlgorithm(), provider);
                signature.initSign(keyPair.getPrivate());
                for (int i = 0; i < message.length; i++) {
                    signature.update(message[i]);
                }
                generatedSigBytes = signature.sign();
                if (deterministicSignatureScheme) {
                    MoreAsserts.assertEquals(goodSigBytes, generatedSigBytes);
                } else {
                    if (Math.abs(goodSigBytes.length - generatedSigBytes.length) > 2) {
                        fail("Generated signature expected to be between "
                                + (goodSigBytes.length - 2) + " and "
                                + (goodSigBytes.length + 2) + " bytes long, but was: "
                                + generatedSigBytes.length + " bytes: "
                                + HexEncoding.encode(generatedSigBytes));
                    }
                    // Assert that the signature verifies using our own Provider
                    assertSignatureVerifiesOneShot(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedOneByteAtATime(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedUsingFixedSizeChunks(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes,
                            3);

                    // Assert that the signature verifies using whatever Provider is chosen by JCA
                    // by default for this signature algorithm and public key.
                    assertSignatureVerifiesOneShot(
                            algorithm, keyPair.getPublic(), message, generatedSigBytes);
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testLongMsgKat() throws Exception {
        byte[] message = TestUtils.generateLargeKatMsg(LONG_MSG_KAT_SEED, LONG_MSG_KAT_SIZE_BYTES);

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyPair keyPair = importDefaultKatKeyPair(algorithm).getKeystoreBackedKeyPair();
                String digest = TestUtils.getSignatureAlgorithmDigest(algorithm);
                String keyAlgorithm = TestUtils.getSignatureAlgorithmKeyAlgorithm(algorithm);
                if ((KeyProperties.DIGEST_NONE.equalsIgnoreCase(digest))
                        && (!KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm))) {
                    // This algorithm does not accept large messages
                    Signature signature = Signature.getInstance(algorithm, provider);
                    signature.initSign(keyPair.getPrivate());
                    try {
                        signature.update(message);
                        byte[] sigBytes = signature.sign();
                        fail("Unexpectedly generated signature (" + sigBytes.length + "): "
                                + HexEncoding.encode(sigBytes));
                    } catch (SignatureException expected) {}

                    // Bogus signature generated using SHA-256 digest -- shouldn't because the
                    // message is too long (and should not be digested/hashed) and because the
                    // signature uses the wrong digest/hash.
                    byte[] sigBytes = SHORT_MSG_KAT_SIGNATURES.get(
                            "SHA256" + algorithm.substring("NONE".length()));
                    assertNotNull(sigBytes);
                    signature = Signature.getInstance(algorithm, provider);
                    signature.initVerify(keyPair.getPublic());
                    try {
                        signature.update(message);
                        signature.verify(sigBytes);
                        fail();
                    } catch (SignatureException expected) {}
                    continue;
                }

                byte[] goodSigBytes = LONG_MSG_KAT_SIGNATURES.get(algorithm);
                assertNotNull(goodSigBytes);

                // Assert that AndroidKeyStore provider can verify the known good signature.
                assertSignatureVerifiesOneShot(
                        algorithm, provider, keyPair.getPublic(), message, goodSigBytes);
                assertSignatureVerifiesFedUsingFixedSizeChunks(
                        algorithm, provider, keyPair.getPublic(), message, goodSigBytes, 718871);

                // Sign the message in one go
                Signature signature = Signature.getInstance(algorithm, provider);
                signature.initSign(keyPair.getPrivate());
                signature.update(message);
                byte[] generatedSigBytes = signature.sign();
                String paddingScheme = TestUtils.getSignatureAlgorithmPadding(algorithm);
                boolean deterministicSignatureScheme =
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1.equalsIgnoreCase(paddingScheme);
                if (deterministicSignatureScheme) {
                    MoreAsserts.assertEquals(goodSigBytes, generatedSigBytes);
                } else {
                    if (Math.abs(goodSigBytes.length - generatedSigBytes.length) > 2) {
                        fail("Generated signature expected to be between "
                                + (goodSigBytes.length - 2) + " and "
                                + (goodSigBytes.length + 2) + " bytes long, but was: "
                                + generatedSigBytes.length + " bytes: "
                                + HexEncoding.encode(generatedSigBytes));
                    }

                    // Assert that the signature verifies using our own Provider
                    assertSignatureVerifiesOneShot(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedUsingFixedSizeChunks(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes,
                            718871);

                    // Assert that the signature verifies using whatever Provider is chosen by JCA
                    // by default for this signature algorithm and public key.
                    assertSignatureVerifiesOneShot(
                            algorithm, keyPair.getPublic(), message, generatedSigBytes);
                }

                // Sign the message by feeding it into the Signature one byte at a time
                generatedSigBytes = generateSignatureFedUsingFixedSizeChunks(
                        algorithm, provider, keyPair.getPrivate(), message, 444307);
                if (deterministicSignatureScheme) {
                    MoreAsserts.assertEquals(goodSigBytes, generatedSigBytes);
                } else {
                    if (Math.abs(goodSigBytes.length - generatedSigBytes.length) > 2) {
                        fail("Generated signature expected to be between "
                                + (goodSigBytes.length - 2) + " and "
                                + (goodSigBytes.length + 2) + " bytes long, but was: "
                                + generatedSigBytes.length + " bytes: "
                                + HexEncoding.encode(generatedSigBytes));
                    }
                    // Assert that the signature verifies using our own Provider
                    assertSignatureVerifiesOneShot(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes);
                    assertSignatureVerifiesFedUsingFixedSizeChunks(
                            algorithm, provider, keyPair.getPublic(), message, generatedSigBytes,
                            718871);

                    // Assert that the signature verifies using whatever Provider is chosen by JCA
                    // by default for this signature algorithm and public key.
                    assertSignatureVerifiesOneShot(
                            algorithm, keyPair.getPublic(), message, generatedSigBytes);
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifySucceedsDespiteMissingAuthorizations() throws Exception {
        KeyProtection spec = new KeyProtection.Builder(0).build();

        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                assertInitVerifySucceeds(algorithm, spec);
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignFailsWhenNotAuthorizedToSign() throws Exception {
        int badPurposes = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                | KeyProperties.PURPOSE_VERIFY;

        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);
                assertInitSignThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good, badPurposes).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatNotAuthorizedToVerify() throws Exception {
        int badPurposes = KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                | KeyProperties.PURPOSE_SIGN;

        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good, badPurposes).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignFailsWhenDigestNotAuthorized() throws Exception {
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);

                String digest = TestUtils.getSignatureAlgorithmDigest(algorithm);
                String badDigest =
                        (KeyProperties.DIGEST_SHA256.equalsIgnoreCase(digest))
                        ? KeyProperties.DIGEST_SHA384 : KeyProperties.DIGEST_SHA256;
                assertInitSignThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good).setDigests(badDigest).build());

                // Check that digest NONE is not treated as ANY.
                if (!KeyProperties.DIGEST_NONE.equalsIgnoreCase(digest)) {
                    assertInitSignThrowsInvalidKeyException(algorithm,
                            TestUtils.buildUpon(good)
                                    .setDigests(KeyProperties.DIGEST_NONE)
                                    .build());
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatDigestNotAuthorized() throws Exception {
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);

                String digest = TestUtils.getSignatureAlgorithmDigest(algorithm);
                String badDigest =
                        (KeyProperties.DIGEST_SHA256.equalsIgnoreCase(digest))
                        ? KeyProperties.DIGEST_SHA384 : KeyProperties.DIGEST_SHA256;
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good).setDigests(badDigest).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignFailsWhenPaddingNotAuthorized() throws Exception {
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                String paddingScheme = TestUtils.getSignatureAlgorithmPadding(algorithm);
                String badPaddingScheme;
                if (paddingScheme == null) {
                    // No padding scheme used by this algorithm -- ignore.
                    continue;
                } else if (KeyProperties.SIGNATURE_PADDING_RSA_PKCS1.equalsIgnoreCase(
                        paddingScheme)) {
                    badPaddingScheme = KeyProperties.SIGNATURE_PADDING_RSA_PSS;
                } else if (KeyProperties.SIGNATURE_PADDING_RSA_PSS.equalsIgnoreCase(
                        paddingScheme)) {
                    badPaddingScheme = KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
                } else {
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
                }

                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);
                assertInitSignThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good).setSignaturePaddings(badPaddingScheme).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatPaddingNotAuthorized() throws Exception {
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                String paddingScheme = TestUtils.getSignatureAlgorithmPadding(algorithm);
                String badPaddingScheme;
                if (paddingScheme == null) {
                    // No padding scheme used by this algorithm -- ignore.
                    continue;
                } else if (KeyProperties.SIGNATURE_PADDING_RSA_PKCS1.equalsIgnoreCase(
                        paddingScheme)) {
                    badPaddingScheme = KeyProperties.SIGNATURE_PADDING_RSA_PSS;
                } else if (KeyProperties.SIGNATURE_PADDING_RSA_PSS.equalsIgnoreCase(
                        paddingScheme)) {
                    badPaddingScheme = KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
                } else {
                    throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
                }

                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good).setSignaturePaddings(badPaddingScheme).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignFailsWhenKeyNotYetValid() throws Exception {
        Date badStartDate = new Date(System.currentTimeMillis() + DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);
                assertInitSignThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good).setKeyValidityStart(badStartDate).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatKeyNotYetValid() throws Exception {
        Date badStartDate = new Date(System.currentTimeMillis() + DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good).setKeyValidityStart(badStartDate).build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignFailsWhenKeyNoLongerValidForOrigination() throws Exception {
        Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);
                assertInitSignThrowsInvalidKeyException(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForOriginationEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatKeyNoLongerValidForOrigination() throws Exception {
        Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForOriginationEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitSignIgnoresThatKeyNoLongerValidForConsumption() throws Exception {
        Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForSigning(algorithm);
                assertInitSignSucceeds(algorithm, good);
                assertInitSignSucceeds(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForConsumptionEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    public void testInitVerifyIgnoresThatKeyNoLongerValidForConsumption() throws Exception {
        Date badEndDate = new Date(System.currentTimeMillis() - DAY_IN_MILLIS);
        for (String algorithm : EXPECTED_SIGNATURE_ALGORITHMS) {
            try {
                KeyProtection good = getMinimalWorkingImportParamsForVerifying(algorithm);
                assertInitVerifySucceeds(algorithm, good);
                assertInitVerifySucceeds(algorithm,
                        TestUtils.buildUpon(good)
                                .setKeyValidityForConsumptionEnd(badEndDate)
                                .build());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    private void assertInitVerifySucceeds(
            String signatureAlgorithm,
            KeyProtection keyProtection) throws Exception {
        int[] resIds = getDefaultKeyAndCertResIds(signatureAlgorithm);
        assertInitVerifySucceeds(
                signatureAlgorithm,
                resIds[0],
                resIds[1],
                keyProtection);
    }

    private void assertInitVerifySucceeds(
            String signatureAlgorithm,
            int privateKeyResId,
            int certResId,
            KeyProtection keyProtection) throws Exception {
        PublicKey publicKey = TestUtils.importIntoAndroidKeyStore(
                "test1", getContext(), privateKeyResId, certResId, keyProtection)
                .getKeystoreBackedKeyPair()
                .getPublic();
        Signature signature = Signature.getInstance(signatureAlgorithm, EXPECTED_PROVIDER_NAME);
        signature.initVerify(publicKey);
    }

    private void assertInitSignSucceeds(
            String signatureAlgorithm,
            KeyProtection keyProtection) throws Exception {
        int[] resIds = getDefaultKeyAndCertResIds(signatureAlgorithm);
        assertInitSignSucceeds(
                signatureAlgorithm,
                resIds[0],
                resIds[1],
                keyProtection);
    }

    private void assertInitSignSucceeds(
            String signatureAlgorithm,
            int privateKeyResId,
            int certResId,
            KeyProtection keyProtection) throws Exception {
        PrivateKey privateKey = TestUtils.importIntoAndroidKeyStore(
                "test1", getContext(), privateKeyResId, certResId, keyProtection)
                .getKeystoreBackedKeyPair()
                .getPrivate();
        Signature signature = Signature.getInstance(signatureAlgorithm, EXPECTED_PROVIDER_NAME);
        signature.initSign(privateKey);
    }

    private void assertInitSignThrowsInvalidKeyException(
            String signatureAlgorithm,
            KeyProtection keyProtection) throws Exception {
        assertInitSignThrowsInvalidKeyException(null, signatureAlgorithm, keyProtection);
    }

    private void assertInitSignThrowsInvalidKeyException(
            String message,
            String signatureAlgorithm,
            KeyProtection keyProtection) throws Exception {
        int[] resIds = getDefaultKeyAndCertResIds(signatureAlgorithm);
        assertInitSignThrowsInvalidKeyException(
                message,
                signatureAlgorithm,
                resIds[0],
                resIds[1],
                keyProtection);
    }

    private void assertInitSignThrowsInvalidKeyException(
            String message,
            String signatureAlgorithm,
            int privateKeyResId,
            int certResId,
            KeyProtection keyProtection) throws Exception {
        PrivateKey privateKey = TestUtils.importIntoAndroidKeyStore(
                "test1", getContext(), privateKeyResId, certResId, keyProtection)
                .getKeystoreBackedKeyPair()
                .getPrivate();
        Signature signature = Signature.getInstance(signatureAlgorithm, EXPECTED_PROVIDER_NAME);
        try {
            signature.initSign(privateKey);
            fail(message);
        } catch (InvalidKeyException expected) {}
    }

    static int[] getDefaultKeyAndCertResIds(String signatureAlgorithm) {
        String keyAlgorithm = TestUtils.getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return new int[] {R.raw.ec_key1_pkcs8, R.raw.ec_key1_cert};
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            return new int[] {R.raw.rsa_key1_pkcs8, R.raw.rsa_key1_cert};
        } else {
            throw new IllegalArgumentException("Unknown key algorithm: " + keyAlgorithm);
        }
    }

    private ImportedKey importDefaultKatKeyPair(String signatureAlgorithm) throws Exception {
        String keyAlgorithm = TestUtils.getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        KeyProtection importParams =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return TestUtils.importIntoAndroidKeyStore(
                    "testEc",
                    getContext(),
                    R.raw.ec_key1_pkcs8,
                    R.raw.ec_key1_cert,
                    importParams);
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            return TestUtils.importIntoAndroidKeyStore(
                    "testRsa",
                    getContext(),
                    R.raw.rsa_key1_pkcs8,
                    R.raw.rsa_key1_cert,
                    importParams);
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }

    private void assertSignatureVerifiesOneShot(
            String algorithm,
            PublicKey publicKey,
            byte[] message,
            byte[] signature) throws Exception {
        assertSignatureVerifiesOneShot(algorithm, null, publicKey, message, signature);
    }

    private void assertSignatureVerifiesOneShot(
            String algorithm,
            Provider provider,
            PublicKey publicKey,
            byte[] message,
            byte[] signature) throws Exception {
        Signature sig = (provider != null)
                ? Signature.getInstance(algorithm, provider) : Signature.getInstance(algorithm);
        sig.initVerify(publicKey);
        sig.update(message);
        if (!sig.verify(signature)) {
            fail("Signature did not verify. algorithm: " + algorithm
                    + ", provider: " + sig.getProvider().getName()
                    + ", signature (" + signature.length + " bytes): "
                    + HexEncoding.encode(signature));
        }
    }

    private void assertSignatureDoesNotVerifyOneShot(
            String algorithm,
            Provider provider,
            PublicKey publicKey,
            byte[] message,
            byte[] signature) throws Exception {
        Signature sig = (provider != null)
                ? Signature.getInstance(algorithm, provider) : Signature.getInstance(algorithm);
        sig.initVerify(publicKey);
        sig.update(message);
        if (sig.verify(signature)) {
            fail("Signature verified unexpectedly. algorithm: " + algorithm
                    + ", provider: " + sig.getProvider().getName()
                    + ", signature (" + signature.length + " bytes): "
                    + HexEncoding.encode(signature));
        }
    }

    private void assertSignatureVerifiesFedOneByteAtATime(
            String algorithm,
            Provider provider,
            PublicKey publicKey,
            byte[] message,
            byte[] signature) throws Exception {
        Signature sig = (provider != null)
                ? Signature.getInstance(algorithm, provider) : Signature.getInstance(algorithm);
        sig.initVerify(publicKey);
        for (int i = 0; i < message.length; i++) {
            sig.update(message[i]);
        }
        if (!sig.verify(signature)) {
            fail("Signature did not verify. algorithm: " + algorithm
                    + ", provider: " + sig.getProvider().getName()
                    + ", signature (" + signature.length + " bytes): "
                    + HexEncoding.encode(signature));
        }
    }

    private byte[] generateSignatureFedUsingFixedSizeChunks(
            String algorithm,
            Provider expectedProvider,
            PrivateKey privateKey,
            byte[] message,
            int chunkSizeBytes) throws Exception {
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(privateKey);
        assertSame(expectedProvider, signature.getProvider());
        int messageRemaining = message.length;
        int messageOffset = 0;
        while (messageRemaining > 0) {
            int actualChunkSizeBytes =  Math.min(chunkSizeBytes, messageRemaining);
            signature.update(message, messageOffset, actualChunkSizeBytes);
            messageOffset += actualChunkSizeBytes;
            messageRemaining -= actualChunkSizeBytes;
        }
        return signature.sign();
    }

    private void assertSignatureVerifiesFedUsingFixedSizeChunks(
            String algorithm,
            Provider provider,
            PublicKey publicKey,
            byte[] message,
            byte[] signature,
            int chunkSizeBytes) throws Exception {
        Signature sig = (provider != null)
                ? Signature.getInstance(algorithm, provider) : Signature.getInstance(algorithm);
        sig.initVerify(publicKey);
        int messageRemaining = message.length;
        int messageOffset = 0;
        while (messageRemaining > 0) {
            int actualChunkSizeBytes =  Math.min(chunkSizeBytes, messageRemaining);
            sig.update(message, messageOffset, actualChunkSizeBytes);
            messageOffset += actualChunkSizeBytes;
            messageRemaining -= actualChunkSizeBytes;
        }
        if (!sig.verify(signature)) {
            fail("Signature did not verify. algorithm: " + algorithm
                    + ", provider: " + sig.getProvider().getName()
                    + ", signature (" + signature.length + " bytes): "
                    + HexEncoding.encode(signature));
        }
    }

    private static KeyProtection getMinimalWorkingImportParamsForSigning(String algorithm) {
        return TestUtils.getMinimalWorkingImportParametersForSigningingWith(algorithm);
    }

    private static KeyProtection getMinimalWorkingImportParamsForVerifying(
            @SuppressWarnings("unused") String algorithm) {
        // No need to authorize anything because verification does not use the private key.
        // Operations using public keys do not need authorization.
        return new KeyProtection.Builder(0).build();
    }

    static Collection<ImportedKey> importKatKeyPairsForSigning(
            Context context, String signatureAlgorithm) throws Exception {
        String keyAlgorithm = TestUtils.getSignatureAlgorithmKeyAlgorithm(signatureAlgorithm);
        KeyProtection importParams =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith(signatureAlgorithm);
        if (KeyProperties.KEY_ALGORITHM_EC.equalsIgnoreCase(keyAlgorithm)) {
            return ECDSASignatureTest.importKatKeyPairs(context, importParams);
        } else if (KeyProperties.KEY_ALGORITHM_RSA.equalsIgnoreCase(keyAlgorithm)) {
            return RSASignatureTest.importKatKeyPairs(context, importParams);
        } else {
            throw new IllegalArgumentException("Unsupported key algorithm: " + keyAlgorithm);
        }
    }
}
