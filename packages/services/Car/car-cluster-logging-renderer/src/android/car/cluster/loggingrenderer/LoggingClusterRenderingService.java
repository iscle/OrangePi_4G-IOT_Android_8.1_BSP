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
package android.car.cluster.loggingrenderer;

import android.car.cluster.renderer.InstrumentClusterRenderingService;
import android.car.cluster.renderer.NavigationRenderer;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import com.google.android.collect.Lists;

/**
 * Dummy implementation of {@link LoggingClusterRenderingService} to log all interaction.
 */
public class LoggingClusterRenderingService extends InstrumentClusterRenderingService {

    private static final String TAG = LoggingClusterRenderingService.class.getSimpleName();

    @Override
    protected NavigationRenderer getNavigationRenderer() {
        NavigationRenderer navigationRenderer = new NavigationRenderer() {
            @Override
            public CarNavigationInstrumentCluster getNavigationProperties() {
                Log.i(TAG, "getNavigationProperties");
                CarNavigationInstrumentCluster config =
                        CarNavigationInstrumentCluster.createCluster(1000);
                config.getExtra().putIntegerArrayList("dummy", Lists.newArrayList(1, 2, 3, 4));
                Log.i(TAG, "getNavigationProperties, returns: " + config);
                return config;
            }


            @Override
            public void onStartNavigation() {
                Log.i(TAG, "onStartNavigation");
            }

            @Override
            public void onStopNavigation() {
                Log.i(TAG, "onStopNavigation");
            }

            @Override
            public void onNextTurnChanged(int event, CharSequence eventName, int turnAngle,
                    int turnNumber, Bitmap image, int turnSide) {
                Log.i(TAG, "event: " + event + ", eventName: " + eventName +
                        ", turnAngle: " + turnAngle + ", turnNumber: " + turnNumber +
                        ", image: " + image + ", turnSide: " + turnSide);
            }

            @Override
            public void onNextTurnDistanceChanged(int distanceMeters, int timeSeconds,
                    int displayDistanceMillis, int displayDistanceUnit) {
                Log.i(TAG, "onNextTurnDistanceChanged, distanceMeters: " + distanceMeters
                        + ", timeSeconds: " + timeSeconds
                        + ", displayDistanceMillis: " + displayDistanceMillis
                        + ", displayDistanceUnit: " + displayDistanceUnit);
            }

            @Override
            public void onEvent(int eventType, Bundle bundle) {
                Log.i(TAG, "onEvent, eventType: " + eventType + ", bundle: " + bundle);
            }
        };

        Log.i(TAG, "createNavigationRenderer, returns: " + navigationRenderer);
        return navigationRenderer;
    }
}
