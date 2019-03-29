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

#ifndef NANOPACKET_H_
#define NANOPACKET_H_

#include <cstddef>
#include <cstdint>
#include <vector>

#include "noncopyable.h"

namespace android {

/*
 * The various reasons for a NanoPacket to be sent.
 */
enum class PacketReason : uint32_t {
    Acknowledge        = 0x00000000,
    NAcknowledge       = 0x00000001,
    NAcknowledgeBusy   = 0x00000002,
    GetHardwareVersion = 0x00001000,
    ReadEventRequest   = 0x00001090,
    WriteEventRequest  = 0x00001091,
};

/*
 * A NanoPacket parsing engine. Used to take a stream of bytes and convert them
 * into an object that can more easily be worked with.
 */
class NanoPacket : public NonCopyable {
  public:
    /*
     * The result of parsing a buffer into the packet.
     */
    enum class ParseResult {
        Success,
        Incomplete,
        CrcMismatch,
    };

    // Formats data into NanoPacket format in the provided buffer.
    NanoPacket(uint32_t sequence_number, PacketReason reason,
        const std::vector<uint8_t> *data = nullptr);

    // Creates an empty NanoPacket for data to be parsed into.
    NanoPacket();

    // Resets the parsing engine to the idle state and clears parsed content.
    void Reset();

    // Parses content from a buffer. Returns true if a packet has been entirely
    // parsed.
    ParseResult Parse(uint8_t *buffer, size_t length, size_t *bytes_parsed);

    // Indicated that parsing of the packet has completed.
    bool ParsingIsComplete() const;

    // The entire content of the message.
    const std::vector<uint8_t>& packet_buffer() const;

    // Obtains the reason for the packet.
    uint32_t reason() const;

    // Obtains the reason as a PacketReason.
    PacketReason TypedReason() const;

    // Obtains the data content of the packet.
    const std::vector<uint8_t>& packet_content() const;

  private:
    /*
     * The current state of the parser.
     */
    enum class ParsingState {
        Idle,
        ParsingSequenceNumber,
        ParsingReason,
        ParsingLength,
        ParsingContent,
        ParsingCrc,
        Complete,
    };

    // Parsing engine state.
    std::vector<uint8_t> packet_buffer_;
    ParsingState parsing_state_;
    uint32_t parsing_progress_;

    // Parsed protocol fields.
    uint32_t sequence_number_;
    uint32_t reason_;
    std::vector<uint8_t> packet_content_;
    uint32_t crc_;

    // Validates that the received packet has a CRC that matches a generated
    // CRC.
    bool ValidateCrc();

    // Deserializes a little-endian word using the parsing_progress_ member to
    // maintain state.
    template<typename T>
    bool DeserializeWord(T *destination, uint8_t byte);
};

}  // namespace android

#include "nanopacket_impl.h"

#endif  // NANOPACKET_H_
