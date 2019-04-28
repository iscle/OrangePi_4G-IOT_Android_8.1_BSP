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
import android.support.test.uiautomator.Direction;

public abstract class AbstractGoogleMessengerHelper extends AbstractStandardAppHelper {

    public AbstractGoogleMessengerHelper(Instrumentation instr) {
        super(instr);
    }

    /*
     * Setup expectations: Google Messenger app is open.
     *
     * This method brings the Messenger app to the home page.
     */
    public abstract void goToHomePage();

    /*
     * Setup expectation: Google Messenger app is on the home page.
     *
     * This method brings up the new conversation page.
     */
    public abstract void goToNewConversationPage();

    /*
     * Setup expectations: Google Messenger app is on the new conversation page.
     *
     * This method moves the Google Messenger app to messages page with the specified contacts.
     */
    public abstract void goToMessagesPage();

    /**
     * Setup expectations: Google Messenger app is on the messages page.
     *
     * This method scrolls through the messages on the messages page.
     *
     * @param direction Direction to scroll, must be UP or DOWN.
     */
    public abstract void scrollMessages(Direction direction);

    /**
     * Setup expectations: Google Messenger app is on the messages page.
     *
     * This method clicks the "send message" textbox on the messages page.
     */
    public abstract void clickComposeMessageText();

    /**
     * Setup expectations:
     *   1. Google Messenger app is on the messages page
     *   2. New message textbox is not empty.
     */
    public abstract void clickSendMessageButton();

    /**
     * Setup expectations: Google Messenger app is on the messages page.
     *
     * This method clicks the "attach media" button and attaches the media file with the given
     * index in the device media gallery view.
     */
    public abstract void attachMediaFromDevice(int index);
}
