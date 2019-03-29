/*
 * Copyright (C) 2015 The Android Open Source Project
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
/* Command-Line utility to exercise firmware interfaces */

#define LOG_TAG "fwtool"

#include <errno.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "debug_cmd.h"
#include "flash_device.h"
#include "fmap.h"
#include "update_fw.h"
#include "update_log.h"
#include "vboot_interface.h"

static void *spi;
static void *ec;

static void *get_spi(void)
{
	if (!spi)
		spi = flash_open("spi", NULL);

	return spi;
}

static void *get_ec(void)
{
	if (!ec)
		ec = flash_open("ec", NULL);

	return ec;
}

static void dump_fmap(struct flash_device *dev)
{
	int i;
	struct fmap *fmap;

	fmap = flash_get_fmap(dev);
	if (!fmap)
		return;

	printf("FMAP '%s' ver %d.%d base 0x%" PRIx64 " size 0x%x\n",
		fmap->name, fmap->ver_major, fmap->ver_minor,
		fmap->base, fmap->size);
	for (i = 0; i < fmap->nareas; i++) {
		struct fmap_area *a = fmap->areas+i;
		printf("%16s @%08x size 0x%08x %2s %s\n",
			a->name, a->offset, a->size,
			a->flags & FMAP_AREA_RO ? "RO" : "",
			a->flags & FMAP_AREA_STATIC ? "static" : "");
	}
}

static void dump_section(struct flash_device *dev, const char *name)
{
	size_t size;
	off_t offset;
	char *content;

	content = reinterpret_cast<char*>(fmap_read_section(dev, name, &size, &offset));
	if (content) {
		content[size - 1] = '\0';
		printf("[%s]@%lx={%s}\n", name, offset, content);
	}
}

static int cmd_flash_fmap(int argc, const char **argv)
{
	if (!get_spi())
		return -ENODEV;

        struct flash_device* dev = reinterpret_cast<struct flash_device*>(spi);
	dump_fmap(dev);
	dump_section(dev, "RO_FRID");
	dump_section(dev, "RW_FWID_A");
	dump_section(dev, "RW_FWID_B");
	return 0;
}

static int cmd_vboot(int argc, const char **argv)
{
	char *hwid = fdt_read_string("hardware-id");
	char *version = fdt_read_string("firmware-version");
	char *ro_version = fdt_read_string("readonly-firmware-version");
	char *type = fdt_read_string("firmware-type");
	char *ec = fdt_read_string("active-ec-firmware");
	printf("HWID: %s\n", hwid);
	printf("Version: %s\n", version);
	printf("RO Version: %s\n", ro_version);
	printf("FW Type: %s\n", type);
	printf("EC: %s\n", ec);
	printf("FW partition: %c\n", vboot_get_mainfw_act());
	free(hwid);
	free(version);
	free(ro_version);
	free(type);
	free(ec);

	return 0;
}

static int cmd_update(int argc, const char **argv)
{
	if (argc < 3)
		return -EINVAL;

	printf("Updating using images main:%s and ec:%s ...\n", argv[1], argv[2]);
	Value mainv(VAL_STRING, argv[1]);
	Value ecv(VAL_STRING, argv[2]);
	update_fw(&mainv, &ecv, 1);
	printf("Done.\n");

	return -ENOENT;
}

static int cmd_vbnv_read(int argc, const char **argv)
{
	if (argc != 2) {
		printf("Usage: fwtool vbnv read <flag>\n");
		printf("where <flag> is one of the following:\n");
		vbnv_usage(0);
		return -EINVAL;
	}

	if (!get_spi())
		return -ENODEV;

	uint8_t val;

	if (vbnv_get_flag(reinterpret_cast<struct flash_device*>(spi), argv[1], &val) == 0)
		printf("%s = %d\n", argv[1], val);

	return 0;
}

static int cmd_vbnv_write(int argc, const char **argv)
{
	if (argc != 3) {
		printf("Usage: fwtool vbnv write <flag> <val>\n");
		printf("where <flag> is one of the following:\n");
		vbnv_usage(1);
		return -EINVAL;
	}

	if (!get_spi())
		return -ENODEV;

	uint8_t val = atoi(argv[2]);
	vbnv_set_flag(reinterpret_cast<struct flash_device*>(spi), argv[1], val);
	return 0;
}

static void sync_slots(void)
{
	static struct {
		char part;
		const char *name_str;
		const char *id_str;
	} part_list[] = {
		{'A', "RW_SECTION_A", "RW_FWID_A"},
		{'B', "RW_SECTION_B", "RW_FWID_B"},
	};


	char cur_part = vboot_get_mainfw_act();
	int cur_index;

	if (cur_part == 'A')
		cur_index = 0;
	else if (cur_part == 'B')
		cur_index = 1;
	else {
		ALOGW("ERROR: Unexpected cur_part value\n");
		return;
	}

	int old_index = cur_index ^ 1;

	if (!get_spi()) {
		ALOGW("ERROR: get_spi failed.\n");
		return;
	}

	size_t cur_id_size;
        struct flash_device* dev = reinterpret_cast<struct flash_device*>(spi);
	char *cur_fwid = reinterpret_cast<char*>(fmap_read_section(dev,
		part_list[cur_index].id_str, &cur_id_size, NULL));

	if ((cur_fwid == NULL) || (cur_id_size == 0)) {
		ALOGW("ERROR: Current FWID read error.\n");
		return;
	}

	ALOGD("Cur fwid: %s\n", cur_fwid);

	size_t old_id_size;
	char *old_fwid = reinterpret_cast<char*>(fmap_read_section(dev,
	        part_list[old_index].id_str, &old_id_size, NULL));

	if ((old_fwid == NULL) || (old_id_size == 0))
		ALOGD("Old FWID read error or FW slot damaged.\n");
	else {
		ALOGD("Old fwid: %s\n", old_fwid);

		if ((cur_id_size == old_id_size) &&
		    !strncmp(cur_fwid, old_fwid, cur_id_size)) {
			ALOGD("Slots already synced.\n");
			free(cur_fwid);
			free(old_fwid);
			return;
		}
	}

	free(cur_fwid);
	free(old_fwid);

	size_t sec_size;
	ALOGD("Reading current firmware slot.\n");
	uint8_t *cur_section = reinterpret_cast<uint8_t*>(fmap_read_section(dev,
	        part_list[cur_index].name_str, &sec_size, NULL));
	if (cur_section == NULL) {
		ALOGW("Error: Could not read current firmware slot.\n");
		return;
	}

	off_t old_offset;
	ALOGD("Reading old firmware slot offset.\n");
	if (fmap_get_section_offset(dev,
				    part_list[old_index].name_str,
				    &old_offset) == -1) {
		ALOGW("Error: Could not read old firmware slot offset.\n");
		free(cur_section);
		return;
	}

	ALOGD("Erasing old firmware slot.\n");
	if (flash_erase(dev, old_offset, sec_size)) {
		ALOGW("Error: Could not erase old firmware slot.\n");
		free(cur_section);
		return;
	}

	ALOGD("Updating old firmware slot.\n");
	if (flash_write(dev, old_offset, cur_section, sec_size))
		ALOGW("Error: Could not update old firmware slot.\n");
	else
		ALOGD("Slot sync complete.\n");

	free(cur_section);
}

static int cmd_mark_boot(int argc, const char **argv)
{
	if (argc != 2) {
		printf("Usage: fwtool mark_boot <status>\n");
		printf("    where status can be:\n");
		printf("    success: This boot was successful.\n");
		return -EINVAL;
	}

	if (!get_spi())
		return -ENODEV;

	if (strcmp(argv[1], "success") == 0) {
		vbnv_set_flag(reinterpret_cast<struct flash_device*>(spi), "boot_result",
                              VB2_FW_RESULT_SUCCESS);
		vbnv_set_flag(reinterpret_cast<struct flash_device*>(spi), "try_count", 0);
		sync_slots();
	} else {
		printf("Invalid arg\n");
		return -EINVAL;
	}

	return 0;
}

static struct command subcmds_flash[] = {
	CMD(flash_fmap, "Dump FMAP information"),
	CMD_GUARD_LAST
};

static struct command subcmds_vbnv[] = {
	CMD(vbnv_read, "Read flag from NvStorage"),
	CMD(vbnv_write, "Write flag from NvStorage"),
	CMD_GUARD_LAST,
};

static struct command cmds[] = {
	SUBCMDS(ec,    "Send commands directly to the EC"),
	SUBCMDS(flash, "Read/Write/Dump flash"),
	CMD(update,    "Update the firmwares"),
	CMD(vboot,     "dump VBoot information"),
	SUBCMDS(vbnv,      "Vboot NvStorage"),
	CMD(mark_boot, "Mark boot result"),
	CMD_GUARD_LAST
};

static void print_usage(struct command *commands, int idx, int prefix,
			int argc, const char **argv)
{
	int i;
	struct command *c = commands;
	fprintf(stderr, "Usage: ");
	for (i = 0; i <= idx; i++)
		fprintf(stderr,"%s ", argv[i]);
	fprintf(stderr, "\n");
	while (c->name) {
		fprintf(stderr, "\t\t%-12s: %s\n", c->name + prefix, c->help);
		c++;
	}
}

static int run_cmd(struct command *commands, int idx, int prefix,
		   int argc, const char **argv)
{
	struct command *c = commands;
	if (argc <= idx + 1)
		goto no_cmd;

	idx += 1;
	while (c->name) {
		if (!strcmp(c->name + prefix, argv[idx])) {
			int nprefx = prefix + strlen(c->name) + 1;
			if (argc > 1 && c->subcmd)
				return run_cmd(c->subcmd, idx, nprefx,
						argc, argv);
			else if (c->handler)
				return c->handler(argc - idx, argv + idx);
			else
				print_usage(c->subcmd, idx, nprefx, argc, argv);
			return -EINVAL;
		}
		c++;
	}
	idx -= 1; /* last command word was unknown */
no_cmd:
	print_usage(commands, idx, prefix, argc, argv);
	return -ENOENT;
}

int main(int argc, const char **argv)
{
	int res = -EINVAL;

	printf("Firmware debug Tool\n");

	res = run_cmd(cmds, 0, 0, argc, argv);

	/* Clean up our flash handlers */
	if (spi)
		flash_close(reinterpret_cast<struct flash_device*>(spi));
	if (ec)
		flash_close(reinterpret_cast<struct flash_device*>(ec));

	return res;
}
