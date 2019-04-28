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

import android.content.Context;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;

import com.android.emergency.ReloadablePreferenceInterface;
import com.android.internal.annotations.VisibleForTesting;

/**
 * {@link AutoCompleteEditTextPreference} that prepopulates the edit text view with the name of the
 * user provided in settings.
 */
public class NameAutoCompletePreference extends AutoCompleteEditTextPreference implements
        ReloadablePreferenceInterface {
    private static final String[] EMPTY_STRING_ARRAY = new String[] {};

    private final SuggestionProvider mSuggestionProvider;

    public NameAutoCompletePreference(Context context, AttributeSet attrs) {
        this(context, attrs, new SuggestionProvider() {
            private final UserManager mUserManager =
                    (UserManager) context.getSystemService(Context.USER_SERVICE);

            @Override
            public boolean hasNameToSuggest() {
                return mUserManager.isUserNameSet();
            }

            @Override
            public String getNameSuggestion() {
                if (!hasNameToSuggest()) {
                    return null;
                }
                return mUserManager.getUserName();
            }
        });
    }

    @VisibleForTesting
    public NameAutoCompletePreference(Context context, AttributeSet attrs,
            SuggestionProvider suggestionProvider) {
        super(context, attrs);
        mSuggestionProvider = suggestionProvider;
        getAutoCompleteTextView().setAdapter(createAdapter());
    }

    @VisibleForTesting
    public String[] createAutocompleteSuggestions() {
        if (!mSuggestionProvider.hasNameToSuggest()) {
            return EMPTY_STRING_ARRAY;
        }
        return new String[] {mSuggestionProvider.getNameSuggestion()};
    }

    private ArrayAdapter createAdapter() {
        UserManager userManager =
                (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        String[] autocompleteSuggestions = createAutocompleteSuggestions();
        return new ArrayAdapter<String>(getContext(),
                android.R.layout.simple_dropdown_item_1line, autocompleteSuggestions);
    }


    @Override
    public void reloadFromPreference() {
        setText(getPersistedString(""));
    }

    @Override
    public boolean isNotSet() {
        return TextUtils.isEmpty(getText());
    }

    @Override
    public CharSequence getSummary() {
        String text = getText();
        return TextUtils.isEmpty(text) ? super.getSummary() : text;
    }

    /**
     * Interface for suggesting a name.
     */
    public interface SuggestionProvider {
        /** @return whether this class has a name to suggest. */
        boolean hasNameToSuggest();

        /**
         * Gets a suggested name.
         * @return a suggest name, or {@code null} if there is no name to suggest.
         */
        @Nullable
        String getNameSuggestion();
    }
}
