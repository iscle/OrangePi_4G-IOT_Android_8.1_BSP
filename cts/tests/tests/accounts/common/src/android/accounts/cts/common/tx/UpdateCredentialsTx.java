package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class UpdateCredentialsTx implements Parcelable {

    public static final Parcelable.Creator<UpdateCredentialsTx> CREATOR =
            new Parcelable.Creator<UpdateCredentialsTx>() {

                @Override
                public UpdateCredentialsTx createFromParcel(Parcel in) {
                    return new UpdateCredentialsTx(in);
                }

                @Override
                public UpdateCredentialsTx[] newArray(int size) {
                    return new UpdateCredentialsTx[size];
                }
            };

    public final Account account;
    public final String authTokenType;
    public final Bundle options;
    public final Bundle result;

    private UpdateCredentialsTx(Parcel in) {
        account = in.readParcelable(null);
        authTokenType = in.readString();
        options = in.readBundle();
        result = in.readBundle();
    }

    public UpdateCredentialsTx(
            Account account,
            String authTokenType,
            Bundle options,
            Bundle result) {
        this.account = account;
        this.authTokenType = authTokenType;
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
        out.writeString(authTokenType);
        out.writeBundle(options);
        out.writeBundle(result);
    }
}
