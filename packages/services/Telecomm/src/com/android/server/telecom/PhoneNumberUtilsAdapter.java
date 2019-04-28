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

/**
 * Interface to avoid static calls to PhoneNumberUtils. Add methods to this interface as needed for
 * refactoring.
 */
public interface PhoneNumberUtilsAdapter {
    boolean isLocalEmergencyNumber(Context context, String number);
    boolean isPotentialLocalEmergencyNumber(Context context, String number);
    boolean isUriNumber(String number);
    boolean isSamePhoneNumber(String number1, String number2);
    String getNumberFromIntent(Intent intent, Context context);
    String convertKeypadLettersToDigits(String number);
    String stripSeparators(String number);
}
