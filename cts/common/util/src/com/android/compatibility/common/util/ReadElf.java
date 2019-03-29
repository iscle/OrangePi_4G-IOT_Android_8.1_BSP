/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.compatibility.common.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * A poor man's implementation of the readelf command. This program is designed to parse ELF
 * (Executable and Linkable Format) files.
 */
// ToDo: consolidate with com.android.compatibility.common.util
public class ReadElf implements AutoCloseable {
    /** The magic values for the ELF identification. */
    private static final byte[] ELFMAG = {
        (byte) 0x7F, (byte) 'E', (byte) 'L', (byte) 'F',
    };

    private static final int EI_NIDENT = 16;

    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;

    private static final int EM_386 = 3;
    private static final int EM_MIPS = 8;
    private static final int EM_ARM = 40;
    private static final int EM_X86_64 = 62;
    // http://en.wikipedia.org/wiki/Qualcomm_Hexagon
    private static final int EM_QDSP6 = 164;
    private static final int EM_AARCH64 = 183;

    private static final int ELFCLASS32 = 1;
    private static final int ELFCLASS64 = 2;

    private static final int ELFDATA2LSB = 1;
    private static final int ELFDATA2MSB = 2;

    private static final int EV_CURRENT = 1;

    private static final long PT_LOAD = 1;

    private static final int SHT_SYMTAB = 2;
    private static final int SHT_STRTAB = 3;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_DYNSYM = 11;
    private static final int SHT_GNU_VERDEF = 0x6ffffffd;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;

    public static class Symbol {
        public static final int STB_LOCAL = 0;
        public static final int STB_GLOBAL = 1;
        public static final int STB_WEAK = 2;
        public static final int STB_LOPROC = 13;
        public static final int STB_HIPROC = 15;

        public static final int STT_NOTYPE = 0;
        public static final int STT_OBJECT = 1;
        public static final int STT_FUNC = 2;
        public static final int STT_SECTION = 3;
        public static final int STT_FILE = 4;
        public static final int STT_COMMON = 5;
        public static final int STT_TLS = 6;

        public static final int SHN_UNDEF = 0;

        public final String name;
        public final int bind;
        public final int type;
        public final int shndx;
        public final long value;
        public final long size;
        public final int other;

        public VerNeed mVerNeed;
        public VerDef mVerDef;

        Symbol(String name, int st_info, int st_shndx, long st_value, long st_size, int st_other) {
            this.name = name;
            this.bind = (st_info >> 4) & 0x0F;
            this.type = st_info & 0x0F;
            this.shndx = st_shndx;
            this.value = st_value;
            this.size = st_size;
            this.other = st_other;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s, %s, %s, %s, %s, %s",
                    name,
                    toBind(),
                    toType(),
                    toShndx(),
                    getExternalLibFileName(),
                    getExternalLibName());
        }

        private String toBind() {
            switch (bind) {
                case STB_LOCAL:
                    return "LOCAL";
                case STB_GLOBAL:
                    return "GLOBAL";
                case STB_WEAK:
                    return "WEAK";
            }
            return "STB_??? (" + bind + ")";
        }

        private String toType() {
            switch (type) {
                case STT_NOTYPE:
                    return "NOTYPE";
                case STT_OBJECT:
                    return "OBJECT";
                case STT_FUNC:
                    return "FUNC";
                case STT_SECTION:
                    return "SECTION";
                case STT_FILE:
                    return "FILE";
                case STT_COMMON:
                    return "COMMON";
                case STT_TLS:
                    return "TLS";
            }
            return "STT_??? (" + type + ")";
        }

        private String toShndx() {
            if (shndx == SHN_UNDEF) {
                return "UNDEF";
            }
            return String.valueOf(shndx);
        }

        // if a symbol is not define locally
        public boolean isGlobalUnd() {
            return (bind != STB_LOCAL && shndx == SHN_UNDEF);
        }

        // if a symbol is extern
        public boolean isExtern() {
            return (bind != STB_LOCAL && shndx != SHN_UNDEF);
        }

        public String getExternalLibFileName() {
            if (mVerNeed != null) {
                return mVerNeed.vn_file_name;
            }
            return null;
        }

        public String getExternalLibName() {
            if (mVerNeed != null) {
                return mVerNeed.vn_vernaux[0].vna_lib_name;
            }
            return null;
        }

        public int getExternalLibVer() {
            if (mVerNeed != null) {
                return mVerNeed.vn_vernaux[0].vna_other;
            }
            return -1;
        }

        public String getVerDefLibName() {
            if (mVerDef != null) {
                return mVerDef.vd_verdaux[0].vda_lib_name;
            }
            return null;
        }

        public int getVerDefVersion() {
            if (mVerDef != null) {
                return mVerDef.vd_version;
            }
            return -1;
        }
    }

    public static class SecHeader {
        public final long sh_name;
        public final long sh_type;
        public final long sh_flags;
        public final long sh_addr;
        public final long sh_offset;
        public final long sh_size;
        public final long sh_link;
        public final long sh_info;
        public final long sh_addralign;
        public final long sh_entsize;

        SecHeader(
                long name,
                long type,
                long flags,
                long addr,
                long offset,
                long size,
                long link,
                long info,
                long addralign,
                long entsize) {
            this.sh_name = name;
            this.sh_type = type;
            this.sh_flags = flags;
            this.sh_addr = addr;
            this.sh_offset = offset;
            this.sh_size = size;
            this.sh_link = link;
            this.sh_info = info;
            this.sh_addralign = addralign;
            this.sh_entsize = entsize;
        }

        @Override
        public String toString() {
            return String.format(
                    "%d, %d, %d, %d, %d, %d, %d, %d, %d, %d",
                    this.sh_name,
                    this.sh_type,
                    this.sh_flags,
                    this.sh_addr,
                    this.sh_offset,
                    this.sh_size,
                    this.sh_link,
                    this.sh_info,
                    this.sh_addralign,
                    this.sh_entsize);
        }
    }

    public static class VerNeed {
        public final int vn_version;
        public final int vn_cnt;
        public final long vn_file;
        public final long vn_aux;
        public final long vn_next;
        public String vn_file_name;
        public VerNAux[] vn_vernaux;

        VerNeed(String file_name, String lib_name, int ndx) {
            this.vn_file_name = file_name.toLowerCase();
            this.vn_vernaux = new VerNAux[1];
            this.vn_vernaux[0] = new VerNAux(lib_name, ndx);

            this.vn_version = 0;
            this.vn_cnt = 0;
            this.vn_file = 0;
            this.vn_aux = 0;
            this.vn_next = 0;
        }

        VerNeed(int ver, int cnt, long file, long aux, long next) {
            this.vn_version = ver;
            this.vn_cnt = cnt;
            this.vn_file = file;
            this.vn_aux = aux;
            this.vn_next = next;
        }

        @Override
        public String toString() {
            String vernauxStr = "";
            for (int i = 0; i < this.vn_cnt; i++) {
                vernauxStr += String.format("    %s\n", this.vn_vernaux[i].toString());
            }
            return String.format(
                    "%s, %d, %d, %d, %d, %d \n%s",
                    this.vn_file_name,
                    this.vn_version,
                    this.vn_cnt,
                    this.vn_file,
                    this.vn_aux,
                    this.vn_next,
                    vernauxStr);
        }
    }

    public static class VerNAux {
        public final long vna_hash;
        public final int vna_flags;
        public final int vna_other;
        public final long vna_name;
        public final long vna_next;
        public String vna_lib_name;

        VerNAux(String lib_name, int ndx) {
            this.vna_lib_name = lib_name;

            this.vna_hash = 0;
            this.vna_flags = 0;
            this.vna_other = ndx;
            this.vna_name = 0;
            this.vna_next = 0;
        }

        VerNAux(long hash, int flags, int other, long name, long next) {
            this.vna_hash = hash;
            this.vna_flags = flags;
            this.vna_other = other;
            this.vna_name = name;
            this.vna_next = next;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s, %d, %d, %d, %d, %d",
                    this.vna_lib_name,
                    this.vna_hash,
                    this.vna_flags,
                    this.vna_other,
                    this.vna_name,
                    this.vna_next);
        }
    }

    public static class VerDef {
        public final int vd_version;
        public final int vd_flags;
        public final int vd_ndx;
        public final int vd_cnt;
        public final long vd_hash;
        public final long vd_aux;
        public final long vd_next;
        public VerDAux[] vd_verdaux;

        VerDef(String lib_name) {
            this.vd_verdaux = new VerDAux[1];
            this.vd_verdaux[0] = new VerDAux(lib_name);

            this.vd_version = 0;
            this.vd_flags = 0;
            this.vd_ndx = 0;
            this.vd_cnt = 0;
            this.vd_hash = 0;
            this.vd_aux = 0;
            this.vd_next = 0;
        }

        VerDef(int ver, int flags, int ndx, int cnt, long hash, long aux, long next) {
            this.vd_version = ver;
            this.vd_flags = flags;
            this.vd_ndx = ndx;
            this.vd_cnt = cnt;
            this.vd_hash = hash;
            this.vd_aux = aux;
            this.vd_next = next;
        }

        @Override
        public String toString() {
            String vStr = "";
            for (int i = 0; i < this.vd_cnt; i++) {
                vStr += String.format("    %s\n", this.vd_verdaux[i].toString());
            }
            return String.format(
                    "%s, %d, %d, %d, %d, %d \n%s",
                    this.vd_verdaux[0].vda_lib_name,
                    this.vd_version,
                    this.vd_flags,
                    this.vd_ndx,
                    this.vd_cnt,
                    this.vd_hash,
                    vStr);
        }
    }

    public static class VerDAux {
        public final long vda_name;
        public final long vda_next;
        public String vda_lib_name;

        VerDAux(String lib_name) {
            this.vda_lib_name = lib_name.toLowerCase();

            this.vda_name = 0;
            this.vda_next = 0;
        }

        VerDAux(long name, long next) {
            this.vda_name = name;
            this.vda_next = next;
        }

        @Override
        public String toString() {
            return String.format("%s, %d, %d", this.vda_lib_name, this.vda_name, this.vda_next);
        }
    }

    private final String mPath;
    private final RandomAccessFile mFile;
    private final byte[] mBuffer = new byte[512];
    private int mEndian;
    private boolean mIsDynamic;
    private boolean mIsPIE;
    private int mType;
    private int mAddrSize;

    /** Symbol Table offset */
    private long mSymTabOffset;

    /** Symbol Table size */
    private long mSymTabSize;

    /** Symbol entry count */
    private int mSymEntCnt;

    /** Dynamic Symbol Table offset */
    private long mDynSymOffset;

    /** Dynamic Symbol Table size */
    private long mDynSymSize;

    /** Dynamic entry count */
    private int mDynSymEntCnt;

    /** Section Header String Table offset */
    private long mShStrTabOffset;

    /** Section Header String Table size */
    private long mShStrTabSize;

    /** String Table offset */
    private long mStrTabOffset;

    /** String Table size */
    private long mStrTabSize;

    /** Dynamic String Table offset */
    private long mDynStrOffset;

    /** Dynamic String Table size */
    private long mDynStrSize;

    /** Dynamic String Table offset */
    private long mDynamicTabOffset;

    /** Dynamic String Table size */
    private long mDynamicTabSize;

    /** Version Symbols Table offset */
    private long mVerSymTabOffset;

    /** Version Symbols Table size */
    private long mVerSymTabSize;

    /** Version Needs Table offset */
    private long mVerNeedTabOffset;

    /** Version Definition Table size */
    private long mVerNeedTabSize;

    private int mVerNeedEntryCnt;

    /** Version Definition Table offset */
    private long mVerDefTabOffset;

    /** Version Needs Table size */
    private long mVerDefTabSize;

    private int mVerDefEntryCnt;

    /** Symbol Table symbol names */
    private Map<String, Symbol> mSymbols;

    /** Symbol Table symbol array */
    private Symbol[] mSymArr;

    /** Dynamic Symbol Table symbol names */
    private Map<String, Symbol> mDynamicSymbols;

    /** Dynamic Symbol Table symbol array */
    private Symbol[] mDynSymArr;

    /** Version Symbols Table */
    private int[] mVerSym;

    /** Version Needed Table */
    private VerNeed[] mVerNeedArr;

    /** Version Definition Table */
    private VerDef[] mVerDefArr;

    public static ReadElf read(File file) throws IOException {
        return new ReadElf(file);
    }

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            ReadElf re = ReadElf.read(new File(arg));
            re.getDynamicSymbol("x");
            re.getSymbol("x");

            Symbol[] symArr;
            System.out.println("===Symbol===");
            symArr = re.getSymArr();
            for (int i = 0; i < symArr.length; i++) {
                System.out.println(String.format("%8x: %s", i, symArr[i].toString()));
            }
            System.out.println("===Dynamic Symbol===");
            symArr = re.getDynSymArr();
            for (int i = 0; i < symArr.length; i++) {
                if (re.mVerNeedEntryCnt > 0) {
                    System.out.println(
                            String.format(
                                    "%8x: %s, %s, %s - %d",
                                    i,
                                    symArr[i].toString(),
                                    symArr[i].getExternalLibName(),
                                    symArr[i].getExternalLibFileName(),
                                    symArr[i].getExternalLibVer()));
                } else {
                    System.out.println(
                            String.format(
                                    "%8x: %s, %s - %d",
                                    i,
                                    symArr[i].toString(),
                                    symArr[i].getVerDefLibName(),
                                    symArr[i].getVerDefVersion()));
                }
            }
            re.close();
        }
    }

    public Map<String, Symbol> getSymbols() throws IOException {
        if (mSymbols == null) {
            getSymbol("");
        }
        return mSymbols;
    }

    public Symbol[] getSymArr() throws IOException {
        if (mSymArr == null) {
            getSymbol("");
        }
        return mSymArr;
    }

    public Map<String, Symbol> getDynamicSymbols() throws IOException {
        if (mDynamicSymbols == null) {
            getDynamicSymbol("");
        }
        return mDynamicSymbols;
    }

    public Symbol[] getDynSymArr() throws IOException {
        if (mDynSymArr == null) {
            getDynamicSymbol("");
        }
        return mDynSymArr;
    }

    public boolean isDynamic() {
        return mIsDynamic;
    }

    public int getType() {
        return mType;
    }

    public boolean isPIE() {
        return mIsPIE;
    }

    private ReadElf(File file) throws IOException {
        mPath = file.getPath();
        mFile = new RandomAccessFile(file, "r");

        if (mFile.length() < EI_NIDENT) {
            throw new IllegalArgumentException("Too small to be an ELF file: " + file);
        }

        readHeader();
    }

    @Override
    public void close() {
        try {
            mFile.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    private void readHeader() throws IOException {
        mFile.seek(0);
        mFile.readFully(mBuffer, 0, EI_NIDENT);

        if (mBuffer[0] != ELFMAG[0]
                || mBuffer[1] != ELFMAG[1]
                || mBuffer[2] != ELFMAG[2]
                || mBuffer[3] != ELFMAG[3]) {
            throw new IllegalArgumentException("Invalid ELF file: " + mPath);
        }

        int elfClass = mBuffer[EI_CLASS];
        if (elfClass == ELFCLASS32) {
            mAddrSize = 4;
        } else if (elfClass == ELFCLASS64) {
            mAddrSize = 8;
        } else {
            throw new IOException("Invalid ELF EI_CLASS: " + elfClass + ": " + mPath);
        }

        mEndian = mBuffer[EI_DATA];
        if (mEndian == ELFDATA2LSB) {
        } else if (mEndian == ELFDATA2MSB) {
            throw new IOException("Unsupported ELFDATA2MSB file: " + mPath);
        } else {
            throw new IOException("Invalid ELF EI_DATA: " + mEndian + ": " + mPath);
        }

        mType = readHalf();

        int e_machine = readHalf();
        if (e_machine != EM_386
                && e_machine != EM_X86_64
                && e_machine != EM_AARCH64
                && e_machine != EM_ARM
                && e_machine != EM_MIPS
                && e_machine != EM_QDSP6) {
            throw new IOException("Invalid ELF e_machine: " + e_machine + ": " + mPath);
        }

        // AbiTest relies on us rejecting any unsupported combinations.
        if ((e_machine == EM_386 && elfClass != ELFCLASS32)
                || (e_machine == EM_X86_64 && elfClass != ELFCLASS64)
                || (e_machine == EM_AARCH64 && elfClass != ELFCLASS64)
                || (e_machine == EM_ARM && elfClass != ELFCLASS32)
                || (e_machine == EM_QDSP6 && elfClass != ELFCLASS32)) {
            throw new IOException(
                    "Invalid e_machine/EI_CLASS ELF combination: "
                            + e_machine
                            + "/"
                            + elfClass
                            + ": "
                            + mPath);
        }

        long e_version = readWord();
        if (e_version != EV_CURRENT) {
            throw new IOException("Invalid e_version: " + e_version + ": " + mPath);
        }

        long e_entry = readAddr();

        long ph_off = readOff();
        long sh_off = readOff();

        long e_flags = readWord();
        int e_ehsize = readHalf();
        int e_phentsize = readHalf();
        int e_phnum = readHalf();
        int e_shentsize = readHalf();
        int e_shnum = readHalf();
        int e_shstrndx = readHalf();

        readSectionHeaders(sh_off, e_shnum, e_shentsize, e_shstrndx);
        readProgramHeaders(ph_off, e_phnum, e_phentsize);
    }

    private void readSectionHeaders(long sh_off, int e_shnum, int e_shentsize, int e_shstrndx)
            throws IOException {
        // Read the Section Header String Table offset first.
        {
            mFile.seek(sh_off + e_shstrndx * e_shentsize);

            long sh_name = readWord();
            long sh_type = readWord();
            long sh_flags = readX(mAddrSize);
            long sh_addr = readAddr();
            long sh_offset = readOff();
            long sh_size = readX(mAddrSize);
            // ...

            if (sh_type == SHT_STRTAB) {
                mShStrTabOffset = sh_offset;
                mShStrTabSize = sh_size;
            }
        }

        for (int i = 0; i < e_shnum; ++i) {
            // Don't bother to re-read the Section Header StrTab.
            if (i == e_shstrndx) {
                continue;
            }

            mFile.seek(sh_off + i * e_shentsize);

            long sh_name = readWord();
            long sh_type = readWord();
            long sh_flags = readX(mAddrSize);
            long sh_addr = readAddr();
            long sh_offset = readOff();
            long sh_size = readX(mAddrSize);
            long sh_link = readWord();
            long sh_info = readWord();
            long sh_addralign = readX(mAddrSize);
            ;
            long sh_entsize = readX(mAddrSize);
            ;

            if (sh_type == SHT_SYMTAB || sh_type == SHT_DYNSYM) {
                final String symTabName = readShStrTabEntry(sh_name);
                if (".symtab".equals(symTabName)) {
                    mSymTabOffset = sh_offset;
                    mSymTabSize = sh_size;
                    mSymEntCnt = (int) (sh_size / sh_entsize);
                } else if (".dynsym".equals(symTabName)) {
                    mDynSymOffset = sh_offset;
                    mDynSymSize = sh_size;
                    mDynSymEntCnt = (int) (sh_size / sh_entsize);
                }
                System.out.println(
                        String.format(
                                "%s, %d, %d, %d, %d, %d",
                                symTabName, sh_offset, sh_size, sh_link, sh_info, sh_entsize));
            } else if (sh_type == SHT_STRTAB) {
                final String strTabName = readShStrTabEntry(sh_name);
                if (".strtab".equals(strTabName)) {
                    mStrTabOffset = sh_offset;
                    mStrTabSize = sh_size;
                } else if (".dynstr".equals(strTabName)) {
                    mDynStrOffset = sh_offset;
                    mDynStrSize = sh_size;
                    System.out.println(
                            String.format(
                                    "%s, %d, %d, %d, %d",
                                    strTabName, sh_offset, sh_size, sh_link, sh_info));
                }
            } else if (sh_type == SHT_DYNAMIC) {
                mIsDynamic = true;
                final String strTabName = readShStrTabEntry(sh_name);
                mDynamicTabOffset = sh_offset;
                mDynamicTabSize = sh_size;
                System.out.println(
                        String.format(
                                "%s, %d, %d, %d, %d",
                                strTabName, sh_offset, sh_size, sh_link, sh_info));
            } else if (sh_type == SHT_GNU_VERSYM) {
                final String strTabName = readShStrTabEntry(sh_name);
                if (".gnu.version".equals(strTabName)) {
                    mVerSymTabOffset = sh_offset;
                    mVerSymTabSize = sh_size;
                }
                System.out.println(
                        String.format(
                                "%s, %d, %d, %d, %d",
                                strTabName, sh_offset, sh_size, sh_link, sh_info));
            } else if (sh_type == SHT_GNU_VERNEED) {
                final String strTabName = readShStrTabEntry(sh_name);
                if (".gnu.version_r".equals(strTabName)) {
                    mVerNeedTabOffset = sh_offset;
                    mVerNeedTabSize = sh_size;
                    mVerNeedEntryCnt = (int) sh_info;
                }
                System.out.println(
                        String.format(
                                "%s, %d, %d, %d, %d",
                                strTabName, sh_offset, sh_size, sh_link, sh_info));
            } else if (sh_type == SHT_GNU_VERDEF) {
                final String strTabName = readShStrTabEntry(sh_name);
                if (".gnu.version_d".equals(strTabName)) {
                    mVerDefTabOffset = sh_offset;
                    mVerDefTabSize = sh_size;
                    mVerDefEntryCnt = (int) sh_info;
                }
                System.out.println(
                        String.format(
                                "%s, %d, %d, %d, %d",
                                strTabName, sh_offset, sh_size, sh_link, sh_info));
            }
        }
    }

    private void readProgramHeaders(long ph_off, int e_phnum, int e_phentsize) throws IOException {
        for (int i = 0; i < e_phnum; ++i) {
            mFile.seek(ph_off + i * e_phentsize);

            long p_type = readWord();
            if (p_type == PT_LOAD) {
                if (mAddrSize == 8) {
                    // Only in Elf64_phdr; in Elf32_phdr p_flags is at the end.
                    long p_flags = readWord();
                }
                long p_offset = readOff();
                long p_vaddr = readAddr();
                // ...

                if (p_vaddr == 0) {
                    mIsPIE = true;
                }
            }
        }
    }

    private HashMap<String, Symbol> readSymbolTable(
            Symbol[] symArr,
            boolean isDynSym,
            long symStrOffset,
            long symStrSize,
            long tableOffset,
            long tableSize)
            throws IOException {
        HashMap<String, Symbol> result = new HashMap<String, Symbol>();
        mFile.seek(tableOffset);
        int i = 0;
        while (mFile.getFilePointer() < tableOffset + tableSize) {
            long st_name = readWord();
            int st_info;
            int st_shndx;
            long st_value;
            long st_size;
            int st_other;
            if (mAddrSize == 8) {
                st_info = readByte();
                st_other = readByte();
                st_shndx = readHalf();
                st_value = readAddr();
                st_size = readX(mAddrSize);
            } else {
                st_value = readAddr();
                st_size = readWord();
                st_info = readByte();
                st_other = readByte();
                st_shndx = readHalf();
            }

            String symName;
            if (st_name == 0) {
                symName = "";
            } else {
                symName = readStrTabEntry(symStrOffset, symStrSize, st_name);
            }

            Symbol sym = new Symbol(symName, st_info, st_shndx, st_value, st_size, st_other);
            if (symName.equals("")) {
                result.put(symName, sym);
            }
            if (isDynSym) {
                if (mVerNeedEntryCnt > 0) {
                    if (sym.type == Symbol.STT_NOTYPE) {
                        sym.mVerNeed = mVerNeedArr[0];
                    } else {
                        sym.mVerNeed = getVerNeed(mVerSym[i]);
                    }
                } else if (mVerDefEntryCnt > 0) {
                    sym.mVerDef = mVerDefArr[mVerSym[i]];
                }
            }
            symArr[i] = sym;
            i++;
        }
        System.out.println(
                String.format(
                        "Info readSymbolTable: %s, isDynSym %b, symbol# %d",
                        mPath, isDynSym, symArr.length));
        return result;
    }

    private String readShStrTabEntry(long strOffset) throws IOException {
        if (mShStrTabOffset == 0 || strOffset < 0 || strOffset >= mShStrTabSize) {
            return null;
        }
        return readString(mShStrTabOffset + strOffset);
    }

    private String readStrTabEntry(long tableOffset, long tableSize, long strOffset)
            throws IOException {
        if (tableOffset == 0 || strOffset < 0 || strOffset >= tableSize) {
            return null;
        }
        return readString(tableOffset + strOffset);
    }

    private String readDynStrTabEntry(long strOffset) throws IOException {
        if (mDynStrOffset == 0 || strOffset < 0 || strOffset >= mDynStrSize) {
            return null;
        }
        return readString(mDynStrOffset + strOffset);
    }

    private int[] getVerSym() throws IOException {
        if (mVerSym == null) {
            mFile.seek(mVerSymTabOffset);
            int cnt = (int) mVerSymTabSize / 2;
            mVerSym = new int[cnt];
            for (int i = 0; i < cnt; i++) {
                mVerSym[i] = readHalf();
                //System.out.println(String.format("%d, %d", i, mVerSym[i]));
            }
        }
        return mVerSym;
    }

    public VerNeed getVerNeed(int ndx) throws IOException {
        // vna_other Contains version index unique for the file which is used in the version symbol table.
        if (ndx < 2) {
            return this.mVerNeedArr[ndx];
        }

        for (int i = 2; i < this.mVerNeedEntryCnt + 2; i++) {
            for (int j = 0; j < this.mVerNeedArr[i].vn_cnt; j++) {
                if (this.mVerNeedArr[i].vn_vernaux[j].vna_other == ndx) {
                    return this.mVerNeedArr[i];
                }
            }
        }
        System.out.println(String.format("no VerNeed found: %d", ndx));
        return null;
    }

    private VerNeed[] getVerNeedArr() throws IOException {
        if (mVerNeedArr == null) {
            mVerNeedArr = new VerNeed[mVerNeedEntryCnt + 2];

            // SHT_GNU_versym 0: local
            mVerNeedArr[0] = new VerNeed("*local*", "*local*", 0);
            // HT_GNU_versym 1: global
            mVerNeedArr[1] = new VerNeed("*global*", "*global*", 1);

            long idx = mVerNeedTabOffset;
            for (int i = 2; i < mVerNeedEntryCnt + 2; i++) {
                mFile.seek(idx);
                mVerNeedArr[i] =
                        new VerNeed(readHalf(), readHalf(), readWord(), readWord(), readWord());
                mVerNeedArr[i].vn_file_name = readDynStrTabEntry(mVerNeedArr[i].vn_file).toLowerCase();

                mVerNeedArr[i].vn_vernaux = new VerNAux[mVerNeedArr[i].vn_cnt];
                long idxAux = idx + mVerNeedArr[i].vn_aux;
                for (int j = 0; j < mVerNeedArr[i].vn_cnt; j++) {
                    mFile.seek(idxAux);
                    mVerNeedArr[i].vn_vernaux[j] =
                            new VerNAux(readWord(), readHalf(), readHalf(), readWord(), readWord());
                    mVerNeedArr[i].vn_vernaux[j].vna_lib_name =
                            readDynStrTabEntry(mVerNeedArr[i].vn_vernaux[j].vna_name);
                    idxAux += mVerNeedArr[i].vn_vernaux[j].vna_next;
                }
                idx += mVerNeedArr[i].vn_next;
                System.out.println(mVerNeedArr[i]);
            }
        }

        return mVerNeedArr;
    }

    private VerDef[] getVerDef() throws IOException {
        if (mVerDefArr == null) {
            mVerDefArr = new VerDef[mVerDefEntryCnt + 2];

            // SHT_GNU_versym 0: local
            mVerDefArr[0] = new VerDef("*local*");
            // HT_GNU_versym 1: global
            mVerDefArr[1] = new VerDef("*global*");

            long idx = mVerDefTabOffset;
            for (int i = 2; i < mVerDefEntryCnt + 2; i++) {
                mFile.seek(idx);
                mVerDefArr[i] =
                        new VerDef(
                                readHalf(),
                                readHalf(),
                                readHalf(),
                                readHalf(),
                                readWord(),
                                readWord(),
                                readWord());

                mVerDefArr[i].vd_verdaux = new VerDAux[mVerDefArr[i].vd_cnt];
                long idxAux = idx + mVerDefArr[i].vd_aux;
                for (int j = 0; j < mVerDefArr[i].vd_cnt; j++) {
                    mFile.seek(idxAux);
                    mVerDefArr[i].vd_verdaux[j] = new VerDAux(readWord(), readWord());
                    mVerDefArr[i].vd_verdaux[j].vda_lib_name =
                            readDynStrTabEntry(mVerDefArr[i].vd_verdaux[j].vda_name).toLowerCase();
                    idxAux += mVerDefArr[i].vd_verdaux[j].vda_next;
                }
                idx += mVerDefArr[i].vd_next;
                System.out.println(mVerDefArr[i]);
            }
        }
        return mVerDefArr;
    }

    private int readHalf() throws IOException {
        return (int) readX(2);
    }

    private long readWord() throws IOException {
        return readX(4);
    }

    private long readOff() throws IOException {
        return readX(mAddrSize);
    }

    private long readAddr() throws IOException {
        return readX(mAddrSize);
    }

    private long readX(int byteCount) throws IOException {
        mFile.readFully(mBuffer, 0, byteCount);

        int answer = 0;
        if (mEndian == ELFDATA2LSB) {
            for (int i = byteCount - 1; i >= 0; i--) {
                answer = (answer << 8) | (mBuffer[i] & 0xff);
            }
        } else {
            final int N = byteCount - 1;
            for (int i = 0; i <= N; ++i) {
                answer = (answer << 8) | (mBuffer[i] & 0xff);
            }
        }

        return answer;
    }

    private String readString(long offset) throws IOException {
        long originalOffset = mFile.getFilePointer();
        mFile.seek(offset);
        mFile.readFully(mBuffer, 0, (int) Math.min(mBuffer.length, mFile.length() - offset));
        mFile.seek(originalOffset);

        for (int i = 0; i < mBuffer.length; ++i) {
            if (mBuffer[i] == 0) {
                return new String(mBuffer, 0, i);
            }
        }

        return null;
    }

    private int readByte() throws IOException {
        return mFile.read() & 0xff;
    }

    public Symbol getSymbol(String name) {
        if (mSymbols == null) {
            try {
                mSymArr = new Symbol[mSymEntCnt];
                mSymbols =
                        readSymbolTable(
                                mSymArr,
                                false,
                                mStrTabOffset,
                                mStrTabSize,
                                mSymTabOffset,
                                mSymTabSize);
            } catch (IOException e) {
                return null;
            }
        }
        return mSymbols.get(name);
    }

    public Symbol getDynamicSymbol(String name) throws IOException {
        if (mDynamicSymbols == null) {
            try {
                int[] verSmyArr = this.getVerSym();
                VerNeed[] verNeedArr = this.getVerNeedArr();
                VerDef[] verDefArr = this.getVerDef();
                mDynSymArr = new Symbol[mDynSymEntCnt];
                mDynamicSymbols =
                        readSymbolTable(
                                mDynSymArr,
                                true,
                                mDynStrOffset,
                                mDynStrSize,
                                mDynSymOffset,
                                mDynSymSize);
            } catch (IOException e) {
                return null;
            }
        }
        return mDynamicSymbols.get(name);
    }
}
