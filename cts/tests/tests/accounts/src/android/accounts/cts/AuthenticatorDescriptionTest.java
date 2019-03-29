package android.accounts.cts;

import android.accounts.AuthenticatorDescription;

import junit.framework.TestCase;

public class AuthenticatorDescriptionTest extends TestCase {

    private String accountType = "com.my.auth";
    private String packageName = "com.android.my.app";
    private AuthenticatorDescription desc;

    @Override
    public void setUp() throws Exception {
        desc = new AuthenticatorDescription(accountType, packageName, 1, 1, 1, 1, true);
    }

    public void testObjectCreationWithNullAccountType() {
        try {
            new AuthenticatorDescription(null, packageName, 1, 1, 1, 1);
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    public void testAccountObjectCreationWithNullPackageName() {
        try {
            new AuthenticatorDescription(accountType, null, 1, 1, 1, 1);
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    public void testObjectCreation() {
        new AuthenticatorDescription(accountType, packageName, -1, 0, -1, 1);
        new AuthenticatorDescription(accountType, packageName, -1, 0, -1, 1, true);
        // no error signifies success.
    }

    public void testDescribeContents() {
        assertEquals(0, desc.describeContents());
    }

    public void testNewKey() {
        AuthenticatorDescription desc = AuthenticatorDescription.newKey(accountType);
        assertEquals(desc.type, accountType);
    }
}
