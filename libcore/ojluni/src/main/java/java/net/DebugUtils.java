/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

package java.net;

import dalvik.system.VMRuntime;


/**
 * This class is to switch debug log by build bype
 * @hide
 */
public
class DebugUtils {

    /**
     * Query build type for debug log judgement.
     * Only for eng, userdebug, debug log can be turned on.
     * @return  turn on/off debug log
     * @hide
     */
    public static boolean isDebugLogOn() {
        String vmProperties[] = VMRuntime.getRuntime().properties();
        for (int i = 0;i< vmProperties.length;i++) {
            int split = vmProperties[i].indexOf('=');
            String key = vmProperties[i].substring(0, split);
            String val = vmProperties[i].substring(split + 1);
            if (key.equals("build-type")
                    && ("eng".equals(val) || "userdebug".equals(val)) ) {
                return true;
            }
        }

        return false;
    }
}
