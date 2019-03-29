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
import android.support.annotation.NonNull;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

/** Represents a user profile. Parcelable to pass between activities. */
public class Profile implements Parcelable {

    private String id;
    private String email;
    private String name;
    private String profileImageUri;
    private int profileImageResource;
    private Long lastUpdatedTime;

    public Profile() {}

    public Profile(@NonNull GoogleSignInAccount account) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.name = account.getDisplayName();
        this.profileImageUri =
                (account.getPhotoUrl() != null) ? account.getPhotoUrl().toString() : null;
        this.lastUpdatedTime = System.currentTimeMillis();
    }

    public Profile(String id, String name, String imageUri) {
        this.id = id;
        this.name = name;
        this.profileImageUri = imageUri;
        this.lastUpdatedTime = System.currentTimeMillis();
    }

    private Profile(Builder builder) {
        setId(builder.id);
        setEmail(builder.email);
        setName(builder.name);
        setProfileImageResource(builder.profileImageResource);
        setLastUpdatedTime(builder.lastUpdatedTime);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getProfileImageResource() {
        return profileImageResource;
    }

    public void setProfileImageResource(int profileImageResource) {
        this.profileImageResource = profileImageResource;
    }

    public void setProfileImageUri(String profileImageUri) {
        this.profileImageUri = profileImageUri;
    }

    public String getProfileImageUri() {
        return profileImageUri;
    }

    public Object getProfileImageSource() {
        if (profileImageUri != null) {
            return profileImageUri;
        }
        if (profileImageResource > 0) {
            return profileImageResource;
        }
        return null;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    public void setLastUpdatedTime(Long lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
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

        Profile profile = (Profile) other;

        if (!id.equals(profile.id)) {
            return false;
        }
        if (email != null ? !email.equals(profile.email) : profile.email != null) {
            return false;
        }
        if (!name.equals(profile.name)) {
            return false;
        }
        if (profileImageUri != null && !profileImageUri.equals(profile.profileImageUri)) {
            return false;
        }
        return lastUpdatedTime != null
                ? lastUpdatedTime.equals(profile.lastUpdatedTime)
                : profile.lastUpdatedTime == null;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + name.hashCode();
        result = 31 * result + profileImageUri.hashCode();
        result = 31 * result + (lastUpdatedTime != null ? lastUpdatedTime.hashCode() : 0);
        return result;
    }

    /** Builder crates profiles. */
    public static final class Builder {
        private String id;
        private String email;
        private String name;
        private int profileImageResource;
        private Long lastUpdatedTime;

        public Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder profileImageResource(int profileImageResource) {
            this.profileImageResource = profileImageResource;
            return this;
        }

        public Builder lastUpdatedTime(Long lastUpdatedTime) {
            this.lastUpdatedTime = lastUpdatedTime;
            return this;
        }

        public Profile build() {
            return new Profile(this);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.email);
        dest.writeString(this.name);
        dest.writeString(this.profileImageUri);
        dest.writeInt(this.profileImageResource);
        dest.writeValue(this.lastUpdatedTime);
    }

    protected Profile(Parcel in) {
        this.id = in.readString();
        this.email = in.readString();
        this.name = in.readString();
        this.profileImageUri = in.readString();
        this.profileImageResource = in.readInt();
        this.lastUpdatedTime = (Long) in.readValue(Long.class.getClassLoader());
    }

    public static final Creator<Profile> CREATOR =
            new Creator<Profile>() {
                @Override
                public Profile createFromParcel(Parcel source) {
                    return new Profile(source);
                }

                @Override
                public Profile[] newArray(int size) {
                    return new Profile[size];
                }
            };
}
