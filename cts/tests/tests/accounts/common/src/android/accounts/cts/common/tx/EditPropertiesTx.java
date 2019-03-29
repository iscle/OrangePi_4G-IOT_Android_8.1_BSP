package android.accounts.cts.common.tx;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class EditPropertiesTx implements Parcelable {

    public static final Parcelable.Creator<EditPropertiesTx> CREATOR =
            new Parcelable.Creator<EditPropertiesTx>() {

                @Override
                public EditPropertiesTx createFromParcel(Parcel in) {
                    return new EditPropertiesTx(in);
                }

                @Override
                public EditPropertiesTx[] newArray(int size) {
                    return new EditPropertiesTx[size];
                }
            };

    public final String accountType;
    public final Bundle result;

    private EditPropertiesTx(Parcel in) {
        accountType = in.readString();
        result = in.readBundle();
    }

    public EditPropertiesTx(
            String accountType,
            Bundle result) {
        this.accountType = accountType;
        this.result = result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(accountType);
        out.writeBundle(result);
    }
}
