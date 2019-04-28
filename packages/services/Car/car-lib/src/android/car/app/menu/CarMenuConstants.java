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

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Contains keys to the metadata of car menu, such as id, title, icon, etc.
 */
public class CarMenuConstants {
    public static class MenuItemConstants {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(flag = true,
                value = {FLAG_BROWSABLE, FLAG_FIRSTITEM})
        public @interface MenuItemFlags {}

        /**
         * Flag: Indicates that the item has children of its own
         */
        public static final int FLAG_BROWSABLE = 0x1;

        /**
         * Flag: Indicates that the menu should scroll to this item
         */
        public static final int FLAG_FIRSTITEM = 0x2;

        @Retention(RetentionPolicy.SOURCE)
        @IntDef(value = {WIDGET_CHECKBOX, WIDGET_TEXT_VIEW})
        public @interface WidgetTypes {}

        /**
         * Use a checkbox widget.
         */
        public static final int WIDGET_CHECKBOX = 0x1;

        /**
         * Use a TextView widget
         */
        public static final int WIDGET_TEXT_VIEW = 0x2;

        /**
         * Key for the car menu title.
         */
        public static final String KEY_TITLE = "android.car.app.menu.title";

        /**
         * Key for the item title.
         */
        public static final String KEY_TEXT = "android.car.app.menu.text";

        /**
         * Key for the left icon.
         */
        public static final String KEY_LEFTICON = "android.car.app.menu.leftIcon";

        /**
         * Key for the right icon.
         */
        public static final String KEY_RIGHTICON = "android.car.app.menu.rightIcon";

        /**
         * Key for the text to be shown to the right of the item.
         */
        public static final String KEY_RIGHTTEXT = "android.car.app.menu.rightText";

        /**
         * Key for the widget type.
         */
        public static final String KEY_WIDGET = "android.car.app.menu.widget";

        /**
         * Key for the widget state.
         */
        public static final String KEY_WIDGET_STATE = "android.car.app.menu.widget_state";

        /**
         * Key for the value of whether the item is a place holder.
         */
        public static final String KEY_EMPTY_PLACEHOLDER = "android.car.app.menu.empty_placeholder";

        /**
         * Key for the flags.
         */
        public static final String KEY_FLAGS = "android.car.app.menu.flags";

        /**
         * Key for the menu item id.
         */
        public static final String KEY_ID = "android.car.app.menu.id";

        /**
         * Key for the remote views.
         */
        public static final String KEY_REMOTEVIEWS = "android.car.app.menu.remoteViews";
    }
}