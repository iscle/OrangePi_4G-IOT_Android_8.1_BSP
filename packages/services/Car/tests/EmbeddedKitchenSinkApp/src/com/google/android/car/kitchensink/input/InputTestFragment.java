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
package com.google.android.car.kitchensink.input;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.hardware.automotive.vehicle.V2_0.VehicleHwKeyInputAction;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.android.car.kitchensink.CarEmulator;
import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test input event handling to system.
 * vehicle hal should have VEHICLE_PROPERTY_HW_KEY_INPUT support for this to work.
 */
public class InputTestFragment extends Fragment {

    private static final String TAG = "CAR.INPUT.KS";

    private static final Button BREAK_LINE = null;

    private final List<View> mButtons = new ArrayList<>();

    private CarEmulator mCarEmulator;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.input_test, container, false);

        Collections.addAll(mButtons,
                BREAK_LINE,
                createButton(R.string.home, KeyEvent.KEYCODE_HOME),
                createButton(R.string.volume_up, KeyEvent.KEYCODE_VOLUME_UP),
                createButton(R.string.volume_down, KeyEvent.KEYCODE_VOLUME_DOWN),
                createButton(R.string.volume_mute, KeyEvent.KEYCODE_VOLUME_MUTE),
                createButton(R.string.voice, KeyEvent.KEYCODE_VOICE_ASSIST),
                BREAK_LINE,
                createButton(R.string.music, KeyEvent.KEYCODE_MUSIC),
                createButton(R.string.music_play, KeyEvent.KEYCODE_MEDIA_PLAY),
                createButton(R.string.music_stop, KeyEvent.KEYCODE_MEDIA_STOP),
                createButton(R.string.next_song, KeyEvent.KEYCODE_MEDIA_NEXT),
                createButton(R.string.prev_song, KeyEvent.KEYCODE_MEDIA_PREVIOUS),
                createButton(R.string.tune_right, KeyEvent.KEYCODE_CHANNEL_UP),
                createButton(R.string.tune_left, KeyEvent.KEYCODE_CHANNEL_DOWN),
                BREAK_LINE,
                createButton(R.string.call_send, KeyEvent.KEYCODE_CALL),
                createButton(R.string.call_end, KeyEvent.KEYCODE_ENDCALL)
                );

        mCarEmulator = CarEmulator.create(getContext());
        addButtonsToPanel((LinearLayout) view.findViewById(R.id.input_buttons), mButtons);

        return view;
    }

    private Button createButton(@StringRes int textResId, int keyCode) {
        Button button = new Button(getContext());
        button.setText(getContext().getString(textResId));
        button.setTextSize(32f);
        // Single touch + key event does not work as touch is happening in other window
        // at the same time. But long press will work.
        button.setOnTouchListener((v, event) -> {
            handleTouchEvent(event, keyCode);
            return true;
        });

        return button;
    }

    private void handleTouchEvent(MotionEvent event, int keyCode) {
        int androidAction = event.getActionMasked();
        Log.i(TAG, "handleTouchEvent, action:" + androidAction + ",keyCode:" + keyCode);

        switch (androidAction) {
            case MotionEvent.ACTION_DOWN:
                mCarEmulator.injectKey(keyCode, VehicleHwKeyInputAction.ACTION_DOWN);
                break;
            case MotionEvent.ACTION_UP:
                mCarEmulator.injectKey(keyCode, VehicleHwKeyInputAction.ACTION_UP);
                break;
            default:
                Log.w(TAG, "Unhandled touch action: " + androidAction);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void addButtonsToPanel(LinearLayout root, List<View> buttons) {
        LinearLayout panel = null;
        for (View button : buttons) {
            if (button == BREAK_LINE || panel == null) {
                panel = new LinearLayout(getContext());
                panel.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(panel);
            } else {
                panel.addView(button);
            }
        }
    }
}
