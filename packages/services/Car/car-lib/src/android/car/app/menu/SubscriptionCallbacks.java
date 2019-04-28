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

import java.util.List;

/**
 * The callbacks to receive menu items updates.
 */
public abstract class SubscriptionCallbacks {
    /**
     * Called when the items with the specified parent id are loaded.
     *
     * @param items The list of menu items. To retrieve the content of the item, use the keys
     *              defined in {@link CarMenuConstants.CarMenuConstants}.
     */
    public abstract void onChildrenLoaded(String parentId, List<Bundle> items);

    /**
     * Called when there is an error loading the items with the specified id.
     */
    public abstract void onError(String id);

    /**
     * Called when the car menu items with the specified parent id are changed.
     *
     * @param item The new menu item. To retrieve the content of the item, use the keys
     *              defined in {@link CarMenuConstants.CarMenuConstants}.
     */
    public abstract void onChildChanged(String parentId, Bundle item);
}