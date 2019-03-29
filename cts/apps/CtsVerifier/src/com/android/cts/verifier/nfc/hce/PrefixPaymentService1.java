package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PrefixPaymentService1 extends HceService {
    static final String TAG = "PrefixPaymentService1";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PrefixPaymentService1.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.PPSE_AID, true),
        HceUtils.buildSelectApdu(HceUtils.MC_AID, true),
        HceUtils.buildCommandApdu("80CA01F000", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "F1239000",
        "F4569000",
        "F789FFAABB9000"
    };

    public PrefixPaymentService1() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}