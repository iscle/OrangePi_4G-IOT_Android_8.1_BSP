package android.accounts.cts.common.tx;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class StartAddAccountSessionTx implements Parcelable {

    public static final Parcelable.Creator<StartAddAccountSessionTx> CREATOR =
            new Parcelable.Creator<StartAddAccountSessionTx>() {

        @Override
        public StartAddAccountSessionTx createFromParcel(Parcel in) {
            return new StartAddAccountSessionTx(in);
        }

        @Override
        public StartAddAccountSessionTx[] newArray(int size) {
            return new StartAddAccountSessionTx[size];
        }
    };

    public final String accountType;
    public final String authTokenType;
    public final List<String> requiredFeatures = new ArrayList<>();
    public final Bundle options;

    private StartAddAccountSessionTx(Parcel in) {
        accountType = in.readString();
        authTokenType = in.readString();
        in.readStringList(requiredFeatures);
        options = in.readBundle();
    }

    public StartAddAccountSessionTx(
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options) {
        this.accountType = accountType;
        this.authTokenType = authTokenType;
        if (requiredFeatures != null) {
            for (String feature : requiredFeatures) {
                this.requiredFeatures.add(feature);
            }
        }
        this.options = options;
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
    }
}
