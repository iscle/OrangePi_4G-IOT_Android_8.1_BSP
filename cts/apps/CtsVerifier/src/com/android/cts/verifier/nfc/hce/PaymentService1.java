package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class PaymentService1 extends HceService {
    static final String TAG = "PaymentService1";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            PaymentService1.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.PPSE_AID, true),
        HceUtils.buildSelectApdu(HceUtils.MC_AID, true),
        HceUtils.buildCommandApdu("80CA01F000", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "FFFF9000",
        "FFEF9000",
        "FFDFFFAABB9000"
    };

    public PaymentService1() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}