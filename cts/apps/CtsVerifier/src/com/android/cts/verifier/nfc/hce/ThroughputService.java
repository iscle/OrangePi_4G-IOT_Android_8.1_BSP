package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class ThroughputService extends HceService {
    static final String TAG = "PaymentService1";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            ThroughputService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu("F0010203040607FF", true),
        HceUtils.buildCommandApdu("80CA010100", true),
        HceUtils.buildCommandApdu("80CA010200", true),
        HceUtils.buildCommandApdu("80CA010300", true),
        HceUtils.buildCommandApdu("80CA010400", true),
        HceUtils.buildCommandApdu("80CA010500", true),
        HceUtils.buildCommandApdu("80CA010600", true),
        HceUtils.buildCommandApdu("80CA010700", true),
        HceUtils.buildCommandApdu("80CA010800", true),
        HceUtils.buildCommandApdu("80CA010900", true),
        HceUtils.buildCommandApdu("80CA010A00", true),
        HceUtils.buildCommandApdu("80CA010B00", true),
        HceUtils.buildCommandApdu("80CA010C00", true),
        HceUtils.buildCommandApdu("80CA010D00", true),
        HceUtils.buildCommandApdu("80CA010E00", true),
        HceUtils.buildCommandApdu("80CA010F00", true),
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "9000",
        "0000FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0001FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0002FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0003FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0004FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0005FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0006FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0007FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0008FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "0009FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "000AFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "000BFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "000CFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "000DFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
        "000EFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF9000",
    };

    public ThroughputService() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}