package android.accounts.cts;

import android.accounts.Account;
import android.os.Parcel;

import junit.framework.TestCase;

public class AccountTest extends TestCase {

    private Account account;

    @Override
    public void setUp() throws Exception {
        account = new Account("abc@xyz.org", "com.my.auth");
    }

    public void testAccountObjectCreationWithNullName() {
        try {
            new Account(null, "com.my.auth");
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    public void testAccountObjectCreationWithNullAccountType() {
        try {
            new Account("abc@xyz.org", null);
            fail();
        } catch (IllegalArgumentException expectedException) {
        }
    }

    public void testDescribeContents() {
        assertEquals(0, account.describeContents());
    }

    public void testWriteToParcel() {
        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        account.writeToParcel(parcel, 0);
        // Reset the position to initial.
        parcel.setDataPosition(0);
        // Create a new account object from just populated parcel,
        // and verify it is equivalent to the original account.
        Account newAccount = new Account(parcel);
        assertEquals(account, newAccount);
    }

}
