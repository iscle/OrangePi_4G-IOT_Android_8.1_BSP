package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class GetAuthTokenTx implements Parcelable {

    public static final Parcelable.Creator<GetAuthTokenTx> CREATOR =
            new Parcelable.Creator<GetAuthTokenTx>() {

                @Override
                public GetAuthTokenTx createFromParcel(Parcel in) {
                    return new GetAuthTokenTx(in);
                }

                @Override
                public GetAuthTokenTx[] newArray(int size) {
                    return new GetAuthTokenTx[size];
                }
            };

    public final Account account;
    public final String authTokenType;
    public final Bundle options;
    public final Bundle result;

    private GetAuthTokenTx(Parcel in) {
        account = in.readParcelable(null);
        authTokenType = in.readString();
        options = in.readBundle();
        result = in.readBundle();
    }

    public GetAuthTokenTx(
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
