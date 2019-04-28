package mediatek.text.util;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * To meet China operator requirements, add customization on patterns to better support Chinese
 * charactors combined with links
 * @hide
 */
public class LinkifyExt {
    private static final String TAG = "LinkifyExt";
    private static final boolean DEBUG = "eng".equals(Build.TYPE);

    /// M: for CHINA OP plug-in @{
    private static Class sMtkPatterns;
    private static Method sGetWebUrlNames;
    private static Method sGetWebUrl;
    private static Method sGetMtkWebUrlPattern;

    static {
        try {
            sMtkPatterns = Class.forName("com.mediatek.util.MtkPatterns");
            sGetWebUrlNames = sMtkPatterns.getDeclaredMethod("getWebProtocolNames", String[].class);
            sGetWebUrlNames.setAccessible(true);
            sGetWebUrl = sMtkPatterns.getDeclaredMethod("getWebUrl", String.class, int.class, int
                    .class);
            sGetWebUrl.setAccessible(true);
            sGetMtkWebUrlPattern = sMtkPatterns.getDeclaredMethod("getMtkWebUrlPattern", Pattern
                    .class);
            sGetMtkWebUrlPattern.setAccessible(true);
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "no extended class found!");
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "no extended method found!");
            e.printStackTrace();
        }
    }
    /// @}

    public static String[] getExtWebProtocolNames(String[] webProtocolNames) {
        if (sGetWebUrlNames != null) {
            try {
                webProtocolNames = (String[]) sGetWebUrlNames.invoke(null, new
                        Object[]{webProtocolNames});
                if (DEBUG) {
                    Log.d(TAG, "getExtWebProtocolNames(), webProtocolNames = " + webProtocolNames);
                }
            } catch (IllegalAccessException e) {
                Log.e(TAG, "sGetWebUrlNames access failed");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "sGetWebUrlNames invoke failed");
            }

        }
        return webProtocolNames;
    }

    public static Pattern getExtWebUrlPattern(Pattern pattern) {
        if (sGetMtkWebUrlPattern != null) {
            try {
                pattern = (Pattern) sGetMtkWebUrlPattern.invoke(null, pattern);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "sGetWebUrlNames access failed");
            } catch (InvocationTargetException e) {
                Log.e(TAG, "sGetWebUrlNames invoke failed");
            }
        }
        return pattern;
    }

    public static Bundle getExtWebUrl(String match, int start, int end, Pattern pattern) {
        if ((pattern != Patterns.AUTOLINK_EMAIL_ADDRESS) && sGetWebUrl != null) {
            try {
                Bundle urlData = (Bundle) sGetWebUrl.invoke(null, match, start, end);
                if (DEBUG) {
                    Log.e(TAG, "getExtWebUrl, urlData = " + urlData);
                }
                return urlData;
            } catch (InvocationTargetException e) {
                Log.e(TAG, "can't invoke sGetWebUrl");
            } catch (IllegalAccessException e) {
                Log.e(TAG, "can't access sGetWebUrl");
            }
        }
        Bundle defaultValue = new Bundle();
        defaultValue.putString("value", match);
        defaultValue.putInt("start", start);
        defaultValue.putInt("end", end);
        return defaultValue;
    }

}
