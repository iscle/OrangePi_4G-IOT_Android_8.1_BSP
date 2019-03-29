package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class InstrumentedAccessibilityService extends AccessibilityService {
    // Match com.android.server.accessibility.AccessibilityManagerService#COMPONENT_NAME_SEPARATOR
    private static final String COMPONENT_NAME_SEPARATOR = ":";

    private static final int TIMEOUT_SERVICE_ENABLE = 10000;
    private static final int TIMEOUT_SERVICE_PERFORM_SYNC = 5000;

    private static final HashMap<Class, WeakReference<InstrumentedAccessibilityService>>
            sInstances = new HashMap<>();

    private final Handler mHandler = new Handler();
    final Object mInterruptWaitObject = new Object();
    public boolean mOnInterruptCalled;


    @Override
    @CallSuper
    protected void onServiceConnected() {
        synchronized (sInstances) {
            sInstances.put(getClass(), new WeakReference<>(this));
            sInstances.notifyAll();
        }
    }

    @Override
    public void onDestroy() {
        synchronized (sInstances) {
            sInstances.remove(getClass());
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Stub method.
    }

    @Override
    public void onInterrupt() {
        synchronized (mInterruptWaitObject) {
            mOnInterruptCalled = true;
            mInterruptWaitObject.notifyAll();
        }
    }

    public void disableSelfAndRemove() {
        disableSelf();

        synchronized (sInstances) {
            sInstances.remove(getClass());
        }
    }

    public void runOnServiceSync(Runnable runner) {
        final SyncRunnable sr = new SyncRunnable(runner, TIMEOUT_SERVICE_PERFORM_SYNC);
        mHandler.post(sr);
        assertTrue("Timed out waiting for runOnServiceSync()", sr.waitForComplete());
    }

    public boolean wasOnInterruptCalled() {
        synchronized (mInterruptWaitObject) {
            return mOnInterruptCalled;
        }
    }

    public Object getInterruptWaitObject() {
        return mInterruptWaitObject;
    }

    private static final class SyncRunnable implements Runnable {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Runnable mTarget;
        private final long mTimeout;

        public SyncRunnable(Runnable target, long timeout) {
            mTarget = target;
            mTimeout = timeout;
        }

        public void run() {
            mTarget.run();
            mLatch.countDown();
        }

        public boolean waitForComplete() {
            try {
                return mLatch.await(mTimeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }

    protected static <T extends InstrumentedAccessibilityService> T enableService(
            Instrumentation instrumentation, Class<T> clazz) {
        final String serviceName = clazz.getSimpleName();
        final Context context = instrumentation.getContext();
        final String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices != null) {
            assertFalse("Service is already enabled", enabledServices.contains(serviceName));
        }

        final AccessibilityManager manager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        final List<AccessibilityServiceInfo> serviceInfos =
                manager.getInstalledAccessibilityServiceList();
        for (AccessibilityServiceInfo serviceInfo : serviceInfos) {
            final String serviceId = serviceInfo.getId();
            if (serviceId.endsWith(serviceName)) {
                ShellCommandBuilder.create(instrumentation)
                        .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                                enabledServices + COMPONENT_NAME_SEPARATOR + serviceId)
                        .putSecureSetting(Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                        .run();

                final T instance = getInstanceForClass(clazz, TIMEOUT_SERVICE_ENABLE);
                if (instance == null) {
                    ShellCommandBuilder.create(instrumentation)
                            .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                                    enabledServices)
                            .run();
                    throw new RuntimeException("Starting accessibility service " + serviceName
                            + " took longer than " + TIMEOUT_SERVICE_ENABLE + "ms");
                }
                return instance;
            }
        }
        throw new RuntimeException("Accessibility service " + serviceName + " not found");
    }

    private static <T extends InstrumentedAccessibilityService> T getInstanceForClass(Class clazz,
            long timeoutMillis) {
        final long timeoutTimeMillis = SystemClock.uptimeMillis() + timeoutMillis;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (sInstances) {
                final WeakReference<InstrumentedAccessibilityService> ref = sInstances.get(clazz);
                if (ref != null) {
                    final T instance = (T) ref.get();
                    if (instance == null) {
                        sInstances.remove(clazz);
                    } else {
                        return instance;
                    }
                }
                try {
                    sInstances.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    return null;
                }
            }
        }
        return null;
    }

    public static void disableAllServices(Instrumentation instrumentation) {
        final Object waitLockForA11yOff = new Object();
        final Context context = instrumentation.getContext();
        final AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        manager.addAccessibilityStateChangeListener(b -> {
            synchronized (waitLockForA11yOff) {
                waitLockForA11yOff.notifyAll();
            }
        });

        ShellCommandBuilder.create(instrumentation)
                .deleteSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .deleteSecureSetting(Settings.Secure.ACCESSIBILITY_ENABLED)
                .run();

        final long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_SERVICE_ENABLE;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (waitLockForA11yOff) {
                if (manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK).isEmpty()) {
                    return;
                }
                try {
                    waitLockForA11yOff.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Disabling all accessibility services took longer than "
                + TIMEOUT_SERVICE_ENABLE + "ms");
    }
}
