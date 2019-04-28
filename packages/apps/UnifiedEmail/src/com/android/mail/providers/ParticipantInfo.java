/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.providers;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;

public class ParticipantInfo implements Parcelable {

    /** the pretty name of the participant if one exists */
    public String name;

    /** the email address of the participant if one exists */
    public String email;

    /** priority of the participant */
    public int priority;

    /** {@code true} if the participant has read the entire conversation; {@code false} otherwise */
    public boolean readConversation;

    public ParticipantInfo(String name, String email, int priority, boolean readConversation) {
        this.name = name;
        this.email = email;
        this.priority = priority;
        this.readConversation = readConversation;
    }

    public boolean markRead(boolean isRead) {
        if (readConversation != isRead) {
            readConversation = isRead;
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, email, priority, readConversation);
    }

    public static final Creator<ParticipantInfo> CREATOR = new Creator<ParticipantInfo>() {
        @Override
        public ParticipantInfo createFromParcel(Parcel parcel) {
            return new ParticipantInfo(parcel);
        }

        @Override
        public ParticipantInfo[] newArray(int size) {
            return new ParticipantInfo[size];
        }
    };

    public ParticipantInfo(Parcel in) {
        name = in.readString();
        email = in.readString();
        priority = in.readInt();
        readConversation = in.readInt() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(name);
        out.writeString(email);
        out.writeInt(priority);
        out.writeInt(readConversation ? 1 : 0);
    }

    @Override
    public String toString() {
        return "[ParticipantInfo: readConversation = " + readConversation +
                ", name = " + name +
                ", email = " + email +
                ", priority = " + priority +
                "]";
    }
}