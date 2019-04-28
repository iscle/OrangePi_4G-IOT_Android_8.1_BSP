/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneNumberUtils;

public class PhoneNumberUtilsAdapterImpl implements PhoneNumberUtilsAdapter {
    @Override
    public boolean isLocalEmergencyNumber(Context context, String number) {
            return PhoneNumberUtils.isLocalEmergencyNumber(context, number);
    }

    @Override
    public boolean isPotentialLocalEmergencyNumber(Context context, String number) {
        return PhoneNumberUtils.isPotentialLocalEmergencyNumber(context, number);
    }

    @Override
    public boolean isUriNumber(String number) {
        return PhoneNumberUtils.isUriNumber(number);
    }

    @Override
    public boolean isSamePhoneNumber(String number1, String number2) {
        return PhoneNumberUtils.compare(number1, number2);
    }

    @Override
    public String getNumberFromIntent(Intent intent, Context context) {
        return PhoneNumberUtils.getNumberFromIntent(intent, context);
    }

    @Override
    public String convertKeypadLettersToDigits(String number) {
        return PhoneNumberUtils.convertKeypadLettersToDigits(number);
    }

    @Override
    public String stripSeparators(String number) {
        return PhoneNumberUtils.stripSeparators(number);
    }
}