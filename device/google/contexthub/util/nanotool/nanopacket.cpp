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

#include "nanopacket.h"

namespace android {

constexpr uint8_t kSyncByte(0x31);

// CRC constants.
constexpr uint32_t kInitialCrc(0xffffffff);
constexpr uint32_t kCrcTable[] = {
    0x00000000, 0x04C11DB7, 0x09823B6E, 0x0D4326D9,
    0x130476DC, 0x17C56B6B, 0x1A864DB2, 0x1E475005,
    0x2608EDB8, 0x22C9F00F, 0x2F8AD6D6, 0x2B4BCB61,
    0x350C9B64, 0x31CD86D3, 0x3C8EA00A, 0x384FBDBD
};

// Computes the CRC of one word.
uint32_t Crc32Word(uint32_t crc, uint32_t data, int cnt) {
    crc = crc ^ data;

    for (int i = 0; i < cnt; i++) {
        crc = (crc << 4) ^ kCrcTable[crc >> 28];
    }

    return crc;
}

// Computes the CRC32 of a buffer given a starting CRC.
uint32_t Crc32(const uint8_t *buffer, int length) {
    int i;
    uint32_t crc = kInitialCrc;

    // Word by word crc32
    for (i = 0; i < (length >> 2); i++) {
        crc = Crc32Word(crc, ((uint32_t *)buffer)[i], 8);
    }

    // Zero pad last word if required.
    if (length & 0x3) {
        uint32_t word = 0;

        for (i*=4; i<length; i++) {
            word |= buffer[i] << ((i & 0x3) * 8);
        }

        crc = Crc32Word(crc, word, 8);
    }

    return crc;
}

NanoPacket::NanoPacket(uint32_t sequence_number, PacketReason reason,
        const std::vector<uint8_t> *data) {
    Reset();
    parsing_state_ = ParsingState::Complete;
    sequence_number_ = sequence_number;
    reason_ = static_cast<uint32_t>(reason);

    // Resize the buffer to accomodate header, footer and data content.
    size_t data_size = data ? data->size() : 0;
    size_t required_buffer_size = 14 + data_size;
    if (packet_buffer_.size() < required_buffer_size) {
        packet_buffer_.resize(required_buffer_size);
    }

    if (packet_content_.size() < required_buffer_size) {
        packet_content_.resize(required_buffer_size);
    }

    // Format the header of the packet.
    packet_buffer_[0] = kSyncByte;
    packet_buffer_[1] = sequence_number;
    packet_buffer_[2] = sequence_number >> 8;
    packet_buffer_[3] = sequence_number >> 16;
    packet_buffer_[4] = sequence_number >> 24;
    packet_buffer_[5] = static_cast<uint32_t>(reason);
    packet_buffer_[6] = static_cast<uint32_t>(reason) >> 8;
    packet_buffer_[7] = static_cast<uint32_t>(reason) >> 16;
    packet_buffer_[8] = static_cast<uint32_t>(reason) >> 24;
    packet_buffer_[9] = data_size;

    // Insert the data content of the packet.
    if (data) {
        std::copy(data->begin(), data->end(), packet_buffer_.begin() + 10);
        std::copy(data->begin(), data->end(), packet_content_.begin());
    }

    // Format the CRC footer.
    uint32_t crc = Crc32(packet_buffer_.data(), required_buffer_size - 4);
    packet_buffer_[data_size + 10] = crc;
    packet_buffer_[data_size + 11] = crc >> 8;
    packet_buffer_[data_size + 12] = crc >> 16;
    packet_buffer_[data_size + 13] = crc >> 24;
}

NanoPacket::NanoPacket() {
    Reset();
}

void NanoPacket::Reset() {
    packet_buffer_.clear();
    parsing_state_ = ParsingState::Idle;
    parsing_progress_ = 0;
    sequence_number_ = 0;
    reason_ = 0;
    packet_content_.clear();
    crc_ = 0;
}

bool NanoPacket::ParsingIsComplete() const {
    return parsing_state_ == ParsingState::Complete;
}

const std::vector<uint8_t>& NanoPacket::packet_buffer() const {
    return packet_buffer_;
}

uint32_t NanoPacket::reason() const {
    return reason_;
}

PacketReason NanoPacket::TypedReason() const {
    return static_cast<PacketReason>(reason_);
}

const std::vector<uint8_t>& NanoPacket::packet_content() const {
    return packet_content_;
}

NanoPacket::ParseResult NanoPacket::Parse(uint8_t *buffer, size_t length,
        size_t *bytes_parsed) {
    for (size_t i = 0; i < length; i++) {
        // Once the state machine is not idle, save all bytes to the current
        // packet to allow CRC to be computed at the end.
        if (parsing_state_ != ParsingState::Idle) {
            packet_buffer_.push_back(buffer[i]);
        }

        // Proceed through the various states of protocol parsing.
        if (parsing_state_ == ParsingState::Idle && buffer[i] == kSyncByte) {
            packet_buffer_.push_back(buffer[i]);
            parsing_state_ = ParsingState::ParsingSequenceNumber;
        } else if (parsing_state_ == ParsingState::ParsingSequenceNumber
                && DeserializeWord(&sequence_number_, buffer[i])) {
            parsing_state_ = ParsingState::ParsingReason;
        } else if (parsing_state_ == ParsingState::ParsingReason
                && DeserializeWord(&reason_, buffer[i])) {
            parsing_state_ = ParsingState::ParsingLength;
        } else if (parsing_state_ == ParsingState::ParsingLength) {
            uint8_t length = buffer[i];
            if (length > 0) {
                packet_content_.resize(buffer[i]);
                parsing_state_ = ParsingState::ParsingContent;
            } else {
                parsing_state_ = ParsingState::ParsingCrc;
            }
        } else if (parsing_state_ == ParsingState::ParsingContent) {
            packet_content_[parsing_progress_++] = buffer[i];

            if (parsing_progress_ == packet_content_.size()) {
                parsing_progress_ = 0;
                parsing_state_ = ParsingState::ParsingCrc;
            }
        } else if (parsing_state_ == ParsingState::ParsingCrc
                && DeserializeWord(&crc_, buffer[i])) {
            *bytes_parsed = i + 1;
            if (ValidateCrc()) {
                parsing_state_ = ParsingState::Complete;
                return ParseResult::Success;
            } else {
                return ParseResult::CrcMismatch;
            }
        }
    }

    *bytes_parsed = length;
    return ParseResult::Incomplete;
}

bool NanoPacket::ValidateCrc() {
    size_t crc_length = packet_buffer_.size() - 4;
    uint32_t computed_crc = Crc32(packet_buffer_.data(), crc_length);

    if (computed_crc != crc_) {
        Reset();
        return false;
    }

    return true;
}

}  // namespace android
