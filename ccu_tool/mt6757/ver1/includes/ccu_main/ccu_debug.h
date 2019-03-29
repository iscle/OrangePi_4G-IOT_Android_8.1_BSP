#ifndef __CCU_DEBUG__
#define __CCU_DEBUG__

#include "ccu_ext_interface/ccu_types.h"
#define CCU_ASSERT_WNO(X,Y) if(!(X)){_ccu_assert("CCU_ASSERT, statement fail: " #X, Y);}
#define CCU_ASSERT(X) if(!(X)){_ccu_assert("CCU_ASSERT, statement fail: " #X, -1);}
#define CCU_ERROR_WNO(X,Y) {_ccu_assert("CCU_ERROR: " #X, Y);}
#define CCU_ERROR(X) {_ccu_assert("CCU_ERROR: " #X, -1);}
#define CCU_WARNING(X) {_ccu_warning("CCU_WARNING: " #X);}

typedef enum CCU_ERROR
{
    CCU_ERROR_NONE                  = 0x00,
    CCU_ERROR_NO_SENSOR_FOUND       = 0x01, // No sensor ID found in the sensor list
    CCU_ERROR_FPS_NOT_SUPPORTED     = 0x02, // Specified frame rate is not supported
    CCU_ERROR_UNKNOWN_MODE          = 0x03, // unknown mode (mode not supported)
    CCU_ERROR_INVALID_ARG           = 0x04, // invalid argument
    CCU_ERROR_VERSION_MISMATCH      = 0x05, // version of sensor driver mismatches to that of main binary
    CCU_ERROR_TABLE_SIZE_MISMATCH   = 0x06, // function table of sensor driver mismatches to that of main binary
    //++++++++++++++++++++++++++++++++++++++++
	ERROR_NONE 						= 0x00,
	ERROR_INVALID_SCENARIO_ID 		= 0x03,
    //++++++++++++++++++++++++++++++++++++++++
    CCU_ERROR_MAX                   = 0xFF
} CCU_ERROR_T;


void _ccu_assert(char *msg, int errno);
void _ccu_warning(char *msg);

#endif

