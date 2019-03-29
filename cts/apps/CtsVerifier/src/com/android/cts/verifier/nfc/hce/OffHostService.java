package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class OffHostService {
    public static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
                    OffHostService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildSelectApdu("A000000151000000", true),
        HceUtils.buildCommandApdu("80CA9F7F00", true),
        HceUtils.buildSelectApdu("A000000003000000", true),
        HceUtils.buildCommandApdu("80CA9F7F00", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "*",
        "*",
        "*",
        "*"
    };
}
