package android.hardware.cts.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.hardware.Sensor;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import junit.framework.Assert;

public class SuspendStateMonitor extends TimerTask {
    private final double firstRealTimeMillis;
    private final double firstUpTimeMillis;
    private double lastSleepTimeSeconds = 0;
    private volatile long lastWakeUpTime = 0;
    Timer sleepMonitoringTimer = new Timer();
    private final List<CountDownLatch> mWaitForWakeUpLatch = new ArrayList<>();
    private final String TAG = "SensorCTSDeviceSuspendMonitor";

    /**
     * Returns the time the device slept since the start of the application,
     * in seconds.
     */
    public double getSleepTimeSeconds() {
        double totalSinceStart = android.os.SystemClock.elapsedRealtime() - firstRealTimeMillis;
        double upTimeSinceStart = android.os.SystemClock.uptimeMillis() - firstUpTimeMillis;
        return (totalSinceStart - upTimeSinceStart) / 1000;
    }

    public long getLastWakeUpTime() {
        return lastWakeUpTime;
    }


    public void waitForWakeUp(int numSeconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized(mWaitForWakeUpLatch) {
            mWaitForWakeUpLatch.add(latch);
        }
        if (numSeconds == -1) {
            // Wait indefinitely.
            latch.await();
        } else {
            // Wait for the specified number of seconds.
            boolean countZero = latch.await(numSeconds, TimeUnit.SECONDS);
            if (!countZero) {
               Log.e(TAG, "Device did not enter suspend state.");
            }
        }
    }

    /**
     * Run every 100ms inside the TimerTask.
     */
    @Override
    public void run() {
        if (getSleepTimeSeconds() - lastSleepTimeSeconds > 0.1) {
            lastSleepTimeSeconds = getSleepTimeSeconds();
            lastWakeUpTime = SystemClock.elapsedRealtime();
            // If any client is waiting for wake-up, call countDown to unblock it.
            synchronized(mWaitForWakeUpLatch) {
                for (CountDownLatch latch : mWaitForWakeUpLatch) {
                    latch.countDown();
                }
            }
        }
    }

    public SuspendStateMonitor() {
        firstRealTimeMillis = android.os.SystemClock.elapsedRealtime();
        firstUpTimeMillis = android.os.SystemClock.uptimeMillis();
        // Every 100 miliseconds, check whether the device has slept.
        sleepMonitoringTimer.schedule(this, 0, 100);
    }
}
