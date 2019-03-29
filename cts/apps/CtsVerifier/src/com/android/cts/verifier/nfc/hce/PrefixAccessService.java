package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PrefixAccessService extends HceService {
    static final String TAG = "PrefixAccessService";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PrefixAccessService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.ACCESS_PREFIX_AID + "FFFF", true),
        HceUtils.buildSelectApdu(HceUtils.ACCESS_PREFIX_AID + "FFAA", true),
        HceUtils.buildSelectApdu(HceUtils.ACCESS_PREFIX_AID + "FFAABBCCDDEEFF", true),
        HceUtils.buildCommandApdu("80CA010000010203", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "FAFE9000",
        "FAFE25929000",
        "FAFEAABB25929000",
        "FAFEFFAACC25929000"
    };

    public PrefixAccessService() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}