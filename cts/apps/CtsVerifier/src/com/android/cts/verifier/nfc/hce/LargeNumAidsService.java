package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;

public class LargeNumAidsService extends HceService {
    static final String TAG = "LargeNumAidsService";

    static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
            LargeNumAidsService.class.getName());

    public static final CommandApdu[] getCommandSequence() {
        CommandApdu[] commands = new CommandApdu[256];
        for (int i = 0; i < 256; i++) {
            commands[i] = HceUtils.buildSelectApdu(HceUtils.LARGE_NUM_AIDS_PREFIX + String.format("%02X", i) +
                    HceUtils.LARGE_NUM_AIDS_POSTFIX, true);
        }
        return commands;
    }

    public static final String[] getResponseSequence() {
        String[] responses = new String[256];
        for (int i = 0; i < 256; i++) {
            responses[i] = "9000" + String.format("%02X", i);
        }
        return responses;
    }

    public LargeNumAidsService() {
        initialize(getCommandSequence(), getResponseSequence());
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }
}