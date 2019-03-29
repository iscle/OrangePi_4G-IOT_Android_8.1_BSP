package android.accounts.cts;

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.cts.common.Fixtures;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity used by {@link android.accounts.cts.MockAccountAuthenticator} to test the
 * behavior of {@link AccountManager} when authenticator returns intent.
 */
public class AccountAuthenticatorDummyActivity extends Activity {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        AccountAuthenticatorResponse response = intent.getParcelableExtra(Fixtures.KEY_CALLBACK);
        Intent result = intent.getParcelableExtra(Fixtures.KEY_RESULT);
        if (response != null) {
            response.onResult(result.getExtras());
        }
        setResult(RESULT_OK, result);
        finish();
    }
}
