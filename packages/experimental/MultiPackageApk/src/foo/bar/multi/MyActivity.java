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

package foo.bar.multi;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;
import foo.bar.multi.parent.R;

import java.util.List;

public class MyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.my_activity);

        String processName = null;
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningProcess : runningProcesses) {
            if (runningProcess.uid == Process.myUid()) {
                processName = runningProcess.processName;
                break;
            }
        }

        TextView title = (TextView) findViewById(foo.bar.multi.parent.R.id.text);
        title.setText("Process: " + processName);
    }
}
