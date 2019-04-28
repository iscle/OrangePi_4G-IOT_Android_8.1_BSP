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

#ifdef WCNSS_QMI
#define LOG_TAG "wcnss_qmi"
#include <cutils/log.h>
#include "wcnss_qmi_client.h"
#include "qmi.h"
#include "qmi_client.h"
#include "device_management_service_v01.h"
#include <cutils/properties.h>

#define SUCCESS 0
#define FAILED -1

#define WLAN_ADDR_SIZE   6
#define DMS_QMI_TIMEOUT (2000)

static qmi_client_type dms_qmi_client;
static int qmi_handle;
static int dms_init_done = FAILED;

/* Android system property for fetching the modem type */
#define QMI_UIM_PROPERTY_BASEBAND               "ro.baseband"

/* Android system property values for various modem types */
#define QMI_UIM_PROP_BASEBAND_VALUE_SVLTE_1     "svlte1"
#define QMI_UIM_PROP_BASEBAND_VALUE_SVLTE_2A    "svlte2a"
#define QMI_UIM_PROP_BASEBAND_VALUE_CSFB        "csfb"
#define QMI_UIM_PROP_BASEBAND_VALUE_SGLTE       "sglte"
#define QMI_UIM_PROP_BASEBAND_VALUE_SGLTE2      "sglte2"
#define QMI_UIM_PROP_BASEBAND_VALUE_MSM         "msm"
#define QMI_UIM_PROP_BASEBAND_VALUE_APQ         "apq"
#define QMI_UIM_PROP_BASEBAND_VALUE_MDMUSB      "mdm"
#define QMI_UIM_PROP_BASEBAND_VALUE_DSDA        "dsda"
#define QMI_UIM_PROP_BASEBAND_VALUE_DSDA_2      "dsda2"

static char *dms_find_modem_port( char *prop_value_ptr)
{
	char *qmi_modem_port_ptr = QMI_PORT_RMNET_0;

	/* Sanity check */
	if (prop_value_ptr == NULL) {
		ALOGE("%s", "NULL prop_value_ptr, using default port",
			__func__);
		return qmi_modem_port_ptr;
	}

	ALOGE("%s: Baseband property value read: %s", __func__,
			prop_value_ptr);

	/* Map the port based on the read property */
	if ((strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_SVLTE_1)  == 0) ||
		(strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_SVLTE_2A) == 0) ||
		(strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_CSFB) == 0)) {
		qmi_modem_port_ptr = QMI_PORT_RMNET_SDIO_0;
	} else if ((strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_MDMUSB) == 0) ||
		(strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_SGLTE2) == 0)) {
		qmi_modem_port_ptr = QMI_PORT_RMNET_USB_0;
	} else if ((strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_MSM) == 0) ||
		(strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_APQ) == 0) ||
		(strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_SGLTE) == 0)) {
		qmi_modem_port_ptr = QMI_PORT_RMNET_0;
	} else if (strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_DSDA) == 0) {
		/* If it is a DSDA configuration, use the existing API */
		qmi_modem_port_ptr = (char *)QMI_PLATFORM_INTERNAL_USE_PORT_ID;
	} else if (strcmp(prop_value_ptr,
		QMI_UIM_PROP_BASEBAND_VALUE_DSDA_2) == 0) {
		/* If it is a DSDA2 configuration, use the existing API */
		qmi_modem_port_ptr = (char *)QMI_PLATFORM_INTERNAL_USE_PORT_ID;
	} else {
		ALOGE("%s: Property value does not match,using default port:%s",
			__func__, qmi_modem_port_ptr);
	}

	ALOGE("%s: QMI port found for modem: %s", __func__, qmi_modem_port_ptr);

	return qmi_modem_port_ptr;
}

int wcnss_init_qmi()
{
	qmi_client_error_type qmi_client_err;
	qmi_idl_service_object_type dms_service;
	char prop_value[PROPERTY_VALUE_MAX];
	char *qmi_modem_port = NULL;

	ALOGE("%s: Initialize wcnss QMI Interface", __func__);

	qmi_handle = qmi_init(NULL, NULL);
	if (qmi_handle < 0) {
		ALOGE("%s: Error while initializing qmi", __func__);
		return FAILED;
	}

	dms_service = dms_get_service_object_v01();
	if (dms_service == NULL) {
		ALOGE("%s: Not able to get the service handle", __func__);
		goto exit;
	}

	/* Find out the modem type */
	memset(prop_value, 0x00, sizeof(prop_value));
	property_get(QMI_UIM_PROPERTY_BASEBAND, prop_value, "");

	/* Map to a respective QMI port */
	qmi_modem_port = dms_find_modem_port(prop_value);
	if (qmi_modem_port == NULL) {
		ALOGE("%s: qmi_modem_port is NULL", __func__);
		goto exit;
	}

	qmi_client_err = qmi_client_init((const char *)qmi_modem_port,
			dms_service, NULL, dms_service, &dms_qmi_client);

	if ((qmi_client_err == QMI_PORT_NOT_OPEN_ERR) &&
			(strcmp(qmi_modem_port, QMI_PORT_RMNET_0) == 0)){
		ALOGE("%s: Retrying with port RMNET_1: %d",
				__func__, qmi_client_err);
		qmi_modem_port = QMI_PORT_RMNET_1;
		qmi_client_err = qmi_client_init((const char *)qmi_modem_port,
			       dms_service, NULL, dms_service, &dms_qmi_client);
	}

	if (qmi_client_err != QMI_NO_ERR){
		ALOGE("%s: Error while Initializing QMI Client: %d",
			__func__, qmi_client_err);
		goto exit;
	}

	dms_init_done = SUCCESS;
	return SUCCESS;

exit:
	qmi_handle = qmi_release(qmi_handle);
	if ( qmi_handle < 0 )    {
		ALOGE("%s: Error while releasing qmi %d",
			 __func__, qmi_handle);
	}
	return FAILED;
}

int wcnss_qmi_get_wlan_address(unsigned char *pBdAddr)
{
	qmi_client_error_type qmi_client_err;
	dms_get_mac_address_req_msg_v01 addr_req;
	dms_get_mac_address_resp_msg_v01 addr_resp;

	if ((dms_init_done == FAILED) || (pBdAddr == NULL)) {
		ALOGE("%s: DMS init fail or pBdAddr is NULL", __func__);
		return FAILED;
	}

	/* clear the request content */
	memset(&addr_req, 0, sizeof(addr_req));

	/*Request to get the WLAN MAC address */
	addr_req.device = DMS_DEVICE_MAC_WLAN_V01;

	qmi_client_err = qmi_client_send_msg_sync(dms_qmi_client,
		QMI_DMS_GET_MAC_ADDRESS_REQ_V01, &addr_req, sizeof(addr_req),
		&addr_resp, sizeof(addr_resp), DMS_QMI_TIMEOUT);

	if (qmi_client_err != QMI_NO_ERR){
		ALOGE("%s: Failed to get Rsp from Modem Error:%d",
				__func__, qmi_client_err);
		return FAILED;
	}

	ALOGE("%s: Mac Address_valid: %d Mac Address Len: %d",
				__func__, addr_resp.mac_address_valid,
				addr_resp.mac_address_len);

	if (addr_resp.mac_address_valid &&
		(addr_resp.mac_address_len == WLAN_ADDR_SIZE)) {
		memcpy(pBdAddr, addr_resp.mac_address,
			addr_resp.mac_address_len);
		ALOGE("%s: Succesfully Read WLAN MAC Address", __func__);
		return SUCCESS;
	} else {
		ALOGE("%s: Failed to Read WLAN MAC Address", __func__);
		return FAILED;
	}
}

void wcnss_qmi_deinit()
{
	qmi_client_error_type qmi_client_err;

	ALOGE("%s: Deinitialize wcnss QMI Interface", __func__);

	if (dms_init_done == FAILED) {
		ALOGE("%s: DMS Service was not Initialized", __func__);
		return;
	}

	qmi_client_err = qmi_client_release(dms_qmi_client);

	if (qmi_client_err != QMI_NO_ERR){
		ALOGE("%s: Error while releasing qmi_client: %d",
			__func__, qmi_client_err);
	}

	qmi_handle = qmi_release(qmi_handle);
	if (qmi_handle < 0)    {
		ALOGE("%s: Error while releasing qmi %d",
			__func__, qmi_handle);
	}

	dms_init_done = FAILED;
}
#endif
