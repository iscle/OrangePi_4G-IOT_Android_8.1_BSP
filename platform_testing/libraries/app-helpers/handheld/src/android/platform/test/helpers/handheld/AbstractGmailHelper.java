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

import java.util.List;

public abstract class AbstractGmailHelper extends AbstractStandardAppHelper {

    public AbstractGmailHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: Gmail is open and the navigation bar is visible.
     *
     * This method will navigate to the Inbox or Primary, depending on the name.
     */
    public abstract void goToInbox();

    /**
     * Alias method for AbstractGmailHelper#goToInbox
     */
    public void goToPrimary() {
        goToInbox();
    }

    /**
     * Setup expectations: Gmail is open on the Inbox or Primary page.
     *
     * This method will open a new e-mail to compose and block until complete.
     */
    public abstract void goToComposeEmail();

    /**
     * Checks if the current view is the compose email view.
     *
     * @return true if the current view is the compose email view, false otherwise.
     */
    public abstract boolean isInComposeEmail();

    /**
     * Checks if the app is open on the Inbox or Primary page.
     *
     * @return true if the current view is the Inbox or Primary page, false otherwise.
     */
    public abstract boolean isInPrimaryOrInbox();

    /**
     * Setup expectations: Gmail is open and on the Inbox or Primary page.
     *
     * This method will open the (index)'th visible e-mail in the list and block until the e-mail is
     * visible in the foreground. The top-most visible e-mail will always be labeled 0. To get the
     * number of visible e-mails, consult the getVisibleEmailCount() function.
     */
    public abstract void openEmailByIndex(int index);

    /**
     * Setup expectations: Gmail is open and on the Inbox or Primary page.
     *
     * This method will return the number of visible e-mails for use with the #openEmailByIndex
     * method.
     */
    public abstract int getVisibleEmailCount();

    /**
     * Setup expectations: Gmail is open and an e-mail is open in the foreground.
     *
     * This method will press reply, send a reply e-mail with the given parameters, and block until
     * the original message is in the foreground again.
     */
    public abstract void sendReplyEmail(String address, String body);

    /**
     * Setup expectations: Gmail is open and composing an e-mail.
     *
     * This method will set the e-mail's To address and block until complete.
     */
    public abstract void setEmailToAddress(String address);

    /**
     * Setup expectations: Gmail is open and composing an e-mail.
     *
     * This method will set the e-mail's subject and block until complete.
     */
    public abstract void setEmailSubject(String subject);

    /**
     * Setup expectations: Gmail is open and composing an e-mail.
     *
     * This method will set the e-mail's Body and block until complete. Focus will remain on the
     * e-mail body after completion.
     */
    public abstract void setEmailBody(String body);

    /**
     * Setup expectations: Gmail is open and composing an e-mail.
     *
     * This method will press send and block until the device is idle on the original e-mail.
     */
    public abstract void clickSendButton();

    /**
     * Setup expectations: Gmail is open and composing an e-mail.
     *
     * This method will get the e-mail's composition's body and block until complete.
     *
     * @return {String} the text contained in the email composition's body.
     */
    public abstract String getComposeEmailBody();

    /**
     * Setup expectations: Gmail is open and the navigation drawer is visible.
     *
     * This method will open the navigation drawer and block until complete.
     */
    public abstract void openNavigationDrawer();

    /**
     * Setup expectations: Gmail is open and the navigation drawer is open.
     *
     * This method will close the navigation drawer and returns true otherwise false
     */
    public abstract boolean closeNavigationDrawer();

    /**
     * Setup expectations: Gmail is open and the navigation drawer is open.
     *
     * This method will scroll the navigation drawer and block until idle. Only accepts UP and DOWN.
     */
    public abstract void scrollNavigationDrawer(Direction dir);

    /**
     * Setup expectations: Gmail is open and a mailbox is open.
     *
     * This method will scroll the mailbox view.
     *
     * @param direction     The direction to scroll, only accepts UP and DOWN.
     * @param amount        The amount to scroll
     * @param scrollToEnd   Whether or not to scroll to the end
     */
    public abstract void scrollMailbox(Direction direction, float amount, boolean scrollToEnd);

    /**
     * Setup expectations: Gmail is open and an email is open.
     *
     * This method will scroll the current email.
     *
     * @param direction     The direction to scroll, only accepts UP and DOWN.
     * @param amount        The amount to scroll
     * @param scrollToEnd   Whether or not to scroll to the end
     */
    public abstract void scrollEmail(Direction direction, float amount, boolean scrollToEnd);

    /**
     * Setup expectations: Gmail is open and the navigation drawer is open.
     *
     * This method will open the mailbox with the given name and block until emails in
     * that mailbox have loaded.
     *
     * @param mailboxName The case insensitive name of the mailbox to open
     */
    public abstract void openMailbox(String mailboxName);

    /**
     * Setup expectations: Gmail is open and an email is open.
     *
     * This method will return to the mailbox the current email was opened from.
     */
    public abstract void returnToMailbox();

    /**
     * Setup expectations: Gmail is open and an email is open.
     *
     * This method starts downloading the attachment at the specified index in the current email.
     * The download happens in the background. This method returns immediately after starting
     * the download and does not wait for the download to complete.
     *
     * @param index The index of the attachment to download
     */
    public abstract void downloadAttachment(int index);

    /**
     * Setup expectations: Gmail is open and an email is open.
     *
     * This method gets every link target in an open email by traversing the UI tree of the body
     * of the open message.
     *
     * @return an iterator over the links in the message
     */
    public abstract List<String> getEmailLinks();

    /**
     * Setup expectations: Gmail is open and an email is open.
     *
     * This method clicks the link in the open email with the given target.
     *
     * @param target the target of the link to click
     */
    public abstract void openEmailLink(String target);
}
