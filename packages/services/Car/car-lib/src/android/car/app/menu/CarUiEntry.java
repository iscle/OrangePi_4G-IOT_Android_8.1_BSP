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
package android.car.app.menu;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * A base class for a car ui entry which is used for loading and manipulating common car
 * app decor window (CarUi).
 *
 * A CarUi provider provides essential ui elements that a car app may want to use. The CarUi is
 * loaded by apps at runtime, similar to a shared library, but via reflection through a class that
 * extends {@link android.car.app.menu.CarUiEntry} from a separate apk
 * called CarUiProvider. Depending on the different platforms, the CarUiProvider may
 * be different and can be customized by different car makers. However, it is required that a
 * set of basic ui elements and functionalities exist in the CarUiProvider. This class defines
 * the set of must have functions in a CarUiProvider.
 */
public abstract class CarUiEntry {
    protected final Context mAppContext;
    protected final Context mUiLibContext;

    public CarUiEntry(Context uiLibContext, Context appContext) {
        mUiLibContext = uiLibContext.createConfigurationContext(
                appContext.getResources().getConfiguration());
        mAppContext = appContext;
    }

    /**
     * Return the content view.
     */
    abstract public View getContentView();

    /**
     * Set {@link android.car.app.menu.CarMenuCallbacks} from a car app for car menu interactions.
     */
    abstract public void setCarMenuCallbacks(CarMenuCallbacks callbacks);

    /**
     * Return the id of the main container in which app can render its own content.
     */
    abstract public int getFragmentContainerId();

    /**
     * Set the background bitmap.
     */
    abstract public void setBackground(Bitmap bitmap);

    /**
     * Replace the menu button with the given bitmap.
     */
    abstract public void setMenuButtonBitmap(Bitmap bitmap);

    /**
     * Hide the menu button.
     */
    abstract public void hideMenuButton();

    /**
     * Restore the menu button.
     */
    abstract public void restoreMenuDrawable();

    /**
     * Set the color of the car menu scrim.
     */
    abstract public void setScrimColor(int color);

    /**
     * Set the title of the car menu.
     */
    abstract public void setTitle(CharSequence title);

    /**
     * Close the car menu.
     */
    abstract public void closeDrawer();

    /**
     * Open the car menu.
     */
    abstract public void openDrawer();

    /**
     * Show the menu associated with the specified id, and set the car menu title.
     */
    abstract public void showMenu(String id, String title);

    /**
     * Set the car menu button color.
     */
    abstract public void setMenuButtonColor(int color);

    /**
     * Make the menu title visible.
     */
    abstract public void showTitle();

    /**
     * Hide the menu title.
     */
    abstract public void hideTitle();

    /**
     * Use the light car theme.
     */
    abstract public void setLightMode();

    /**
     * Use the dark car theme.
     */
    abstract public void setDarkMode();

    /**
     * Use automatic light/dark car theme based on ui mode.
     */
    abstract public void setAutoLightDarkMode();

    /**
     * Called when the activity's onRestoreInstanceState is called.
     */
    abstract public void onRestoreInstanceState(Bundle savedInstanceState);

    /**
     * Called when the activity's onSaveInstanceState is called.
     */
    abstract public void onSaveInstanceState(Bundle outState);

    /**
     * Show the search box and set the click listener for the search box.
     */
    abstract public void showSearchBox(View.OnClickListener listener);

    /**
     * Set the color of the search box.
     */
    abstract public void setSearchBoxColors(int backgroundColor, int searchLogoColor,
            int textColor, int hintTextColor);

    /**
     * Set the search box edit listener for monitoring input.
     */
    abstract public void setSearchBoxEditListener(SearchBoxEditListener listener);

    /**
     * Called when activity's onStart is called.
     */
    abstract public void onStart();

    /**
     * Called when activity's onResume is called.
     */
    abstract public void onResume();

    /**
     * Called when activity's onPause is called.
     */
    abstract public void onPause();

    /**
     * Called when activity's onStop is called.
     */
    abstract public void onStop();

    /**
     * Start input on the search box and show IME.
     * @param hint hint text to show in the search box.
     * @param searchBoxClickListener search box click listener.
     * @return The search box {@link android.widget.EditText}.
     */
    abstract public EditText startInput(String hint,
            View.OnClickListener searchBoxClickListener);

    /**
     * Set the view in the end of the search box as the search result is loading.
     */
    abstract public void setSearchBoxEndView(View view);

    /**
     * Returns the current user entered text in the search box.
     */
    abstract public CharSequence getSearchBoxText();

    /**
     * Called when input should be stopped.
     */
    abstract public void stopInput();

    /**
     * Show a toast message.
     * @param msg text to show
     * @param duration toast duration in millisecond.
     */
    abstract public void showToast(String msg, long duration);
}
