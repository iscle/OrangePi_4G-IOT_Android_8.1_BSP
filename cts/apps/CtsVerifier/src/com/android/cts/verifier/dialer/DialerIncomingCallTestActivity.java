/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.dialer;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.voicemail.DefaultDialerChanger;
import java.util.Observable;
import java.util.Observer;

/** Tests that a third party dialer will receive incoming calls. */
public class DialerIncomingCallTestActivity extends PassFailButtons.Activity implements Observer {

  private DefaultDialerChanger mDefaultDialerChanger;
  private ImageView mDialerIncomingCallStatusIcon;
  private TextView mDialerIncomingCallInstructions;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    View view = getLayoutInflater().inflate(R.layout.dialer_incoming_call, null);
    setContentView(view);
    setInfoResources(
        R.string.dialer_incoming_call_test, R.string.dialer_incoming_call_test_instructions, -1);
    setPassFailButtonClickListeners();
    mDialerIncomingCallStatusIcon = (ImageView) findViewById(R.id.dialer_incoming_call_status);
    mDialerIncomingCallInstructions =
        (TextView) findViewById(R.id.dialer_check_incoming_call_explanation);

    getPassButton().setEnabled(false);
    DialerCallTestService.getObservable().addObserver(this);
    mDefaultDialerChanger = new DefaultDialerChanger(this);
  }

  @Override
  public void update(Observable observable, Object data) {
    if (DialerCallTestService.getObservable().getOnIncoming()) {
      getPassButton().setEnabled(true);
      mDialerIncomingCallStatusIcon.setImageDrawable(getDrawable(R.drawable.fs_good));
      mDialerIncomingCallInstructions.setText(R.string.dialer_incoming_call_detected);
      mDefaultDialerChanger.setRestorePending(true);
    }
  }

  @Override
  protected void onDestroy() {
    mDefaultDialerChanger.destroy();
    DialerCallTestService.getObservable().deleteObserver(this);
    super.onDestroy();
  }
}
