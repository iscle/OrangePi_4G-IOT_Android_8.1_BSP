package com.mediatek.server;

import android.os.ServiceManager;
import android.util.Log;
import com.android.server.AlarmManagerService.Alarm;

import dalvik.system.PathClassLoader;

import java.lang.reflect.Method;
import java.util.ArrayList;

public class MtkDataShaping {
    private static final String TAG = "MtkDataShapingUtils";
    private static final String DATASHPAING_SERVICE_NAME = "data_shaping";
    private static final String DATASHPAING_SERVICE_CLASS =
            "com.mediatek.datashaping.DataShapingServiceImpl";

    /***
     * get data shaping service then call its API:openLteGateByDataShaping by
     * reflex mechanism
     * @param triggerList ArrayList<Alarm> , alarm list
     */

    public static void openLteGateByDataShaping(ArrayList<Alarm> triggerList) {
        try {
            Object instance = (Object) ServiceManager
                    .getService(DATASHPAING_SERVICE_NAME);
            if (instance == null) {
                Log.d(TAG, "openLteGateByDataShaping datashaping instance is null");
                return;
            }
            PathClassLoader classLoader = MtkSystemServer.sClassLoader;
            Class<?> dataShapingManagerClass = Class.forName(
                    DATASHPAING_SERVICE_CLASS, false,
                    classLoader);
            Class<?> paraClass[] = { ArrayList.class };
            Method setVideoScale = dataShapingManagerClass.getDeclaredMethod(
                    "openLteGateByDataShaping", paraClass);
            setVideoScale.setAccessible(true);
            setVideoScale.invoke(instance, triggerList);
        } catch (Exception e) {
            Log.e(TAG, "Exception openLteGateByDataShaping  in " + e);
            e.printStackTrace();
        }
    }

    /***
     * get data shaping service then call its API:setDeviceIdleMode by reflex
     * mechanism
     * @param enabled , boolean. Device idle mode or not
     **/
    public static void setDeviceIdleMode(boolean enabled) {
        try {
            Object instance = (Object) ServiceManager
                    .getService(DATASHPAING_SERVICE_NAME);
            if (instance == null) {
                Log.d(TAG, "setDeviceIdleMode datashaping instance is null");
                return;
            }
            PathClassLoader classLoader = MtkSystemServer.sClassLoader;
            Class<?> paraClass[] = { boolean.class };
            Class<?> dataShapingManagerClass = Class.forName(
                    DATASHPAING_SERVICE_CLASS, false, classLoader);
            Method setVideoScale = dataShapingManagerClass.getDeclaredMethod(
                    "setDeviceIdleMode", paraClass);
            setVideoScale.setAccessible(true);
            setVideoScale.invoke(instance, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Exception setDeviceIdleMode  in " + e);
            e.printStackTrace();
        }
    }
}
