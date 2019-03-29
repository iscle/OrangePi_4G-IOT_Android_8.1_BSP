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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.voicemail.DefaultDialerChanger;
import java.util.Observable;
import java.util.Observer;

/** Tests that a third party dialer can show a heads up notification on an incoming call. */
public class DialerShowsHunOnIncomingCallActivity extends PassFailButtons.Activity
    implements Observer {

  private CheckBox mConfirmHunShown;
  private DefaultDialerChanger mDefaultDialerChanger;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    View view = getLayoutInflater().inflate(R.layout.dialer_hun_on_incoming, null);
    setContentView(view);
    setInfoResources(
        R.string.dialer_shows_hun_test, R.string.dialer_shows_hun_test_instructions, -1);
    setPassFailButtonClickListeners();
    getPassButton().setEnabled(false);

    mConfirmHunShown = findViewById(R.id.dialer_shows_hun_check_box);

    mConfirmHunShown.setOnCheckedChangeListener(
        (CompoundButton unusedButton, boolean unusedBoolean) -> onCheckedChangeListener());
    DialerCallTestService.getObservable().addObserver(this);
    mDefaultDialerChanger = new DefaultDialerChanger(this);
  }

  @Override
  public void update(Observable observable, Object data) {
    if (DialerCallTestService.getObservable().getOnIncoming()) {
      NotificationManager notificationManager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

      String id = "dialer_hun_test";
      NotificationChannel channel =
          new NotificationChannel(id, "incoming_call", NotificationManager.IMPORTANCE_MAX);
      channel.enableLights(true);
      channel.enableVibration(true);
      channel.setShowBadge(false);
      notificationManager.createNotificationChannel(channel);

      Notification notification =
          new Notification.Builder(DialerShowsHunOnIncomingCallActivity.this)
              .setContentTitle(getResources().getString(R.string.dialer_incoming_call_hun_teaser))
              .setContentText(getResources().getString(R.string.dialer_incoming_call_hun_desc))
              .setSmallIcon(R.drawable.fs_good)
              .setChannelId(id)
              .build();

      int notifyID = 1;
      notificationManager.notify(notifyID, notification);
    }
  }

  private void onCheckedChangeListener() {
    mDefaultDialerChanger.setRestorePending(true);
    if (mConfirmHunShown.isChecked()) {
      getPassButton().setEnabled(true);
    } else {
      getPassButton().setEnabled(false);
    }
  }

  @Override
  protected void onDestroy() {
    mDefaultDialerChanger.destroy();
    DialerCallTestService.getObservable().deleteObserver(this);
    super.onDestroy();
  }
}
