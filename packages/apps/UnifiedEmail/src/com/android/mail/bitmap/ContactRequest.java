/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.mail.bitmap;

import android.text.TextUtils;

import com.android.bitmap.RequestKey;
import com.android.mail.bitmap.ContactResolver.ContactDrawableInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A request object for contact images. ContactRequests have a destination because multiple
 * ContactRequests can share the same decoded data.
 */
public class ContactRequest implements RequestKey {

    private final String mName;
    private final String mEmail;

    public byte[] bytes;

    public ContactRequest(final String name, final String email) {
        mName = name;
        mEmail = normalizeEmail(email);
    }

    private String normalizeEmail(final String email) {
        if (TextUtils.isEmpty(email)) {
            throw new IllegalArgumentException("Email must not be empty.");
        }
        // todo: b/10258788
        return email;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ContactRequest that = (ContactRequest) o;

        // Only count email, so we can pull results out of the cache that are from other contact
        // requests.
        //noinspection RedundantIfStatement
        if (mEmail != null ? !mEmail.equals(that.mEmail) : that.mEmail != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        // Only count email, so we can pull results out of the cache that are from other contact
        // requests.
        return mEmail != null ? mEmail.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "[" + super.toString() + " mName=" + mName + " mEmail=" + mEmail + "]";
    }

    @Override
    public Cancelable createFileDescriptorFactoryAsync(RequestKey key, Callback callback) {
        return null;
    }

    @Override
    public InputStream createInputStream() throws IOException {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean hasOrientationExif() throws IOException {
        return false;
    }

    public String getEmail() {
        return mEmail;
    }

    public String getDisplayName() {
        return !TextUtils.isEmpty(mName) ? mName : mEmail;
    }

    /**
     * This ContactRequest wrapper provides implementations of equals() and hashcode() that
     * include the destination. We need to put multiple ContactRequests in a set,
     * but its implementations of equals() and hashcode() don't include the destination.
     */
    public static class ContactRequestHolder {

        public final ContactRequest contactRequest;
        public final ContactDrawableInterface destination;

        public ContactRequestHolder(final ContactRequest contactRequest,
                final ContactDrawableInterface destination) {
            this.contactRequest = contactRequest;
            this.destination = destination;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ContactRequestHolder that = (ContactRequestHolder) o;

            if (contactRequest != null ? !contactRequest.equals(that.contactRequest)
                    : that.contactRequest != null) {
                return false;
            }
            //noinspection RedundantIfStatement
            if (destination != null ? !destination.equals(that.destination)
                    : that.destination != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = contactRequest != null ? contactRequest.hashCode() : 0;
            result = 31 * result + (destination != null ? destination.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return contactRequest.toString();
        }

        public String getEmail() {
            return contactRequest.getEmail();
        }

        public String getDisplayName() {
            return contactRequest.getDisplayName();
        }
    }
}
