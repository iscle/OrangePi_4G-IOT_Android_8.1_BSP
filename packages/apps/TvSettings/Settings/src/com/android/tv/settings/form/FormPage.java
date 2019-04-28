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

package com.android.tv.settings.form;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements a FormPage. This is a form page that can be used by MultiPagedForm
 * to create fragments
 */
public class FormPage implements Parcelable {

    public static final String DATA_KEY_SUMMARY_STRING =
            "com.android.tv.settings.form.FormPage.summaryString";

    public static final String DATA_KEY_SECONDARY_STRING =
            "com.android.tv.settings.form.FormPage.secondaryString";

    enum Type {
        MULTIPLE_CHOICE, TEXT_INPUT, PASSWORD_INPUT, INTENT
    }

    public final static Parcelable.Creator<FormPage> CREATOR = new Parcelable.Creator<FormPage>() {

        @Override
        public FormPage createFromParcel(Parcel parcel) {
            return new FormPage(parcel);
        }

        @Override
        public FormPage[] newArray(int size) {
            return new FormPage[size];
        }
    };

    /**
     * Create a form page resulting in a password input field.
     *
     * @param title the title of this form page. Should be unique for this
     *            Activity.
     * @return an incomplete form page suitable for use in MultiPagedForm.
     */
    public static FormPage createPasswordInputForm(String title) {
        return new FormPage(title, Type.PASSWORD_INPUT, null, null);
    }

    /**
     * Create a form page resulting in a text input field.
     *
     * @param title the title of this form page. Should be unique for this
     *            Activity.
     * @return an incomplete form page suitable for use in MultiPagedForm.
     */
    public static FormPage createTextInputForm(String title) {
        return new FormPage(title, Type.TEXT_INPUT, null, null);
    }

    /**
     * Create a form page resulting in a list of choices.
     *
     * @param title the title of this form page. Should be unique for this
     *            Activity.
     * @param formChoices the choices for this form page.
     * @return an incomplete form page suitable for use in MultiPagedForm.
     */
    public static FormPage createMultipleChoiceForm(String title, ArrayList<String> formChoices) {
        return new FormPage(title, Type.MULTIPLE_CHOICE, formChoices, null);
    }

    /**
     * Create a form page which launches an intent to get its results.
     *
     * @param title the title of this form page. Should be unique for this
     *            Activity.
     * @param formIntent the intent to launch for this form page. This intent
     *            should return the form page's results via the result intent's
     *            extras bundle. This bundle must contain the
     *            DATA_KEY_SUMMARY_STRING key as this key is used to display the
     *            results of the intent to the user.
     * @return an incomplete form page suitable for use in MultiPagedForm.
     */
    public static FormPage createIntentForm(String title, Intent formIntent) {
        return new FormPage(title, Type.INTENT, null, formIntent);
    }

    private final String mPageTitle;
    private final Type mFormType;
    private final ArrayList<String> mFormChoices;
    private final Intent mFormIntent;
    private Bundle mFormData;
    private String mError;
    private String mErrorIconUri;
    private boolean mCompleted;

    public FormPage(String pageTitle, Type formType, ArrayList<String> formChoices,
            Intent formIntent) {
        mPageTitle = pageTitle;
        mFormType = formType;
        mFormChoices = formChoices;
        mFormIntent = formIntent;
        mFormData = null;
        mError = null;
        mErrorIconUri = null;
        mCompleted = false;
    }

    public FormPage(Parcel in) {
        mPageTitle = in.readString();
        mFormType = Type.valueOf(in.readString());
        mFormChoices = new ArrayList<>();
        in.readStringList(mFormChoices);
        mFormIntent = in.readParcelable(null);
        mFormData = in.readParcelable(null);
        mError = in.readString();
        mErrorIconUri = in.readString();
        mCompleted = in.readByte() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mPageTitle);
        out.writeString(mFormType.name());
        out.writeStringList(mFormChoices);
        out.writeParcelable(mFormIntent, 0);
        out.writeParcelable(mFormData, 0);
        out.writeString(mError);
        out.writeString(mErrorIconUri);
        out.writeByte((byte) (mCompleted ? 1 : 0));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PageTitle: " + mPageTitle + "\n");
        sb.append("FormType: " + mFormType.name() + "\n");
        sb.append("FormChoices: " + mFormChoices + "\n");
        sb.append("FormIntent: " + mFormIntent + "\n");
        sb.append("FormData: " + mFormData + "\n");
        sb.append("FormError: " + mError + "\n");
        sb.append("FormErrorIconUri: " + mErrorIconUri + "\n");
        sb.append("FormCompleted: " + mCompleted + "\n");
        return sb.toString();
    }

    public String getTitle() {
        return mPageTitle;
    }

    public Bundle getData() {
        return mFormData;
    }

    public String getDataSummary() {
        return (mFormData != null && mFormData.containsKey(DATA_KEY_SUMMARY_STRING)) ? mFormData
                .getString(DATA_KEY_SUMMARY_STRING) : "";
    }

    public String getDataSecondary() {
        return (mFormData != null && mFormData.containsKey(DATA_KEY_SECONDARY_STRING))
                ? mFormData.getString(DATA_KEY_SECONDARY_STRING)
                : "";
    }

    public void clearData() {
        mFormData = null;
    }

    public String getError() {
        return mError;
    }

    public String getErrorIconUri() {
        return mErrorIconUri;
    }

    public void complete(Bundle data) {
        mFormData = data;
        mCompleted = true;
    }

    public void setError(String error, String errorIconUri) {
        mError = error;
        mErrorIconUri = errorIconUri;
        mCompleted = false;
    }

    public List<String> getChoices() {
        return mFormChoices;
    }

    Type getType() {
        return mFormType;
    }

    Intent getIntent() {
        return mFormIntent;
    }

    boolean isComplete() {
        return mCompleted;
    }
}
