/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.messaging.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.Menu;
import android.view.MenuItem;

/** Helper method to tint menu item icons. */
public class MenuTinter {
    private MenuTinter() {}

    /**
     * Tints the menu icons
     *
     * @param context resource context
     * @param menu menu to tint
     * @param refColor color to tint
     */
    public static void tintMenu(Context context, Menu menu, int refColor) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            Drawable drawable = item.getIcon();
            if (drawable != null) {
                drawable = DrawableCompat.wrap(drawable);
                drawable.mutate();
                DrawableCompat.setTint(drawable, ContextCompat.getColor(context, refColor));
                item.setIcon(drawable);
            }
        }
    }
}
