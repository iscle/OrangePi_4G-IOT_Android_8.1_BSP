package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PrefixTransportService1 extends HceService {
    static final String TAG = "PrefixTransportService1";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PrefixTransportService1.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFFF", true),
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFAA", true),
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFAABBCCDDEEFF", true),
        HceUtils.buildCommandApdu("80CA01FFAA", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "25929000",
        "FFEF25929000",
        "FFDFFFAABB25929000",
        "FFDFFFAACC25929000"
    };

    public PrefixTransportService1() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}