/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.autofillservice.cts;

import static android.autofillservice.cts.Helper.getAutofillIds;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.CustomDescription;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveInfo;
import android.service.autofill.Validator;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Helper class used to produce a {@link FillResponse} based on expected fields that should be
 * present in the {@link AssistStructure}.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
 *               .addDataset(new CannedDataset.Builder("dataset_name")
 *                   .setField("resource_id1", AutofillValue.forText("value1"))
 *                   .setField("resource_id2", AutofillValue.forText("value2"))
 *                   .build())
 *               .build());
 * </pre class="prettyprint">
 */
final class CannedFillResponse {

    private final ResponseType mResponseType;
    private final List<CannedDataset> mDatasets;
    private final String mFailureMessage;
    private final int mSaveType;
    private final Validator mValidator;
    private final String[] mRequiredSavableIds;
    private final String[] mOptionalSavableIds;
    private final String mSaveDescription;
    private final CustomDescription mCustomDescription;
    private final Bundle mExtras;
    private final RemoteViews mPresentation;
    private final IntentSender mAuthentication;
    private final String[] mAuthenticationIds;
    private final String[] mIgnoredIds;
    private final int mNegativeActionStyle;
    private final IntentSender mNegativeActionListener;
    private final int mFlags;

    private CannedFillResponse(Builder builder) {
        mResponseType = builder.mResponseType;
        mDatasets = builder.mDatasets;
        mFailureMessage = builder.mFailureMessage;
        mValidator = builder.mValidator;
        mRequiredSavableIds = builder.mRequiredSavableIds;
        mOptionalSavableIds = builder.mOptionalSavableIds;
        mSaveDescription = builder.mSaveDescription;
        mCustomDescription = builder.mCustomDescription;
        mSaveType = builder.mSaveType;
        mExtras = builder.mExtras;
        mPresentation = builder.mPresentation;
        mAuthentication = builder.mAuthentication;
        mAuthenticationIds = builder.mAuthenticationIds;
        mIgnoredIds = builder.mIgnoredIds;
        mNegativeActionStyle = builder.mNegativeActionStyle;
        mNegativeActionListener = builder.mNegativeActionListener;
        mFlags = builder.mFlags;
    }

    /**
     * Constant used to pass a {@code null} response to the
     * {@link FillCallback#onSuccess(FillResponse)} method.
     */
    static final CannedFillResponse NO_RESPONSE =
            new Builder(ResponseType.NULL).build();

    /**
     * Constant used to emulate a timeout by not calling any method on {@link FillCallback}.
     */
    static final CannedFillResponse DO_NOT_REPLY_RESPONSE =
            new Builder(ResponseType.TIMEOUT).build();


    String getFailureMessage() {
        return mFailureMessage;
    }

    ResponseType getResponseType() {
        return mResponseType;
    }

    /**
     * Creates a new response, replacing the dataset field ids by the real ids from the assist
     * structure.
     */
    FillResponse asFillResponse(Function<String, ViewNode> nodeResolver) {
        final FillResponse.Builder builder = new FillResponse.Builder();
        if (mDatasets != null) {
            for (CannedDataset cannedDataset : mDatasets) {
                final Dataset dataset = cannedDataset.asDataset(nodeResolver);
                assertWithMessage("Cannot create datase").that(dataset).isNotNull();
                builder.addDataset(dataset);
            }
        }
        if (mRequiredSavableIds != null) {
            final SaveInfo.Builder saveInfo =
                    mRequiredSavableIds == null || mRequiredSavableIds.length == 0
                        ? new SaveInfo.Builder(mSaveType)
                            : new SaveInfo.Builder(mSaveType,
                                    getAutofillIds(nodeResolver, mRequiredSavableIds));

            saveInfo.setFlags(mFlags);

            if (mValidator != null) {
                saveInfo.setValidator(mValidator);
            }
            if (mOptionalSavableIds != null) {
                saveInfo.setOptionalIds(getAutofillIds(nodeResolver, mOptionalSavableIds));
            }
            if (mSaveDescription != null) {
                saveInfo.setDescription(mSaveDescription);
            }
            saveInfo.setNegativeAction(mNegativeActionStyle, mNegativeActionListener);

            if (mCustomDescription != null) {
                saveInfo.setCustomDescription(mCustomDescription);
            }
            builder.setSaveInfo(saveInfo.build());
        }
        if (mIgnoredIds != null) {
            builder.setIgnoredIds(getAutofillIds(nodeResolver, mIgnoredIds));
        }
        if (mAuthenticationIds != null) {
            builder.setAuthentication(getAutofillIds(nodeResolver, mAuthenticationIds),
                    mAuthentication, mPresentation);
        }
        return builder
                .setClientState(mExtras)
                .build();
    }

    @Override
    public String toString() {
        return "CannedFillResponse: [type=" + mResponseType
                + ",datasets=" + mDatasets
                + ", requiredSavableIds=" + Arrays.toString(mRequiredSavableIds)
                + ", optionalSavableIds=" + Arrays.toString(mOptionalSavableIds)
                + ", flags=" + mFlags
                + ", failureMessage=" + mFailureMessage
                + ", saveDescription=" + mSaveDescription
                + ", mCustomDescription=" + mCustomDescription
                + ", hasPresentation=" + (mPresentation != null)
                + ", hasAuthentication=" + (mAuthentication != null)
                + ", authenticationIds=" + Arrays.toString(mAuthenticationIds)
                + ", ignoredIds=" + Arrays.toString(mIgnoredIds)
                + "]";
    }

    enum ResponseType {
        NORMAL,
        NULL,
        TIMEOUT
    }

    static class Builder {
        private final List<CannedDataset> mDatasets = new ArrayList<>();
        private final ResponseType mResponseType;
        private String mFailureMessage;
        private Validator mValidator;
        private String[] mRequiredSavableIds;
        private String[] mOptionalSavableIds;
        private String mSaveDescription;
        public CustomDescription mCustomDescription;
        public int mSaveType = -1;
        private Bundle mExtras;
        private RemoteViews mPresentation;
        private IntentSender mAuthentication;
        private String[] mAuthenticationIds;
        private String[] mIgnoredIds;
        private int mNegativeActionStyle;
        private IntentSender mNegativeActionListener;
        private int mFlags;

        public Builder(ResponseType type) {
            mResponseType = type;
        }

        public Builder() {
            this(ResponseType.NORMAL);
        }

        public Builder addDataset(CannedDataset dataset) {
            assertWithMessage("already set failure").that(mFailureMessage).isNull();
            mDatasets.add(dataset);
            return this;
        }

        /**
         * Sets the validator for this request
         */
        public Builder setValidator(Validator validator) {
            mValidator = validator;
            return this;
        }

        /**
         * Sets the required savable ids based on they {@code resourceId}.
         */
        public Builder setRequiredSavableIds(int type, String... ids) {
            mSaveType = type;
            mRequiredSavableIds = ids;
            return this;
        }

        public Builder setFlags(int flags) {
            mFlags = flags;
            return this;
        }

        /**
         * Sets the optional savable ids based on they {@code resourceId}.
         */
        public Builder setOptionalSavableIds(String... ids) {
            mOptionalSavableIds = ids;
            return this;
        }

        /**
         * Sets the description passed to the {@link SaveInfo}.
         */
        public Builder setSaveDescription(String description) {
            mSaveDescription = description;
            return this;
        }

        /**
         * Sets the description passed to the {@link SaveInfo}.
         */
        public Builder setCustomDescription(CustomDescription description) {
            mCustomDescription = description;
            return this;
        }

        /**
         * Sets the extra passed to {@link
         * android.service.autofill.FillResponse.Builder#setClientState(Bundle)}.
         */
        public Builder setExtras(Bundle data) {
            mExtras = data;
            return this;
        }

        /**
         * Sets the view to present the response in the UI.
         */
        public Builder setPresentation(RemoteViews presentation) {
            mPresentation = presentation;
            return this;
        }

        /**
         * Sets the authentication intent.
         */
        public Builder setAuthentication(IntentSender authentication, String... ids) {
            mAuthenticationIds = ids;
            mAuthentication = authentication;
            return this;
        }

        /**
         * Sets the ignored fields based on resource ids.
         */
        public Builder setIgnoreFields(String...ids) {
            mIgnoredIds = ids;
            return this;
        }

        /**
         * Sets the negative action spec.
         */
        public Builder setNegativeAction(int style, IntentSender listener) {
            mNegativeActionStyle = style;
            mNegativeActionListener = listener;
            return this;
        }

        public CannedFillResponse build() {
            return new CannedFillResponse(this);
        }

        /**
         * Sets the response to call {@link FillCallback#onFailure(CharSequence)}.
         */
        public Builder returnFailure(String message) {
            assertWithMessage("already added datasets").that(mDatasets).isEmpty();
            mFailureMessage = message;
            return this;
        }
    }

    /**
     * Helper class used to produce a {@link Dataset} based on expected fields that should be
     * present in the {@link AssistStructure}.
     *
     * <p>Typical usage:
     *
     * <pre class="prettyprint">
     * InstrumentedAutoFillService.setFillResponse(new CannedFillResponse.Builder()
     *               .addDataset(new CannedDataset.Builder("dataset_name")
     *                   .setField("resource_id1", AutofillValue.forText("value1"))
     *                   .setField("resource_id2", AutofillValue.forText("value2"))
     *                   .build())
     *               .build());
     * </pre class="prettyprint">
     */
    static class CannedDataset {
        private final Map<String, AutofillValue> mFieldValues;
        private final Map<String, RemoteViews> mFieldPresentations;
        private final RemoteViews mPresentation;
        private final IntentSender mAuthentication;
        private final String mId;

        private CannedDataset(Builder builder) {
            mFieldValues = builder.mFieldValues;
            mFieldPresentations = builder.mFieldPresentations;
            mPresentation = builder.mPresentation;
            mAuthentication = builder.mAuthentication;
            mId = builder.mId;
        }

        /**
         * Creates a new dataset, replacing the field ids by the real ids from the assist structure.
         */
        Dataset asDataset(Function<String, ViewNode> nodeResolver) {
            final Dataset.Builder builder = (mPresentation == null)
                    ? new Dataset.Builder()
                    : new Dataset.Builder(mPresentation);

            if (mFieldValues != null) {
                for (Map.Entry<String, AutofillValue> entry : mFieldValues.entrySet()) {
                    final String id = entry.getKey();
                    final ViewNode node = nodeResolver.apply(id);
                    if (node == null) {
                        throw new AssertionError("No node with resource id " + id);
                    }
                    final AutofillId autofillid = node.getAutofillId();
                    final AutofillValue value = entry.getValue();
                    final RemoteViews presentation = mFieldPresentations.get(id);
                    if (presentation != null) {
                        builder.setValue(autofillid, value, presentation);
                    } else {
                        builder.setValue(autofillid, value);
                    }
                }
            }
            builder.setId(mId).setAuthentication(mAuthentication);
            return builder.build();
        }

        @Override
        public String toString() {
            return "CannedDataset " + mId + " : [hasPresentation=" + (mPresentation != null)
                    + ", fieldPresentations=" + (mFieldPresentations)
                    + ", hasAuthentication=" + (mAuthentication != null)
                    + ", fieldValues=" + mFieldValues + "]";
        }

        static class Builder {
            private final Map<String, AutofillValue> mFieldValues = new HashMap<>();
            private final Map<String, RemoteViews> mFieldPresentations = new HashMap<>();
            private RemoteViews mPresentation;
            private IntentSender mAuthentication;
            private String mId;

            public Builder() {

            }

            public Builder(RemoteViews presentation) {
                mPresentation = presentation;
            }

            /**
             * Sets the canned value of a text field based on its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, String text) {
                return setField(id, AutofillValue.forText(text));
            }

            /**
             * Sets the canned value of a list field based on its its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, int index) {
                return setField(id, AutofillValue.forList(index));
            }

            /**
             * Sets the canned value of a toggle field based on its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, boolean toggled) {
                return setField(id, AutofillValue.forToggle(toggled));
            }

            /**
             * Sets the canned value of a date field based on its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, long date) {
                return setField(id, AutofillValue.forDate(date));
            }

            /**
             * Sets the canned value of a date field based on its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, AutofillValue value) {
                mFieldValues.put(id, value);
                return this;
            }

            /**
             * Sets the canned value of a field based on its {@code id}.
             *
             * <p>The meaning of the id is defined by the object using the canned dataset.
             * For example, {@link InstrumentedAutoFillService.Replier} resolves the id based on
             * {@link IdMode}.
             */
            public Builder setField(String id, String text, RemoteViews presentation) {
                setField(id, text);
                mFieldPresentations.put(id, presentation);
                return this;
            }

            /**
             * Sets the view to present the response in the UI.
             */
            public Builder setPresentation(RemoteViews presentation) {
                mPresentation = presentation;
                return this;
            }

            /**
             * Sets the authentication intent.
             */
            public Builder setAuthentication(IntentSender authentication) {
                mAuthentication = authentication;
                return this;
            }

            /**
             * Sets the name.
             */
            public Builder setId(String id) {
                mId = id;
                return this;
            }

            public CannedDataset build() {
                return new CannedDataset(this);
            }
        }
    }
}
