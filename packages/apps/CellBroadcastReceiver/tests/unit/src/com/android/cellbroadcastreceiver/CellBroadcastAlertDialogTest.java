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
 * limitations under the License
 */

package com.android.cellbroadcastreceiver;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.telephony.CellBroadcastMessage;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CellBroadcastAlertDialogTest extends
        CellBroadcastActivityTestCase<CellBroadcastAlertDialog> {

    @Mock
    private NotificationManager mMockedNotificationManager;

    @Mock
    private IPowerManager.Stub mMockedPowerManagerService;

    @Captor
    private ArgumentCaptor<Integer> mInt;

    @Captor
    private ArgumentCaptor<Notification> mNotification;

    private PowerManager mPowerManager;

    public CellBroadcastAlertDialogTest() {
        super(CellBroadcastAlertDialog.class);
    }

    @Override
    protected Intent createActivityIntent() {
        ArrayList<CellBroadcastMessage> messageList = new ArrayList<>(1);
        messageList.add(new CellBroadcastMessage(
                CellBroadcastAlertServiceTest.createMessage(12412)));

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                        CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastMessage.SMS_CB_MESSAGE_EXTRA,
                        messageList);
        return intent;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        injectSystemService(NotificationManager.class, mMockedNotificationManager);
        // PowerManager is a final class so we can't use Mockito to mock it, but we can mock
        // its underlying service.
        doReturn(true).when(mMockedPowerManagerService).isInteractive();
        mPowerManager = new PowerManager(mContext, mMockedPowerManagerService, null);
        injectSystemService(PowerManager.class, mPowerManager);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testTitleAndMessageText() throws Throwable {
        startActivity();
        waitForMs(100);

        CharSequence etremeAlertString =
                getActivity().getResources().getText(R.string.cmas_extreme_alert);
        assertEquals(etremeAlertString, getActivity().getTitle());
        assertEquals(etremeAlertString,
                ((TextView) getActivity().findViewById(R.id.alertTitle)).getText());

        assertEquals(CellBroadcastAlertServiceTest.createMessage(34596).getMessageBody(),
                (String) ((TextView) getActivity().findViewById(R.id.message)).getText());

        stopActivity();
    }

    public void testAddToNotification() throws Throwable {
        startActivity();
        waitForMs(100);
        stopActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(1)).notify(mInt.capture(),
                mNotification.capture());
        Bundle b = mNotification.getValue().extras;

        assertEquals(1, (int) mInt.getValue());

        assertEquals(getActivity().getTitle(), b.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals(CellBroadcastAlertServiceTest.createMessage(98235).getMessageBody(),
                b.getCharSequence(Notification.EXTRA_TEXT));
    }
}