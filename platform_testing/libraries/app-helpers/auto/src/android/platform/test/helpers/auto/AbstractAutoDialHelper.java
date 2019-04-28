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

package android.platform.test.helpers;

import android.app.Instrumentation;

public abstract class AbstractAutoDialHelper extends AbstractStandardAppHelper {

    public AbstractAutoDialHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: The app is open and the drawer is open
     *
     * This method is used to dial the phonenumber on dialpad
     *
     * @param phoneNumber  phone number to dial.
     */
    public abstract void dialANumber(String phoneNumber);

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * This method is used to end call.
     */
    public abstract void endCall();

    /**
     * Setup expectations: The app is open and the drawer is open.
     *
     * This method is used to open call history details.
     */
    public abstract void openCallHistory();

    /**
     * Setup expectations: The app is open and the drawer is open.
     *
     * This method is used to open missed call details.
     */
    public abstract void openMissedCall();

    /**
     * Setup expectations: The app is open and in "Dial a number" drawer option
     *
     * This method is used to delete the number entered on dialpad using backspace
     */
    public abstract void deleteDialedNumber();

    /**
     * Setup expectations: The app is open and in "Dial a number" drawer option
     *
     * This method is used to get the number entered on dialpad
     */
    public abstract String getDialedNumber();

    /**
     * Setup expectations: The app is open and there is an ongoing call.
     *
     * This method is used to get the name of the contact for the ongoing call
     */
    public abstract String getDialedContactName();

    /**
     * Setup expectations: The app is open and phonenumber is entered on the dialpad
     *
     * This method is used to make a call
     *
     */
    public abstract void makeCall();

    /**
     * Setup expectations: The app is open
     *
     * This method is used to dial a number from call history, missed call[s], recent
     * call[s] list
     *
     * @param phoneNumber  phoneNumber to be dialed
     */
    public abstract void dialNumberFromList(String phoneNumber);

    /**
     * Setup expectations: The app is open and there is an ongoing call
     *
     * This method is used to enter number on the in-call dialpad
     */
    public abstract void inCallDialPad(String phoneNumber);

    /**
     * Setup expectations: The app is open and there is an ongoing call
     *
     * This method is used to mute the ongoing call
     */
    public abstract void muteCall();

    /**
     * Setup expectations: The app is open and there is an ongoing call
     *
     * This method is used to unmute the ongoing call
     */
    public abstract void unmuteCall();
}
