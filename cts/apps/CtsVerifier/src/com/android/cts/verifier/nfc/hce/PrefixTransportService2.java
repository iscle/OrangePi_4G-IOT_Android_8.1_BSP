package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PrefixTransportService2 extends HceService {
    static final String TAG = "PrefixTransportService2";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PrefixTransportService2.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFFF", true),
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFAA", true),
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_PREFIX_AID + "FFAABBCCDDEEFF", true),
        HceUtils.buildCommandApdu("80CA01FFBB", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "36039000",
        "FFBB25929000",
        "FFDFFFBBBB25929000",
        "FFDFFFBBCC25929000"
    };

    public PrefixTransportService2() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}