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

#include "apf_interpreter.h"

#include <string.h> // For memcmp

#include "apf.h"

// Return code indicating "packet" should accepted.
#define PASS_PACKET 1
// Return code indicating "packet" should be dropped.
#define DROP_PACKET 0
// Verify an internal condition and accept packet if it fails.
#define ASSERT_RETURN(c) if (!(c)) return PASS_PACKET
// If "c" is of an unsigned type, generate a compile warning that gets promoted to an error.
// This makes bounds checking simpler because ">= 0" can be avoided. Otherwise adding
// superfluous ">= 0" with unsigned expressions generates compile warnings.
#define ENFORCE_UNSIGNED(c) ((c)==(uint32_t)(c))

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
                  uint32_t filter_age) {
// Is offset within program bounds?
#define IN_PROGRAM_BOUNDS(p) (ENFORCE_UNSIGNED(p) && (p) < program_len)
// Is offset within packet bounds?
#define IN_PACKET_BOUNDS(p) (ENFORCE_UNSIGNED(p) && (p) < packet_len)
// Accept packet if not within program bounds
#define ASSERT_IN_PROGRAM_BOUNDS(p) ASSERT_RETURN(IN_PROGRAM_BOUNDS(p))
// Accept packet if not within packet bounds
#define ASSERT_IN_PACKET_BOUNDS(p) ASSERT_RETURN(IN_PACKET_BOUNDS(p))
  // Program counter.
  uint32_t pc = 0;
// Accept packet if not within program or not ahead of program counter
#define ASSERT_FORWARD_IN_PROGRAM(p) ASSERT_RETURN(IN_PROGRAM_BOUNDS(p) && (p) >= pc)
  // Memory slot values.
  uint32_t memory[MEMORY_ITEMS] = {};
  // Fill in pre-filled memory slot values.
  memory[MEMORY_OFFSET_PACKET_SIZE] = packet_len;
  memory[MEMORY_OFFSET_FILTER_AGE] = filter_age;
  ASSERT_IN_PACKET_BOUNDS(APF_FRAME_HEADER_SIZE);
  // Only populate if IP version is IPv4.
  if ((packet[APF_FRAME_HEADER_SIZE] & 0xf0) == 0x40) {
      memory[MEMORY_OFFSET_IPV4_HEADER_SIZE] = (packet[APF_FRAME_HEADER_SIZE] & 15) * 4;
  }
  // Register values.
  uint32_t registers[2] = {};
  // Count of instructions remaining to execute. This is done to ensure an
  // upper bound on execution time. It should never be hit and is only for
  // safety. Initialize to the number of bytes in the program which is an
  // upper bound on the number of instructions in the program.
  uint32_t instructions_remaining = program_len;

  do {
      if (pc == program_len) {
          return PASS_PACKET;
      } else if (pc == (program_len + 1)) {
          return DROP_PACKET;
      }
      ASSERT_IN_PROGRAM_BOUNDS(pc);
      const uint8_t bytecode = program[pc++];
      const uint32_t opcode = EXTRACT_OPCODE(bytecode);
      const uint32_t reg_num = EXTRACT_REGISTER(bytecode);
#define REG (registers[reg_num])
#define OTHER_REG (registers[reg_num ^ 1])
      // All instructions have immediate fields, so load them now.
      const uint32_t len_field = EXTRACT_IMM_LENGTH(bytecode);
      uint32_t imm = 0;
      int32_t signed_imm = 0;
      if (len_field != 0) {
          const uint32_t imm_len = 1 << (len_field - 1);
          ASSERT_FORWARD_IN_PROGRAM(pc + imm_len - 1);
          uint32_t i;
          for (i = 0; i < imm_len; i++)
              imm = (imm << 8) | program[pc++];
          // Sign extend imm into signed_imm.
          signed_imm = imm << ((4 - imm_len) * 8);
          signed_imm >>= (4 - imm_len) * 8;
      }
      switch (opcode) {
          case LDB_OPCODE:
          case LDH_OPCODE:
          case LDW_OPCODE:
          case LDBX_OPCODE:
          case LDHX_OPCODE:
          case LDWX_OPCODE: {
              uint32_t offs = imm;
              if (opcode >= LDBX_OPCODE) {
                  // Note: this can overflow and actually decrease offs.
                  offs += registers[1];
              }
              ASSERT_IN_PACKET_BOUNDS(offs);
              uint32_t load_size;
              switch (opcode) {
                  case LDB_OPCODE:
                  case LDBX_OPCODE:
                    load_size = 1;
                    break;
                  case LDH_OPCODE:
                  case LDHX_OPCODE:
                    load_size = 2;
                    break;
                  case LDW_OPCODE:
                  case LDWX_OPCODE:
                    load_size = 4;
                    break;
                  // Immediately enclosing switch statement guarantees
                  // opcode cannot be any other value.
              }
              const uint32_t end_offs = offs + (load_size - 1);
              // Catch overflow/wrap-around.
              ASSERT_RETURN(end_offs >= offs);
              ASSERT_IN_PACKET_BOUNDS(end_offs);
              uint32_t val = 0;
              while (load_size--)
                  val = (val << 8) | packet[offs++];
              REG = val;
              break;
          }
          case JMP_OPCODE:
              // This can jump backwards. Infinite looping prevented by instructions_remaining.
              pc += imm;
              break;
          case JEQ_OPCODE:
          case JNE_OPCODE:
          case JGT_OPCODE:
          case JLT_OPCODE:
          case JSET_OPCODE:
          case JNEBS_OPCODE: {
              // Load second immediate field.
              uint32_t cmp_imm = 0;
              if (reg_num == 1) {
                  cmp_imm = registers[1];
              } else if (len_field != 0) {
                  uint32_t cmp_imm_len = 1 << (len_field - 1);
                  ASSERT_FORWARD_IN_PROGRAM(pc + cmp_imm_len - 1);
                  uint32_t i;
                  for (i = 0; i < cmp_imm_len; i++)
                      cmp_imm = (cmp_imm << 8) | program[pc++];
              }
              switch (opcode) {
                  case JEQ_OPCODE:
                      if (registers[0] == cmp_imm)
                          pc += imm;
                      break;
                  case JNE_OPCODE:
                      if (registers[0] != cmp_imm)
                          pc += imm;
                      break;
                  case JGT_OPCODE:
                      if (registers[0] > cmp_imm)
                          pc += imm;
                      break;
                  case JLT_OPCODE:
                      if (registers[0] < cmp_imm)
                          pc += imm;
                      break;
                  case JSET_OPCODE:
                      if (registers[0] & cmp_imm)
                          pc += imm;
                      break;
                  case JNEBS_OPCODE: {
                      // cmp_imm is size in bytes of data to compare.
                      // pc is offset of program bytes to compare.
                      // imm is jump target offset.
                      // REG is offset of packet bytes to compare.
                      ASSERT_FORWARD_IN_PROGRAM(pc + cmp_imm - 1);
                      ASSERT_IN_PACKET_BOUNDS(REG);
                      const uint32_t last_packet_offs = REG + cmp_imm - 1;
                      ASSERT_RETURN(last_packet_offs >= REG);
                      ASSERT_IN_PACKET_BOUNDS(last_packet_offs);
                      if (memcmp(program + pc, packet + REG, cmp_imm))
                          pc += imm;
                      // skip past comparison bytes
                      pc += cmp_imm;
                      break;
                  }
              }
              break;
          }
          case ADD_OPCODE:
              registers[0] += reg_num ? registers[1] : imm;
              break;
          case MUL_OPCODE:
              registers[0] *= reg_num ? registers[1] : imm;
              break;
          case DIV_OPCODE: {
              const uint32_t div_operand = reg_num ? registers[1] : imm;
              ASSERT_RETURN(div_operand);
              registers[0] /= div_operand;
              break;
          }
          case AND_OPCODE:
              registers[0] &= reg_num ? registers[1] : imm;
              break;
          case OR_OPCODE:
              registers[0] |= reg_num ? registers[1] : imm;
              break;
          case SH_OPCODE: {
              const int32_t shift_val = reg_num ? (int32_t)registers[1] : signed_imm;
              if (shift_val > 0)
                  registers[0] <<= shift_val;
              else
                  registers[0] >>= -shift_val;
              break;
          }
          case LI_OPCODE:
              REG = signed_imm;
              break;
          case EXT_OPCODE:
              if (
// If LDM_EXT_OPCODE is 0 and imm is compared with it, a compiler error will result,
// instead just enforce that imm is unsigned (so it's always greater or equal to 0).
#if LDM_EXT_OPCODE == 0
                  ENFORCE_UNSIGNED(imm) &&
#else
                  imm >= LDM_EXT_OPCODE &&
#endif
                  imm < (LDM_EXT_OPCODE + MEMORY_ITEMS)) {
                REG = memory[imm - LDM_EXT_OPCODE];
              } else if (imm >= STM_EXT_OPCODE && imm < (STM_EXT_OPCODE + MEMORY_ITEMS)) {
                memory[imm - STM_EXT_OPCODE] = REG;
              } else switch (imm) {
                  case NOT_EXT_OPCODE:
                    REG = ~REG;
                    break;
                  case NEG_EXT_OPCODE:
                    REG = -REG;
                    break;
                  case SWAP_EXT_OPCODE: {
                    uint32_t tmp = REG;
                    REG = OTHER_REG;
                    OTHER_REG = tmp;
                    break;
                  }
                  case MOV_EXT_OPCODE:
                    REG = OTHER_REG;
                    break;
                  // Unknown extended opcode
                  default:
                    // Bail out
                    return PASS_PACKET;
              }
              break;
          // Unknown opcode
          default:
              // Bail out
              return PASS_PACKET;
      }
  } while (instructions_remaining--);
  return PASS_PACKET;
}
