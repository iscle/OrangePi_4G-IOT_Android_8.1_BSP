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

package android.telephony.cts;

import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailService;
import android.telephony.VisualVoicemailSms;

import java.util.concurrent.CompletableFuture;

/**
 * A mock {@link VisualVoicemailService} that the tests can set callbacks
 */
public class MockVisualVoicemailService extends VisualVoicemailService {

    private static CompletableFuture<VisualVoicemailSms> sSmsFuture;

    public static void setSmsFuture(CompletableFuture<VisualVoicemailSms> future) {
        sSmsFuture = future;
    }

    @Override
    public void onCellServiceConnected(VisualVoicemailTask task,
            PhoneAccountHandle phoneAccountHandle) {
        // Do nothing, cannot be tested by automatic CTS
    }

    @Override
    public void onSmsReceived(VisualVoicemailTask task, VisualVoicemailSms sms) {
        if (sSmsFuture != null) {
            sSmsFuture.complete(sms);
        }
    }

    @Override
    public void onSimRemoved(VisualVoicemailTask task, PhoneAccountHandle phoneAccountHandle) {
        // Do nothing, cannot be tested by automatic CTS
    }

    @Override
    public void onStopped(VisualVoicemailTask task) {
        // TODO(twyen): test after task timeout is implemented.
    }
}
