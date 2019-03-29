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

#include <stdio.h>
#include <printf.h>
#include <cpu/cpuMath.h>

#define FLAG_ALT            (1 << 0)
#define FLAG_ZERO_EXTEND    (1 << 1)
#define FLAG_IS_SIGNED      (1 << 2)
#define FLAG_NEG_PAD        (1 << 3)
#define FLAG_CAPS           (1 << 4)

struct PrintfData
{
    uint64_t    number;
    void       *userData;
    uint32_t    fieldWidth;
    uint32_t    precision;
    uint32_t    flags;
    uint8_t     posChar;
    uint8_t     base;
};

static uint32_t StrPrvPrintfEx_number(printf_write_c putc_, struct PrintfData *data, bool *bail)
{
    char buf[64];
    uint32_t idx = sizeof(buf) - 1;
    uint32_t chr, i;
    uint32_t numPrinted = 0;

    *bail = false;

#ifdef USE_PRINTF_FLAG_CHARS
    if (data->fieldWidth > sizeof(buf) - 1)
        data->fieldWidth = sizeof(buf) - 1;

    if (data->precision > sizeof(buf) - 1)
        data->precision = sizeof(buf) - 1;
#endif

    buf[idx--] = 0;    //terminate

    if (data->flags & FLAG_IS_SIGNED) {

        if (((int64_t)data->number) < 0) {

            data->posChar = '-';
            data->number = -data->number;
        }
    }

    do {
        if (data->base == 8) {

            chr = (data->number & 0x07) + '0';
            data->number >>= 3;
        }
        else if (data->base == 10) {

            uint64_t t = U64_DIV_BY_CONST_U16(data->number, 10);
            chr = (data->number - t * 10) + '0';
            data->number = t;
        }
        else {

            chr = data->number & 0x0F;
            data->number >>= 4;
            chr = (chr >= 10) ? (chr + (data->flags & FLAG_CAPS ? 'A' : 'a') - 10) : (chr + '0');
        }

        buf[idx--] = chr;

        numPrinted++;

    } while (data->number);

#ifdef USE_PRINTF_FLAG_CHARS
    while (data->precision > numPrinted) {

        buf[idx--] = '0';
        numPrinted++;
    }

    if (data->flags & FLAG_ALT) {

        if (data->base == 8) {

            if (buf[idx+1] != '0') {
                buf[idx--] = '0';
                numPrinted++;
            }
        }
        else if (data->base == 16) {

            buf[idx--] = data->flags & FLAG_CAPS ? 'X' : 'x';
            numPrinted++;
            buf[idx--] = '0';
            numPrinted++;
        }
    }


    if (!(data->flags & FLAG_NEG_PAD)) {

        if (data->fieldWidth > 0 && data->posChar != '\0')
            data->fieldWidth--;

        while (data->fieldWidth > numPrinted) {

            buf[idx--] = data->flags & FLAG_ZERO_EXTEND ? '0' : ' ';
            numPrinted++;
        }
    }
#endif

    if (data->posChar != '\0') {

        buf[idx--] = data->posChar;
        numPrinted++;
    }

    idx++;

    for(i = 0; i < numPrinted; i++) {

        if (!putc_(data->userData,(buf + idx)[i])) {

            *bail = true;
            break;
        }
    }

#ifdef USE_PRINTF_FLAG_CHARS
    if (!*bail && data->flags & FLAG_NEG_PAD) {

        for(i = numPrinted; i < data->fieldWidth; i++) {

            if (!putc_(data->userData, ' ')) {

                *bail = true;
                break;
            }
        }
    }
#endif

    return i;
}

static uint32_t StrVPrintf_StrLen_withMax(const char* s, uint32_t max)
{
    uint32_t len = 0;

    while ((*s++) && (len < max)) len++;

    return len;
}

static uint32_t StrVPrintf_StrLen(const char* s)
{
    uint32_t len = 0;

    while (*s++) len++;

    return len;
}

static inline char prvGetChar(const char** fmtP)
{

    return *(*fmtP)++;
}

uint32_t cvprintf(printf_write_c putc_f, uint32_t flags, void* userData, const char* fmtStr, va_list vl)
{

    char c, t;
    uint32_t numPrinted = 0;
    double dbl;
    long double ldbl;
    struct PrintfData data;

    data.userData = userData;

#define putc_(_ud,_c)                \
        do {                 \
            if (!putc_f(_ud,_c))    \
                goto out;    \
        } while(0)

    while ((c = prvGetChar(&fmtStr)) != 0) {

        if (c == '\n') {

            putc_(userData,c);
            numPrinted++;
        }
        else if (c == '%') {
            uint32_t len, i;
            const char* str;
            bool useChar = false, useShort = false, useLong = false, useLongLong = false, useLongDouble =false, useSizeT = false, usePtrdiffT = false;
            bool havePrecision = false, bail = false;

            data.fieldWidth = 0;
            data.precision = 0;
            data.flags = 0;
            data.posChar = 0;

more_fmt:

            c = prvGetChar(&fmtStr);

            switch(c) {

                case '%':

                    putc_(userData,c);
                    numPrinted++;
                    break;

                case 'c':

                    t = va_arg(vl,unsigned int);
                    putc_(userData,t);
                    numPrinted++;
                    break;

                case 's':

                    str = va_arg(vl,char*);
                    if (!str) str = "(null)";

                    if (data.precision)
                        len = StrVPrintf_StrLen_withMax(str,data.precision);
                    else
                        len = StrVPrintf_StrLen(str);

#ifdef USE_PRINTF_FLAG_CHARS
                    if (!(data.flags & FLAG_NEG_PAD)) {
                        for(i = len; i < data.fieldWidth; i++) {
                            putc_(userData, ' ');
                            numPrinted++;
                        }
                    }
#endif

                    for(i = 0; i < len; i++) {
                        putc_(userData,*str++);
                        numPrinted++;
                    }

#ifdef USE_PRINTF_FLAG_CHARS
                    if (data.flags & FLAG_NEG_PAD) {
                        for(i = len; i < data.fieldWidth; i++) {
                            putc_(userData, ' ');
                            numPrinted++;
                        }
                    }
#endif

                    break;

                case '.':

                    havePrecision = true;
                    goto more_fmt;

                case '0':

                    if (!(data.flags & FLAG_ZERO_EXTEND) && !data.fieldWidth && !havePrecision) {

                        data.flags |= FLAG_ZERO_EXTEND;
                        goto more_fmt;
                    }

                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':

                    if (havePrecision)
                        data.precision = (data.precision * 10) + c - '0';
                    else
                        data.fieldWidth = (data.fieldWidth * 10) + c - '0';
                    goto more_fmt;

                case '#':

                    data.flags |= FLAG_ALT;
                    goto more_fmt;

                case '-':

                    data.flags |= FLAG_NEG_PAD;
                    goto more_fmt;

                case '+':

                    data.posChar = '+';
                    goto more_fmt;

                case ' ':

                    if (data.posChar != '+')
                        data.posChar = ' ';
                    goto more_fmt;

#define GET_UVAL64() \
        useSizeT ? va_arg(vl, size_t) :                 \
        usePtrdiffT ? va_arg(vl, ptrdiff_t) :           \
        useLongLong ? va_arg(vl, unsigned long long) :  \
        useLong ? va_arg(vl, unsigned long) :           \
        useChar ? (unsigned char)va_arg(vl, unsigned int) : \
        useShort ? (unsigned short)va_arg(vl, unsigned int) : \
        va_arg(vl, unsigned int)

#define GET_SVAL64() \
        useSizeT ? va_arg(vl, size_t) :                 \
        usePtrdiffT ? va_arg(vl, ptrdiff_t) :           \
        useLongLong ? va_arg(vl, signed long long) :    \
        useLong ? va_arg(vl, signed long) :             \
        useChar ? (signed char)va_arg(vl, signed int) : \
        useShort ? (signed short)va_arg(vl, signed int) : \
        va_arg(vl, signed int)

                case 'u':

                    data.number = GET_UVAL64();
                    data.base = 10;
                    data.flags &= ~(FLAG_ALT | FLAG_CAPS);
                    numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    if (bail)
                        goto out;
                    break;

                case 'd':
                case 'i':

                    data.number = GET_SVAL64();
                    data.base = 10;
                    data.flags &= ~(FLAG_ALT | FLAG_CAPS);
                    data.flags |= FLAG_IS_SIGNED;
                    numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    if (bail)
                        goto out;
                    break;

                case 'o':

                    data.number = GET_UVAL64();
                    data.base = 8;
                    data.flags &= ~FLAG_CAPS;
                    data.posChar = '\0';
                    numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    if (bail)
                        goto out;
                    break;

                case 'X':

                    data.flags |= FLAG_CAPS;

                case 'x':

                    data.number = GET_UVAL64();
                    data.base = 16;
                    data.posChar = '\0';
                    numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    if (bail)
                        goto out;
                    break;

                case 'p':

                    data.number = (uintptr_t)va_arg(vl, const void*);
                    data.base = 16;
                    data.flags &= ~FLAG_CAPS;
                    data.flags |= FLAG_ALT;
                    data.posChar = '\0';
                    numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    if (bail)
                        goto out;
                    break;

#undef GET_UVAL64
#undef GET_SVAL64

                case 'F':

                    data.flags |= FLAG_CAPS;

                case 'f':

                    if (flags & PRINTF_FLAG_CHRE) {
                        if (flags & PRINTF_FLAG_SHORT_DOUBLE) {
                            if (useLongDouble) {
                                dbl = va_arg(vl, double);
                                data.number = *(uint64_t *)(&dbl);
                            } else {
                                // just grab the 32-bits
                                data.number = va_arg(vl, uint32_t);
                            }
                        } else {
                            if (useLongDouble) {
                                ldbl = va_arg(vl, long double);
                                data.number = *(uint64_t *)(&ldbl);
                            } else {
                                dbl = va_arg(vl, double);
                                data.number = *(uint64_t *)(&dbl);
                            }
                        }
                        data.base = 16;
                        data.flags |= FLAG_ALT;
                        data.posChar = '\0';
                        numPrinted += StrPrvPrintfEx_number(putc_f, &data, &bail);
                    } else {
                        bail = true;
                    }
                    if (bail)
                        goto out;
                    break;

                case 'h':

                    if (useShort)
                        useChar = true;
                    useShort = true;
                    goto more_fmt;

                case 'L':

                    useLongDouble = true;
                    goto more_fmt;

                case 'l':

                    if (useLong)
                        useLongLong = true;
                    useLong = true;
                    goto more_fmt;

                case 'z':

                    useSizeT = true;
                    goto more_fmt;

                case 't':

                    usePtrdiffT = true;
                    goto more_fmt;

                default:

                    putc_(userData,c);
                    numPrinted++;
                    break;

            }
        }
        else {

            putc_(userData,c);
            numPrinted++;
        }
    }

out:

    return numPrinted;
}
