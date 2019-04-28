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
package android.car.app.menu;

import android.os.Bundle;

/**
 * The callbacks that a car app needs to pass to a car ui provider for car menu interactions.
 */
public abstract class CarMenuCallbacks {
    /**
     * Called when the car menu wants to get the root.
     *
     * @param hints Hints that the Drawer can use to modify behavior. It can be null.
     * @return The {@link RootMenu} which contains the root id and any hints
     */
    public abstract RootMenu getRootMenu(Bundle hints);

    /**
     * Query for information about the menu items that are contained within
     * the specified id and subscribes to receive updates when they change.
     *
     * @param parentId The id of the parent menu item whose list of children
     *            will be subscribed.
     * @param callback The callback to receive the list of children.
     */
    public abstract void subscribe(String parentId, SubscriptionCallbacks callback);

    /**
     * Unsubscribe for changes to the children of the specified id.
     * @param parentId The id of the parent menu item whose list of children
     *            will be unsubscribed.
     */
    public abstract void unsubscribe(String parentId, SubscriptionCallbacks callbacks);

    /**
     * Called when the car menu has been opened.
     */
    public abstract void onCarMenuOpened();

    /**
     * Called when the car menu has been closed.
     */
    public abstract void onCarMenuClosed();

    /**
     * Called when a car menu item with the specified id has been clicked.
     */
    public abstract void onItemClicked(String id);

    /**
     * Called when a car menu item with the specified id has been long clicked.
     */
    public abstract boolean onItemLongClicked(String id);

    /**
     * Called when the menu button is clicked.
     */
    public abstract boolean onMenuClicked();

    /**
     * Called when the menu is opening.
     */
    public abstract void onCarMenuOpening();

    /**
     * Called when the menu is closing.
     */
    public abstract void onCarMenuClosing();
}
