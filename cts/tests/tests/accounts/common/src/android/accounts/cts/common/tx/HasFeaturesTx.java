package android.accounts.cts.common.tx;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class HasFeaturesTx implements Parcelable {

    public static final Parcelable.Creator<HasFeaturesTx> CREATOR =
            new Parcelable.Creator<HasFeaturesTx>() {

                @Override
                public HasFeaturesTx createFromParcel(Parcel in) {
                    return new HasFeaturesTx(in);
                }

                @Override
                public HasFeaturesTx[] newArray(int size) {
                    return new HasFeaturesTx[size];
                }
            };

    public final Account account;
    public final List<String> features = new ArrayList<>();
    public final Bundle result;

    private HasFeaturesTx(Parcel in) {
        account = in.readParcelable(null);
        in.readStringList(features);
        result = in.readBundle();
    }

    public HasFeaturesTx(
            Account account,
            String[] features,
            Bundle result) {
        this.account = account;
        if (features != null) {
            for (String feature : features) {
                this.features.add(feature);
            }
        }
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(account, flags);
        out.writeStringList(features);
        out.writeBundle(result);
    }
}
