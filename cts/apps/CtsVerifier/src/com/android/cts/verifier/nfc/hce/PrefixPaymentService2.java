package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PrefixPaymentService2 extends HceService {
    static final String TAG = "PrefixPaymentService2";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PrefixPaymentService2.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.PPSE_AID, true),
        HceUtils.buildSelectApdu(HceUtils.MC_AID, true),
        HceUtils.buildCommandApdu("80CA02F000", true),
        HceUtils.buildSelectApdu("F0000000FFFFFFFFFFFFFFFFFFFFFFFF", true),
        HceUtils.buildSelectApdu("F000000000", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "FAAA9000",
        "FBBB9000",
        "F789FFCCDD9000",
        "FFBAFEBECA",
        "F0BABEFECA"
    };

    public PrefixPaymentService2() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}