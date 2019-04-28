/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Utility class wrapping a {@link AlertDialog} in a {@link DialogFragment}
 * <p> In order to properly handle Dialog lifecycle we follow the practice of wrapping of them
 * in a Dialog Fragment.
 * <p> If buttons are to be used (enabled by setting a button message), the creator {@link Activity}
 * must implement {@link SimpleDialogListener}.
 */
public class SimpleDialog extends DialogFragment {
    private static final String TITLE = "title";
    private static final String MESSAGE = "message";
    private static final String NEGATIVE_BUTTON_MESSAGE = "negativeButtonMessage";
    private static final String POSITIVE_BUTTON_MESSAGE = "positiveButtonMessage";

    /**
     * Use the {@link Builder} instead. Keeping the constructor public only because
     * a {@link DialogFragment} must have an empty constructor that is public.
     */
    public SimpleDialog() {
    }

    @Override
    public AlertDialog onCreateDialog(Bundle savedInstanceState) {
        final SimpleDialogListener dialogListener = (SimpleDialogListener) getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        Bundle args = getArguments();
        if (args.containsKey(TITLE)) {
            builder.setTitle(args.getInt(TITLE));
        }

        if (args.containsKey(MESSAGE)) {
            builder.setMessage(args.getInt(MESSAGE));
        }

        if (args.containsKey(NEGATIVE_BUTTON_MESSAGE)) {
            builder.setNegativeButton(args.getInt(NEGATIVE_BUTTON_MESSAGE),
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialogListener.onNegativeButtonClick(SimpleDialog.this);
                }
            });
        }

        if (args.containsKey(POSITIVE_BUTTON_MESSAGE)) {
            builder.setPositiveButton(args.getInt(POSITIVE_BUTTON_MESSAGE),
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialogListener.onPositiveButtonClick(SimpleDialog.this);
                }
            });
        }

        return builder.create();
    }

    /**
     * Throws an exception informing of a lack of a handler for a dialog button click
     * <p> Useful when implementing {@link SimpleDialogListener}
     */
    public static void throwButtonClickHandlerNotImplemented(DialogFragment dialog) {
        throw new IllegalArgumentException("Button click handler not implemented for dialog: "
                + dialog.getTag());
    }

    public static class Builder implements DialogBuilder {
        private Integer mTitle;
        private Integer mMessage;
        private Integer mNegativeButtonMessage;
        private Integer mPositiveButtonMessage;
        private Boolean mCancelable;

        /**
         * Sets the title
         * @param title Title resource id.
         */
        public Builder setTitle(Integer title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the message
         * @param message Message resource id.
         */
        public Builder setMessage(int message) {
            mMessage = message;
            return this;
        }

        /**
         * Sets a message for the button.
         * <p> Makes the button appear (without setting a button message, a button is not displayed)
         * <p> Callback must be handled by a creator {@link Activity},
         * which must implement {@link SimpleDialogListener}.
         * @param negativeButtonMessage Message resource id.
         */
        public Builder setNegativeButtonMessage(int negativeButtonMessage) {
            mNegativeButtonMessage = negativeButtonMessage;
            return this;
        }

        /**
         * Sets a message for the button.
         * <p> Makes the button appear (without setting a button message, a button is not displayed)
         * <p> Callback must be handled by a creator {@link Activity},
         * which must implement {@link SimpleDialogListener}.
         * @param positiveButtonMessage Message resource id.
         */
        public Builder setPositiveButtonMessage(int positiveButtonMessage) {
            mPositiveButtonMessage = positiveButtonMessage;
            return this;
        }

        /**
         * Sets whether the dialog is cancelable or not.  Default is true.
         */
        public Builder setCancelable(boolean cancelable) {
            mCancelable = cancelable;
            return this;
        }

        /**
         * Creates an {@link SimpleDialog} with the arguments supplied to this builder.
         */
        @Override
        public SimpleDialog build() {
            SimpleDialog instance = new SimpleDialog();
            Bundle args = new Bundle();

            if (mTitle != null) {
                args.putInt(TITLE, mTitle);
            }

            if (mMessage != null) {
                args.putInt(MESSAGE, mMessage);
            }

            if (mNegativeButtonMessage != null) {
                args.putInt(NEGATIVE_BUTTON_MESSAGE, mNegativeButtonMessage);
            }

            if (mPositiveButtonMessage != null) {
                args.putInt(POSITIVE_BUTTON_MESSAGE, mPositiveButtonMessage);
            }

            if (mCancelable != null) {
                instance.setCancelable(mCancelable);
            }

            instance.setArguments(args);
            return instance;
        }
    }

    /**
     * Interface for handling callbacks from {@link SimpleDialog} buttons.
     *
     * <p>If multiple dialogs are used in a context of a single {@link Activity},
     * a consumer of the interface can differentiate between dialogs using
     * e.g. a {@link DialogFragment#getTag()}, or {@link DialogFragment#getArguments()}.
     */
    public interface SimpleDialogListener {
        /**
         * Called when a user clicks on the positive dialog button.
         * <p> To be implemented by a host {@link Activity} object.
         * @param dialog {@link DialogFragment} where the click happened.
         */
        void onPositiveButtonClick(DialogFragment dialog);

        /**
         * Called when a user clicks on the negative dialog button.
         * <p> To be implemented by a host {@link Activity} object.
         * @param dialog {@link DialogFragment} where the click happened.
         */
        void onNegativeButtonClick(DialogFragment dialog);
    }
}