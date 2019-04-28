/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.accessories;

import android.app.Fragment;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.old.Action;
import com.android.tv.settings.dialog.old.ActionFragment;
import com.android.tv.settings.dialog.old.DialogActivity;
import com.android.tv.settings.util.AccessibilityHelper;

import java.util.ArrayList;
import java.util.Locale;

/**
 * BluetoothPairingDialog asks the user to enter a PIN / Passkey / simple
 * confirmation for pairing with a remote Bluetooth device.
 */
public class BluetoothPairingDialog extends DialogActivity {

    private static final String KEY_PAIR = "action_pair";
    private static final String KEY_CANCEL = "action_cancel";

    private static final String TAG = "BluetoothPairingDialog";
    private static final boolean DEBUG = false;

    private static final int BLUETOOTH_PIN_MAX_LENGTH = 16;
    private static final int BLUETOOTH_PASSKEY_MAX_LENGTH = 6;

    private BluetoothDevice mDevice;
    private int mType;
    private String mPairingKey;
    private boolean mPairingInProgress = false;

    /**
     * Dismiss the dialog if the bond state changes to bonded or none, or if
     * pairing was canceled for {@link #mDevice}.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DEBUG) {
                Log.d(TAG, "onReceive. Broadcast Intent = " + intent.toString());
            }
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.ERROR);
                if (bondState == BluetoothDevice.BOND_BONDED ||
                        bondState == BluetoothDevice.BOND_NONE) {
                    dismiss();
                }
            } else if (BluetoothDevice.ACTION_PAIRING_CANCEL.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || device.equals(mDevice)) {
                    dismiss();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (!BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
            Log.e(TAG, "Error: this activity may be started only with intent " +
                    BluetoothDevice.ACTION_PAIRING_REQUEST);
            finish();
            return;
        }

        mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        mType = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

        if (DEBUG) {
            Log.d(TAG, "Requested pairing Type = " + mType + " , Device = " + mDevice);
        }

        switch (mType) {
            case BluetoothDevice.PAIRING_VARIANT_PIN:
            case BluetoothDevice.PAIRING_VARIANT_PASSKEY:
                createUserEntryDialog();
                break;

            case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                int passkey =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
                if (passkey == BluetoothDevice.ERROR) {
                    Log.e(TAG, "Invalid Confirmation Passkey received, not showing any dialog");
                    finish();
                    return;
                }
                mPairingKey = String.format(Locale.US, "%06d", passkey);
                createConfirmationDialog();
                break;

            case BluetoothDevice.PAIRING_VARIANT_CONSENT:
            case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                createConfirmationDialog();
                break;

            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY:
            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN:
                int pairingKey =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
                if (pairingKey == BluetoothDevice.ERROR) {
                    Log.e(TAG,
                            "Invalid Confirmation Passkey or PIN received, not showing any dialog");
                    finish();
                    return;
                }
                if (mType == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY) {
                    mPairingKey = String.format("%06d", pairingKey);
                } else {
                    mPairingKey = String.format("%04d", pairingKey);
                }
                createConfirmationDialog();
                break;

            default:
                Log.e(TAG, "Incorrect pairing type received, not showing any dialog");
                finish();
                return;
        }

        // Fade out the old activity, and fade in the new activity.
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

        // TODO: don't do this
        final ViewGroup contentView = (ViewGroup) findViewById(android.R.id.content);
        final View topLayout = contentView.getChildAt(0);

        // Set the activity background
        final ColorDrawable bgDrawable =
                new ColorDrawable(getColor(R.color.dialog_activity_background));
        bgDrawable.setAlpha(255);
        topLayout.setBackground(bgDrawable);

        // Make sure pairing wakes up day dream
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_PAIRING_CANCEL);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mReceiver);

        // Finish the activity if we get placed in the background and cancel pairing
        if (!mPairingInProgress) {
            cancelPairing();
        }
        dismiss();

        super.onPause();
    }

    @Override
    public void onActionClicked(Action action) {
        String key = action.getKey();
        if (KEY_PAIR.equals(key)) {
            onPair(null);
            dismiss();
        } else if (KEY_CANCEL.equals(key)) {
            cancelPairing();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            cancelPairing();
        }
        return super.onKeyDown(keyCode, event);
    }

    private ArrayList<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();

        switch (mType) {
            case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
            case BluetoothDevice.PAIRING_VARIANT_CONSENT:
            case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                actions.add(new Action.Builder()
                        .key(KEY_PAIR)
                        .title(getString(R.string.bluetooth_pair))
                        .build());

                actions.add(new Action.Builder()
                        .key(KEY_CANCEL)
                        .title(getString(R.string.bluetooth_cancel))
                        .build());
                break;
            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN:
            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY:
                actions.add(new Action.Builder()
                        .key(KEY_CANCEL)
                        .title(getString(R.string.bluetooth_cancel))
                        .build());
                break;
        }

        return actions;
    }

    private void dismiss() {
        finish();
    }

    private void cancelPairing() {
        if (DEBUG) {
            Log.d(TAG, "cancelPairing");
        }
        mDevice.cancelPairingUserInput();
    }

    private void createUserEntryDialog() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, EntryDialogFragment.newInstance(mDevice, mType))
                .commit();
    }

    private void createConfirmationDialog() {
        // Build a Dialog activity view, with Action Fragment

        final ArrayList<Action> actions = getActions();

        final Fragment actionFragment = ActionFragment.newInstance(actions);
        final Fragment contentFragment =
                ConfirmationDialogFragment.newInstance(mDevice, mPairingKey, mType);

        setContentAndActionFragments(contentFragment, actionFragment);
    }

    private void onPair(String value) {
        if (DEBUG) {
            Log.d(TAG, "onPair: " + value);
        }
        switch (mType) {
            case BluetoothDevice.PAIRING_VARIANT_PIN:
                byte[] pinBytes = BluetoothDevice.convertPinToBytes(value);
                if (pinBytes == null) {
                    return;
                }
                mDevice.setPin(pinBytes);
                mPairingInProgress = true;
                break;

            case BluetoothDevice.PAIRING_VARIANT_PASSKEY:
                try {
                    int passkey = Integer.parseInt(value);
                    mDevice.setPasskey(passkey);
                    mPairingInProgress = true;
                } catch (NumberFormatException e) {
                    Log.d(TAG, "pass key " + value + " is not an integer");
                }
                break;

            case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
            case BluetoothDevice.PAIRING_VARIANT_CONSENT:
                mDevice.setPairingConfirmation(true);
                mPairingInProgress = true;
                break;

            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY:
            case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN:
                // Do nothing.
                break;

            case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                mDevice.setRemoteOutOfBandData();
                mPairingInProgress = true;
                break;

            default:
                Log.e(TAG, "Incorrect pairing type received");
        }
    }

    public static class EntryDialogFragment extends Fragment {

        private static final String ARG_DEVICE = "ConfirmationDialogFragment.DEVICE";
        private static final String ARG_TYPE = "ConfirmationDialogFragment.TYPE";

        private BluetoothDevice mDevice;
        private int mType;

        public static EntryDialogFragment newInstance(BluetoothDevice device, int type) {
            final EntryDialogFragment fragment = new EntryDialogFragment();
            final Bundle b = new Bundle(2);
            fragment.setArguments(b);
            b.putParcelable(ARG_DEVICE, device);
            b.putInt(ARG_TYPE, type);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final Bundle args = getArguments();
            mDevice = args.getParcelable(ARG_DEVICE);
            mType = args.getInt(ARG_TYPE);
        }

        @Override
        public @Nullable View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                Bundle savedInstanceState) {
            final View v = inflater.inflate(R.layout.bt_pairing_passkey_entry, container, false);

            final TextView titleText = (TextView) v.findViewById(R.id.title_text);
            final EditText textInput = (EditText) v.findViewById(R.id.text_input);

            textInput.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    String value = textInput.getText().toString();
                    if (actionId == EditorInfo.IME_ACTION_NEXT ||
                        (actionId == EditorInfo.IME_NULL &&
                         event.getAction() == KeyEvent.ACTION_DOWN)) {
                        ((BluetoothPairingDialog)getActivity()).onPair(value);
                    }
                    return true;
                }
            });

            final String instructions;
            final int maxLength;
            switch (mType) {
                case BluetoothDevice.PAIRING_VARIANT_PIN:
                    instructions = getString(R.string.bluetooth_enter_pin_msg, mDevice.getName());
                    final TextView instructionText = (TextView) v.findViewById(R.id.hint_text);
                    instructionText.setText(getString(R.string.bluetooth_pin_values_hint));
                    // Maximum of 16 characters in a PIN
                    maxLength = BLUETOOTH_PIN_MAX_LENGTH;
                    textInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                    break;

                case BluetoothDevice.PAIRING_VARIANT_PASSKEY:
                    instructions = getString(R.string.bluetooth_enter_passkey_msg,
                            mDevice.getName());
                    // Maximum of 6 digits for passkey
                    maxLength = BLUETOOTH_PASSKEY_MAX_LENGTH;
                    textInput.setInputType(InputType.TYPE_CLASS_TEXT);
                    break;

                default:
                    throw new IllegalStateException("Incorrect pairing type for" +
                            " createPinEntryView: " + mType);
            }

            titleText.setText(Html.fromHtml(instructions));

            textInput.setFilters(new InputFilter[]{new LengthFilter(maxLength)});

            return v;
        }
    }

    public static class ConfirmationDialogFragment extends Fragment {

        private static final String ARG_DEVICE = "ConfirmationDialogFragment.DEVICE";
        private static final String ARG_PAIRING_KEY = "ConfirmationDialogFragment.PAIRING_KEY";
        private static final String ARG_TYPE = "ConfirmationDialogFragment.TYPE";

        private BluetoothDevice mDevice;
        private String mPairingKey;
        private int mType;

        public static ConfirmationDialogFragment newInstance(BluetoothDevice device,
                String pairingKey, int type) {
            final ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            final Bundle b = new Bundle(3);
            b.putParcelable(ARG_DEVICE, device);
            b.putString(ARG_PAIRING_KEY, pairingKey);
            b.putInt(ARG_TYPE, type);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = getArguments();

            mDevice = args.getParcelable(ARG_DEVICE);
            mPairingKey = args.getString(ARG_PAIRING_KEY);
            mType = args.getInt(ARG_TYPE);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            final View v = inflater.inflate(R.layout.bt_pairing_passkey_display, container, false);

            final TextView titleText = (TextView) v.findViewById(R.id.title);
            final TextView instructionText = (TextView) v.findViewById(R.id.pairing_instructions);

            titleText.setText(getString(R.string.bluetooth_pairing_request));

            if (AccessibilityHelper.forceFocusableViews(getActivity())) {
                titleText.setFocusable(true);
                titleText.setFocusableInTouchMode(true);
                instructionText.setFocusable(true);
                instructionText.setFocusableInTouchMode(true);
            }

            final String instructions;

            switch (mType) {
                case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY:
                case BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN:
                    instructions = getString(R.string.bluetooth_display_passkey_pin_msg,
                            mDevice.getName(), mPairingKey);

                    // Since its only a notification, send an OK to the framework,
                    // indicating that the dialog has been displayed.
                    if (mType == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY) {
                        mDevice.setPairingConfirmation(true);
                    } else if (mType == BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN) {
                        byte[] pinBytes = BluetoothDevice.convertPinToBytes(mPairingKey);
                        mDevice.setPin(pinBytes);
                    }
                    break;

                case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                    instructions = getString(R.string.bluetooth_confirm_passkey_msg,
                            mDevice.getName(), mPairingKey);
                    break;

                case BluetoothDevice.PAIRING_VARIANT_CONSENT:
                case BluetoothDevice.PAIRING_VARIANT_OOB_CONSENT:
                    instructions = getString(R.string.bluetooth_incoming_pairing_msg,
                            mDevice.getName());

                    break;
                default:
                    instructions = "";
            }

            instructionText.setText(Html.fromHtml(instructions));

            return v;
        }
    }
}
