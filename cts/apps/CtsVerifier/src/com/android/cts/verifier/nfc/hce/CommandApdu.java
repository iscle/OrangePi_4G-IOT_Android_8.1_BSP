package com.android.cts.verifier.nfc.hce;

import android.os.Parcel;
import android.os.Parcelable;

public class CommandApdu implements Parcelable {
    private String mApdu;
    private boolean mReachable;

    public CommandApdu(String apdu, boolean reachable) {
        mApdu = apdu;
        mReachable = reachable;
    }

    public boolean isReachable() {
        return mReachable;
    }

    public String getApdu() {
        return mApdu;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CommandApdu> CREATOR =
            new Parcelable.Creator<CommandApdu>() {
        @Override
        public CommandApdu createFromParcel(Parcel source) {
            String apdu = source.readString();
            boolean reachable = source.readInt() != 0 ? true : false;
            return new CommandApdu(apdu, reachable);
        }

        @Override
        public CommandApdu[] newArray(int size) {
            return new CommandApdu[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mApdu);
        dest.writeInt(mReachable ? 1 : 0);
    }

}
