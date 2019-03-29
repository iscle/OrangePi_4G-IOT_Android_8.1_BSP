package android.cts.backup.sharedprefrestoreapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * Test activity that verifies SharedPreference restore behavior.
 * The activity lifecycle is driven by KeyValueBackupRestoreTest and is roughly the following:
 *
 * 1. This activity is launched; it creates a new SharedPreferences instance and writes
 *       a known value to the INT_PREF element's via that instance.  The instance is
 *       kept live.
 *   2. The app is backed up, storing this known value in the backup dataset.
 *   3. Next, the activity is instructed to write a different value to the INT_PREF
 *       shared preferences element.  At this point, the app's current on-disk state
 *       and the live shared preferences instance are in agreement, holding a value
 *       different from that in the backup.
 *   4. The runner triggers a restore for this app.  This will rewrite the shared prefs
 *       file itself with the backed-up content (i.e. different from what was just
 *       committed from this activity).
 *   5. Finally, the runner instructs the activity to compare the value of its existing
 *       shared prefs instance's INT_PREF element with what was previously written.
 *       The test passes if these differ, i.e. if the live shared prefs instance picked
 *       up the newly-restored data.
 *
 */
public class SharedPrefsRestoreTestActivity extends Activity {
    static final String TAG = "SharedPrefsTest";

    // Shared prefs test activity actions
    static final String INIT_ACTION = "android.backup.cts.backuprestore.INIT";
    static final String UPDATE_ACTION = "android.backup.cts.backuprestore.UPDATE";
    static final String TEST_ACTION = "android.backup.cts.backuprestore.TEST";

    static final String RESULT_ACTION = "android.backup.cts.backuprestore.RESULT";

    private static final String TEST_PREFS_1 = "test-prefs-1";
    private static final String INT_PREF = "int-pref";
    private static final int INT_PREF_VALUE = 1;
    private static final int INT_PREF_MODIFIED_VALUE = 99999;
    private static final int INT_PREF_DEFAULT_VALUE = 0;
    private static final String EXTRA_SUCCESS = "EXTRA_SUCCESS";

    SharedPreferences mPrefs;
    int mLastValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        mLastValue = 0;

        processLaunchCommand(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        processLaunchCommand(intent);
    }

    private void processLaunchCommand(Intent intent) {
        final String action = intent.getAction();
        Log.i(TAG, "processLaunchCommand: " + action);

        boolean success = false;

        if (INIT_ACTION.equals(action)) {
            // We'll issue a backup after setting this value in shared prefs
            success = setPrefValue(INT_PREF_VALUE);
        } else if (UPDATE_ACTION.equals(action)) {
            // We'll issue a *restore* after setting this value, which will send a broadcast
            // to our receiver to read from the live instance and ensure that the value is
            // different from what was just written.
            success = setPrefValue(INT_PREF_MODIFIED_VALUE);
        } else if (TEST_ACTION.equals(action)) {
            final int currentValue = mPrefs.getInt(INT_PREF, INT_PREF_DEFAULT_VALUE);
            Log.i(TAG, "current value: " + currentValue + " last value : " + mLastValue);

            if (currentValue != mLastValue && currentValue != INT_PREF_DEFAULT_VALUE) {
                success = true;
            }
        } else {
            // Should never happen
            Log.e(TAG, "Unexpected intent action");
            return;
        }

        sendBroadcast(new Intent().setAction(RESULT_ACTION).putExtra(EXTRA_SUCCESS, success));
    }

    // Write a known value prior to backup
    private boolean setPrefValue(int value) {
        Log.i(TAG, "mLastValue = " + mLastValue + " setPrefValue: " + value );
        mLastValue = value;
        boolean success = mPrefs.edit().putInt(INT_PREF, value).commit();
        Log.i(TAG, "setPrefValue success: " + success);
        return success;
    }
}
