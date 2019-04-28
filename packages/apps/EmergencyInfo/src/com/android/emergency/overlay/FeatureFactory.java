/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.emergency.overlay;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.emergency.edit.EmergencyContactsFeatureProvider;
import com.android.emergency.R;

/**
 * Abstract class for creating feature controllers. Allows OEM implementations to define their own
 * factories with their own controllers containing whatever code is needed to implement the
 * features. To provide a factory implementation, implementers should override
 * {@link R.string#config_featureFactory} in their override.
 */
public abstract class FeatureFactory {
    private static final String LOG_TAG = "FeatureFactory";
    private static final boolean DEBUG = false;

    protected static FeatureFactory sFactory;

    /** @return a singleton factory instance. */
    public static FeatureFactory getFactory(Context context) {
        if (sFactory != null) {
            return sFactory;
        }

        if (DEBUG) Log.d(LOG_TAG, "getFactory");
        final String clsName = context.getString(R.string.config_featureFactory);
        if (TextUtils.isEmpty(clsName)) {
            throw new UnsupportedOperationException("No feature factory configured");
        }
        try {
            sFactory = (FeatureFactory) context.getClassLoader().loadClass(clsName).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new FactoryNotFoundException(e);
        }

        if (DEBUG) Log.d(LOG_TAG, "started " + sFactory.getClass().getSimpleName());
        return sFactory;
    }

    public abstract EmergencyContactsFeatureProvider getEmergencyContactsFeatureProvider();

    /** Exception thrown when a factory can't be created. */
    public static final class FactoryNotFoundException extends RuntimeException {
        /**
         * @param throwable The underlying cause for this exception.
         */
        public FactoryNotFoundException(Throwable throwable) {
            super("Unable to create factory. Did you misconfigure Proguard?", throwable);
        }
    }
}
