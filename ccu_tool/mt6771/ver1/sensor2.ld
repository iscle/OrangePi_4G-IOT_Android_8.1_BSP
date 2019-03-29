MEMORY
{
	ddr :	ORIGIN = 0x10000000 + 256K - 16K,	LENGTH = 16K
}

REGION_ALIAS("REGION_TEXT", ddr)
REGION_ALIAS("REGION_RODATA", ddr)
REGION_ALIAS("REGION_DATA", ddr)
REGION_ALIAS("REGION_BSS", ddr)

SECTIONS
{

	.text : ALIGN(4)
	{
		_f_text = .;
		*(.text.getfunctable)
		* (.text .text.* .gnu.linkonce.t.*)
		_e_text = .;
	} > REGION_TEXT

	.rodata : ALIGN(4)
	{
		_f_rodata = .;
		*(.rodata .rodata.* .gnu.linkonce.r.*)
		_e_rodata = .;
	} > REGION_RODATA

	. = ALIGN(4);
	.data : ALIGN(4)
	{
		_f_data = .;
		*(.data .data.* .gnu.linkonce.d.*)
	} > REGION_DATA

	.bss (NOLOAD) : ALIGN(8)
	{
		*(.dynbss)
		*(.bss .bss.* .gnu.linkonce.b.*)
		*(COMMON)
		_e_bss = .;
	} > REGION_BSS AT > REGION_DATA

}
