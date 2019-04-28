/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.test.launcherhelper;

import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Factory class that handles registering of {@link ILauncherStrategy} and providing a suitable
 * launcher helper based on current launcher available
 */
public class LauncherStrategyFactory {

    private static final String LOG_TAG = LauncherStrategyFactory.class.getSimpleName();
    private static LauncherStrategyFactory sInstance;
    private UiDevice mUiDevice;
    private Map<String, ILauncherStrategy> mInstanceMap;
    private Set<Class <? extends ILauncherStrategy>> mKnownLauncherStrategies;

    private LauncherStrategyFactory(UiDevice uiDevice) {
        mUiDevice = uiDevice;
        mInstanceMap = new HashMap<>();
        mKnownLauncherStrategies = new HashSet<>();
        registerLauncherStrategy(AospLauncherStrategy.class);
        registerLauncherStrategy(AutoLauncherStrategy.class);
        registerLauncherStrategy(GoogleExperienceLauncherStrategy.class);
        registerLauncherStrategy(Launcher3Strategy.class);
        registerLauncherStrategy(NexusLauncherStrategy.class);
        registerLauncherStrategy(PixelCLauncherStrategy.class);
        registerLauncherStrategy(LeanbackLauncherStrategy.class);
        registerLauncherStrategy(WearLauncherStrategy.class);
        registerLauncherStrategy(TvLauncherStrategy.class);
    }

    /**
     * Retrieves an instance of the {@link LauncherStrategyFactory}
     * @param uiDevice
     * @return
     */
    public static LauncherStrategyFactory getInstance(UiDevice uiDevice) {
        if (sInstance == null) {
            sInstance = new LauncherStrategyFactory(uiDevice);
        }
        return sInstance;
    }

    /**
     * Registers an {@link ILauncherStrategy}.
     * <p>Note that the registration is by class so that the caller does not need to instantiate
     * multiple instances of the same class.
     * @param launcherStrategy
     */
    public void registerLauncherStrategy(Class<? extends ILauncherStrategy> launcherStrategy) {
        // ignore repeated registering attempts
        if (!mKnownLauncherStrategies.contains(launcherStrategy)) {
            try {
                ILauncherStrategy strategy = launcherStrategy.newInstance();
                strategy.setUiDevice(mUiDevice);
                mInstanceMap.put(strategy.getSupportedLauncherPackage(), strategy);
            } catch (InstantiationException | IllegalAccessException e) {
                Log.e(LOG_TAG, "exception while creating instance: "
                        + launcherStrategy.getCanonicalName());
            }
        }
    }

    /**
     * Retrieves a {@link ILauncherStrategy} that supports the current default launcher
     * <p>
     * {@link ILauncherStrategy} maybe registered via
     * {@link LauncherStrategyFactory#registerLauncherStrategy(Class)} by identifying the
     * launcher package name supported
     * @throw RuntimeException if no valid launcher strategy is found
     * @return
     */
    public ILauncherStrategy getLauncherStrategy() {
        String launcherPkg = mUiDevice.getLauncherPackageName();
        if (mInstanceMap.containsKey(launcherPkg)) {
            return mInstanceMap.get(launcherPkg);
        } else {
            throw new RuntimeException(String.format(
                    "Could not find a launcher strategy for package, %s", launcherPkg));
        }
    }

    /**
     * Retrieves a {@link ILeanbackLauncherStrategy} that supports the current default launcher
     * for TV. Either Leanback Launcher or new TV Launcher
     * @return
     */
    public ILeanbackLauncherStrategy getLeanbackLauncherStrategy() {
        ILauncherStrategy launcherStrategy = getLauncherStrategy();
        if (launcherStrategy instanceof ILeanbackLauncherStrategy) {
            return (ILeanbackLauncherStrategy)launcherStrategy;
        }
        throw new RuntimeException("This LauncherStrategy is suitable for TV.");
    }

    /**
     * Retrieves a {@link IAutoLauncherStrategy} that supports the current default auto
     * launcher
     * @return
     */
    public IAutoLauncherStrategy getAutoLauncherStrategy() {
        ILauncherStrategy launcherStrategy = getLauncherStrategy();
        if (launcherStrategy instanceof IAutoLauncherStrategy) {
            return (IAutoLauncherStrategy) launcherStrategy;
        }
        throw new RuntimeException("This LauncherStrategy is not for auto launcher.");
    }
}
