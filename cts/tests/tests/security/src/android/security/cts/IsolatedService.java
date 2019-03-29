/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.cts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.platform.test.annotations.SecurityTest;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SecurityTest
public class IsolatedService extends Service {

    private static final String TAG = IsolatedService.class.getSimpleName();
    private static final String SERVICE_MANAGER_CLASS_NAME = "android.os.ServiceManager";
    private static final String SERVICE_MANAGER_INTERNAL_CACHE_NAME = "sCache";
    private static final String GET_SERVICE_METHOD_NAME = "getService";

    private String[] getServicesCachedInServiceManager() {
        ArrayList<String> cachedServices = new ArrayList<String>();
        try {
            Class<?> serviceManager = Class.forName(SERVICE_MANAGER_CLASS_NAME);
            Field cacheField = serviceManager.getDeclaredField(SERVICE_MANAGER_INTERNAL_CACHE_NAME);
            cacheField.setAccessible(true);
            HashMap<String, IBinder> sCache = (HashMap<String, IBinder>) cacheField.get(null);
            for (Map.Entry<String, IBinder> serviceEntry : sCache.entrySet()) {
                if (serviceEntry.getValue() != null) {
                    cachedServices.add(serviceEntry.getKey());
                }
            }
        } catch (ClassCastException | ReflectiveOperationException exc) {
            Log.w(TAG, "Unable to retrieve service manager cache via reflection ", exc);
        }
        return cachedServices.toArray(new String[cachedServices.size()]);
    }

    private IBinder getServiceFromServiceManager(String serviceName) {
        try {
            Class<?> serviceManager = Class.forName(SERVICE_MANAGER_CLASS_NAME);
            Method getServiceMethod =
                    serviceManager.getDeclaredMethod(GET_SERVICE_METHOD_NAME, String.class);
            IBinder service = (IBinder) getServiceMethod.invoke(null, serviceName);
            return service;
        } catch (ClassCastException | ReflectiveOperationException exc) {
            Log.w(TAG, "Unable to call ServiceManager.getService() ", exc);
        }
        return null;
    }

    private final IIsolatedService.Stub mBinder = new IIsolatedService.Stub() {

        public String[] getCachedSystemServices() {
            return getServicesCachedInServiceManager();
        }

        public IBinder getSystemService(String serviceName) {
            return getServiceFromServiceManager(serviceName);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
