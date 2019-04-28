/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

/*  libasf - An Advanced Systems Format media file parser
*  Copyright (C) 2006-2010 Juho Vähä-Herttua
*
*  This library is free software; you can redistribute it and/or
*  modify it under the terms of the GNU Lesser General Public
*  License as published by the Free Software Foundation; either
*  version 2.1 of the License, or (at your option) any later version.
*   *  This library is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
*  Lesser General Public License for more details.
*
*  You should have received a copy of the GNU Lesser General Public
*  License along with this library; if not, write to the Free Software
*  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA   */

#ifndef BYTEIO_H
#define BYTEIO_H

#ifndef ASF_H
#include "asf.h"
#endif

class ASFByteIO {
public:
    static uint16_t asf_byteio_getWLE(uint8_t *data);
    static uint32_t asf_byteio_getDWLE(uint8_t *data);
    static uint64_t asf_byteio_getQWLE(uint8_t *data);
    static void asf_byteio_getGUID(asf_guid_t *guid, uint8_t *data);
    static void asf_byteio_get_string(uint16_t *string, uint16_t strlen, uint8_t *data);
    static int asf_byteio_read(uint8_t *data, int size, asf_iostream_t *iostream);
};

#endif
