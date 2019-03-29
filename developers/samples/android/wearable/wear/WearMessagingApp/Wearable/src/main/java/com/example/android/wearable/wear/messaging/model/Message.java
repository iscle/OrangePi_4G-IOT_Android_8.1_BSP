/*
 * Copyright 2017 Google Inc.
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
package com.example.android.wearable.wear.messaging.model;

import android.os.Parcel;
import android.os.Parcelable;

/** Data representation of each message. Stores the sender, text content, and the sent time. */
public class Message implements Parcelable {
    private String id;
    private String senderId;
    private String name;
    private String text;
    private long sentTime;

    public Message() {}

    public Message(Message message, String name) {
        senderId = message.getSenderId();
        this.name = name;
        text = message.getText();
    }

    private Message(Builder builder) {
        id = builder.id;
        senderId = builder.senderId;
        text = builder.text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    public long getSentTime() {
        return sentTime;
    }

    public void setSentTime(long sentTime) {
        this.sentTime = sentTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Message Builder */
    public static final class Builder {
        private String id;
        private String senderId;
        private String text;

        public Builder() {}

        public Builder id(String val) {
            id = val;
            return this;
        }

        public Builder senderId(String val) {
            senderId = val;
            return this;
        }

        public Builder text(String val) {
            text = val;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param other - the reference object with which to compare.
     * @return true/false based on all fields being equal or equally null
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Message message = (Message) other;

        if (sentTime != message.sentTime) {
            return false;
        }
        if (id != null ? !id.equals(message.id) : message.id != null) {
            return false;
        }
        if (!senderId.equals(message.senderId)) {
            return false;
        }
        if (name != null ? !name.equals(message.name) : message.name != null) {
            return false;
        }
        return text != null ? text.equals(message.text) : message.text == null;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + senderId.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (int) (sentTime ^ (sentTime >>> 32));
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.senderId);
        dest.writeString(this.name);
        dest.writeString(this.text);
        dest.writeLong(this.sentTime);
    }

    protected Message(Parcel in) {
        this.senderId = in.readString();
        this.name = in.readString();
        this.text = in.readString();
        this.sentTime = in.readLong();
    }

    public static final Creator<Message> CREATOR =
            new Creator<Message>() {
                @Override
                public Message createFromParcel(Parcel source) {
                    return new Message(source);
                }

                @Override
                public Message[] newArray(int size) {
                    return new Message[size];
                }
            };
}
