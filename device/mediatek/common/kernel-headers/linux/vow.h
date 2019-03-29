#ifndef __VOW_H__
#define __VOW_H__

/***********************************************************************************
** VOW Control Message
************************************************************************************/
#define VOW_DEVNAME "vow"
#define VOW_IOC_MAGIC    'V'

static char const *const kVOWDeviceName = "/dev/vow";


//below is control message
#define VOW_SET_CONTROL           _IOW(VOW_IOC_MAGIC, 0x03, unsigned int)
#define VOW_SET_SPEAKER_MODEL     _IOW(VOW_IOC_MAGIC, 0x04, unsigned int)
#define VOW_CLR_SPEAKER_MODEL     _IOW(VOW_IOC_MAGIC, 0x05, unsigned int)
#define VOW_SET_APREG_INFO        _IOW(VOW_IOC_MAGIC, 0x09, unsigned int)
#define VOW_CHECK_STATUS          _IOW(VOW_IOC_MAGIC, 0x0C, unsigned int)



/***********************************************************************************
** Type Define
************************************************************************************/
enum vow_control_cmd_t {
	VOWControlCmd_Init = 0,
	VOWControlCmd_ReadVoiceData,
	VOWControlCmd_EnableDebug,
	VOWControlCmd_DisableDebug,
};

enum vow_eint_status_t {
	VOW_EINT_DISABLE = -2,
	VOW_EINT_FAIL = -1,
	VOW_EINT_PASS = 0,
	VOW_EINT_RETRY = 1,
	NUM_OF_VOW_EINT_STATUS
};

enum vow_flag_type_t {
	VOW_FLAG_DEBUG = 0,
	VOW_FLAG_PRE_LEARN,
	VOW_FLAG_DMIC_LOWPOWER,
	VOW_FLAG_PERIODIC_ENABLE,
	VOW_FLAG_FORCE_PHASE1_DEBUG,
	VOW_FLAG_FORCE_PHASE2_DEBUG,
	VOW_FLAG_SWIP_LOG_PRINT,
	NUM_OF_VOW_FLAG_TYPE
};

enum vow_pwr_status_t {
	VOW_PWR_OFF = 0,
	VOW_PWR_ON = 1,
	NUM_OF_VOW_PWR_STATUS
};

enum vow_ipi_result_t {
	VOW_IPI_SUCCESS = 0,
	VOW_IPI_CLR_SMODEL_ID_NOTMATCH,
	VOW_IPI_SET_SMODEL_NO_FREE_SLOT,
};

struct vow_eint_data_struct_t {
	int size;        /* size of data section */
	int eint_status; /* eint status */
	int id;
	char *data;      /* reserved for future extension */
};

struct vow_model_info_t {
	long  id;
	long  addr;
	long  size;
	long  return_size_addr;
	void *data;
};

#endif //__VOW_H__
