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
package com.android.cts.verifier.sensors.sixdof.Dialogs;

import com.android.cts.verifier.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.DialogFragment;
import android.app.AlertDialog;

/**
 * Dialog for instructions on what to to on lap 2
 */
public class Lap2Dialog extends DialogFragment {
    Lap2DialogListener mListener;

    public static Lap2Dialog newInstance() {
        Lap2Dialog dialog = new Lap2Dialog();
        return dialog;
    }

    public interface Lap2DialogListener {
        void onLap2Start();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction.
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Inflate and set the layout for the dialog.
        // Pass null as the parent view because its going in the dialog layout.
        builder.setTitle(R.string.test1_pass2)
                .setMessage(R.string.lap2_instructions)
                .setNegativeButton(R.string.got_it, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mListener.onLap2Start();
                    }
                });

        // Create the AlertDialog object and return it.
        return builder.create();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host fragment implements the callback interface.
        try {
            mListener = (Lap2DialogListener) getTargetFragment();
            mListener.onLap2Start();
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception.
            throw new ClassCastException(activity.toString()
                    + " must implement Lap2DialogListener");
        }
    }
}
