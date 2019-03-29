GetFuncTable = 0x5000;
ENTRY(GetFuncTable)

OUTPUT_FORMAT("elf32-md32")
OUTPUT_ARCH("md32")

/* Define memory regions.  */
MEMORY
{
        PM (rx): ORIGIN = 61K, LENGTH = 3K
        DM (!x): ORIGIN = 31232, LENGTH = 1536
}

PHDRS
{
    PM_PHDRS PT_LOAD ;
    DM_PHDRS PT_LOAD ;
}

SECTIONS
{
  .text :
  {
    *(.text.getfunctable)
    *(.text)
    *(.text.*)
    *(.gnu.linkonce.t.*)
    /* . += 0x1000; */
  } > PM : PM_PHDRS

  .rdata :
  {
    __RDATA_START = .;
    *(.rdata_4) *(.rdata_2) *(.rdata_1) *(.rdata.*) *(.gnu.linkonce.r.*) *(.rodata*)
    __RDATA_END = .;
  } > DM : DM_PHDRS

  .data :
  {
    __DATA_START = .;
    *(.data_4) *(.data_2) *(.data_1) *(.data) *(.data.*) *(.gnu.linkonce.d.*)
    __DATA_END = .;
  } > DM

  /* It's important to have a zero-expanded DM region. */
  .BSS (COPY) :
  /*.bss (NOLOAD) :*/
  {
    __BSS_START = .;
    *(.bss_4) *(.bss_2) *(.bss_1) *(.bss) *(COMMON) *(.bss.*) *(.gnu.linkonce.b.*)
    __BSS_END = .;
  } > DM

/* You may change the sizes of the following sections to fit the actual
   size your program requires.

   The heap and stack are aligned to the bus width, as a speed optimization
   for accessing data located there.  */

  .comment        0 : { *(.comment) }

  /* DWARF debug sections.
     Symbols in the DWARF debugging sections are relative to the beginning
     of the section so we begin them at 0.  */

  .debug_aranges  0 : { *(.debug_aranges) }
  .debug_pubnames 0 : { *(.debug_pubnames) }
  .debug_info     0 : { *(.debug_info .gnu.linkonce.wi.*) }
  .debug_abbrev   0 : { *(.debug_abbrev) }
  .debug_line     0 : { *(.debug_line) }
  .debug_frame    0 : { *(.debug_frame) }
  .debug_str      0 : { *(.debug_str) }
  .debug_loc      0 : { *(.debug_loc) }
  .debug_macinfo  0 : { *(.debug_macinfo) }

  /* DWARF 3 */
  .debug_pubtypes 0 : { *(.debug_pubtypes) }
  .debug_ranges   0 : { *(.debug_ranges) }

  /* DWARF Extension.  */
  .debug_macro    0 : { *(.debug_macro) }
}

__DATA_IMAGE_START = LOADADDR(.data);

