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

// A brief overview of APF:
//
// APF machine is composed of:
//  1. A read-only program consisting of bytecodes as described below.
//  2. Two 32-bit registers, called R0 and R1.
//  3. Sixteen 32-bit memory slots.
//  4. A read-only packet.
// The program is executed by the interpreter below and parses the packet
// to determine if the application processor (AP) should be woken up to
// handle the packet or if can be dropped.
//
// APF bytecode description:
//
// The APF interpreter uses big-endian byte order for loads from the packet
// and for storing immediates in instructions.
//
// Each instruction starts with a byte composed of:
//  Top 5 bits form "opcode" field, see *_OPCODE defines below.
//  Next 2 bits form "size field", which indicate the length of an immediate
//  value which follows the first byte.  Values in this field:
//                 0 => immediate value is 0 and no bytes follow.
//                 1 => immediate value is 1 byte big.
//                 2 => immediate value is 2 bytes big.
//                 3 => immediate value is 4 bytes big.
//  Bottom bit forms "register" field, which indicates which register this
//  instruction operates on.
//
//  There are three main categories of instructions:
//  Load instructions
//    These instructions load byte(s) of the packet into a register.
//    They load either 1, 2 or 4 bytes, as determined by the "opcode" field.
//    They load into the register specified by the "register" field.
//    The immediate value that follows the first byte of the instruction is
//    the byte offset from the begining of the packet to load from.
//    There are "indexing" loads which add the value in R1 to the byte offset
//    to load from. The "opcode" field determines which loads are "indexing".
//  Arithmetic instructions
//    These instructions perform simple operations, like addition, on register
//    values. The result of these instructions is always written into R0. One
//    argument of the arithmetic operation is R0's value. The other argument
//    of the arithmetic operation is determined by the "register" field:
//            If the "register" field is 0 then the immediate value following
//            the first byte of the instruction is used as the other argument
//            to the arithmetic operation.
//            If the "register" field is 1 then R1's value is used as the other
//            argument to the arithmetic operation.
//  Conditional jump instructions
//    These instructions compare register R0's value with another value, and if
//    the comparison succeeds, jump (i.e. adjust the program counter). The
//    immediate value that follows the first byte of the instruction
//    represents the jump target offset, i.e. the value added to the program
//    counter if the comparison succeeds. The other value compared is
//    determined by the "register" field:
//            If the "register" field is 0 then another immediate value
//            follows the jump target offset. This immediate value is of the
//            same size as the jump target offset, and represents the value
//            to compare against.
//            If the "register" field is 1 then register R1's value is
//            compared against.
//    The type of comparison (e.g. equal to, greater than etc) is determined
//    by the "opcode" field. The comparison interprets both values being
//    compared as unsigned values.
//
//  Miscellaneous details:
//
//  Pre-filled memory slot values
//    When the APF program begins execution, three of the sixteen memory slots
//    are pre-filled by the interpreter with values that may be useful for
//    programs:
//      Slot #13 is filled with the IPv4 header length. This value is calculated
//               by loading the first byte of the IPv4 header and taking the
//               bottom 4 bits and multiplying their value by 4. This value is
//               set to zero if the first 4 bits after the link layer header are
//               not 4, indicating not IPv4.
//      Slot #14 is filled with size of the packet in bytes, including the
//               link-layer header if any.
//      Slot #15 is filled with the filter age in seconds. This is the number of
//               seconds since the AP send the program to the chipset. This may
//               be used by filters that should have a particular lifetime. For
//               example, it can be used to rate-limit particular packets to one
//               every N seconds.
//  Special jump targets:
//    When an APF program executes a jump to the byte immediately after the last
//      byte of the progam (i.e., one byte past the end of the program), this
//      signals the program has completed and determined the packet should be
//      passed to the AP.
//    When an APF program executes a jump two bytes past the end of the program,
//      this signals the program has completed and determined the packet should
//      be dropped.
//  Jump if byte sequence doesn't match:
//    This is a special instruction to facilitate matching long sequences of
//    bytes in the packet. Initially it is encoded like a conditional jump
//    instruction with two exceptions:
//      The first byte of the instruction is always followed by two immediate
//        fields: The first immediate field is the jump target offset like other
//        conditional jump instructions. The second immediate field specifies the
//        number of bytes to compare.
//      These two immediate fields are followed by a sequence of bytes. These
//        bytes are compared with the bytes in the packet starting from the
//        position specified by the value of the register specified by the
//        "register" field of the instruction.

// Number of memory slots, see ldm/stm instructions.
#define MEMORY_ITEMS 16
// Upon program execution starting some memory slots are prefilled:
#define MEMORY_OFFSET_IPV4_HEADER_SIZE 13 // 4*([APF_FRAME_HEADER_SIZE]&15)
#define MEMORY_OFFSET_PACKET_SIZE 14      // Size of packet in bytes.
#define MEMORY_OFFSET_FILTER_AGE 15       // Age since filter installed in seconds.

// Leave 0 opcode unused as it's a good indicator of accidental incorrect execution (e.g. data).
#define LDB_OPCODE 1    // Load 1 byte from immediate offset, e.g. "ldb R0, [5]"
#define LDH_OPCODE 2    // Load 2 bytes from immediate offset, e.g. "ldh R0, [5]"
#define LDW_OPCODE 3    // Load 4 bytes from immediate offset, e.g. "ldw R0, [5]"
#define LDBX_OPCODE 4   // Load 1 byte from immediate offset plus register, e.g. "ldbx R0, [5]R0"
#define LDHX_OPCODE 5   // Load 2 byte from immediate offset plus register, e.g. "ldhx R0, [5]R0"
#define LDWX_OPCODE 6   // Load 4 byte from immediate offset plus register, e.g. "ldwx R0, [5]R0"
#define ADD_OPCODE 7    // Add, e.g. "add R0,5"
#define MUL_OPCODE 8    // Multiply, e.g. "mul R0,5"
#define DIV_OPCODE 9    // Divide, e.g. "div R0,5"
#define AND_OPCODE 10   // And, e.g. "and R0,5"
#define OR_OPCODE 11    // Or, e.g. "or R0,5"
#define SH_OPCODE 12    // Left shift, e.g, "sh R0, 5" or "sh R0, -5" (shifts right)
#define LI_OPCODE 13    // Load immediate, e.g. "li R0,5" (immediate encoded as signed value)
#define JMP_OPCODE 14   // Unconditional jump, e.g. "jmp label"
#define JEQ_OPCODE 15   // Compare equal and branch, e.g. "jeq R0,5,label"
#define JNE_OPCODE 16   // Compare not equal and branch, e.g. "jne R0,5,label"
#define JGT_OPCODE 17   // Compare greater than and branch, e.g. "jgt R0,5,label"
#define JLT_OPCODE 18   // Compare less than and branch, e.g. "jlt R0,5,label"
#define JSET_OPCODE 19  // Compare any bits set and branch, e.g. "jset R0,5,label"
#define JNEBS_OPCODE 20 // Compare not equal byte sequence, e.g. "jnebs R0,5,label,0x1122334455"
#define EXT_OPCODE 21   // Immediate value is one of *_EXT_OPCODE
// Extended opcodes. These all have an opcode of EXT_OPCODE
// and specify the actual opcode in the immediate field.
#define LDM_EXT_OPCODE 0   // Load from memory, e.g. "ldm R0,5"
  // Values 0-15 represent loading the different memory slots.
#define STM_EXT_OPCODE 16  // Store to memory, e.g. "stm R0,5"
  // Values 16-31 represent storing to the different memory slots.
#define NOT_EXT_OPCODE 32  // Not, e.g. "not R0"
#define NEG_EXT_OPCODE 33  // Negate, e.g. "neg R0"
#define SWAP_EXT_OPCODE 34 // Swap, e.g. "swap R0,R1"
#define MOV_EXT_OPCODE 35  // Move, e.g. "move R0,R1"

#define EXTRACT_OPCODE(i) (((i) >> 3) & 31)
#define EXTRACT_REGISTER(i) ((i) & 1)
#define EXTRACT_IMM_LENGTH(i) (((i) >> 1) & 3)
