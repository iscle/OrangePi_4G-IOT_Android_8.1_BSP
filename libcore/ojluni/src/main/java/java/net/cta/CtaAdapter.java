package java.net.cta;

import dalvik.system.PathClassLoader;

import java.lang.ClassLoader;
import java.lang.reflect.Method;

/**
 * This class implements as a CTA adapter
 * @hide
 */
public
class CtaAdapter {
    /** Cached com.mediatek.cta.CtaHttp.isSendingPermitted method. */
    private static Method enforceCheckPermissionMethod;

    /** @hide */
    public static boolean isSendingPermitted(int port) {
        try {
            if (enforceCheckPermissionMethod == null) {
                String jarPath = "system/framework/mediatek-cta.jar";
                ClassLoader classLoader = new PathClassLoader(jarPath, ClassLoader.getSystemClassLoader());
                String className = "com.mediatek.cta.CtaHttp";

                Class<?> cls = Class.forName(className, false, classLoader);
                enforceCheckPermissionMethod =
                        cls.getMethod("isSendingPermitted", int.class);
            }
            return (Boolean) enforceCheckPermissionMethod.invoke(null, port);
        } catch (ReflectiveOperationException e) {
            System.out.println("e:" + e);
            if (e.getCause() instanceof SecurityException) {
                throw new SecurityException(e.getCause());
            }
        } catch (Exception ee) {
            System.out.println("ee:" + ee);
        }
        return true;

    }
}

