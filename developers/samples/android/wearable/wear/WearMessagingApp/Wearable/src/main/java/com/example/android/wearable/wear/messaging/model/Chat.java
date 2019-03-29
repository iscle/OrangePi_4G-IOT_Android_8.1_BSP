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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Data representation of a chat. */
public class Chat implements Parcelable {
    private String id;
    private String alias;

    // Map using User's id as a hash for profile details for everyone in the conversation.
    private Map<String, Profile> participants;
    //TODO: remove me if not being used
    private Map<String, String> lastReadMessages;

    private Message lastMessage;

    public Chat() {
        id = UUID.randomUUID().toString();
    }

    public Chat(String id) {
        this.id = id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Map<String, Profile> getParticipants() {
        return participants;
    }

    public void setParticipants(Map<String, Profile> participants) {
        this.participants = participants;
    }

    private void generateAliasFromParticipants() {
        StringBuilder participantNames = new StringBuilder();

        // Iterates through each participants then generates a comma separated string as the alias.
        for (Profile profile : participants.values()) {
            participantNames.append(profile.getName()).append(",");
        }
        participantNames.setLength(participantNames.length() - 1); // Remove last comma (,)

        this.alias = participantNames.toString();
    }

    /**
     * Sets the participants and adds an alias. The alias is displayed in the chat list.
     *
     * @param participants chat participants
     */
    public void setParticipantsAndAlias(Map<String, Profile> participants) {
        setParticipants(participants);
        generateAliasFromParticipants();
    }

    /**
     * Sets the participants and specified alias
     *
     * @param participants chat participants
     * @param alias chat alias
     */
    public void setParticipantsAndAlias(Map<String, Profile> participants, String alias) {
        setParticipants(participants);
        this.alias = alias;
    }

    public Map<String, String> getLastReadMessages() {
        return lastReadMessages;
    }

    public void setLastReadMessages(Map<String, String> lastReadMessages) {
        this.lastReadMessages = lastReadMessages;
    }

    public void addParticipant(Profile participant) {
        participants.put(participant.getId(), participant);
    }

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.alias);
        dest.writeParcelable(this.lastMessage, 0);
        dest.writeInt(this.participants != null ? this.participants.size() : -1);
        for (Map.Entry<String, Profile> entry : this.participants.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }

        dest.writeInt(this.lastReadMessages != null ? this.lastReadMessages.size() : -1);
        if (this.lastReadMessages != null) {
            for (Map.Entry<String, String> entry : this.lastReadMessages.entrySet()) {
                dest.writeString(entry.getKey());
                dest.writeString(entry.getValue());
            }
        }
    }

    protected Chat(Parcel in) {
        this.id = in.readString();
        this.alias = in.readString();
        this.lastMessage = in.readParcelable(Message.class.getClassLoader());

        int participantsSize = in.readInt();
        if (participantsSize != -1) {
            this.participants = new HashMap<>(participantsSize);
            for (int i = 0; i < participantsSize; i++) {
                String key = in.readString();
                Profile value = in.readParcelable(Profile.class.getClassLoader());
                this.participants.put(key, value);
            }
        }

        int lastReadMessagesSize = in.readInt();
        if (lastReadMessagesSize != -1) {
            this.lastReadMessages = new HashMap<>(lastReadMessagesSize);
            for (int i = 0; i < lastReadMessagesSize; i++) {
                String key = in.readString();
                String value = in.readString();
                this.lastReadMessages.put(key, value);
            }
        }
    }

    public static final Parcelable.Creator<Chat> CREATOR =
            new Parcelable.Creator<Chat>() {
                @Override
                public Chat createFromParcel(Parcel source) {
                    return new Chat(source);
                }

                @Override
                public Chat[] newArray(int size) {
                    return new Chat[size];
                }
            };

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

        Chat chat = (Chat) other;

        if (!id.equals(chat.id)) {
            return false;
        }
        if (alias != null ? !alias.equals(chat.alias) : chat.alias != null) {
            return false;
        }
        if (!participants.equals(chat.participants)) {
            return false;
        }
        if (lastReadMessages != null
                ? !lastReadMessages.equals(chat.lastReadMessages)
                : chat.lastReadMessages != null) {
            return false;
        }
        return lastMessage != null
                ? lastMessage.equals(chat.lastMessage)
                : chat.lastMessage == null;
    }

    /**
     * Returns a hash code value for the object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + participants.hashCode();
        result = 31 * result + (lastReadMessages != null ? lastReadMessages.hashCode() : 0);
        result = 31 * result + (lastMessage != null ? lastMessage.hashCode() : 0);
        return result;
    }
}
