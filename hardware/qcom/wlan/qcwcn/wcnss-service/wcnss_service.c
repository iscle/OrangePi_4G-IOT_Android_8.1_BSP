/*--------------------------------------------------------------------------
Copyright (c) 2013, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of The Linux Foundation nor
      the names of its contributors may be used to endorse or promote
      products derived from this software without specific prior written
      permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
--------------------------------------------------------------------------*/

#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <ctype.h>
#include <grp.h>
#include <utime.h>
#include <sys/stat.h>
#include <sys/sendfile.h>
#define LOG_TAG "wcnss_service"
#include <cutils/log.h>
#include <cutils/properties.h>
#ifdef WCNSS_QMI
#include "wcnss_qmi_client.h"
#include "mdm_detect.h"
#endif

#define SUCCESS 0
#define FAILED -1
#define BYTE_0  0
#define BYTE_1  8
#define BYTE_2  16
#define BYTE_3  24

#define MAX_FILE_LENGTH    (1024)
#define WCNSS_MAX_CMD_LEN  (128)

/* control messages to wcnss driver */
#define WCNSS_USR_CTRL_MSG_START    0x00000000
#define WCNSS_USR_SERIAL_NUM        (WCNSS_USR_CTRL_MSG_START + 1)
#define WCNSS_USR_HAS_CAL_DATA      (WCNSS_USR_CTRL_MSG_START + 2)
#define WCNSS_USR_WLAN_MAC_ADDR     (WCNSS_USR_CTRL_MSG_START + 3)


#define WCNSS_CAL_CHUNK (3*1024)
#define WCNSS_CAL_FILE  "/data/misc/wifi/WCNSS_qcom_wlan_cal.bin"
#define WCNSS_FACT_FILE "/data/misc/wifi/WCN_FACTORY"
#define WCNSS_DEVICE    "/dev/wcnss_wlan"
#define WCNSS_CTRL      "/dev/wcnss_ctrl"
#define WLAN_INI_FILE_DEST   "/data/misc/wifi/WCNSS_qcom_cfg.ini"
#define WLAN_INI_FILE_SOURCE "/system/etc/wifi/WCNSS_qcom_cfg.ini"
#define WCNSS_HAS_CAL_DATA\
		"/sys/module/wcnsscore/parameters/has_calibrated_data"
#define WLAN_DRIVER_ATH_DEFAULT_VAL "0"

#define ASCII_A		65
#define ASCII_a		97
#define ASCII_0		48
#define HEXA_A		10
#define HEX_BASE		16

#ifdef WCNSS_QMI
#define WLAN_ADDR_SIZE   6
unsigned char wlan_nv_mac_addr[WLAN_ADDR_SIZE];
#define MAC_ADDR_ARRAY(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5]
#define MAC_ADDRESS_STR "%02x:%02x:%02x:%02x:%02x:%02x"

/* As we Want to write in 00:0a:f5:11:22:33 format in sysfs file
   so taking mac length as 12 char + 5 for ":" + NULL
 */
#define WLAN_MAC_ADDR_STRING 18
#endif

int wcnss_write_cal_data(int fd_dev)
{
	int rcount = 0;
	int size = 0;
	int rc = 0;
	int wcount = 0;
	int fd_file;
	struct stat st;

	char buf[WCNSS_CAL_CHUNK];

	ALOGI("wcnss_write_cal_data trying to write cal");

	rc = stat(WCNSS_CAL_FILE, &st);
	if (rc < 0) {
		ALOGE("Failed to stat cal file : %s",
				strerror(errno));
		goto exit;
	}

	size = st.st_size;

	fd_file = open(WCNSS_CAL_FILE, O_RDONLY);
	if (fd_file < 0) {
		ALOGE("cal file doesn't exist: %s",
				strerror(errno));
		rc = fd_file;
		goto exit;
	}

	/* write the file size first, so that platform driver knows
	 * when it recieves the full data */
	wcount = write(fd_dev, (void *)&size, 4);
	if (wcount != 4) {
		ALOGE("Failed to write to wcnss device : %s",
				strerror(errno));
		rc = wcount;
		goto exit_close;
	}

	do {
		rcount = read(fd_file, (void *)buf, sizeof(buf));
		if (rcount < 0) {
			ALOGE("Failed to read from cal file ; %s",
					strerror(errno));
			rc = rcount;
			goto exit_remove;
		}

		if (!rcount)
			break;

		wcount = write(fd_dev, buf, rcount);
		if (wcount < 0) {
			ALOGE("Failed to write to wcnss device : %s",
				strerror(errno));
			rc = wcount;
			goto exit_close;
		}

	} while (rcount);
	close(fd_file);

	return SUCCESS;

exit_remove:
	close(fd_file);
	remove("WCNSS_CAL_FILE");
	return rc;

exit_close:
	close(fd_file);

exit:
	return rc;
}


int wcnss_read_and_store_cal_data(int fd_dev)
{
	int rcount = 0;
	int wcount = 0;
	int fd_file = -1;
	int rc = 0;

	char buf[WCNSS_CAL_CHUNK];

	ALOGI("wcnss_read_and_store_cal_data trying to read cal");

	do {
		/* wait on this read until data comes from fw */
		rcount = read(fd_dev, (void *)buf, sizeof(buf));
		if (rcount < 0) {
			ALOGE("Failed to read from wcnss device : %s",
					strerror(errno));
			rc = rcount;
			goto exit;
		}

		/* truncate the file only if there is fw data, this read
		 * may never return if the fw decides that no more cal is
		 * required; and the data we have now is good enough.
		 */
		if (fd_file < 0) {
			fd_file = open(WCNSS_CAL_FILE, O_WRONLY
					| O_CREAT | O_TRUNC, 0664);
			if (fd_file < 0) {
				ALOGE("Failed to open cal file : %s",
						strerror(errno));
				rc = fd_file;
				goto exit;
			}
		}

		if (!rcount)
			break;

		wcount = write(fd_file, buf, rcount);
		if (wcount < 0) {
			ALOGE("Failed to write to cal file : %s",
				strerror(errno));
			rc = wcount;
			goto exit_remove;
		}

	} while (rcount);

	close(fd_file);

	return SUCCESS;

exit_remove:
	close(fd_file);
	remove(WCNSS_CAL_FILE);

exit:
	return rc;
}


void find_full_path(char *cur_dir, char *file_to_find, char *full_path)
{
	DIR *dir;
	struct stat st;
	struct dirent *dr;
	char cwd[1024];
	int rc;

	chdir(cur_dir);

	dir = opendir(".");

	if (dir != NULL) {
		while ((dr = readdir(dir))) {

			rc = lstat(dr->d_name, &st);
			if (rc < 0) {
				ALOGE("lstat failed %s", strerror(errno));
				return;
			}
			if (S_ISDIR(st.st_mode)) {
				if ((strcmp(dr->d_name, ".")) &&
					(strcmp(dr->d_name, ".."))) {
				find_full_path(dr->d_name,
						file_to_find, full_path);
				}
			} else if (!strcmp(file_to_find, dr->d_name)) {
				getcwd(cwd, sizeof(cwd));
				snprintf(full_path, MAX_FILE_LENGTH, "%s/%s",
					cwd, file_to_find);
			}
		}
		closedir(dir);
	}

	chdir("..");
}

void setup_wlan_config_file()
{
	int rfd;
	int wfd;
	struct stat st_dest, st_src;
	int rc_dest;
	int rc;
	struct group *grp;
	struct utimbuf new_time;

	rc = stat(WLAN_INI_FILE_SOURCE, &st_src);
	if (rc != 0) {
		ALOGE("source file do not exist %s", WLAN_INI_FILE_SOURCE);
		return;
	}

	rc_dest = stat(WLAN_INI_FILE_DEST, &st_dest);
	if (rc_dest == 0 && st_dest.st_size &&
			(st_dest.st_mtime > st_src.st_mtime)) {
		ALOGE("wlan ini file exists %s and is newer than %s",
				WLAN_INI_FILE_DEST, WLAN_INI_FILE_SOURCE);
		goto out_nocopy;
	}

	rfd = open(WLAN_INI_FILE_SOURCE, O_RDONLY);
	if (rfd < 0) {
		ALOGE("Failed to open ini source file: %s", strerror(errno));
		return;
	}

	wfd = open(WLAN_INI_FILE_DEST, O_WRONLY | O_CREAT | O_TRUNC, 0660);
	if (wfd < 0) {
		ALOGE("Failed to open ini dest file: %s", strerror(errno));
		close(rfd);
		return;
	}

	rc = sendfile(wfd, rfd, 0, st_src.st_size);
	if (rc != st_src.st_size) {
		ALOGE("Failed to copy ini file: %s", strerror(errno));
		goto out;
	}

	new_time.actime = st_src.st_atime;
	new_time.modtime = st_src.st_mtime;

	rc = utime(WLAN_INI_FILE_DEST, &new_time);
	if (rc != 0)
		ALOGE("could not preserve the timestamp %s", strerror(errno));

	grp = getgrnam("wifi");
	if (grp != NULL) {
		rc = chown(WLAN_INI_FILE_DEST, -1, grp->gr_gid);
		if (rc != 0)
			ALOGE("Failed change group of ini file %s", strerror(errno));
	} else {
			ALOGE("Failed to get group wifi %s", strerror(errno));
	}

	property_set("wlan.driver.config", WLAN_INI_FILE_DEST);

out:
	close(rfd);
	close(wfd);
	return;

out_nocopy:
	property_set("wlan.driver.config", WLAN_INI_FILE_DEST);
	return;
}
unsigned int convert_string_to_hex(char* string)
{
	int idx = 0;
	unsigned long int hex_num = 0;
	for(idx; string[idx] != '\0'; idx++){
		if(isalpha(string[idx])) {
			if(string[idx] >='a' && string[idx] <='f') {
				hex_num = hex_num * HEX_BASE + ((int)string[idx]
					       - ASCII_a + HEXA_A);
			} else if ( string[idx] >='A' && string[idx] <='F') {
				hex_num = hex_num * HEX_BASE + ((int)string[idx]
						- ASCII_A + HEXA_A);
			} else
				hex_num = hex_num * HEX_BASE + (int)string[idx];
		} else {
			hex_num = hex_num * HEX_BASE + (string[idx]- ASCII_0);
		}
	}
	hex_num = hex_num & 0xFFFFFFFF;
	return hex_num;
}


void setup_wcnss_parameters(int *cal, int nv_mac_addr)
{
	char msg[WCNSS_MAX_CMD_LEN];
	char serial[PROPERTY_VALUE_MAX];
	int fd, rc, pos = 0;
	struct stat st;
	unsigned int serial_num = 0;

	fd = open(WCNSS_CTRL, O_WRONLY);
	if (fd < 0) {
		ALOGE("Failed to open %s : %s", WCNSS_CTRL, strerror(errno));
		return;
	}

#ifdef WCNSS_QMI
	if (SUCCESS == nv_mac_addr)
	{
		pos = 0;
		msg[pos++] = WCNSS_USR_WLAN_MAC_ADDR >> BYTE_1;
		msg[pos++] = WCNSS_USR_WLAN_MAC_ADDR >> BYTE_0;
		msg[pos++] = wlan_nv_mac_addr[0];
		msg[pos++] = wlan_nv_mac_addr[1];
		msg[pos++] = wlan_nv_mac_addr[2];
		msg[pos++] = wlan_nv_mac_addr[3];
		msg[pos++] = wlan_nv_mac_addr[4];
		msg[pos++] = wlan_nv_mac_addr[5];

		ALOGI("WLAN MAC Addr:" MAC_ADDRESS_STR,
			MAC_ADDR_ARRAY(wlan_nv_mac_addr));

		if (write(fd, msg, pos) < 0) {
			ALOGE("Failed to write to %s : %s", WCNSS_CTRL,
						strerror(errno));
			goto fail;
		}
	}
#endif

	pos = 0;
	msg[pos++] = WCNSS_USR_HAS_CAL_DATA >> BYTE_1;
	msg[pos++] = WCNSS_USR_HAS_CAL_DATA >> BYTE_0;

	rc = stat(WCNSS_FACT_FILE, &st);
	if (rc == 0) {
		ALOGE("Factory file found, deleting cal file");
		unlink(WCNSS_CAL_FILE);
		goto fail_resp;
	}

	rc = stat(WCNSS_CAL_FILE, &st);
	if (rc != 0) {
		ALOGE("CAL file not found");
		goto fail_resp;
	}

	/* has cal data */
	msg[pos++] = 1;

	if (write(fd, msg, pos) < 0) {
		ALOGE("Failed to write to %s : %s", WCNSS_CTRL,
				strerror(errno));
		goto fail;
	}

	ALOGI("Correctly triggered cal file");
	*cal = SUCCESS;
	close(fd);
	return;

fail_resp:
	msg[pos++] = 0;
	if (write(fd, msg, pos) < 0)
		ALOGE("Failed to write to %s : %s", WCNSS_CTRL,
				strerror(errno));

fail:
	*cal = FAILED;
	close(fd);
	return;
}

void setup_wlan_driver_ath_prop()
{
	property_set("wlan.driver.ath", WLAN_DRIVER_ATH_DEFAULT_VAL);
}

#ifdef WCNSS_QMI
int check_modem_compatability(struct dev_info *mdm_detect_info)
{
	char args[MODEM_BASEBAND_PROPERTY_SIZE] = {0};
	int ret = 0;
	/* Get the hardware property */
	ret = property_get(MODEM_BASEBAND_PROPERTY, args, "");
	if (ret > MODEM_BASEBAND_PROPERTY_SIZE) {
		ALOGE("property [%s] has size [%d] that exceeds max [%d]",
				MODEM_BASEBAND_PROPERTY, ret, MODEM_BASEBAND_PROPERTY_SIZE);
		return 0;
	}
	/* This will check for the type of hardware, and if the
	   hardware type needs external modem, it will check if the
	   modem type is external*/
	if(!strncmp(MODEM_BASEBAND_VALUE_APQ, args, 3)) {

		for (ret = 0; ret < mdm_detect_info->num_modems; ret++) {
			if (mdm_detect_info->mdm_list[ret].type == MDM_TYPE_EXTERNAL) {
				ALOGE("Hardware supports external modem");
				return 1;
			}
		}
		ALOGE("Hardware does not support external modem");
		return 0;
	}
	return 1;
}
#endif

int main(int argc, char *argv[])
{
	int rc;
	int fd_dev, ret_cal;
	int nv_mac_addr = FAILED;
#ifdef WCNSS_QMI
	struct dev_info mdm_detect_info;
	int nom = 0;
#endif

	setup_wlan_config_file();

#ifdef WCNSS_QMI
	/* Call ESOC API to get the number of modems.
	   If the number of modems is not zero, only then proceed
	   with the eap_proxy intialization.*/

	nom = get_system_info(&mdm_detect_info);

	if (nom > 0)
		ALOGE("Failed to get system info, ret %d", nom);

	if (mdm_detect_info.num_modems == 0) {
		ALOGE("wcnss_service: No Modem support for this target"
				" number of modems is %d", mdm_detect_info.num_modems);
		goto nomodem;
	}

	ALOGE("wcnss_service: num_modems = %d", mdm_detect_info.num_modems);

	if(!check_modem_compatability(&mdm_detect_info)) {
		ALOGE("wcnss_service: Target does not have external modem");
		goto nomodem;
	}

	/* initialize the DMS client and request the wlan mac address */

	if (SUCCESS == wcnss_init_qmi()) {

		rc = wcnss_qmi_get_wlan_address(wlan_nv_mac_addr);

		if (rc == SUCCESS) {
			nv_mac_addr = SUCCESS;
			ALOGE("WLAN MAC Addr:" MAC_ADDRESS_STR,
					MAC_ADDR_ARRAY(wlan_nv_mac_addr));
		} else
			ALOGE("Failed to Get MAC addr from modem");

		wcnss_qmi_deinit();
	}
	else
		ALOGE("Failed to Initialize wcnss QMI Interface");

nomodem:
#endif
	setup_wcnss_parameters(&ret_cal, nv_mac_addr);

	fd_dev = open(WCNSS_DEVICE, O_RDWR);
	if (fd_dev < 0) {
		ALOGE("Failed to open wcnss device : %s",
				strerror(errno));
		return fd_dev;
	}

	if (ret_cal != FAILED) {
		rc = wcnss_write_cal_data(fd_dev);
		if (rc != SUCCESS)
			ALOGE("No cal data is written to WCNSS %d", rc);
		else
			ALOGE("Cal data is successfully written to WCNSS");
	}

	setup_wlan_driver_ath_prop();

	rc = wcnss_read_and_store_cal_data(fd_dev);
	if (rc != SUCCESS)
		ALOGE("Failed to read and save cal data %d", rc);
	else
		ALOGI("Calibration data was successfull written to %s",
			WCNSS_CAL_FILE);

	close(fd_dev);

	return rc;
}
