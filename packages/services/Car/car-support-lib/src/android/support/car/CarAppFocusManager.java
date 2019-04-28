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

package android.support.car;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Enables applications to set and listen for the current application focus (such as active
 * navigation). Typically, only one such application should be running at a time. When another
 * application gets ownership of a given APP_FOCUS_TYPE_*, the old app should stop using the
 * feature represented by the focus type.
 */
public abstract class CarAppFocusManager implements CarManagerBase {
    /**
     * Receives notifications when app focus changes.
     */
    public interface OnAppFocusChangedListener {
        /**
         * Indicates the application focus has changed. The {@link CarAppFocusManager} instance
         * causing the change does not get this notification.
         * @param manager the {@link CarAppFocusManager} this listener is attached to.  Useful if
         * the app wished to unregister the listener.
         * @param appType application type for which status changed
         * @param active returns {@code true} if active
         */
        void onAppFocusChanged(CarAppFocusManager manager, @AppFocusType int appType,
                boolean active);
    }

    /**
     * Receives notifications when the application focus ownership changes.
     */
    public interface OnAppFocusOwnershipCallback {
        /**
         * Lost ownership for the focus, which occurs when another app has set the focus.
         * The app losing focus should stop the action associated with the focus.
         * For example, a navigation app running active navigation should stop navigation
         * upon getting this for {@link CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION}.
         * @param manager the {@link CarAppFocusManager} this callback is attached to.  Useful if
         * the app wishes to unregister the callback.
         * @param appType
         */
        void onAppFocusOwnershipLost(CarAppFocusManager manager, @AppFocusType int appType);

        /**
         * Granted ownership for the focus, which happens after app has requested the focus.
         * The app getting focus can start the action associated with the focus.
         * For example, navigation app can start navigation
         * upon getting this for {@link CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION}.
         * @param manager the {@link CarAppFocusManager} this callback is attached to.  Useful if
         * the app wishes to unregister the callback.
         * @param appType
         */
        void onAppFocusOwnershipGranted(CarAppFocusManager manager, @AppFocusType int appType);
    }

    /**
     * Represents navigation focus.
     * <p/>
     * When a program loses navigation focus they should no longer send navigation data to the
     * instrument cluster via the
     * {@link android.support.car.navigation.CarNavigationStatusManager}.  Furthermore they
     * should stop sending audio updates and any notifications regarding navigation.
     * Essentially, these apps should stop all navigation activities as this means another app is
     * navigating.
     */
    public static final int APP_FOCUS_TYPE_NAVIGATION = 1;
    /**
     * Represents voice command focus.
     * @hide
     */
    public static final int APP_FOCUS_TYPE_VOICE_COMMAND = 2;
    /**
     * Update this after adding a new app type.
     * @hide
     */
    public static final int APP_FOCUS_TYPE_MAX = 2;

    /** @hide */
    @IntDef({
        APP_FOCUS_TYPE_NAVIGATION,
        APP_FOCUS_TYPE_VOICE_COMMAND
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AppFocusType {}

    /**
     * A failed focus change request.
     */
    public static final int APP_FOCUS_REQUEST_FAILED = 0;
    /**
     * A successful focus change request.
     */
    public static final int APP_FOCUS_REQUEST_SUCCEEDED = 1;

    /** @hide */
    @IntDef({
        APP_FOCUS_REQUEST_FAILED,
        APP_FOCUS_REQUEST_SUCCEEDED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AppFocusRequestResult {}

    /**
     * Register listener to monitor app focus changes.
     * Multiple listeners can be registered for a single focus and the same listener can be used
     * for multiple focuses.
     * @param listener Listener to register for focus events.
     * @param appType Application type to get notification for.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract void addFocusListener(OnAppFocusChangedListener listener,
            @AppFocusType int appType) throws CarNotConnectedException;

    /**
     * Unregister listener for app type and stop listening to focus change events.
     * @param listener Listener to unregister from focus events.
     * @param appType Application type to get notification for.
     */
    public abstract void removeFocusListener(OnAppFocusChangedListener listener,
            @AppFocusType int appType);

    /**
     * Unregister listener for all app types and stop listening to focus change events.
     * @param listener Listener to unregister from focus events.
     */
    public abstract void removeFocusListener(OnAppFocusChangedListener listener);

    /**
     * Check if the current process owns the given focus.
     * @param appType Application type.
     * @param callback Callback that was used to request ownership.
     * @return Returns {@code true} if current callback owns focus for application type.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract boolean isOwningFocus(@AppFocusType int appType,
            OnAppFocusOwnershipCallback callback) throws CarNotConnectedException;

    /**
     * Request application focus.
     * <p>
     * By requesting this, the app gains the focus for this appType
     * <p>
     * This call is asynchronous, focus may not be granted immediately.
     * {@link OnAppFocusOwnershipCallback#onAppFocusOwnershipGranted(CarAppFocusManager, int)} will
     * be sent to the app when focus is granted.
     * <p>
     * {@link OnAppFocusOwnershipCallback#onAppFocusOwnershipLost(CarAppFocusManager, int)}
     * will be sent to the app that currently holds focus.
     * The foreground app will have higher priority; other apps cannot
     * set the same focus while owner is in foreground.
     * <p>
     * The callback provided here is the identifier for the focus.  Apps need to pass it into
     * other app focus methods such as {@link #isOwningFocus(int, OnAppFocusOwnershipCallback)}
     * or {@link #abandonAppFocus(OnAppFocusOwnershipCallback)}.
     *
     * @param appType Application type to request focus for.
     * @param ownershipCallback Ownership callback to request app focus for. Cannot be null.
     *
     * @return {@link #APP_FOCUS_REQUEST_FAILED} or {@link #APP_FOCUS_REQUEST_SUCCEEDED}
     * @throws SecurityException if owner cannot be changed.
     * @throws CarNotConnectedException if the connection to the car service has been lost.
     */
    public abstract int requestAppFocus(int appType,
            OnAppFocusOwnershipCallback ownershipCallback)
            throws SecurityException, CarNotConnectedException;

    /**
     * Abandon the given focus (mark it as inactive).
     * @param ownershipCallback Ownership callback to abandon app focus for. Cannot be null.
     * @param appType Application type to abandon focus for.
     */
    public abstract void abandonAppFocus(OnAppFocusOwnershipCallback ownershipCallback,
            @AppFocusType int appType);

    /**
     * Abandon all focuses (mark them as inactive).
     * @param ownershipCallback Ownership callback to abandon focus for. Cannot be null.
     */
    public abstract void abandonAppFocus(OnAppFocusOwnershipCallback ownershipCallback);
}
