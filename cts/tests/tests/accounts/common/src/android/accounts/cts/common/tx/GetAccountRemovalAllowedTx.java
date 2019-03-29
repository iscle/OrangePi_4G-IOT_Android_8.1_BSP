package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class GetAccountRemovalAllowedTx implements Parcelable {

    public static final Parcelable.Creator<GetAccountRemovalAllowedTx> CREATOR =
            new Parcelable.Creator<GetAccountRemovalAllowedTx>() {

                @Override
                public GetAccountRemovalAllowedTx createFromParcel(Parcel in) {
                    return new GetAccountRemovalAllowedTx(in);
                }

                @Override
                public GetAccountRemovalAllowedTx[] newArray(int size) {
                    return new GetAccountRemovalAllowedTx[size];
                }
            };

    public final Account account;
    public final Bundle result;

    private GetAccountRemovalAllowedTx(Parcel in) {
        account = in.readParcelable(null);
        result = in.readBundle();
    }

    public GetAccountRemovalAllowedTx(
            Account account,
            Bundle result) {
        this.account = account;
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(account, flags);
        out.writeBundle(result);
    }
}
