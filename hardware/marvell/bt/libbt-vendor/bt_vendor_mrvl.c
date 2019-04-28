/******************************************************************************
 *
 *  Copyright (C) 2012 Marvell International Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#define LOG_TAG "bt_vendor_mrvl"

#include <time.h>
#include <errno.h>
#include <sched.h>
#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <termios.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/mman.h>
#include <pthread.h>
#include <utils/Log.h>

#include "bt_vendor_lib.h"


/* ioctl command to release the read thread before driver close */
#define MBTCHAR_IOCTL_RELEASE _IO('M', 1)

#define VERSION "M002"


/***********************************************************
 *  Externs
 ***********************************************************
 */
void hw_mrvl_config_start(void);
void hw_mrvl_sco_config(void);

/***********************************************************
 *  Local variables
 ***********************************************************
 */
static const char mchar_port[] = "/dev/mbtchar0";
static int mchar_fd = -1;

/***********************************************************
 *  Global variables
 ***********************************************************
 */
bt_vendor_callbacks_t *vnd_cb;
unsigned char bdaddr[6];

/***********************************************************
 *  Local functions
 ***********************************************************
 */
static int bt_vnd_mrvl_if_init(const bt_vendor_callbacks_t *p_cb,
		unsigned char *local_bdaddr)
{
	ALOGI("Marvell BT Vendor Lib: ver %s", VERSION);
	vnd_cb = (bt_vendor_callbacks_t *) p_cb;
	memcpy(bdaddr, local_bdaddr, sizeof(bdaddr));
	return 0;
}

static int bt_vnd_mrvl_if_op(bt_vendor_opcode_t opcode, void *param)
{
	int ret = 0;
	int *power_state = NULL;
	int local_st = 0;

	/* ALOGD("opcode = %d", opcode); */
	switch (opcode) {
	case BT_VND_OP_POWER_CTRL:
		power_state = (int *)param;
		if (BT_VND_PWR_OFF == *power_state) {
			ALOGD("Power off");
		} else if (BT_VND_PWR_ON == *power_state) {
			ALOGD("Power on");
		} else {
			ret = -1;
		}
		break;
	case BT_VND_OP_FW_CFG:
		hw_mrvl_config_start();
		break;
	case BT_VND_OP_SCO_CFG:
		hw_mrvl_sco_config();
		break;
	case BT_VND_OP_USERIAL_OPEN:
		mchar_fd = open(mchar_port, O_RDWR|O_NOCTTY);
		if (mchar_fd < 0) {
			ALOGE("Fail to open port %s", mchar_port);
			ret = -1;
		} else {
			ALOGD("open port %s success", mchar_port);
			ret = 1;
		}
		((int *)param)[0] = mchar_fd;
		break;
	case BT_VND_OP_USERIAL_CLOSE:
		if (mchar_fd < 0) {
			ret = -1;
		} else {
			/* mbtchar port is blocked on read. Release the port
			 * before we close it.
			 */
			ioctl(mchar_fd, MBTCHAR_IOCTL_RELEASE, &local_st);
			/* Give it sometime before we close the mbtchar */
			usleep(1000);
			ALOGD("close port %s", mchar_port);
			if (close(mchar_fd) < 0) {
				ALOGE("Fail to close port %s", mchar_port);
				ret = -1;
			} else {
				mchar_fd = -1; /* closed successfully */
			}
		}
		break;
	case BT_VND_OP_GET_LPM_IDLE_TIMEOUT:
		break;
	case BT_VND_OP_LPM_SET_MODE:
		/* TODO: Enable or disable LPM mode on BT Controller.
		 * ret = xx;
		 */
		if (vnd_cb)
			vnd_cb->lpm_cb(ret);

		break;
	case BT_VND_OP_LPM_WAKE_SET_STATE:
		break;
	case BT_VND_OP_EPILOG:
		if (vnd_cb)
			vnd_cb->epilog_cb(BT_VND_OP_RESULT_SUCCESS);
		break;
	default:
		ret = -1;
		break;
	} /* switch (opcode) */

	return ret;
}

static void bt_vnd_mrvl_if_cleanup(void)
{
	return;
}

const bt_vendor_interface_t BLUETOOTH_VENDOR_LIB_INTERFACE = {
	sizeof(bt_vendor_interface_t),
	bt_vnd_mrvl_if_init,
	bt_vnd_mrvl_if_op,
	bt_vnd_mrvl_if_cleanup,
};

