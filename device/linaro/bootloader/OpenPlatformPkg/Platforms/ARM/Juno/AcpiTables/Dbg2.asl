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
 * [DBG2] ACPI Table
 *
 */

[0004]                    Signature : "DBG2"    [Debug Port table type 2]
[0004]                 Table Length : 0000005A
[0001]                     Revision : 00
[0001]                     Checksum : 06
[0006]                       Oem ID : "LINARO"
[0008]                 Oem Table ID : "ARM-JUNO"
[0004]                 Oem Revision : 00000000
[0004]              Asl Compiler ID : "INTL"
[0004]        Asl Compiler Revision : 20140926

[0004]                  Info Offset : 0000002C
[0004]                   Info Count : 00000001

[0001]                     Revision : 00
[0002]                       Length : 002C
[0001]               Register Count : 01
[0002]              Namepath Length : 0005
[0002]              Namepath Offset : 0026
[0002]              OEM Data Length : 0000 [Optional field not present]
[0002]              OEM Data Offset : 0000 [Optional field not present]
[0002]                    Port Type : 8000
[0002]                 Port Subtype : 0003
[0002]                     Reserved : 0000
[0002]          Base Address Offset : 0016
[0002]          Address Size Offset : 0022

[000C]        Base Address Register : [Generic Address Structure]
[0001]                     Space ID : 00 [SystemMemory]
[0001]                    Bit Width : 20
[0001]                   Bit Offset : 00
[0001]         Encoded Access Width : 03 [DWord Access:32]
[0008]                      Address : 000000007FF80000

[0004]                 Address Size : 00001000

[0004]                     Namepath : "COM1"
[0001]                     OEM Data : 00
