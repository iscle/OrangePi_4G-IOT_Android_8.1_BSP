package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class StartUpdateCredentialsSessionTx implements Parcelable {

    public static final Parcelable.Creator<StartUpdateCredentialsSessionTx> CREATOR =
            new Parcelable.Creator<StartUpdateCredentialsSessionTx>() {

                @Override
                public StartUpdateCredentialsSessionTx createFromParcel(Parcel in) {
                    return new StartUpdateCredentialsSessionTx(in);
                }

                @Override
                public StartUpdateCredentialsSessionTx[] newArray(int size) {
                    return new StartUpdateCredentialsSessionTx[size];
                }
            };

    public final Account account;
    public final String authTokenType;
    public final Bundle options;

    private StartUpdateCredentialsSessionTx(Parcel in) {
        account = in.readParcelable(null);
        authTokenType = in.readString();
        options = in.readBundle();
    }

    public StartUpdateCredentialsSessionTx(
            Account account,
            String authTokenType,
            Bundle options) {
        this.account = account;
        this.authTokenType = authTokenType;
        this.options = options;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(account, flags);
        out.writeString(authTokenType);
        out.writeBundle(options);
    }
}
