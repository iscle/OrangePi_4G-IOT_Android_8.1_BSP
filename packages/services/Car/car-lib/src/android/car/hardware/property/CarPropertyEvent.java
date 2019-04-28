/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.car.hardware.property;

import android.car.hardware.CarPropertyValue;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class CarPropertyEvent implements Parcelable {
    public static final int PROPERTY_EVENT_PROPERTY_CHANGE = 0;
    public static final int PROPERTY_EVENT_ERROR = 1;

    /**
     * EventType of this message
     */
    private final int mEventType;
    private final CarPropertyValue<?> mCarPropertyValue;

    // Getters.

    /**
     * @return EventType field
     */
    public int getEventType() { return mEventType; }

    /**
     * Returns {@link CarPropertyValue} associated with this event.
     */
    public CarPropertyValue<?> getCarPropertyValue() { return mCarPropertyValue; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
        dest.writeParcelable(mCarPropertyValue, flags);
    }

    public static final Parcelable.Creator<CarPropertyEvent> CREATOR
            = new Parcelable.Creator<CarPropertyEvent>() {
        public CarPropertyEvent createFromParcel(Parcel in) {
            return new CarPropertyEvent(in);
        }

        public CarPropertyEvent[] newArray(int size) {
            return new CarPropertyEvent[size];
        }
    };

    /**
     * Constructor for {@link CarPropertyEvent}.
     */
    public CarPropertyEvent(int eventType, CarPropertyValue<?> carPropertyValue) {
        mEventType  = eventType;
        mCarPropertyValue = carPropertyValue;
    }

    private CarPropertyEvent(Parcel in) {
        mEventType  = in.readInt();
        mCarPropertyValue = in.readParcelable(CarPropertyValue.class.getClassLoader());
    }

    @Override
    public String toString() {
        return "CarPropertyEvent{" +
                "mEventType=" + mEventType +
                ", mCarPropertyValue=" + mCarPropertyValue +
                '}';
    }
}
