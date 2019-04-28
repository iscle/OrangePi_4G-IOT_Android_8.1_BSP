/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.services.telephony;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConferenceParticipant;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;

/**
 * Represents a participant in a conference call.
 */
public class ConferenceParticipantConnection extends Connection {
    /**
     * RFC5767 states that a SIP URI with an unknown number should use an address of
     * {@code anonymous@anonymous.invalid}.  E.g. the host name is anonymous.invalid.
     */
    private static final String ANONYMOUS_INVALID_HOST = "anonymous.invalid";

    /**
     * The user entity URI For the conference participant.
     */
    private final Uri mUserEntity;

    /**
     * The endpoint URI For the conference participant.
     */
    private final Uri mEndpoint;

    /**
     * The connection which owns this participant.
     */
    private final com.android.internal.telephony.Connection mParentConnection;

    /**
     * Creates a new instance.
     *
     * @param participant The conference participant to create the instance for.
     */
    public ConferenceParticipantConnection(
            com.android.internal.telephony.Connection parentConnection,
            ConferenceParticipant participant) {

        mParentConnection = parentConnection;

        int presentation = getParticipantPresentation(participant);
        Uri address;
        if (presentation != PhoneConstants.PRESENTATION_ALLOWED) {
            address = null;
        } else {
            String countryIso = getCountryIso(parentConnection.getCall().getPhone());
            address = getParticipantAddress(participant.getHandle(), countryIso);
        }
        setAddress(address, presentation);
        setCallerDisplayName(participant.getDisplayName(), presentation);

        mUserEntity = participant.getHandle();
        mEndpoint = participant.getEndpoint();

        setCapabilities();
    }

    /**
     * Changes the state of the conference participant.
     *
     * @param newState The new state.
     */
    public void updateState(int newState) {
        Log.v(this, "updateState endPoint: %s state: %s", Log.pii(mEndpoint),
                Connection.stateToString(newState));
        if (newState == getState()) {
            return;
        }

        switch (newState) {
            case STATE_INITIALIZING:
                setInitializing();
                break;
            case STATE_RINGING:
                setRinging();
                break;
            case STATE_DIALING:
                setDialing();
                break;
            case STATE_HOLDING:
                setOnHold();
                break;
            case STATE_ACTIVE:
                setActive();
                break;
            case STATE_DISCONNECTED:
                setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
                destroy();
                break;
            default:
                setActive();
        }
    }

    /**
     * Disconnects the current {@code ConferenceParticipantConnection} from the conference.
     * <p>
     * Sends a participant disconnect signal to the associated parent connection.  The participant
     * connection is not disconnected and cleaned up here.  On successful disconnection of the
     * participant, the conference server will send an update to the conference controller
     * indicating the disconnection was successful.
     */
    @Override
    public void onDisconnect() {
        mParentConnection.onDisconnectConferenceParticipant(mUserEntity);
    }

    /**
     * Retrieves the user handle for this connection.
     *
     * @return The userEntity.
     */
    public Uri getUserEntity() {
        return mUserEntity;
    }

    /**
     * Retrieves the endpoint for this connection.
     *
     * @return The endpoint.
     */
    public Uri getEndpoint() {
        return mEndpoint;
    }

    /**
     * Configures the capabilities applicable to this connection.  A
     * conference participant can only be disconnected from a conference since there is not
     * actual connection to the participant which could be split from the conference.
     */
    private void setCapabilities() {
        int capabilities = CAPABILITY_DISCONNECT_FROM_CONFERENCE;
        setConnectionCapabilities(capabilities);
    }

    /**
     * Determines the number presentation for a conference participant.  Per RFC5767, if the host
     * name contains {@code anonymous.invalid} we can assume that there is no valid caller ID
     * information for the caller, otherwise we'll assume that the URI can be shown.
     *
     * @param participant The conference participant.
     * @return The number presentation.
     */
    private int getParticipantPresentation(ConferenceParticipant participant) {
        Uri address = participant.getHandle();
        if (address == null) {
            return PhoneConstants.PRESENTATION_RESTRICTED;
        }

        String number = address.getSchemeSpecificPart();
        // If no number, bail early and set restricted presentation.
        if (TextUtils.isEmpty(number)) {
            return PhoneConstants.PRESENTATION_RESTRICTED;
        }
        // Per RFC3261, the host name portion can also potentially include extra information:
        // E.g. sip:anonymous1@anonymous.invalid;legid=1
        // In this case, hostName will be anonymous.invalid and there is an extra parameter for
        // legid=1.
        // Parameters are optional, and the address (e.g. test@test.com) will always be the first
        // part, with any parameters coming afterwards.
        String hostParts[] = number.split("[;]");
        String addressPart = hostParts[0];

        // Get the number portion from the address part.
        // This will typically be formatted similar to: 6505551212@test.com
        String numberParts[] = addressPart.split("[@]");

        // If we can't parse the host name out of the URI, then there is probably other data
        // present, and is likely a valid SIP URI.
        if (numberParts.length != 2) {
            return PhoneConstants.PRESENTATION_ALLOWED;
        }
        String hostName = numberParts[1];

        // If the hostname portion of the SIP URI is the invalid host string, presentation is
        // restricted.
        if (hostName.equals(ANONYMOUS_INVALID_HOST)) {
            return PhoneConstants.PRESENTATION_RESTRICTED;
        }

        return PhoneConstants.PRESENTATION_ALLOWED;
    }

    /**
     * Attempts to build a tel: style URI from a conference participant.
     * Conference event package data contains SIP URIs, so we try to extract the phone number and
     * format into a typical tel: style URI.
     *
     * @param address The conference participant's address.
     * @param countryIso The country ISO of the current subscription; used when formatting the
     *                   participant phone number to E.164 format.
     * @return The participant's address URI.
     */
    @VisibleForTesting
    public static Uri getParticipantAddress(Uri address, String countryIso) {
        if (address == null) {
            return address;
        }
        // Even if address is already in tel: format, still parse it and rebuild.
        // This is to recognize tel URIs such as:
        // tel:6505551212;phone-context=ims.mnc012.mcc034.3gppnetwork.org

        // Conference event package participants are identified using SIP URIs (see RFC3261).
        // A valid SIP uri has the format: sip:user:password@host:port;uri-parameters?headers
        // Per RFC3261, the "user" can be a telephone number.
        // For example: sip:1650555121;phone-context=blah.com@host.com
        // In this case, the phone number is in the user field of the URI, and the parameters can be
        // ignored.
        //
        // A SIP URI can also specify a phone number in a format similar to:
        // sip:+1-212-555-1212@something.com;user=phone
        // In this case, the phone number is again in user field and the parameters can be ignored.
        // We can get the user field in these instances by splitting the string on the @, ;, or :
        // and looking at the first found item.
        String number = address.getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            return address;
        }

        String numberParts[] = number.split("[@;:]");
        if (numberParts.length == 0) {
            return address;
        }
        number = numberParts[0];

        // Attempt to format the number in E.164 format and use that as part of the TEL URI.
        // RFC2806 recommends to format telephone numbers using E.164 since it is independent of
        // how the dialing of said numbers takes place.
        // If conversion to E.164 fails, the returned value is null.  In that case, fallback to the
        // number which was in the CEP data.
        String formattedNumber = null;
        if (!TextUtils.isEmpty(countryIso)) {
            formattedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
        }

        return Uri.fromParts(PhoneAccount.SCHEME_TEL,
                formattedNumber != null ? formattedNumber : number, null);
    }

    /**
     * Given a {@link Phone} instance, determines the country ISO associated with the phone's
     * subscription.
     *
     * @param phone The phone instance.
     * @return The country ISO.
     */
    private String getCountryIso(Phone phone) {
        if (phone == null) {
            return null;
        }

        int subId = phone.getSubId();

        SubscriptionInfo subInfo = TelecomAccountRegistry.getInstance(null).
                getSubscriptionManager().getActiveSubscriptionInfo(subId);

        if (subInfo == null) {
            return null;
        }
        // The SubscriptionInfo reports ISO country codes in lower case.  Convert to upper case,
        // since ultimately we use this ISO when formatting the CEP phone number, and the phone
        // number formatting library expects uppercase ISO country codes.
        return subInfo.getCountryIso().toUpperCase();
    }

    /**
     * Builds a string representation of this conference participant connection.
     *
     * @return String representation of connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ConferenceParticipantConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" endPoint:");
        sb.append(Log.pii(mEndpoint));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" addressPresentation:");
        sb.append(getAddressPresentation());
        sb.append(" parentConnection:");
        sb.append(Log.pii(mParentConnection.getAddress()));
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append("]");

        return sb.toString();
    }
}
