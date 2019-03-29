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
#include <gelf.h>
#include <libelf.h>
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

#define FLASH_BASE  0x10000000
#define RAM_BASE    0x80000000

#define FLASH_SIZE  0x10000000  //256MB ought to be enough for everyone
#define RAM_SIZE    0x10000000  //256MB ought to be enough for everyone

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

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(ary) (sizeof(ary) / sizeof((ary)[0]))
#endif

#define DBG(fmt, ...) printf(fmt "\n", ##__VA_ARGS__)
#define ERR(fmt, ...) fprintf(stderr, fmt "\n", ##__VA_ARGS__)

// Prints the given message followed by the most recent libelf error
#define ELF_ERR(fmt, ...) ERR(fmt ": %s\n", ##__VA_ARGS__, elf_errmsg(-1))

struct ElfAppSection {
    void  *data;
    size_t size;
};

struct ElfNanoApp {
    struct ElfAppSection flash;
    struct ElfAppSection data;
    struct ElfAppSection relocs;
    struct ElfAppSection symtab;

    // Not parsed from file, but constructed via genElfNanoRelocs
    struct ElfAppSection packedNanoRelocs;
};

static void fatalUsage(const char *name, const char *msg, const char *arg)
{
    if (msg && arg)
        fprintf(stderr, "Error: %s: %s\n\n", msg, arg);
    else if (msg)
        fprintf(stderr, "Error: %s\n\n", msg);

    fprintf(stderr, "USAGE: %s [-v] [-k <key id>] [-a <app id>] [-r] [-n <layout name>] [-i <layout id>] <input file> [<output file>]\n"
                    "       -v               : be verbose\n"
                    "       -n <layout name> : app, os, key\n"
                    "       -i <layout id>   : 1 (app), 2 (key), 3 (os)\n"
                    "       -f <layout flags>: 16-bit hex value, stored as layout-specific flags\n"
                    "       -a <app ID>      : 64-bit hex number != 0\n"
                    "       -e <app version> : 32-bit hex number\n"
                    "       -k <key ID>      : 64-bit hex number != 0\n"
                    "       -r               : bare (no AOSP header); used only for inner OS image generation\n"
                    "       -s               : treat input as statically linked ELF (app layout only)\n"
                    "       layout ID and layout name control the same parameter, so only one of them needs to be used\n"
                    , name);
    exit(1);
}

static uint8_t *packNanoRelocs(struct NanoRelocEntry *nanoRelocs, uint32_t outNumRelocs, uint32_t *finalPackedNanoRelocSz, bool verbose)
{
    uint32_t i, j, k;
    uint8_t *packedNanoRelocs;
    uint32_t packedNanoRelocSz;
    uint32_t lastOutType = 0, origin = 0;

    //sort by type and then offset
    for (i = 0; i < outNumRelocs; i++) {
        struct NanoRelocEntry t;

        for (k = i, j = k + 1; j < outNumRelocs; j++) {
            if (nanoRelocs[j].type > nanoRelocs[k].type)
                continue;
            if ((nanoRelocs[j].type < nanoRelocs[k].type) || (nanoRelocs[j].ofstInRam < nanoRelocs[k].ofstInRam))
                k = j;
        }
        memcpy(&t, nanoRelocs + i, sizeof(struct NanoRelocEntry));
        memcpy(nanoRelocs + i, nanoRelocs + k, sizeof(struct NanoRelocEntry));
        memcpy(nanoRelocs + k, &t, sizeof(struct NanoRelocEntry));

        if (verbose)
            fprintf(stderr, "SortedReloc[%3" PRIu32 "] = {0x%08" PRIX32 ",0x%02" PRIX8 "}\n", i, nanoRelocs[i].ofstInRam, nanoRelocs[i].type);
    }

    //produce output nanorelocs in packed format
    packedNanoRelocs = malloc(outNumRelocs * 6); //definitely big enough
    packedNanoRelocSz = 0;
    for (i = 0; i < outNumRelocs; i++) {
        uint32_t displacement;

        if (lastOutType != nanoRelocs[i].type) {  //output type if ti changed
            if (nanoRelocs[i].type - lastOutType == 1) {
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_RELOC_TYPE_NEXT;
                if (verbose)
                    fprintf(stderr, "Out: RelocTC (1) // to 0x%02" PRIX8 "\n", nanoRelocs[i].type);
            }
            else {
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_RELOC_TYPE_CHG;
                packedNanoRelocs[packedNanoRelocSz++] = nanoRelocs[i].type - lastOutType - 1;
                if (verbose)
                    fprintf(stderr, "Out: RelocTC (0x%02" PRIX8 ")  // to 0x%02" PRIX8 "\n", (uint8_t)(nanoRelocs[i].type - lastOutType - 1), nanoRelocs[i].type);
            }
            lastOutType = nanoRelocs[i].type;
            origin = 0;
        }
        displacement = nanoRelocs[i].ofstInRam - origin;
        origin = nanoRelocs[i].ofstInRam + 4;
        if (displacement & 3) {
            fprintf(stderr, "Unaligned relocs are not possible!\n");
            exit(-5);
        }
        displacement /= 4;

        //might be start of a run. look into that
        if (!displacement) {
            for (j = 1; j + i < outNumRelocs && j < MAX_RUN_LEN && nanoRelocs[j + i].type == lastOutType && nanoRelocs[j + i].ofstInRam - nanoRelocs[j + i - 1].ofstInRam == 4; j++);
            if (j >= MIN_RUN_LEN) {
                if (verbose)
                    fprintf(stderr, "Out: Reloc0  x%" PRIX32 "\n", j);
                packedNanoRelocs[packedNanoRelocSz++] = TOKEN_CONSECUTIVE;
                packedNanoRelocs[packedNanoRelocSz++] = j - MIN_RUN_LEN;
                origin = nanoRelocs[j + i - 1].ofstInRam + 4;  //reset origin to last one
                i += j - 1;  //loop will increment anyways, hence +1
                continue;
            }
        }

        //produce output
        if (displacement <= MAX_8_BIT_NUM) {
            if (verbose)
                fprintf(stderr, "Out: Reloc8  0x%02" PRIX32 "\n", displacement);
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
        }
        else if (displacement <= MAX_16_BIT_NUM) {
            if (verbose)
                fprintf(stderr, "Out: Reloc16 0x%06" PRIX32 "\n", displacement);
                        displacement -= MAX_8_BIT_NUM;
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_16BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
        }
        else if (displacement <= MAX_24_BIT_NUM) {
            if (verbose)
                fprintf(stderr, "Out: Reloc24 0x%08" PRIX32 "\n", displacement);
                        displacement -= MAX_16_BIT_NUM;
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_24BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 16;
        }
        else  {
            if (verbose)
                fprintf(stderr, "Out: Reloc32 0x%08" PRIX32 "\n", displacement);
            packedNanoRelocs[packedNanoRelocSz++] = TOKEN_32BIT_OFST;
            packedNanoRelocs[packedNanoRelocSz++] = displacement;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 8;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 16;
            packedNanoRelocs[packedNanoRelocSz++] = displacement >> 24;
        }
    }

    *finalPackedNanoRelocSz = packedNanoRelocSz;
    return packedNanoRelocs;
}

static int finalizeAndWrite(uint8_t *buf, uint32_t bufUsed, uint32_t bufSz, FILE *out, uint32_t layoutFlags, uint64_t appId)
{
    int ret;
    struct AppInfo app;
    struct SectInfo *sect;
    struct BinHdr *bin = (struct BinHdr *) buf;
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
    uint32_t dataOffset = sizeof(outHeader) + sizeof(app);
    uint32_t hdrDiff = dataOffset - sizeof(*bin);
    app.sect = bin->sect;
    app.vec  = bin->vec;

    assertMem(bufUsed + hdrDiff, bufSz);

    memmove(buf + dataOffset, buf + sizeof(*bin), bufUsed - sizeof(*bin));
    bufUsed += hdrDiff;
    memcpy(buf, &outHeader, sizeof(outHeader));
    memcpy(buf + sizeof(outHeader), &app, sizeof(app));
    sect = &app.sect;

    //if we have any bytes to output, show stats
    if (bufUsed) {
        uint32_t codeAndRoDataSz = sect->data_data;
        uint32_t relocsSz = sect->rel_end - sect->rel_start;
        uint32_t gotSz = sect->got_end - sect->data_start;
        uint32_t bssSz = sect->bss_end - sect->bss_start;

        fprintf(stderr,"Final binary size %" PRIu32 " bytes\n", bufUsed);
        fprintf(stderr, "\n");
        fprintf(stderr, "       FW header size (flash):      %6zu bytes\n", FLASH_RELOC_OFFSET);
        fprintf(stderr, "       Code + RO data (flash):      %6" PRIu32 " bytes\n", codeAndRoDataSz);
        fprintf(stderr, "       Relocs (flash):              %6" PRIu32 " bytes\n", relocsSz);
        fprintf(stderr, "       GOT + RW data (flash & RAM): %6" PRIu32 " bytes\n", gotSz);
        fprintf(stderr, "       BSS (RAM):                   %6" PRIu32 " bytes\n", bssSz);
        fprintf(stderr, "\n");
        fprintf(stderr,"Runtime flash use: %" PRIu32 " bytes\n", (uint32_t)(codeAndRoDataSz + relocsSz + gotSz + FLASH_RELOC_OFFSET));
        fprintf(stderr,"Runtime RAM use: %" PRIu32 " bytes\n", gotSz + bssSz);
    }

    ret = fwrite(buf, bufUsed, 1, out) == 1 ? 0 : 2;
    if (ret)
        fprintf(stderr, "Failed to write output file: %s\n", strerror(errno));

    return ret;
}

static int handleApp(uint8_t **pbuf, uint32_t bufUsed, FILE *out, uint32_t layoutFlags, uint64_t appId, uint32_t appVer, bool verbose)
{
    uint32_t i, numRelocs, numSyms, outNumRelocs = 0, packedNanoRelocSz;
    struct NanoRelocEntry *nanoRelocs = NULL;
    struct RelocEntry *relocs;
    struct SymtabEntry *syms;
    uint8_t *packedNanoRelocs;
    uint32_t t;
    struct BinHdr *bin;
    int ret = -1;
    struct SectInfo *sect;
    uint8_t *buf = *pbuf;
    uint32_t bufSz = bufUsed * 3 /2;

    //make buffer 50% bigger than bufUsed in case relocs grow out of hand
    buf = reallocOrDie(buf, bufSz);
    *pbuf = buf;

    //sanity checks
    bin = (struct BinHdr*)buf;
    if (bufUsed < sizeof(*bin)) {
        fprintf(stderr, "File size too small\n");
        goto out;
    }

    if (bin->hdr.magic != NANOAPP_FW_MAGIC) {
        fprintf(stderr, "Magic value is wrong: found %08" PRIX32
                        "; expected %08" PRIX32 "\n",
                        bin->hdr.magic, NANOAPP_FW_MAGIC);
        goto out;
    }

    sect = &bin->sect;
    bin->hdr.appVer = appVer;

    //do some math
    relocs = (struct RelocEntry*)(buf + sect->rel_start - FLASH_BASE);
    syms = (struct SymtabEntry*)(buf + sect->rel_end - FLASH_BASE);
    numRelocs = (sect->rel_end - sect->rel_start) / sizeof(struct RelocEntry);
    numSyms = (bufUsed + FLASH_BASE - sect->rel_end) / sizeof(struct SymtabEntry);

    //sanity
    if (numRelocs * sizeof(struct RelocEntry) + sect->rel_start != sect->rel_end) {
        fprintf(stderr, "Relocs of nonstandard size\n");
        goto out;
    }
    if (numSyms * sizeof(struct SymtabEntry) + sect->rel_end != bufUsed + FLASH_BASE) {
        fprintf(stderr, "Syms of nonstandard size\n");
        goto out;
    }

    //show some info
    fprintf(stderr, "\nRead %" PRIu32 " bytes of binary.\n", bufUsed);

    if (verbose)
        fprintf(stderr, "Found %" PRIu32 " relocs and a %" PRIu32 "-entry symbol table\n", numRelocs, numSyms);

    //handle relocs
    nanoRelocs = malloc(sizeof(struct NanoRelocEntry[numRelocs]));
    if (!nanoRelocs) {
        fprintf(stderr, "Failed to allocate a nano-reloc table\n");
        goto out;
    }

    for (i = 0; i < numRelocs; i++) {
        uint32_t relocType = relocs[i].info & 0xff;
        uint32_t whichSym = relocs[i].info >> 8;
        uint32_t *valThereP;

        if (whichSym >= numSyms) {
            fprintf(stderr, "Reloc %" PRIu32 " references a nonexistent symbol!\n"
                            "INFO:\n"
                            "        Where: 0x%08" PRIX32 "\n"
                            "        type: %" PRIu32 "\n"
                            "        sym: %" PRIu32 "\n",
                i, relocs[i].where, relocs[i].info & 0xff, whichSym);
            goto out;
        }

        if (verbose) {
            const char *seg;

            fprintf(stderr, "Reloc[%3" PRIu32 "]:\n {@0x%08" PRIX32 ", type %3" PRIu32 ", -> sym[%3" PRIu32 "]: {@0x%08" PRIX32 "}, ",
                i, relocs[i].where, relocs[i].info & 0xff, whichSym, syms[whichSym].addr);

            if (IS_IN_RANGE_E(relocs[i].where, sect->bss_start, sect->bss_end))
                seg = ".bss";
            else if (IS_IN_RANGE_E(relocs[i].where, sect->data_start, sect->data_end))
                seg = ".data";
            else if (IS_IN_RANGE_E(relocs[i].where, sect->got_start, sect->got_end))
                seg = ".got";
            else if (IS_IN_RANGE_E(relocs[i].where, FLASH_BASE, FLASH_BASE + sizeof(struct BinHdr)))
                seg = "APPHDR";
            else
                seg = "???";

            fprintf(stderr, "in   %s}\n", seg);
        }
        /* handle relocs inside the header */
        if (IS_IN_FLASH(relocs[i].where) && relocs[i].where - FLASH_BASE < sizeof(struct BinHdr) && relocType == RELOC_TYPE_SECT) {
            /* relocs in header are special - runtime corrects for them */
            if (syms[whichSym].addr) {
                fprintf(stderr, "Weird in-header sect reloc %" PRIu32 " to symbol %" PRIu32 " with nonzero addr 0x%08" PRIX32 "\n",
                        i, whichSym, syms[whichSym].addr);
                goto out;
            }

            valThereP = (uint32_t*)(buf + relocs[i].where - FLASH_BASE);
            if (!IS_IN_FLASH(*valThereP)) {
                fprintf(stderr, "In-header reloc %" PRIu32 " of location 0x%08" PRIX32 " is outside of FLASH!\n"
                                "INFO:\n"
                                "        type: %" PRIu32 "\n"
                                "        sym: %" PRIu32 "\n"
                                "        Sym Addr: 0x%08" PRIX32 "\n",
                                i, relocs[i].where, relocType, whichSym, syms[whichSym].addr);
                goto out;
            }

            // binary header generated by objcopy, .napp header and final FW header in flash are of different size.
            // we subtract binary header offset here, so all the entry points are relative to beginning of "sect".
            // FW will use &sect as a base to call these vectors; no more problems with different header sizes;
            // Assumption: offsets between sect & vec, vec & code are the same in all images (or, in a simpler words, { sect, vec, code }
            // must go together). this is enforced by linker script, and maintained by all tools and FW download code in the OS.
            *valThereP -= FLASH_BASE + BINARY_RELOC_OFFSET;

            if (verbose)
                fprintf(stderr, "  -> Nano reloc skipped for in-header reloc\n");

            continue; /* do not produce an output reloc */
        }

        if (!IS_IN_RAM(relocs[i].where)) {
            fprintf(stderr, "In-header reloc %" PRIu32 " of location 0x%08" PRIX32 " is outside of RAM!\n"
                            "INFO:\n"
                            "        type: %" PRIu32 "\n"
                            "        sym: %" PRIu32 "\n"
                            "        Sym Addr: 0x%08" PRIX32 "\n",
                            i, relocs[i].where, relocType, whichSym, syms[whichSym].addr);
            goto out;
        }

        valThereP = (uint32_t*)(buf + relocs[i].where + sect->data_data - RAM_BASE - FLASH_BASE);

        nanoRelocs[outNumRelocs].ofstInRam = relocs[i].where - RAM_BASE;

        switch (relocType) {
            case RELOC_TYPE_ABS_S:
            case RELOC_TYPE_ABS_D:
                t = *valThereP;

                (*valThereP) += syms[whichSym].addr;

                if (IS_IN_FLASH(syms[whichSym].addr)) {
                    (*valThereP) -= FLASH_BASE + BINARY_RELOC_OFFSET;
                    nanoRelocs[outNumRelocs].type = NANO_RELOC_TYPE_FLASH;
                }
                else if (IS_IN_RAM(syms[whichSym].addr)) {
                    (*valThereP) -= RAM_BASE;
                    nanoRelocs[outNumRelocs].type = NANO_RELOC_TYPE_RAM;
                }
                else {
                    fprintf(stderr, "Weird reloc %" PRIu32 " to symbol %" PRIu32 " in unknown memory space (addr 0x%08" PRIX32 ")\n",
                            i, whichSym, syms[whichSym].addr);
                    goto out;
                }
                if (verbose)
                    fprintf(stderr, "  -> Abs reference fixed up 0x%08" PRIX32 " -> 0x%08" PRIX32 "\n", t, *valThereP);
                break;

            case RELOC_TYPE_SECT:
                if (syms[whichSym].addr) {
                    fprintf(stderr, "Weird sect reloc %" PRIu32 " to symbol %" PRIu32 " with nonzero addr 0x%08" PRIX32 "\n",
                            i, whichSym, syms[whichSym].addr);
                    goto out;
                }

                t = *valThereP;

                if (IS_IN_FLASH(*valThereP)) {
                    nanoRelocs[outNumRelocs].type = NANO_RELOC_TYPE_FLASH;
                    *valThereP -= FLASH_BASE + BINARY_RELOC_OFFSET;
                }
                else if (IS_IN_RAM(*valThereP)) {
                    nanoRelocs[outNumRelocs].type = NANO_RELOC_TYPE_RAM;
                    *valThereP -= RAM_BASE;
                }
                else {
                    fprintf(stderr, "Weird sec reloc %" PRIu32 " to symbol %" PRIu32
                                    " in unknown memory space (addr 0x%08" PRIX32 ")\n",
                                    i, whichSym, *valThereP);
                    goto out;
                }
                if (verbose)
                    fprintf(stderr, "  -> Sect reference fixed up 0x%08" PRIX32 " -> 0x%08" PRIX32 "\n", t, *valThereP);
                break;

            default:
                fprintf(stderr, "Weird reloc %" PRIX32 " type %" PRIX32 " to symbol %" PRIX32 "\n", i, relocType, whichSym);
                goto out;
        }

        if (verbose)
            fprintf(stderr, "  -> Nano reloc calculated as 0x%08" PRIX32 ",0x%02" PRIX8 "\n", nanoRelocs[i].ofstInRam, nanoRelocs[i].type);
        outNumRelocs++;
    }

    packedNanoRelocs = packNanoRelocs(nanoRelocs, outNumRelocs, &packedNanoRelocSz, verbose);

    //overwrite original relocs and symtab with nanorelocs and adjust sizes
    memcpy(relocs, packedNanoRelocs, packedNanoRelocSz);
    bufUsed -= sizeof(struct RelocEntry[numRelocs]);
    bufUsed -= sizeof(struct SymtabEntry[numSyms]);
    bufUsed += packedNanoRelocSz;
    assertMem(bufUsed, bufSz);
    sect->rel_end = sect->rel_start + packedNanoRelocSz;

    //sanity
    if (sect->rel_end - FLASH_BASE != bufUsed) {
        fprintf(stderr, "Relocs end and file end not coincident\n");
        goto out;
    }

    //adjust headers for easy access (RAM)
    if (!IS_IN_RAM(sect->data_start) || !IS_IN_RAM(sect->data_end) || !IS_IN_RAM(sect->bss_start) ||
        !IS_IN_RAM(sect->bss_end) || !IS_IN_RAM(sect->got_start) || !IS_IN_RAM(sect->got_end)) {
        fprintf(stderr, "data, bss, or got not in ram\n");
        goto out;
    }
    sect->data_start -= RAM_BASE;
    sect->data_end -= RAM_BASE;
    sect->bss_start -= RAM_BASE;
    sect->bss_end -= RAM_BASE;
    sect->got_start -= RAM_BASE;
    sect->got_end -= RAM_BASE;

    //adjust headers for easy access (FLASH)
    if (!IS_IN_FLASH(sect->data_data) || !IS_IN_FLASH(sect->rel_start) || !IS_IN_FLASH(sect->rel_end)) {
        fprintf(stderr, "data.data, or rel not in flash\n");
        goto out;
    }
    sect->data_data -= FLASH_BASE + BINARY_RELOC_OFFSET;
    sect->rel_start -= FLASH_BASE + BINARY_RELOC_OFFSET;
    sect->rel_end -= FLASH_BASE + BINARY_RELOC_OFFSET;

    ret = finalizeAndWrite(buf, bufUsed, bufSz, out, layoutFlags, appId);
out:
    free(nanoRelocs);
    return ret;
}

static void elfExtractSectionPointer(const Elf_Data *data, const char *name, struct ElfNanoApp *app)
{
    // Maps section names to their byte offset in struct ElfNanoApp. Note that
    // this assumes that the linker script puts text/code in the .flash section,
    // RW data in .data, that relocs for .data are included in .rel.data, and
    // the symbol table is emitted in .symtab
    const struct SectionMap {
        const char *name;
        size_t offset;
    } sectionMap[] = {
        {
            .name = ".flash",
            .offset = offsetof(struct ElfNanoApp, flash),
        },
        {
            .name = ".data",
            .offset = offsetof(struct ElfNanoApp, data),
        },
        {
            .name = ".rel.data",
            .offset = offsetof(struct ElfNanoApp, relocs),
        },
        {
            .name = ".symtab",
            .offset = offsetof(struct ElfNanoApp, symtab),
        },
    };
    struct ElfAppSection *appSection;
    uint8_t *appBytes = (uint8_t *) app;

    for (size_t i = 0; i < ARRAY_SIZE(sectionMap); i++) {
        if (strcmp(name, sectionMap[i].name) != 0) {
            continue;
        }
        appSection = (struct ElfAppSection *) &appBytes[sectionMap[i].offset];

        appSection->data = data->d_buf;
        appSection->size = data->d_size;

        DBG("Found section %s with size %zu", name, appSection->size);
        break;
    }
}

// Populates a struct ElfNanoApp with data parsed from the ELF
static bool elfParse(Elf *elf, struct ElfNanoApp *app)
{
    size_t shdrstrndx;
    Elf_Scn *scn = NULL;
    GElf_Shdr shdr;
    char *sectionName;
    Elf_Data *elf_data;

    memset(app, 0, sizeof(*app));
    if (elf_getshdrstrndx(elf, &shdrstrndx) != 0) {
        ELF_ERR("Couldn't get section name string table index");
        return false;
    }

    while ((scn = elf_nextscn(elf, scn)) != NULL) {
        if (gelf_getshdr(scn, &shdr) != &shdr) {
            ELF_ERR("Error getting section header");
            return false;
        }
        sectionName = elf_strptr(elf, shdrstrndx, shdr.sh_name);

        elf_data = elf_getdata(scn, NULL);
        if (!elf_data) {
            ELF_ERR("Error getting data for section %s", sectionName);
            return false;
        }

        elfExtractSectionPointer(elf_data, sectionName, app);
    }

    return true;
}

static bool loadNanoappElfFile(const char *fileName, struct ElfNanoApp *app)
{
    int fd;
    Elf *elf;

    if (elf_version(EV_CURRENT) == EV_NONE) {
        ELF_ERR("Failed to initialize ELF library");
        return false;
    }

    fd = open(fileName, O_RDONLY, 0);
    if (fd < 0) {
        ERR("Failed to open file %s for reading: %s", fileName, strerror(errno));
        return false;
    }

    elf = elf_begin(fd, ELF_C_READ, NULL);
    if (elf == NULL) {
        ELF_ERR("Failed to open ELF");
        return false;
    }

    if (!elfParse(elf, app)) {
        ERR("Failed to parse ELF file");
        return false;
    }

    return true;
}

// Subtracts the fixed memory region offset from an absolute address and returns
// the associated NANO_RELOC_* value, or NANO_RELOC_LAST if the address is not
// in the expected range.
// Not strictly tied to ELF usage, but handled slightly differently.
static uint8_t fixupAddrElf(uint32_t *addr)
{
    uint8_t type;

    // TODO: this assumes that the host running this tool has the same
    // endianness as the image file/target processor
    if (IS_IN_FLASH(*addr)) {
        DBG("Fixup addr 0x%08" PRIX32 " (flash) --> 0x%08" PRIX32, *addr,
            (uint32_t) (*addr - (FLASH_BASE + BINARY_RELOC_OFFSET)));
        *addr -= FLASH_BASE + BINARY_RELOC_OFFSET;
        type = NANO_RELOC_TYPE_FLASH;
    } else if (IS_IN_RAM(*addr)) {
        DBG("Fixup addr 0x%08" PRIX32 " (ram)   --> 0x%08" PRIX32, *addr,
            *addr - RAM_BASE);
        *addr -= RAM_BASE;
        type = NANO_RELOC_TYPE_RAM;
    } else {
        DBG("Error: invalid address 0x%08" PRIX32, *addr);
        type = NANO_RELOC_LAST;
    }

    return type;
}

// Fixup addresses in the header to be relative. Not strictly tied to the ELF
// format, but used only in that program flow in the current implementation.
static bool fixupHeaderElf(const struct ElfNanoApp *app)
{
    struct BinHdr *hdr = (struct BinHdr *) app->flash.data;

    DBG("Appyling fixups to header");
    if (fixupAddrElf(&hdr->sect.data_start) != NANO_RELOC_TYPE_RAM ||
        fixupAddrElf(&hdr->sect.data_end)   != NANO_RELOC_TYPE_RAM ||
        fixupAddrElf(&hdr->sect.bss_start)  != NANO_RELOC_TYPE_RAM ||
        fixupAddrElf(&hdr->sect.bss_end)    != NANO_RELOC_TYPE_RAM ||
        fixupAddrElf(&hdr->sect.got_start)  != NANO_RELOC_TYPE_RAM ||
        fixupAddrElf(&hdr->sect.got_end)    != NANO_RELOC_TYPE_RAM) {
        ERR(".data, .bss, or .got not in RAM address space!");
        return false;
    }

    if (fixupAddrElf(&hdr->sect.rel_start) != NANO_RELOC_TYPE_FLASH ||
        fixupAddrElf(&hdr->sect.rel_end)   != NANO_RELOC_TYPE_FLASH ||
        fixupAddrElf(&hdr->sect.data_data) != NANO_RELOC_TYPE_FLASH) {
        ERR(".data loadaddr, or .relocs not in flash address space!");
        return false;
    }

    if (fixupAddrElf(&hdr->vec.init)   != NANO_RELOC_TYPE_FLASH ||
        fixupAddrElf(&hdr->vec.end)    != NANO_RELOC_TYPE_FLASH ||
        fixupAddrElf(&hdr->vec.handle) != NANO_RELOC_TYPE_FLASH) {
        ERR("Entry point(s) not in flash address space!");
        return false;
    }

    return true;
}

// Fixup addresses in .data, .init_array/.fini_array, and .got, and generates
// packed array of nano reloc entries. The app header must have already been
// fixed up.
static bool genElfNanoRelocs(struct ElfNanoApp *app, bool verbose)
{
    const struct BinHdr *hdr = (const struct BinHdr *) app->flash.data;
    const struct SectInfo *sect = &hdr->sect;
    bool success = false;

    size_t numDataRelocs = app->relocs.size / sizeof(Elf32_Rel);
    size_t gotCount = (sect->got_end - sect->got_start) / sizeof(uint32_t);
    size_t numInitFuncs  = (sect->bss_start - sect->data_end) / sizeof(uint32_t);

    size_t totalRelocCount = (numDataRelocs + numInitFuncs + gotCount);
    struct NanoRelocEntry *nanoRelocs = malloc(
        totalRelocCount * sizeof(struct NanoRelocEntry));
    if (!nanoRelocs) {
        ERR("Couldn't allocate memory for nano relocs! Needed %zu bytes",
            totalRelocCount * sizeof(struct NanoRelocEntry));
        return false;
    }

    uint8_t *data = app->data.data;
    const Elf32_Rel *relocs = (const Elf32_Rel *) app->relocs.data;
    const Elf32_Sym *syms   = (const Elf32_Sym *) app->symtab.data;
    size_t numRelocs = 0;

    DBG("Parsing relocs for .data (%zu):", numDataRelocs);
    for (size_t i = 0; i < numDataRelocs; i++) {
        uint32_t type = ELF32_R_TYPE(relocs[i].r_info);
        uint32_t sym = ELF32_R_SYM(relocs[i].r_info);

        DBG(" [%3zu] 0x%08" PRIx32 " type %2" PRIu32 " symIdx %3" PRIu32
            " --> 0x%08" PRIx32, i, relocs[i].r_offset, type, sym,
            syms[sym].st_value);
        // Note that R_ARM_TARGET1 is used for .init_array/.fini_array support,
        // and can be interpreted either as ABS32 or REL32, depending on the
        // runtime; we expect it to be ABS32.
        if (type == R_ARM_ABS32 || type == R_ARM_TARGET1) {
            if (!IS_IN_RAM(relocs[i].r_offset)) {
                ERR("Reloc for .data not in RAM address range!");
                goto out;
            }
            uint32_t offset = relocs[i].r_offset - RAM_BASE;
            uint32_t *addr = (uint32_t *) &data[offset];

            nanoRelocs[numRelocs].type = fixupAddrElf(addr);
            nanoRelocs[numRelocs].ofstInRam = offset;
            numRelocs++;
        } else {
            // TODO: Assuming that the ELF only contains absolute addresses in
            // the .data section; may need to handle other relocation types in
            // the future
            ERR("Error: Unexpected reloc type %" PRIu32 " at index %zu",
                type, i);
            goto out;
        }
    }

    DBG("Updating GOT entries (%zu):", gotCount);
    for (uint32_t offset = sect->got_start; offset < sect->got_end;
            offset += sizeof(uint32_t)) {
        uint32_t *addr = (uint32_t *) &data[offset];
        // Skip values that are set to 0, these seem to be padding (?)
        if (*addr) {
            nanoRelocs[numRelocs].type = fixupAddrElf(addr);
            nanoRelocs[numRelocs].ofstInRam = offset;
            numRelocs++;
        }
    }

    uint32_t packedNanoRelocSz = 0;
    app->packedNanoRelocs.data = packNanoRelocs(
        nanoRelocs, numRelocs, &packedNanoRelocSz, verbose);
    app->packedNanoRelocs.size = packedNanoRelocSz;
    success = true;
out:
    free(nanoRelocs);
    return success;
}

static int handleAppStatic(const char *fileName, FILE *out, uint32_t layoutFlags, uint64_t appId, uint32_t appVer, bool verbose)
{
    struct ElfNanoApp app;

    if (!loadNanoappElfFile(fileName, &app)
            || !fixupHeaderElf(&app)
            || !genElfNanoRelocs(&app, verbose)) {
        exit(2);
    }

    // Construct a single contiguous buffer, with extra room to fit the
    // ImageHeader that will be prepended by finalizeAndWrite(). Note that this
    // will allocate a bit more space than is needed, because some of the data
    // from BinHdr will get discarded.
    // TODO: this should be refactored to just write the binary components in
    // order rather than allocating a big buffer, and moving data around
    size_t bufSize = app.flash.size + app.data.size + app.packedNanoRelocs.size
        + sizeof(struct ImageHeader);
    uint8_t *buf = malloc(bufSize);
    if (!buf) {
        ERR("Failed to allocate %zu bytes for final app", bufSize);
        exit(2);
    }

    size_t offset = 0;
    memcpy(buf, app.flash.data, app.flash.size);
    offset += app.flash.size;
    memcpy(&buf[offset], app.data.data, app.data.size);
    offset += app.data.size;
    memcpy(&buf[offset], app.packedNanoRelocs.data, app.packedNanoRelocs.size);
    offset += app.packedNanoRelocs.size;

    // Update rel_end in the header to reflect the packed reloc size
    struct BinHdr *hdr = (struct BinHdr *) buf;
    hdr->sect.rel_end = hdr->sect.rel_start + app.packedNanoRelocs.size;
    hdr->hdr.appVer = appVer;

    return finalizeAndWrite(buf, offset, bufSize, out, layoutFlags, appId);
    // TODO: should free all memory we allocated... just letting the OS handle
    // it for now
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
    bool staticElf = false;

    for (int i = 1; i < argc; i++) {
        char *end = NULL;
        if (argv[i][0] == '-') {
            prev = argv[i];
            if (!strcmp(argv[i], "-v"))
                verbose = true;
            else if (!strcmp(argv[i], "-r"))
                bareData = true;
            else if (!strcmp(argv[i], "-s"))
                staticElf = true;
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

    if (staticElf && layoutId != LAYOUT_APP)
        fatalUsage(appName, "Only app layout is supported for static option", NULL);

    if (layoutId == LAYOUT_APP && !appId)
        fatalUsage(appName, "App layout requires app ID", NULL);
    if (layoutId == LAYOUT_KEY && !keyId)
        fatalUsage(appName, "Key layout requires key ID", NULL);
    if (layoutId == LAYOUT_OS && (keyId || appId))
        fatalUsage(appName, "OS layout does not need any ID", NULL);

    if (!staticElf) {
        buf = loadFile(posArg[0], &bufUsed);
        fprintf(stderr, "Read %" PRIu32 " bytes\n", bufUsed);
    }

    if (!posArg[1])
        out = stdout;
    else
        out = fopen(posArg[1], "w");
    if (!out)
        fatalUsage(appName, "failed to create/open output file", posArg[1]);

    switch(layoutId) {
    case LAYOUT_APP:
        if (staticElf) {
            ret = handleAppStatic(posArg[0], out, layoutFlags, appId, appVer, verbose);
        } else {
            ret = handleApp(&buf, bufUsed, out, layoutFlags, appId, appVer, verbose);
        }
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
