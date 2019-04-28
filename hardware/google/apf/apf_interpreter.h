/*
 * Copyright 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef APF_INTERPRETER_H_
#define APF_INTERPRETER_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Version of APF instruction set processed by accept_packet().
 * Should be returned by wifi_get_packet_filter_info.
 */
#define APF_VERSION 2

/**
 * Runs a packet filtering program over a packet.
 *
 * @param program the program bytecode.
 * @param program_len the length of {@code apf_program} in bytes.
 * @param packet the packet bytes, starting from the 802.3 header and not
 *               including any CRC bytes at the end.
 * @param packet_len the length of {@code packet} in bytes.
 * @param filter_age the number of seconds since the filter was programmed.
 *
 * @return non-zero if packet should be passed to AP, zero if
 *         packet should be dropped.
 */
int accept_packet(const uint8_t* program, uint32_t program_len,
                  const uint8_t* packet, uint32_t packet_len,
                  uint32_t filter_age);

#ifdef __cplusplus
}
#endif

#endif  // APF_INTERPRETER_H_
