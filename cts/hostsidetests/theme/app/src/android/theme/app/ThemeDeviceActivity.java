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

package android.theme.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.theme.app.modifiers.DatePickerModifier;
import android.theme.app.modifiers.ProgressBarModifier;
import android.theme.app.modifiers.SearchViewModifier;
import android.theme.app.modifiers.TimePickerModifier;
import android.theme.app.modifiers.ViewCheckedModifier;
import android.theme.app.modifiers.ViewPressedModifier;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.DatePicker;

import java.io.File;
import java.lang.Override;

/**
 * A activity which display various UI elements with non-modifiable themes.
 */
public class ThemeDeviceActivity extends Activity {
    public static final String EXTRA_THEME = "theme";
    public static final String EXTRA_OUTPUT_DIR = "outputDir";

    private static final String TAG = "ThemeDeviceActivity";

    /**
     * Delay that allows the Holo-style CalendarView to settle to its final
     * position.
     */
    private static final long HOLO_CALENDAR_VIEW_ADJUSTMENT_DURATION = 540;

    private Theme mTheme;
    private ReferenceViewGroup mViewGroup;
    private File mOutputDir;
    private int mLayoutIndex;
    private boolean mIsRunning;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        final int themeIndex = intent.getIntExtra(EXTRA_THEME, -1);
        if (themeIndex < 0) {
            Log.e(TAG, "No theme specified");
            finish();
        }

        final String outputDir = intent.getStringExtra(EXTRA_OUTPUT_DIR);
        if (outputDir == null) {
            Log.e(TAG, "No output directory specified");
            finish();
        }

        mOutputDir = new File(outputDir);
        mTheme = THEMES[themeIndex];

        setTheme(mTheme.id);
        setContentView(R.layout.theme_test);

        mViewGroup = (ReferenceViewGroup) findViewById(R.id.reference_view_group);

        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_TURN_SCREEN_ON
                | LayoutParams.FLAG_DISMISS_KEYGUARD );
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsRunning = true;

        setNextLayout();
    }

    @Override
    protected void onPause() {
        mIsRunning = false;

        if (!isFinishing()) {
            // The activity paused for some reason, likely a system crash
            // dialog. Finish it so we can move to the next theme.
            Log.w(TAG, "onPause() called without a call to finish()", new RuntimeException());
            finish();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mLayoutIndex < LAYOUTS.length) {
            final Intent data = new Intent();
            data.putExtra(GenerateImagesActivity.EXTRA_REASON, "Only rendered "
                    + mLayoutIndex + "/" + LAYOUTS.length + " layouts");
            setResult(RESULT_CANCELED, data);
        }

        super.onDestroy();
    }

    /**
     * Sets the next layout in the UI.
     */
    private void setNextLayout() {
        if (mLayoutIndex >= LAYOUTS.length) {
            setResult(RESULT_OK);
            finish();
            return;
        }

        mViewGroup.removeAllViews();

        final Layout layout = LAYOUTS[mLayoutIndex++];
        final String layoutName = String.format("%s_%s", mTheme.name, layout.name);
        final View view = getLayoutInflater().inflate(layout.id, mViewGroup, false);
        if (layout.modifier != null) {
            layout.modifier.modifyView(view);
        }

        mViewGroup.addView(view);
        view.setFocusable(false);

        Log.v(TAG, "Rendering layout " + layoutName
                + " (" + mLayoutIndex + "/" + LAYOUTS.length + ")");

        final Runnable generateBitmapRunnable = new Runnable() {
            @Override
            public void run() {
                new BitmapTask(view, layoutName).execute();
            }
        };

        if (view instanceof DatePicker && mTheme.spec == Theme.HOLO) {
            // The Holo-styled DatePicker uses a CalendarView that has a
            // non-configurable adjustment duration of 540ms.
            view.postDelayed(generateBitmapRunnable, HOLO_CALENDAR_VIEW_ADJUSTMENT_DURATION);
        } else {
            view.post(generateBitmapRunnable);
        }
    }

    private class BitmapTask extends GenerateBitmapTask {
        public BitmapTask(View view, String name) {
            super(view, mOutputDir, name);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success && mIsRunning) {
                setNextLayout();
            } else {
                Log.e(TAG, "Failed to render view to bitmap: " + mName + " (activity running? "
                        + mIsRunning + ")");
                finish();
            }
        }
    }

    /**
     * A class to encapsulate information about a theme.
     */
    static class Theme {
        public static final int HOLO = 0;
        public static final int MATERIAL = 1;

        public final int spec;
        public final int id;
        public final int apiLevel;
        public final String name;

        private Theme(int spec, int id, int apiLevel, String name) {
            this.spec = spec;
            this.id = id;
            this.apiLevel = apiLevel;
            this.name = name;
        }
    }

    // List of themes to verify.
    static final Theme[] THEMES = {
            // Holo
            new Theme(Theme.HOLO, android.R.style.Theme_Holo,
                    Build.VERSION_CODES.HONEYCOMB, "holo"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Dialog,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialog"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Dialog_MinWidth,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialog_minwidth"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Dialog_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialog_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Dialog_NoActionBar_MinWidth,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialog_noactionbar_minwidth"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_DialogWhenLarge,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialogwhenlarge"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_DialogWhenLarge_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_dialogwhenlarge_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_InputMethod,
                    Build.VERSION_CODES.HONEYCOMB, "holo_inputmethod"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_NoActionBar_Fullscreen,
                    Build.VERSION_CODES.HONEYCOMB, "holo_noactionbar_fullscreen"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_NoActionBar_Overscan,
                    Build.VERSION_CODES.JELLY_BEAN_MR2, "holo_noactionbar_overscan"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_NoActionBar_TranslucentDecor,
                    Build.VERSION_CODES.KITKAT, "holo_noactionbar_translucentdecor"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Panel,
                    Build.VERSION_CODES.HONEYCOMB, "holo_panel"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Wallpaper,
                    Build.VERSION_CODES.HONEYCOMB, "holo_wallpaper"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Wallpaper_NoTitleBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_wallpaper_notitlebar"),

            // Holo Light
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_DarkActionBar,
                    Build.VERSION_CODES.ICE_CREAM_SANDWICH, "holo_light_darkactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_Dialog,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialog"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_Dialog_MinWidth,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialog_minwidth"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialog_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialog_noactionbar_minwidth"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_DialogWhenLarge,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialogwhenlarge"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_DialogWhenLarge_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_dialogwhenlarge_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_NoActionBar,
                    Build.VERSION_CODES.HONEYCOMB_MR2, "holo_light_noactionbar"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_NoActionBar_Fullscreen,
                    Build.VERSION_CODES.HONEYCOMB_MR2, "holo_light_noactionbar_fullscreen"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_NoActionBar_Overscan,
                    Build.VERSION_CODES.JELLY_BEAN_MR2, "holo_light_noactionbar_overscan"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_NoActionBar_TranslucentDecor,
                    Build.VERSION_CODES.KITKAT, "holo_light_noactionbar_translucentdecor"),
            new Theme(Theme.HOLO, android.R.style.Theme_Holo_Light_Panel,
                    Build.VERSION_CODES.HONEYCOMB, "holo_light_panel"),

            // Material
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material,
                    Build.VERSION_CODES.LOLLIPOP, "material"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog_Alert,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog_alert"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog_MinWidth,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog_minwidth"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog_NoActionBar_MinWidth,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog_noactionbar_minwidth"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Dialog_Presentation,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialog_presentation"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_DialogWhenLarge,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialogwhenlarge"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_DialogWhenLarge_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_dialogwhenlarge_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_InputMethod,
                    Build.VERSION_CODES.LOLLIPOP, "material_inputmethod"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_NoActionBar_Fullscreen,
                    Build.VERSION_CODES.LOLLIPOP, "material_noactionbar_fullscreen"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_NoActionBar_Overscan,
                    Build.VERSION_CODES.LOLLIPOP, "material_noactionbar_overscan"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_NoActionBar_TranslucentDecor,
                    Build.VERSION_CODES.LOLLIPOP, "material_noactionbar_translucentdecor"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Panel,
                    Build.VERSION_CODES.LOLLIPOP, "material_panel"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Settings,
                    Build.VERSION_CODES.LOLLIPOP, "material_settings"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Voice,
                    Build.VERSION_CODES.LOLLIPOP, "material_voice"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Wallpaper,
                    Build.VERSION_CODES.LOLLIPOP, "material_wallpaper"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Wallpaper_NoTitleBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_wallpaper_notitlebar"),

            // Material Light
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light,
                    Build.VERSION_CODES.LOLLIPOP, "material_light"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_DarkActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_darkactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog_Alert,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog_alert"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog_MinWidth,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog_minwidth"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog_NoActionBar_MinWidth,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog_noactionbar_minwidth"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Dialog_Presentation,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialog_presentation"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_DialogWhenLarge,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialogwhenlarge"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_DialogWhenLarge_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_dialogwhenlarge_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_LightStatusBar,
                    Build.VERSION_CODES.M, "material_light_lightstatusbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_NoActionBar,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_noactionbar"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_noactionbar_fullscreen"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_NoActionBar_Overscan,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_noactionbar_overscan"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_NoActionBar_TranslucentDecor,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_noactionbar_translucentdecor"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Panel,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_panel"),
            new Theme(Theme.MATERIAL, android.R.style.Theme_Material_Light_Voice,
                    Build.VERSION_CODES.LOLLIPOP, "material_light_voice")
    };

    /**
     * A class to encapsulate information about a layout.
     */
    private static class Layout {
        public final int id;
        public final String name;
        public final LayoutModifier modifier;

        private Layout(int id, String name) {
            this(id, name, null);
        }

        private Layout(int id, String name, LayoutModifier modifier) {
            this.id = id;
            this.name = name;
            this.modifier = modifier;
        }
    }

    // List of layouts to verify for each theme.
    private static final Layout[] LAYOUTS = {
            new Layout(R.layout.button, "button"),
            new Layout(R.layout.button, "button_pressed",
                    new ViewPressedModifier()),
            new Layout(R.layout.checkbox, "checkbox"),
            new Layout(R.layout.checkbox, "checkbox_checked",
                    new ViewCheckedModifier()),
            new Layout(R.layout.chronometer, "chronometer"),
            new Layout(R.layout.color_blue_bright, "color_blue_bright"),
            new Layout(R.layout.color_blue_dark, "color_blue_dark"),
            new Layout(R.layout.color_blue_light, "color_blue_light"),
            new Layout(R.layout.color_green_dark, "color_green_dark"),
            new Layout(R.layout.color_green_light, "color_green_light"),
            new Layout(R.layout.color_orange_dark, "color_orange_dark"),
            new Layout(R.layout.color_orange_light, "color_orange_light"),
            new Layout(R.layout.color_purple, "color_purple"),
            new Layout(R.layout.color_red_dark, "color_red_dark"),
            new Layout(R.layout.color_red_light, "color_red_light"),
            new Layout(R.layout.datepicker, "datepicker",
                    new DatePickerModifier()),
            new Layout(R.layout.edittext, "edittext"),
            new Layout(R.layout.progressbar_horizontal_0, "progressbar_horizontal_0"),
            new Layout(R.layout.progressbar_horizontal_100, "progressbar_horizontal_100"),
            new Layout(R.layout.progressbar_horizontal_50, "progressbar_horizontal_50"),
            new Layout(R.layout.progressbar_large, "progressbar_large",
                    new ProgressBarModifier()),
            new Layout(R.layout.progressbar_small, "progressbar_small",
                    new ProgressBarModifier()),
            new Layout(R.layout.progressbar, "progressbar",
                    new ProgressBarModifier()),
            new Layout(R.layout.radiobutton_checked, "radiobutton_checked"),
            new Layout(R.layout.radiobutton, "radiobutton"),
            new Layout(R.layout.radiogroup_horizontal, "radiogroup_horizontal"),
            new Layout(R.layout.radiogroup_vertical, "radiogroup_vertical"),
            new Layout(R.layout.ratingbar_0, "ratingbar_0"),
            new Layout(R.layout.ratingbar_2point5, "ratingbar_2point5"),
            new Layout(R.layout.ratingbar_5, "ratingbar_5"),
            new Layout(R.layout.ratingbar_0, "ratingbar_0_pressed",
                    new ViewPressedModifier()),
            new Layout(R.layout.ratingbar_2point5, "ratingbar_2point5_pressed",
                    new ViewPressedModifier()),
            new Layout(R.layout.ratingbar_5, "ratingbar_5_pressed",
                    new ViewPressedModifier()),
            new Layout(R.layout.searchview, "searchview_query",
                    new SearchViewModifier(SearchViewModifier.QUERY)),
            new Layout(R.layout.searchview, "searchview_query_hint",
                    new SearchViewModifier(SearchViewModifier.QUERY_HINT)),
            new Layout(R.layout.seekbar_0, "seekbar_0"),
            new Layout(R.layout.seekbar_100, "seekbar_100"),
            new Layout(R.layout.seekbar_50, "seekbar_50"),
            new Layout(R.layout.spinner, "spinner"),
            new Layout(R.layout.switch_button_checked, "switch_button_checked"),
            new Layout(R.layout.switch_button, "switch_button"),
            new Layout(R.layout.textview, "textview"),
            new Layout(R.layout.timepicker, "timepicker",
                    new TimePickerModifier()),
            new Layout(R.layout.togglebutton_checked, "togglebutton_checked"),
            new Layout(R.layout.togglebutton, "togglebutton"),
            new Layout(R.layout.zoomcontrols, "zoomcontrols"),
    };
}
