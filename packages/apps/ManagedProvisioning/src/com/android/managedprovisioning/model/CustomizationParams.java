/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.model;

import android.annotation.Nullable;
import android.content.Context;
import android.webkit.URLUtil;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;

/**
 * Captures parameters related to brand customization (e.g. tint color).
 */
public class CustomizationParams {
    @VisibleForTesting public static final int DEFAULT_COLOR_ID_MP = R.color.gray_status_bar;
    @VisibleForTesting public static final int DEFAULT_COLOR_ID_DO = R.color.blue;
    @VisibleForTesting public static final int DEFAULT_COLOR_ID_BUTTON = R.color.blue;
    @VisibleForTesting public static final int DEFAULT_COLOR_ID_SWIPER = R.color.teal2;

    /** Status bar color */
    public final int statusBarColor;

    /** Animation swiper color */
    public final int swiperColor;

    /** 'Accept & Continue' button color */
    public final int buttonColor;

    /** Name of the organization where the device is being provisioned. */
    public final @Nullable String orgName;

    /** Support url of the organization where the device is being provisioned. */
    public final @Nullable String supportUrl;

    /**
     * Computes an instance from {@link ProvisioningParams} and required helper classes.
     * @param params {@link ProvisioningParams} instance to compute the values from
     * @param context {@link Context} instance to resolve color ids
     * @param utils {@link Utils} instance to determine the provisioning mode
     */
    public static CustomizationParams createInstance(ProvisioningParams params, Context context,
            Utils utils) {
        boolean isProfileOwner = utils.isProfileOwnerAction(params.provisioningAction);

        int statusBarColor, swiperColor, buttonColor;
        if (params.mainColor != null) {
            statusBarColor = swiperColor = buttonColor = params.mainColor;
        } else {
            statusBarColor = context.getColor(
                    isProfileOwner ? DEFAULT_COLOR_ID_MP : DEFAULT_COLOR_ID_DO);
            swiperColor = context.getColor(DEFAULT_COLOR_ID_SWIPER);
            buttonColor = context.getColor(DEFAULT_COLOR_ID_BUTTON);
        }

        String supportUrl = URLUtil.isNetworkUrl(params.supportUrl) ? params.supportUrl : null;

        return new CustomizationParams(statusBarColor, swiperColor, buttonColor,
                params.organizationName, supportUrl);
    }

    private CustomizationParams(int statusBarColor, int swiperColor, int buttonColor,
            String orgName, String supportUrl) {
        this.statusBarColor = statusBarColor;
        this.swiperColor = swiperColor;
        this.buttonColor = buttonColor;
        this.orgName = orgName;
        this.supportUrl = supportUrl;
    }
}