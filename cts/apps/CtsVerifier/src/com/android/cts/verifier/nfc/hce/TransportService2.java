package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class TransportService2 extends HceService {
    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            TransportService2.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu(HceUtils.TRANSPORT_AID, true),
        HceUtils.buildCommandApdu("80CA01E100", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "81CA9000",
        "7483624748FEFE9000"
    };

    public TransportService2() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}
