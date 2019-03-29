package android.accounts.cts.common.tx;

import android.os.Parcel;
import android.os.Parcelable;

public class GetAuthTokenLabelTx implements Parcelable {

    public static final Parcelable.Creator<GetAuthTokenLabelTx> CREATOR =
            new Parcelable.Creator<GetAuthTokenLabelTx>() {

                @Override
                public GetAuthTokenLabelTx createFromParcel(Parcel in) {
                    return new GetAuthTokenLabelTx(in);
                }

                @Override
                public GetAuthTokenLabelTx[] newArray(int size) {
                    return new GetAuthTokenLabelTx[size];
                }
            };

    public final String authTokenType;
    public final String result;

    private GetAuthTokenLabelTx(Parcel in) {
        authTokenType = in.readString();
        result = in.readString();
    }

    public GetAuthTokenLabelTx(
            String authTokenType,
            String result) {
        this.authTokenType = authTokenType;
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(authTokenType);
        out.writeString(result);
    }
}
