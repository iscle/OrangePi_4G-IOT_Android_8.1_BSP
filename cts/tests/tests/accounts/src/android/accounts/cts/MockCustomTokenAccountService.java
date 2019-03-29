package android.accounts.cts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class MockCustomTokenAccountService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return AccountManagerTest.getMockAuthenticator(this).getIBinder();
    }
}
