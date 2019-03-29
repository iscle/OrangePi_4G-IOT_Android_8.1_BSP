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

package android.keystore.cts;

import java.math.BigInteger;
import java.security.spec.ECField;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;

abstract class ECCurves {
    private ECCurves() {}

    // NIST EC curve parameters copied from "Standards for Efficient Cryptography 2 (SEC 2)".

    static ECParameterSpec NIST_P_192_SPEC = createNistPCurveSpec(
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFF", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFF99DEF836146BC9B1B4D22831", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFC", 16),
            new BigInteger("64210519E59C80E70FA7E9AB72243049FEB8DEECC146B9B1", 16),
            new BigInteger("188DA80EB03090F67CBF20EB43A18800F4FF0AFD82FF1012", 16),
            new BigInteger("07192B95FFC8DA78631011ED6B24CDD573F977A11E794811", 16),
            1,
            HexEncoding.decode("3045AE6FC8422F64ED579528D38120EAE12196D5"));

    static ECParameterSpec NIST_P_224_SPEC = createNistPCurveSpec(
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF000000000000000000000001", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFF16A2E0B8F03E13DD29455C5C2A3D", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFE", 16),
            new BigInteger("B4050A850C04B3ABF54132565044B0B7D7BFD8BA270B39432355FFB4", 16),
            new BigInteger("B70E0CBD6BB4BF7F321390B94A03C1D356C21122343280D6115C1D21", 16),
            new BigInteger("BD376388B5F723FB4C22DFE6CD4375A05A07476444D5819985007E34", 16),
            1,
            HexEncoding.decode("BD71344799D5C7FCDC45B59FA3B9AB8F6A948BC5"));

    static ECParameterSpec NIST_P_256_SPEC = createNistPCurveSpec(
            new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFF"
                    + "FFFFFFFF", 16),
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2"
                    + "FC632551", 16),
            new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFF"
                    + "FFFFFFFC", 16),
            new BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E"
                    + "27D2604B", 16),
            new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945"
                    + "D898C296", 16),
            new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB64068"
                    + "37BF51F5", 16),
            1,
            HexEncoding.decode("C49D360886E704936A6678E1139D26B7819F7E90"));

    static ECParameterSpec NIST_P_384_SPEC = createNistPCurveSpec(
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFEFFFFFFFF0000000000000000FFFFFFFF", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81"
                    + "F4372DDF581A0DB248B0A77AECEC196ACCC52973", 16),
            new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFEFFFFFFFF0000000000000000FFFFFFFC", 16),
            new BigInteger("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F"
                    + "5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF", 16),
            new BigInteger("AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E0"
                    + "82542A385502F25DBF55296C3A545E3872760AB7", 16),
            new BigInteger("3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113"
                    + "B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F", 16),
            1,
            HexEncoding.decode("A335926AA319A27A1D00896A6773A4827ACDAC73"));

    static ECParameterSpec NIST_P_521_SPEC = createNistPCurveSpec(
            new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFF", 16),
            new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFA51868783BF2F966B7FCC0148F709A5D03BB5C9B8"
                    + "899C47AEBB6FB71E91386409", 16),
            new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFC", 16),
            new BigInteger("0051953EB9618E1C9A1F929A21A0B68540EEA2DA725B99B315F3"
                    + "B8B489918EF109E156193951EC7E937B1652C0BD3BB1BF073573DF88"
                    + "3D2C34F1EF451FD46B503F00", 16),
            new BigInteger("00C6858E06B70404E9CD9E3ECB662395B4429C648139053FB521"
                    + "F828AF606B4D3DBAA14B5E77EFE75928FE1DC127A2FFA8DE3348B3C1"
                    + "856A429BF97E7E31C2E5BD66", 16),
            new BigInteger("011839296A789A3BC0045C8A5FB42C7D1BD998F54449579B4468"
                    + "17AFBD17273E662C97EE72995EF42640C550B9013FAD0761353C7086"
                    + "A272C24088BE94769FD16650", 16),
            1,
            HexEncoding.decode("D09E8800291CB85396CC6717393284AAA0DA64BA"));

    private static ECParameterSpec createNistPCurveSpec(
            BigInteger p,
            BigInteger order,
            BigInteger a,
            BigInteger b,
            BigInteger gx,
            BigInteger gy,
            int cofactor,
            byte[] seed) {
        ECField field = new ECFieldFp(p);
        EllipticCurve curve = new EllipticCurve(field, a, b, seed);
        ECPoint generator = new ECPoint(gx, gy);
        return new ECParameterSpec(
                curve,
                generator,
                order,
                cofactor);
    }
}
