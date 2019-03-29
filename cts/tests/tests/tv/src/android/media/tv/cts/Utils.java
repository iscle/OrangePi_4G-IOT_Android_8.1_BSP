package android.media.tv.cts;

import android.content.Context;
import android.content.pm.PackageManager;

public class Utils {
    private Utils() { }

    public static boolean hasTvInputFramework(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LIVE_TV);
    }
}
