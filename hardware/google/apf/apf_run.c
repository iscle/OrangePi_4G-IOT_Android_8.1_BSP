/*
 * Copyright 2016, The Android Open Source Project
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

// Simple program to try running an APF program against a packet.

#include <libgen.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "apf_interpreter.h"

// Parses hex in "input". Allocates and fills "*output" with parsed bytes.
// Returns length in bytes of "*output".
int parse_hex(char* input, uint8_t** output) {
    int length = strlen(input);
    if (length & 1) {
        fprintf(stderr, "Argument not even number of characters: %s\n", input);
        exit(1);
    }
    length >>= 1;
    *output = malloc(length);
    if (*output == NULL) {
        fprintf(stderr, "Out of memory, tried to allocate %d\n", length);
        exit(1);
    }
    for (int i = 0; i < length; i++) {
        char byte[3] = { input[i*2], input[i*2+1], 0 };
        char* end_ptr;
        (*output)[i] = strtol(byte, &end_ptr, 16);
        if (end_ptr != byte + 2) {
            fprintf(stderr, "Failed to parse hex %s\n", byte);
            exit(1);
        }
    }
    return length;
}

int main(int argc, char* argv[]) {
    if (argc != 4) {
        fprintf(stderr,
                "Usage: %s <program> <packet> <program age>\n"
                "  program:     APF program, in hex\n"
                "  packet:      Packet to run through program, in hex\n"
                "  program age: Age of program in seconds.\n",
                basename(argv[0]));
        exit(1);
    }
    uint8_t* program;
    uint32_t program_len = parse_hex(argv[1], &program);
    uint8_t* packet;
    uint32_t packet_len = parse_hex(argv[2], &packet);
    uint32_t filter_age = atoi(argv[3]);
    int ret = accept_packet(program, program_len, packet, packet_len,
                            filter_age);
    printf("Packet %sed\n", ret ? "pass" : "dropp");
    free(program);
    free(packet);
    return ret;
}