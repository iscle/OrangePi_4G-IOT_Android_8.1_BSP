/*
 * Copyright (c) 2015, Graeme Gregory <graeme.gregory@linaro.org>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 * notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the 
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * 
 * NB: This License is also known as the "BSD 2-Clause License".
 * 
 *
 * [SPCR] ACPI Table
 *
 */

[0004]                    Signature : "SPCR"    [Serial Port Console Redirection table]
[0004]                 Table Length : 00000050
[0001]                     Revision : 02
[0001]                     Checksum : 29
[0006]                       Oem ID : "LINARO"
[0008]                 Oem Table ID : "ARM-JUNO"
[0004]                 Oem Revision : 00000000
[0004]              Asl Compiler ID : "INTL"
[0004]        Asl Compiler Revision : 20140926

[0001]               Interface Type : 03
[0003]                     Reserved : 000000

[000C]         Serial Port Register : [Generic Address Structure]
[0001]                     Space ID : 00 [SystemMemory]
[0001]                    Bit Width : 20
[0001]                   Bit Offset : 00
[0001]         Encoded Access Width : 03 [DWord Access:32]
[0008]                      Address : 000000007FF80000

[0001]               Interrupt Type : 08
[0001]          PCAT-compatible IRQ : 00
[0004]                    Interrupt : 00000073
[0001]                    Baud Rate : 07
[0001]                       Parity : 00
[0001]                    Stop Bits : 01
[0001]                 Flow Control : 00
[0001]                Terminal Type : 03
[0001]                     Reserved : 00
[0002]                PCI Device ID : FFFF
[0002]                PCI Vendor ID : FFFF
[0001]                      PCI Bus : 00
[0001]                   PCI Device : 00
[0001]                 PCI Function : 00
[0004]                    PCI Flags : 00000000
[04Bh]                  PCI Segment : 00
[04Ch]                     Reserved : 00000000

