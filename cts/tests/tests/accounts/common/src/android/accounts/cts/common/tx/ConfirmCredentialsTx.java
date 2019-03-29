package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class ConfirmCredentialsTx implements Parcelable {

    public static final Parcelable.Creator<ConfirmCredentialsTx> CREATOR =
            new Parcelable.Creator<ConfirmCredentialsTx>() {

                @Override
                public ConfirmCredentialsTx createFromParcel(Parcel in) {
                    return new ConfirmCredentialsTx(in);
                }

                @Override
                public ConfirmCredentialsTx[] newArray(int size) {
                    return new ConfirmCredentialsTx[size];
                }
            };

    public final Account account;
    public final Bundle options;
    public final Bundle result;

    private ConfirmCredentialsTx(Parcel in) {
        account = in.readParcelable(null);
        options = in.readBundle();
        result = in.readBundle();
    }

    public ConfirmCredentialsTx(
            Account account,
            Bundle options,
            Bundle result) {
        this.account = account;
        this.options = options;
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(account, flags);
        out.writeBundle(options);
        out.writeBundle(result);
    }
}
