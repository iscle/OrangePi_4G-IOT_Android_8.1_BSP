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
package android.car.cluster.renderer;

import android.annotation.Nullable;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

/**
 * A wrapper over {@link NavigationRenderer} that runs all its methods in the context of provided
 * looper. It is guaranteed that all calls will be invoked in order they were called.
 */
/* package */ class ThreadSafeNavigationRenderer extends NavigationRenderer {

    private final Handler mHandler;
    private final NavigationRenderer mRenderer;

    private final static int MSG_NAV_START = 1;
    private final static int MSG_NAV_STOP = 2;
    private final static int MSG_NAV_NEXT_TURN = 3;
    private final static int MSG_NAV_NEXT_TURN_DISTANCE = 4;
    private final static int MSG_EVENT = 5;

    /** Creates thread-safe {@link NavigationRenderer}. Returns null if renderer == null */
    @Nullable
    static NavigationRenderer createFor(Looper looper, NavigationRenderer renderer) {
        return renderer == null ? null : new ThreadSafeNavigationRenderer(looper, renderer);
    }

    private ThreadSafeNavigationRenderer(Looper looper, NavigationRenderer renderer) {
        mRenderer = renderer;
        mHandler = new NavigationRendererHandler(looper, renderer);
    }

    @Override
    public CarNavigationInstrumentCluster getNavigationProperties() {
        if (mHandler.getLooper() == Looper.myLooper()) {
            return mRenderer.getNavigationProperties();
        } else {
            return runAndWaitResult(mHandler,
                    new RunnableWithResult<CarNavigationInstrumentCluster>() {
                        @Override
                        protected CarNavigationInstrumentCluster createResult() {
                            return mRenderer.getNavigationProperties();
                        }
                    });
        }
    }

    @Override
    public void onStartNavigation() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_NAV_START));
    }

    @Override
    public void onStopNavigation() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_NAV_STOP));
    }

    @Override
    public void onNextTurnChanged(int event, CharSequence eventName, int turnAngle, int turnNumber,
            Bitmap image, int turnSide) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_NAV_NEXT_TURN,
                new NextTurn(event, eventName, turnAngle, turnNumber, image, turnSide)));
    }

    @Override
    public void onNextTurnDistanceChanged(int distanceMeters, int timeSeconds,
            int displayDistanceMillis, int displayDistanceUnit) {
        ManeuverDistance distance = new ManeuverDistance(distanceMeters, timeSeconds,
                displayDistanceMillis, displayDistanceUnit);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_NAV_NEXT_TURN_DISTANCE, distance));
    }

    @Override
    public void onEvent(int eventType, Bundle bundle) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_EVENT, eventType, 0, bundle));
    }

    private static class NavigationRendererHandler extends RendererHandler<NavigationRenderer> {

        NavigationRendererHandler(Looper looper, NavigationRenderer renderer) {
            super(looper, renderer);
        }

        @Override
        public void handleMessage(Message msg, NavigationRenderer renderer) {
            switch (msg.what) {
                case MSG_NAV_START:
                    renderer.onStartNavigation();
                    break;
                case MSG_NAV_STOP:
                    renderer.onStopNavigation();
                    break;
                case MSG_NAV_NEXT_TURN:
                    NextTurn nt = (NextTurn) msg.obj;
                    renderer.onNextTurnChanged(nt.event, nt.eventName, nt.turnAngle, nt.turnNumber,
                            nt.bitmap, nt.turnSide);
                    break;
                case MSG_NAV_NEXT_TURN_DISTANCE:
                    ManeuverDistance d = (ManeuverDistance) msg.obj;
                    renderer.onNextTurnDistanceChanged(
                            d.meters, d.seconds, d.displayDistanceMillis, d.displayDistanceUnit);
                    break;
                case MSG_EVENT:
                    Bundle bundle = (Bundle) msg.obj;
                    renderer.onEvent(msg.arg1, bundle);
                    break;
                default:
                    throw new IllegalArgumentException("Msg: " + msg.what);
            }
        }
    }

    private static <E> E runAndWaitResult(Handler handler, final RunnableWithResult<E> runnable) {
        final CountDownLatch latch = new CountDownLatch(1);

        Runnable wrappedRunnable = new Runnable() {
            @Override
            public void run() {
                runnable.run();
                latch.countDown();
            }
        };

        handler.post(wrappedRunnable);

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return runnable.getResult();
    }

    private static class NextTurn {
        private final int event;
        private final CharSequence eventName;
        private final int turnAngle;
        private final int turnNumber;
        private final Bitmap bitmap;
        private final int turnSide;

        NextTurn(int event, CharSequence eventName, int turnAngle, int turnNumber, Bitmap bitmap,
                int turnSide) {
            this.event = event;
            this.eventName = eventName;
            this.turnAngle = turnAngle;
            this.turnNumber = turnNumber;
            this.bitmap = bitmap;
            this.turnSide = turnSide;
        }
    }

    private static abstract class RunnableWithResult<T> implements Runnable {
        private volatile T result;

        protected abstract T createResult();

        @Override
        public void run() {
            result = createResult();
        }

        public T getResult() {
            return result;
        }
    }

    private static abstract class RendererHandler<T> extends Handler {

        private final WeakReference<T> mRendererRef;

        RendererHandler(Looper looper, T renderer) {
            super(looper);
            mRendererRef = new WeakReference<>(renderer);
        }

        @Override
        public void handleMessage(Message msg) {
            T renderer = mRendererRef.get();
            if (renderer != null) {
                handleMessage(msg, renderer);
            }
        }

        public abstract void handleMessage(Message msg, T renderer);
    }

    private static class ManeuverDistance {
        final int meters;
        final int seconds;
        final int displayDistanceMillis;
        final int displayDistanceUnit;

        ManeuverDistance(int meters, int seconds, int displayDistanceMillis,
                int displayDistanceUnit) {
            this.meters = meters;
            this.seconds = seconds;
            this.displayDistanceMillis = displayDistanceMillis;
            this.displayDistanceUnit = displayDistanceUnit;
        }
    }
}
