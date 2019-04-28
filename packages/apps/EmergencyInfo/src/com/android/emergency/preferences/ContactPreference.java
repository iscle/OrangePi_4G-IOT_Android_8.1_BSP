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
package com.android.emergency.preferences;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.emergency.CircleFramedDrawable;
import com.android.emergency.EmergencyContactManager;
import com.android.emergency.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.List;


/**
 * A {@link Preference} to display or call a contact using the specified URI string.
 */
public class ContactPreference extends Preference {

    private static final String TAG = "ContactPreference";

    static final ContactFactory DEFAULT_CONTACT_FACTORY = new ContactFactory() {
        @Override
        public EmergencyContactManager.Contact getContact(Context context, Uri phoneUri) {
            return EmergencyContactManager.getContact(context, phoneUri);
        }
    };

    private final ContactFactory mContactFactory;
    private EmergencyContactManager.Contact mContact;
    @Nullable private RemoveContactPreferenceListener mRemoveContactPreferenceListener;
    @Nullable private AlertDialog mRemoveContactDialog;

    /**
     * Listener for removing a contact.
     */
    public interface RemoveContactPreferenceListener {
        /**
         * Callback to remove a contact preference.
         */
        void onRemoveContactPreference(ContactPreference preference);
    }

    /**
     * Interface for getting a contact for a phone number Uri.
     */
    public interface ContactFactory {
        /**
         * Gets a {@link EmergencyContactManager.Contact} for a phone {@link Uri}.
         *
         * @param context The context to use.
         * @param phoneUri The phone uri.
         * @return a contact for the given phone uri.
         */
        EmergencyContactManager.Contact getContact(Context context, Uri phoneUri);
    }

    public ContactPreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        mContactFactory = DEFAULT_CONTACT_FACTORY;
    }

    /**
     * Instantiates a ContactPreference that displays an emergency contact, taking in a Context and
     * the Uri.
     */
    public ContactPreference(Context context, @NonNull Uri phoneUri) {
        this(context, phoneUri, DEFAULT_CONTACT_FACTORY);
    }

    @VisibleForTesting
    ContactPreference(Context context, @NonNull Uri phoneUri,
            @NonNull ContactFactory contactFactory) {
        super(context);
        mContactFactory = contactFactory;
        setOrder(DEFAULT_ORDER);

        setPhoneUri(phoneUri);

        setWidgetLayoutResource(R.layout.preference_user_delete_widget);
        setPersistent(false);
    }

    public void setPhoneUri(@NonNull Uri phoneUri) {
        if (mContact != null && !phoneUri.equals(mContact.getPhoneUri()) &&
                mRemoveContactDialog != null) {
            mRemoveContactDialog.dismiss();
        }
        mContact = mContactFactory.getContact(getContext(), phoneUri);

        setTitle(mContact.getName());
        setKey(mContact.getPhoneUri().toString());
        String summary = mContact.getPhoneType() == null ?
                mContact.getPhoneNumber() :
                String.format(
                        getContext().getResources().getString(R.string.phone_type_and_phone_number),
                        mContact.getPhoneType(),
                        BidiFormatter.getInstance().unicodeWrap(mContact.getPhoneNumber(),
                                TextDirectionHeuristics.LTR));
        setSummary(summary);

        // Update the message to show the correct name.
        if (mRemoveContactDialog != null) {
            mRemoveContactDialog.setMessage(
                    String.format(getContext().getString(R.string.remove_contact),
                            mContact.getName()));
        }

        //TODO: Consider doing the following in a non-UI thread.
        Drawable icon;
        if (mContact.getPhoto() != null) {
            icon = new CircleFramedDrawable(mContact.getPhoto(),
                    (int) getContext().getResources().getDimension(R.dimen.circle_avatar_size));
        } else {
            icon = getContext().getResources().getDrawable(R.drawable.ic_account_circle);
        }
        setIcon(icon);
    }

    /** Listener to be informed when a contact preference should be deleted. */
    public void setRemoveContactPreferenceListener(
            RemoveContactPreferenceListener removeContactListener) {
        mRemoveContactPreferenceListener = removeContactListener;
        if (mRemoveContactPreferenceListener == null) {
            mRemoveContactDialog = null;
            return;
        }
        if (mRemoveContactDialog != null) {
            return;
        }
        // Create the remove contact dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setNegativeButton(getContext().getString(R.string.cancel), null);
        builder.setPositiveButton(getContext().getString(R.string.remove),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface,
                                        int which) {
                        if (mRemoveContactPreferenceListener != null) {
                            mRemoveContactPreferenceListener
                                    .onRemoveContactPreference(ContactPreference.this);
                        }
                    }
                });
        builder.setMessage(String.format(getContext().getString(R.string.remove_contact),
                mContact.getName()));
        mRemoveContactDialog = builder.create();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View deleteContactIcon = holder.findViewById(R.id.delete_contact);
        if (mRemoveContactPreferenceListener == null) {
            deleteContactIcon.setVisibility(View.GONE);
        } else {
            deleteContactIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showRemoveContactDialog(null);
                }
            });

        }
    }

    public Uri getPhoneUri() {
        return mContact.getPhoneUri();
    }

    @VisibleForTesting
    EmergencyContactManager.Contact getContact() {
        return mContact;
    }

    @VisibleForTesting
    AlertDialog getRemoveContactDialog() {
        return mRemoveContactDialog;
    }

    /**
     * Calls the contact.
     */
    public void callContact() {
        Intent callIntent =
                new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + mContact.getPhoneNumber()));
        PackageManager packageManager = getContext().getPackageManager();
        List<ResolveInfo> infos =
                packageManager.queryIntentActivities(callIntent, PackageManager.MATCH_SYSTEM_ONLY);
        if (infos == null || infos.isEmpty()) {
            return;
        }
        callIntent.setComponent(new ComponentName(infos.get(0).activityInfo.packageName,
                infos.get(0).activityInfo.name));

        MetricsLogger.action(getContext(), MetricsEvent.ACTION_CALL_EMERGENCY_CONTACT);
        getContext().startActivity(callIntent);
    }

    /**
     * Displays a contact card for the contact.
     */
    public void displayContact() {
        Intent displayIntent = new Intent(Intent.ACTION_VIEW);
        displayIntent.setData(mContact.getContactLookupUri());
        try {
            getContext().startActivity(displayIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                           getContext().getString(R.string.fail_display_contact),
                           Toast.LENGTH_LONG).show();
            Log.w(TAG, "No contact app available to display the contact", e);
            return;
        }

    }

    /** Shows the dialog to remove the contact, restoring it from {@code state} if it's not null. */
    private void showRemoveContactDialog(Bundle state) {
        if (mRemoveContactDialog == null) {
            return;
        }
        if (state != null) {
            mRemoveContactDialog.onRestoreInstanceState(state);
        }
        mRemoveContactDialog.show();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (mRemoveContactDialog == null || !mRemoveContactDialog.isShowing()) {
            return superState;
        }
        final SavedState myState = new SavedState(superState);
        myState.isDialogShowing = true;
        myState.dialogBundle = mRemoveContactDialog.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        if (myState.isDialogShowing) {
            showRemoveContactDialog(myState.dialogBundle);
        }
    }

    private static class SavedState extends BaseSavedState {
        boolean isDialogShowing;
        Bundle dialogBundle;

        public SavedState(Parcel source) {
            super(source);
            isDialogShowing = source.readInt() == 1;
            dialogBundle = source.readBundle();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(isDialogShowing ? 1 : 0);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
