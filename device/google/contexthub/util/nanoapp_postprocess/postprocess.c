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

#include <assert.h>
#include <fcntl.h>
#include <sys/types.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdio.h>
#include <stddef.h>
#include <errno.h>

#include <nanohub/nanohub.h>
#include <nanohub/nanoapp.h>
#include <nanohub/appRelocFormat.h>

//This code assumes it is run on a LE CPU with unaligned access abilities. Sorry.

#define FLASH_BASE  0x10000000u
#define RAM_BASE    0x80000000u

#define FLASH_SIZE  0x10000000u  //256MB ought to be enough for everyone
#define RAM_SIZE    0x10000000u  //256MB ought to be enough for everyone

//caution: double evaluation
#define IS_IN_RANGE_E(_val, _rstart, _rend) (((_val) >= (_rstart)) && ((_val) < (_rend)))
#define IS_IN_RANGE(_val, _rstart, _rsz)    IS_IN_RANGE_E((_val), (_rstart), ((_rstart) + (_rsz)))
#define IS_IN_RAM(_val)              IS_IN_RANGE(_val, RAM_BASE, RAM_SIZE)
#define IS_IN_FLASH(_val)            IS_IN_RANGE(_val, FLASH_BASE, FLASH_SIZE)


#define NANO_RELOC_TYPE_RAM    0
#define NANO_RELOC_TYPE_FLASH  1
#define NANO_RELOC_LAST        2 //must be <= (RELOC_TYPE_MASK >> RELOC_TYPE_SHIFT)

struct RelocEntry {
    uint32_t where;
    uint32_t info;  //bottom 8 bits is type, top 24 is sym idx
};

#define RELOC_TYPE_ABS_S    2
#define RELOC_TYPE_ABS_D    21
#define RELOC_TYPE_SECT     23


struct SymtabEntry {
    uint32_t a;
    uint32_t addr;
    uint32_t b, c;
};

struct NanoRelocEntry {
    uint32_t ofstInRam;
    uint8_t type;
};

struct NanoAppInfo {
    union {
        struct BinHdr *bin;
        uint8_t *data;
    };
    size_t dataSizeUsed;
    size_t dataSizeAllocated;
    size_t codeAndDataSize;   // not including symbols, relocs and BinHdr
    size_t codeAndRoDataSize; // also not including GOT & RW data in flash
    struct SymtabEntry *symtab;
    size_t symtabSize; // number of symbols
    struct RelocEntry *reloc;
    size_t relocSize; // number of reloc entries
    struct NanoRelocEntry *nanoReloc;
    size_t nanoRelocSize; // number of nanoReloc entries <= relocSize
    uint8_t *packedNanoReloc;
    size_t packedNanoRelocSize;

    bool debug;
};

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(ary) (sizeof(ary) / sizeof((ary)[0]))
#endif

static FILE *stdlog = NULL;

#define DBG(fmt, ...) fprintf(stdlog, fmt "\n", ##__VA_ARGS__)
#define ERR(fmt, ...) fprintf(stderr, fmt "\n", ##__VA_ARGS__)

static void fatalUsage(const char *name, const char *msg, const char *arg)
{
    if (msg && arg)
        ERR("Error: %s: %s\n", msg, arg);
    else if (msg)
        ERR("Error: %s\n", msg);

    ERR("USAGE: %s [-v] [-k <key id>] [-a <app id>] [-r] [-n <layout name>] [-i <layout id>] <input file> [<output file>]\n"
        "       -v               : be verbose\n"
        "       -n <layout name> : app, os, key\n"
        "       -i <layout id>   : 1 (app), 2 (key), 3 (os)\n"
        "       -f <layout flags>: 16-bit hex value, stored as layout-specific flags\n"
        "       -a <app ID>      : 64-bit hex number != 0\n"
        "       -e <app ver>     : 32-bit hex number\n"
        "       -k <key ID>      : 64-bit hex number != 0\n"
        "       -r               : bare (no AOSP header); used only for inner OS image generation\n"
        "       layout ID and layout name control the same parameter, so only one of them needs to be used\n"
        , name);
    exit(1);
}

bool packNanoRelocs(struct NanoAppInfo *app)
{
    size_t i, j, k;
    uint8_t *packedNanoRelocs;
    uint32_t packedNanoRelocSz;
    uint32_t lastOutType = 0, origin = 0;
    bool verbose = app->debug;

    //sort by type and then offset
    for (i = 0; i < app->nanoRelocSize; i++) {
        struct NanoRelocEntry t;

        for (k = i, j = k + 1; j < app->nanoRelocSize; j++) {
            if (app->nanoReloc[j].type > app->nanoReloc[k].type)
                continue;
            if ((app->nanoReloc[j].type < app->nanoReloc[k].type) || (app->nanoReloc[j].ofstInRam < app->nanoReloc[k].ofstInRam))
                k = j;
        }
        memcpy(&t, app->nanoReloc + i, sizeof(struct NanoRelocEntry));
        memcpy(app->nanoReloc + i, app->nanoReloc + k, sizeof(struct NanoRelocEntry));
        memcpy(app->nanoReloc + k, &t, sizeof(struct NanoRelocEntry));

        if (app->debug)
            DBG("SortedReloc[%3zu] = {0x%08" PRIX32 ",0x%02" PRIX8 "}", i, app->nanoReloc[i].ofstInRam, app->nanoReloc[i].type);
    }

    //produce output nanorelocs in packed format
    packedNanoRelocs = malloc(app->nanoRelocSize * 6); //definitely big enough
    packedNanoRelocSz = 0;

    if (!packedNanoRelocs) {
        ERR("Failed to allocate memory for packed relocs");
        return false;
    }

    for (i = 0; i < app->nanoRelocSize; i++) {
        uint32_t displacement;

        if (lastOutType != app->nanoReloc[i].type) {  //output type if ti changed
            if (app->nanoReloc[i].type - lastOutType == 1) {
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_RELOC_TYPE_NEXT;
                if (verbose)
                    DBG("Out: RelocTC [size 1] // to 0x%02" PRIX8, app->nanoReloc[i].type);
            } else {
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_RELOC_TYPE_CHG;
                packedNanoRelocs[packedNanoRelocSz++] = app->nanoReloc[i].type - lastOutType - 1;
                if (verbose)
                    DBG("Out: RelocTC [size 2] (0x%02" PRIX8 ")  // to 0x%02" PRIX8,
                        (uint8_t)(app->nanoReloc[i].type - lastOutType - 1), app->nanoReloc[i].type);
            }
            lastOutType = app->nanoReloc[i].type;
            origin = 0;
        }
        displacement = app->nanoReloc[i].ofstInRam - origin;
        origin = app->nanoReloc[i].ofstInRam + 4;
        if (displacement & 3) {
            ERR("Unaligned relocs are not possible!");
            return false;
        }
        displacement /= 4;

        //might be start of a run. look into that
        if (!displacement) {
            for (j = 1; (j + i) < app->nanoRelocSize && j < MAX_RUN_LEN &&
                        app->nanoReloc[j + i].type == lastOutType &&
                        (app->nanoReloc[j + i].ofstInRam - app->nanoReloc[j + i - 1].ofstInRam) == 4; j++);
            if (j >= MIN_RUN_LEN) {
                if (verbose)
                    DBG("Out: Reloc0 [size 2]; repeat=%zu", j);
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_CONSECUTIVE;
                packedNanoRelocs[packedNanoRelocSz++] = j - MIN_RUN_LEN;
                origin = app->nanoReloc[j + i - 1].ofstInRam + 4;  //reset origin to last one
                i += j - 1;  //loop will increment anyways, hence +1
                continue;
            }
        }

        //produce output
        if (displacement <= MAX_8_BIT_NUM) {
            if (verbose)
                DBG("Out: Reloc8 [size 1] 0x%02" PRIX32, displacement);
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
        } else if (displacement <= MAX_16_BIT_NUM) {
            if (verbose)
                DBG("Out: Reloc16 [size 3] 0x%06" PRIX32, displacement);
                        displacement -= MAX_8_BIT_NUM;
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_16BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
        } else if (displacement <= MAX_24_BIT_NUM) {
            if (verbose)
                DBG("Out: Reloc24 [size 4] 0x%08" PRIX32, displacement);
                        displacement -= MAX_16_BIT_NUM;
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_24BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 16;
        } else {
            if (verbose)
                DBG("Out: Reloc32 [size 5] 0x%08" PRIX32, displacement);
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_32BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 16;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 24;
        }
    }

    app->packedNanoReloc = packedNanoRelocs;
    app->packedNanoRelocSize = packedNanoRelocSz;

    return true;
}

static int finalizeAndWrite(struct NanoAppInfo *inf, FILE *out, uint32_t layoutFlags, uint64_t appId)
{
    bool good = true;
    struct AppInfo app;
    struct SectInfo *sect;
    struct BinHdr *bin = inf->bin;
    struct ImageHeader outHeader = {
        .aosp = (struct nano_app_binary_t) {
            .header_version = 1,
            .magic = NANOAPP_AOSP_MAGIC,
            .app_id = appId,
            .app_version = bin->hdr.appVer,
            .flags       = 0, // encrypted (1), signed (2) (will be set by other tools)
        },
        .layout = (struct ImageLayout) {
            .magic = GOOGLE_LAYOUT_MAGIC,
            .version = 1,
            .payload = LAYOUT_APP,
            .flags = layoutFlags,
        },
    };

    app.sect = bin->sect;
    app.vec  = bin->vec;
    sect = &app.sect;

    //if we have any bytes to output, show stats
    if (inf->codeAndRoDataSize) {
        size_t binarySize = 0;
        size_t gotSz = sect->got_end - sect->data_start;
        size_t bssSz = sect->bss_end - sect->bss_start;

        good = fwrite(&outHeader, sizeof(outHeader), 1, out) == 1 && good;
        binarySize += sizeof(outHeader);

        good = fwrite(&app, sizeof(app), 1, out) == 1 && good;
        binarySize += sizeof(app);

        good = fwrite(&bin[1], inf->codeAndDataSize, 1, out) == 1 && good;
        binarySize += inf->codeAndDataSize;

        if (inf->packedNanoReloc && inf->packedNanoRelocSize) {
            good = fwrite(inf->packedNanoReloc, inf->packedNanoRelocSize, 1, out) == 1 && good;
            binarySize += inf->packedNanoRelocSize;
        }

        if (!good) {
            ERR("Failed to write output file: %s\n", strerror(errno));
        } else {
            DBG("Final binary size %zu bytes", binarySize);
            DBG("");
            DBG("       FW header size (flash):      %6zu bytes", FLASH_RELOC_OFFSET);
            DBG("       Code + RO data (flash):      %6zu bytes", inf->codeAndRoDataSize);
            DBG("       Relocs (flash):              %6zu bytes", inf->packedNanoRelocSize);
            DBG("       GOT + RW data (flash & RAM): %6zu bytes", gotSz);
            DBG("       BSS (RAM):                   %6zu bytes", bssSz);
            DBG("");
            DBG("Runtime flash use: %zu bytes",
                (size_t)(inf->codeAndRoDataSize + inf->packedNanoRelocSize + gotSz + FLASH_RELOC_OFFSET));
            DBG("Runtime RAM use: %zu bytes", gotSz + bssSz);
        }
    }

    return good ? 0 : 2;
}

// Subtracts the fixed memory region offset from an absolute address and returns
// the associated NANO_RELOC_* value, or NANO_RELOC_LAST if the address is not
// in the expected range.
static uint8_t fixupAddress(uint32_t *addr, struct SymtabEntry *sym, bool debug)
{
    uint8_t type;
    uint32_t old = *addr;

    (*addr) += sym->addr;
    // TODO: this assumes that the host running this tool has the same
    // endianness as the image file/target processor
    if (IS_IN_RAM(*addr)) {
        *addr -= RAM_BASE;
        type = NANO_RELOC_TYPE_RAM;
        if (debug)
            DBG("Fixup addr 0x%08" PRIX32 " (RAM) --> 0x%08" PRIX32, old, *addr);
    } else if (IS_IN_FLASH(*addr)) {
        *addr -= FLASH_BASE + BINARY_RELOC_OFFSET;
        type = NANO_RELOC_TYPE_FLASH;
        if (debug)
            DBG("Fixup addr 0x%08" PRIX32 " (FLASH) --> 0x%08" PRIX32, old, *addr);
    } else {
        ERR("Error: invalid address 0x%08" PRIX32, *addr);
        type = NANO_RELOC_LAST;
    }

    return type;
}

static void relocDiag(const struct NanoAppInfo *app, const struct RelocEntry *reloc, const char *msg)
{
    size_t symIdx = reloc->info >> 8;
    uint8_t symType = reloc->info;

    ERR("Reloc %zu %s", reloc - app->reloc, msg);
    ERR("INFO:");
    ERR("        Where: 0x%08" PRIX32, reloc->where);
    ERR("        type: %" PRIu8, symType);
    ERR("        sym: %zu", symIdx);
    if (symIdx < app->symtabSize) {
        struct SymtabEntry *sym = &app->symtab[symIdx];
        ERR("        addr: %" PRIu32, sym->addr);
    } else {
        ERR("        addr: <invalid>");
    }
}

static uint8_t fixupReloc(struct NanoAppInfo *app, struct RelocEntry *reloc,
                          struct SymtabEntry *sym, struct NanoRelocEntry *nanoReloc)
{
    uint8_t type;
    uint32_t *addr;
    uint32_t relocOffset = reloc->where;
    uint32_t flashDataOffset = 0;

    if (IS_IN_FLASH(relocOffset)) {
        relocOffset -= FLASH_BASE;
        flashDataOffset = 0;
    } else if (IS_IN_RAM(reloc->where)) {
        relocOffset = reloc->where - RAM_BASE;
        flashDataOffset = app->bin->sect.data_data - FLASH_BASE;
    } else {
        relocDiag(app, reloc, "is neither in RAM nor in FLASH");
        return NANO_RELOC_LAST;
    }

    addr = (uint32_t*)(app->data + flashDataOffset + relocOffset);

    if (flashDataOffset + relocOffset >= app->dataSizeUsed - sizeof(*addr)) {
        relocDiag(app, reloc, "points outside valid data area");
        return NANO_RELOC_LAST;
    }

    switch (reloc->info & 0xFF) {
    case RELOC_TYPE_ABS_S:
    case RELOC_TYPE_ABS_D:
        type = fixupAddress(addr, sym, app->debug);
        break;

    case RELOC_TYPE_SECT:
        if (sym->addr) {
            relocDiag(app, reloc, "has section relocation with non-zero symbol address");
            return NANO_RELOC_LAST;
        }
        type = fixupAddress(addr, sym, app->debug);
        break;
    default:
        relocDiag(app, reloc, "has unknown type");
        type = NANO_RELOC_LAST;
    }

    if (nanoReloc && type != NANO_RELOC_LAST) {
        nanoReloc->ofstInRam = relocOffset;
        nanoReloc->type = type;
    }

    return type;
}

static int handleApp(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint32_t layoutFlags, uint64_t appId, uint32_t appVer, bool verbose)
{
    uint32_t i;
    struct BinHdr *bin;
    int ret = -1;
    struct SectInfo *sect;
    uint8_t *buf = *pbuf;
    uint32_t bufSz = bufUsed * 3 /2;
    struct NanoAppInfo app;

    //make buffer 50% bigger than bufUsed in case relocs grow out of hand
    buf = reallocOrDie(buf, bufSz);
    *pbuf = buf;

    //sanity checks
    bin = (struct BinHdr*)buf;
    if (bufUsed < sizeof(*bin)) {
        ERR("File size too small: %" PRIu32, bufUsed);
        goto out;
    }

    if (bin->hdr.magic != NANOAPP_FW_MAGIC) {
        ERR("Magic value is wrong: found %08" PRIX32"; expected %08" PRIX32, bin->hdr.magic, NANOAPP_FW_MAGIC);
        goto out;
    }

    sect = &bin->sect;
    bin->hdr.appVer = appVer;

    if (!IS_IN_FLASH(sect->rel_start) || !IS_IN_FLASH(sect->rel_end) || !IS_IN_FLASH(sect->data_data)) {
        ERR("relocation data or initialized data is not in FLASH");
        goto out;
    }
    if (!IS_IN_RAM(sect->data_start) || !IS_IN_RAM(sect->data_end) || !IS_IN_RAM(sect->bss_start) ||
        !IS_IN_RAM(sect->bss_end) || !IS_IN_RAM(sect->got_start) || !IS_IN_RAM(sect->got_end)) {
        ERR("data, bss, or got not in ram\n");
        goto out;
    }

    //do some math
    app.reloc = (struct RelocEntry*)(buf + sect->rel_start - FLASH_BASE);
    app.symtab = (struct SymtabEntry*)(buf + sect->rel_end - FLASH_BASE);
    app.relocSize = (sect->rel_end - sect->rel_start) / sizeof(struct RelocEntry);
    app.nanoRelocSize = 0;
    app.symtabSize = (struct SymtabEntry*)(buf + bufUsed) - app.symtab;
    app.data = buf;
    app.dataSizeAllocated = bufSz;
    app.dataSizeUsed = bufUsed;
    app.codeAndRoDataSize = sect->data_data - FLASH_BASE - sizeof(*bin);
    app.codeAndDataSize = sect->rel_start - FLASH_BASE - sizeof(*bin);
    app.debug = verbose;
    app.nanoReloc = NULL;
    app.packedNanoReloc = NULL;

    //sanity
    if (app.relocSize * sizeof(struct RelocEntry) + sect->rel_start != sect->rel_end) {
        ERR("Relocs of nonstandard size");
        goto out;
    }
    if (app.symtabSize * sizeof(struct SymtabEntry) + sect->rel_end != bufUsed + FLASH_BASE) {
        ERR("Syms of nonstandard size");
        goto out;
    }

    //show some info

    if (verbose)
        DBG("Found %zu relocs and a %zu-entry symbol table", app.relocSize, app.symtabSize);

    //handle relocs
    app.nanoReloc = malloc(sizeof(struct NanoRelocEntry[app.relocSize]));
    if (!app.nanoReloc) {
        ERR("Failed to allocate a nano-reloc table\n");
        goto out;
    }

    for (i = 0; i < app.relocSize; i++) {
        struct RelocEntry *reloc = &app.reloc[i];
        struct NanoRelocEntry *nanoReloc = &app.nanoReloc[app.nanoRelocSize];
        uint32_t relocType = reloc->info & 0xff;
        uint32_t whichSym = reloc->info >> 8;
        struct SymtabEntry *sym = &app.symtab[whichSym];

        if (whichSym >= app.symtabSize) {
            relocDiag(&app, reloc, "references a nonexistent symbol");
            goto out;
        }

        if (verbose) {
            const char *seg;

            if (IS_IN_RANGE_E(reloc->where, sect->bss_start, sect->bss_end))
                seg = ".bss";
            else if (IS_IN_RANGE_E(reloc->where, sect->data_start, sect->data_end))
                seg = ".data";
            else if (IS_IN_RANGE_E(reloc->where, sect->got_start, sect->got_end))
                seg = ".got";
            else if (IS_IN_RANGE_E(reloc->where, FLASH_BASE, FLASH_BASE + sizeof(struct BinHdr)))
                seg = "APPHDR";
            else
                seg = "???";

            DBG("Reloc[%3" PRIu32 "]:\n {@0x%08" PRIX32 ", type %3" PRIu32 ", -> sym[%3" PRIu32 "]: {@0x%08" PRIX32 "}, in   %s}",
                i, reloc->where, reloc->info & 0xff, whichSym, sym->addr, seg);
        }
        /* handle relocs inside the header */
        if (IS_IN_FLASH(reloc->where) && reloc->where - FLASH_BASE < sizeof(struct BinHdr) && relocType == RELOC_TYPE_SECT) {
            /* relocs in header are special - runtime corrects for them */
            // binary header generated by objcopy, .napp header and final FW header in flash are of different layout and size.
            // we subtract binary header offset here, so all the entry points are relative to beginning of "sect".
            // FW will use &sect as a base to call these vectors; no more problems with different header sizes;
            // Assumption: offsets between sect & vec, vec & code are the same in all images (or, in a simpler words, { sect, vec, code }
            // must go together). this is enforced by linker script, and maintained by all tools and FW download code in the OS.

            switch (fixupReloc(&app, reloc, sym, NULL)) {
            case NANO_RELOC_TYPE_RAM:
                relocDiag(&app, reloc, "is in APPHDR but relocated to RAM");
                goto out;
            case NANO_RELOC_TYPE_FLASH:
                break;
            default:
                // other error happened; it is already reported
                goto out;
            }

            if (verbose)
                DBG("  -> Nano reloc skipped for in-header reloc");

            continue; /* do not produce an output reloc */
        }

        // any other relocs may only happen in RAM
        if (!IS_IN_RAM(reloc->where)) {
            relocDiag(&app, reloc, "is not in RAM");
            goto out;
        }

        if (fixupReloc(&app, reloc, sym, nanoReloc) != NANO_RELOC_LAST) {
            app.nanoRelocSize++;
            if (verbose)
                DBG("  -> Nano reloc calculated as 0x%08" PRIX32 ",0x%02" PRIX8 "\n", nanoReloc->ofstInRam, nanoReloc->type);
        }
    }

    if (!packNanoRelocs(&app))
        goto out;

    // we're going to write packed relocs; set correct size
    sect->rel_end = sect->rel_start + app.packedNanoRelocSize;

    //adjust headers for easy access (RAM)
    sect->data_start -= RAM_BASE;
    sect->data_end -= RAM_BASE;
    sect->bss_start -= RAM_BASE;
    sect->bss_end -= RAM_BASE;
    sect->got_start -= RAM_BASE;
    sect->got_end -= RAM_BASE;

    //adjust headers for easy access (FLASH)
    sect->data_data -= FLASH_BASE + BINARY_RELOC_OFFSET;
    sect->rel_start -= FLASH_BASE + BINARY_RELOC_OFFSET;
    sect->rel_end -= FLASH_BASE + BINARY_RELOC_OFFSET;

    ret = finalizeAndWrite(&app, out, layoutFlags, appId);
out:
    free(app.nanoReloc);
    free(app.packedNanoReloc);
    return ret;
}

static int handleKey(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint32_t layoutFlags, uint64_t appId, uint64_t keyId)
{
    uint8_t *buf = *pbuf;
    struct KeyInfo ki = { .data = keyId };
    bool good = true;

    struct ImageHeader outHeader = {
        .aosp = (struct nano_app_binary_t) {
            .header_version = 1,
            .magic = NANOAPP_AOSP_MAGIC,
            .app_id = appId,
        },
        .layout = (struct ImageLayout) {
            .magic = GOOGLE_LAYOUT_MAGIC,
            .version = 1,
            .payload = LAYOUT_KEY,
            .flags = layoutFlags,
        },
    };

    good = good && fwrite(&outHeader, sizeof(outHeader), 1, out) == 1;
    good = good && fwrite(&ki, sizeof(ki), 1, out) ==  1;
    good = good && fwrite(buf, bufUsed, 1, out) == 1;

    return good ? 0 : 2;
}

static int handleOs(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint32_t layoutFlags, bool bare)
{
    uint8_t *buf = *pbuf;
    bool good;

    struct OsUpdateHdr os = {
        .magic = OS_UPDT_MAGIC,
        .marker = OS_UPDT_MARKER_INPROGRESS,
        .size = bufUsed
    };

    struct ImageHeader outHeader = {
        .aosp = (struct nano_app_binary_t) {
            .header_version = 1,
            .magic = NANOAPP_AOSP_MAGIC,
        },
        .layout = (struct ImageLayout) {
            .magic = GOOGLE_LAYOUT_MAGIC,
            .version = 1,
            .payload = LAYOUT_OS,
            .flags = layoutFlags,
        },
    };

    if (!bare)
        good = fwrite(&outHeader, sizeof(outHeader), 1, out) == 1;
    else
        good = fwrite(&os, sizeof(os), 1, out) == 1;
    good = good && fwrite(buf, bufUsed, 1, out) == 1;

    return good ? 0 : 2;
}

int main(int argc, char **argv)
{
    uint32_t bufUsed = 0;
    bool verbose = false;
    uint8_t *buf = NULL;
    uint64_t appId = 0;
    uint64_t keyId = 0;
    uint32_t appVer = 0;
    uint32_t layoutId = 0;
    uint32_t layoutFlags = 0;
    int ret = -1;
    uint32_t *u32Arg = NULL;
    uint64_t *u64Arg = NULL;
    const char **strArg = NULL;
    const char *appName = argv[0];
    int posArgCnt = 0;
    const char *posArg[2] = { NULL };
    FILE *out = NULL;
    const char *layoutName = "app";
    const char *prev = NULL;
    bool bareData = false;

    for (int i = 1; i < argc; i++) {
        char *end = NULL;
        if (argv[i][0] == '-') {
            prev = argv[i];
            if (!strcmp(argv[i], "-v"))
                verbose = true;
            else if (!strcmp(argv[i], "-r"))
                bareData = true;
            else if (!strcmp(argv[i], "-a"))
                u64Arg = &appId;
            else if (!strcmp(argv[i], "-e"))
                u32Arg = &appVer;
            else if (!strcmp(argv[i], "-k"))
                u64Arg = &keyId;
            else if (!strcmp(argv[i], "-n"))
                strArg = &layoutName;
            else if (!strcmp(argv[i], "-i"))
                u32Arg = &layoutId;
            else if (!strcmp(argv[i], "-f"))
                u32Arg = &layoutFlags;
            else
                fatalUsage(appName, "unknown argument", argv[i]);
        } else {
            if (u64Arg) {
                uint64_t tmp = strtoull(argv[i], &end, 16);
                if (*end == '\0')
                    *u64Arg = tmp;
                u64Arg = NULL;
            } else if (u32Arg) {
                uint32_t tmp = strtoul(argv[i], &end, 16);
                if (*end == '\0')
                    *u32Arg = tmp;
                u32Arg = NULL;
            } else if (strArg) {
                    *strArg = argv[i];
                strArg = NULL;
            } else {
                if (posArgCnt < 2)
                    posArg[posArgCnt++] = argv[i];
                else
                    fatalUsage(appName, "too many positional arguments", argv[i]);
            }
            prev = NULL;
        }
    }
    if (prev)
        fatalUsage(appName, "missing argument after", prev);

    if (!posArgCnt)
        fatalUsage(appName, "missing input file name", NULL);

    if (!layoutId) {
        if (strcmp(layoutName, "app") == 0)
            layoutId = LAYOUT_APP;
        else if (strcmp(layoutName, "os") == 0)
            layoutId = LAYOUT_OS;
        else if (strcmp(layoutName, "key") == 0)
            layoutId = LAYOUT_KEY;
        else
            fatalUsage(appName, "Invalid layout name", layoutName);
    }

    if (layoutId == LAYOUT_APP && !appId)
        fatalUsage(appName, "App layout requires app ID", NULL);
    if (layoutId == LAYOUT_KEY && !keyId)
        fatalUsage(appName, "Key layout requires key ID", NULL);
    if (layoutId == LAYOUT_OS && (keyId || appId))
        fatalUsage(appName, "OS layout does not need any ID", NULL);

    if (!posArg[1]) {
        out = stdout;
        stdlog = stderr;
    } else {
        out = fopen(posArg[1], "w");
        stdlog = stdout;
    }
    if (!out)
        fatalUsage(appName, "failed to create/open output file", posArg[1]);

    buf = loadFile(posArg[0], &bufUsed);
    DBG("Read %" PRIu32 " bytes from %s", bufUsed, posArg[0]);

    switch(layoutId) {
    case LAYOUT_APP:
        ret = handleApp(&buf, bufUsed, out, layoutFlags, appId, appVer, verbose);
        break;
    case LAYOUT_KEY:
        ret = handleKey(&buf, bufUsed, out, layoutFlags, appId, keyId);
        break;
    case LAYOUT_OS:
        ret = handleOs(&buf, bufUsed, out, layoutFlags, bareData);
        break;
    }

    free(buf);
    fclose(out);
    return ret;
}
