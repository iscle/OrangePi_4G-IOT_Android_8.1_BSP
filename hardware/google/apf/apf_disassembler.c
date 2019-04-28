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

#include <stdint.h>
#include <stdio.h>

#include "apf.h"

// If "c" is of an unsigned type, generate a compile warning that gets promoted to an error.
// This makes bounds checking simpler because ">= 0" can be avoided. Otherwise adding
// superfluous ">= 0" with unsigned expressions generates compile warnings.
#define ENFORCE_UNSIGNED(c) ((c)==(uint32_t)(c))

static void print_opcode(const char* opcode) {
  printf("%-6s", opcode);
}

// Mapping from opcode number to opcode name.
static const char* opcode_names [] = {
    [LDB_OPCODE] = "ldb",
    [LDH_OPCODE] = "ldh",
    [LDW_OPCODE] = "ldw",
    [LDBX_OPCODE] = "ldb",
    [LDHX_OPCODE] = "ldh",
    [LDWX_OPCODE] = "ldw",
    [ADD_OPCODE] = "add",
    [MUL_OPCODE] = "mul",
    [DIV_OPCODE] = "div",
    [AND_OPCODE] = "and",
    [OR_OPCODE] = "or",
    [SH_OPCODE] = "sh",
    [LI_OPCODE] = "li",
    [JMP_OPCODE] = "jmp",
    [JEQ_OPCODE] = "jeq",
    [JNE_OPCODE] = "jne",
    [JGT_OPCODE] = "jgt",
    [JLT_OPCODE] = "jlt",
    [JSET_OPCODE] = "jset",
    [JNEBS_OPCODE] = "jnebs",
};

static void print_jump_target(uint32_t target, uint32_t program_len) {
    if (target == program_len) {
        printf("pass");
    } else if (target == program_len + 1) {
        printf("drop");
    } else {
        printf("%u", target);
    }
}

// Disassembles an APF program. A hex dump of the program is supplied on stdin.
//
// NOTE: This is a simple debugging tool not meant for shipping or production use.  It is by no
//       means hardened against malicious input and contains known vulnerabilities.
//
// Example usage:
// adb shell dumpsys wifi ipmanager | sed '/Last program:/,+1!d;/Last program:/d;s/[ ]*//' | out/host/linux-x86/bin/apf_disassembler
int main(void) {
  uint32_t program_len = 0;
  uint8_t program[10000];

  // Read in hex program bytes
  int byte;
  while (scanf("%2x", &byte) == 1 && program_len < sizeof(program)) {
      program[program_len++] = byte;
  }

  for (uint32_t pc = 0; pc < program_len;) {
      printf("%8u: ", pc);
      const uint8_t bytecode = program[pc++];
      const uint32_t opcode = EXTRACT_OPCODE(bytecode);
#define PRINT_OPCODE() print_opcode(opcode_names[opcode])
      const uint32_t reg_num = EXTRACT_REGISTER(bytecode);
      // All instructions have immediate fields, so load them now.
      const uint32_t len_field = EXTRACT_IMM_LENGTH(bytecode);
      uint32_t imm = 0;
      int32_t signed_imm = 0;
      if (len_field != 0) {
          const uint32_t imm_len = 1 << (len_field - 1);
          uint32_t i;
          for (i = 0; i < imm_len && pc < program_len; i++)
              imm = (imm << 8) | program[pc++];
          // Sign extend imm into signed_imm.
          signed_imm = imm << ((4 - imm_len) * 8);
          signed_imm >>= (4 - imm_len) * 8;
      }
      switch (opcode) {
          case LDB_OPCODE:
          case LDH_OPCODE:
          case LDW_OPCODE:
              PRINT_OPCODE();
              printf("r%d, [%u]", reg_num, imm);
              break;
          case LDBX_OPCODE:
          case LDHX_OPCODE:
          case LDWX_OPCODE:
              PRINT_OPCODE();
              printf("r%d, [%u+r1]", reg_num, imm);
              break;
          case JMP_OPCODE:
              PRINT_OPCODE();
              print_jump_target(pc + imm, program_len);
              break;
          case JEQ_OPCODE:
          case JNE_OPCODE:
          case JGT_OPCODE:
          case JLT_OPCODE:
          case JSET_OPCODE:
          case JNEBS_OPCODE: {
              PRINT_OPCODE();
              printf("r0, ");
              // Load second immediate field.
              uint32_t cmp_imm = 0;
              if (reg_num == 1) {
                  printf("r1, ");
              } else if (len_field == 0) {
                  printf("0, ");
              } else {
                  uint32_t cmp_imm_len = 1 << (len_field - 1);
                  uint32_t i;
                  for (i = 0; i < cmp_imm_len && pc < program_len; i++)
                      cmp_imm = (cmp_imm << 8) | program[pc++];
                  printf("0x%x, ", cmp_imm);
              }
              if (opcode == JNEBS_OPCODE) {
                  print_jump_target(pc + imm + cmp_imm, program_len);
                  printf(", ");
                  while (cmp_imm--)
                      printf("%02x", program[pc++]);
              } else {
                  print_jump_target(pc + imm, program_len);
              }
              break;
          }
          case ADD_OPCODE:
          case SH_OPCODE:
              PRINT_OPCODE();
              if (reg_num) {
                  printf("r0, r1");
              } else {
                  printf("r0, %d", signed_imm);
              }
              break;
          case MUL_OPCODE:
          case DIV_OPCODE:
          case AND_OPCODE:
          case OR_OPCODE:
              PRINT_OPCODE();
              if (reg_num) {
                  printf("r0, r1");
              } else {
                  printf("r0, %u", imm);
              }
              break;
          case LI_OPCODE:
              PRINT_OPCODE();
              printf("r%d, %d", reg_num, signed_imm);
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
                  print_opcode("ldm");
                  printf("r%d, m[%u]", reg_num, imm - LDM_EXT_OPCODE);
              } else if (imm >= STM_EXT_OPCODE && imm < (STM_EXT_OPCODE + MEMORY_ITEMS)) {
                  print_opcode("stm");
                  printf("r%d, m[%u]", reg_num, imm - STM_EXT_OPCODE);
              } else switch (imm) {
                  case NOT_EXT_OPCODE:
                      print_opcode("not");
                      printf("r%d", reg_num);
                      break;
                  case NEG_EXT_OPCODE:
                      print_opcode("neg");
                      printf("r%d", reg_num);
                      break;
                  case SWAP_EXT_OPCODE:
                      print_opcode("swap");
                      break;
                  case MOV_EXT_OPCODE:
                      print_opcode("mov");
                      printf("r%d, r%d", reg_num, reg_num ^ 1);
                      break;
                  default:
                      printf("unknown_ext %u", imm);
                      break;
              }
              break;
          // Unknown opcode
          default:
              printf("unknown %u", opcode);
              break;
      }
      printf("\n");
  }
  return 0;
}
