package android.accounts.cts.common.tx;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class AddAccountTx implements Parcelable {

    public static final Parcelable.Creator<AddAccountTx> CREATOR =
            new Parcelable.Creator<AddAccountTx>() {

                @Override
                public AddAccountTx createFromParcel(Parcel in) {
                    return new AddAccountTx(in);
                }

                @Override
                public AddAccountTx[] newArray(int size) {
                    return new AddAccountTx[size];
                }
            };

    public final String accountType;
    public final String authTokenType;
    public final List<String> requiredFeatures = new ArrayList<>();
    public final Bundle options;
    public final Bundle result;

    private AddAccountTx(Parcel in) {
        accountType = in.readString();
        authTokenType = in.readString();
        in.readStringList(requiredFeatures);
        options = in.readBundle();
        result = in.readBundle();
    }

    public AddAccountTx(
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options,
            Bundle result) {
        this.accountType = accountType;
        this.authTokenType = authTokenType;
        if (requiredFeatures != null) {
            for (String feature : requiredFeatures) {
                this.requiredFeatures.add(feature);
            }
        }
        this.options = options;
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(accountType);
        out.writeString(authTokenType);
        out.writeStringList(requiredFeatures);
        out.writeBundle(options);
        out.writeBundle(result);
    }
}
