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

import android.annotation.Nullable;
import android.content.res.ColorStateList;
import android.support.annotation.VisibleForTesting;

import com.android.managedprovisioning.R;
import com.android.setupwizardlib.GlifLayout;

/**
 * Base class for setting up the layout.
 */
public abstract class SetupGlifLayoutActivity extends SetupLayoutActivity {
    public SetupGlifLayoutActivity() {
        super();
    }

    @VisibleForTesting
    protected SetupGlifLayoutActivity(Utils utils) {
        super(utils);
    }

    protected void initializeLayoutParams(int layoutResourceId, @Nullable Integer headerResourceId,
            boolean showProgressBar, int mainColor) {
        setContentView(layoutResourceId);
        GlifLayout layout = (GlifLayout) findViewById(R.id.setup_wizard_layout);

        setMainColor(mainColor);
        layout.setPrimaryColor(ColorStateList.valueOf(mainColor));

        if (headerResourceId != null) {
            layout.setHeaderText(headerResourceId);
        }

        if (showProgressBar) {
            layout.setProgressBarShown(true);
        }

        layout.setIcon(LogoUtils.getOrganisationLogo(this, mainColor));
    }
}