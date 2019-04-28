/* Copyright (c) 2017, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

#define LOG_TAG "QCameraExtZoomTranslator"

#include <stdlib.h>
#include <utils/Errors.h>
#include "QCameraExtZoomTranslator.h"
#include <dlfcn.h>

extern "C" {
#include "mm_camera_dbg.h"
}

namespace qcamera {

/*===========================================================================
 * FUNCTION   : QCameraExtZoomTranslator constructor
 *
 * DESCRIPTION: class constructor
 *
 * PARAMETERS : none
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraExtZoomTranslator::QCameraExtZoomTranslator()
{
    mLibHandle = NULL;
    mInitSuccess = false;
    memset(&mInitData, 0, sizeof(zoom_trans_init_data));
}

/*===========================================================================
 * FUNCTION   : QCameraExtZoomTranslator destructor
 *
 * DESCRIPTION: class destructor
 *
 * PARAMETERS : none
 *
 * RETURN     : void
 *
 *==========================================================================*/
QCameraExtZoomTranslator::~QCameraExtZoomTranslator()
{
    // dlclose the lib here and not in deinit
}


/*===========================================================================
 * FUNCTION   : create
 *
 * DESCRIPTION: This is a static method to create QCameraExtZoomTranslator object.
 *              It calls the private constructor of the class and only returns a
 *              valid object if the library loading succeeds.
 *
 * PARAMETERS : None
 *
 * RETURN     : Valid object pointer if succeeds
 *              NULL if fails
 *
 *==========================================================================*/
QCameraExtZoomTranslator* QCameraExtZoomTranslator::create()
{
    QCameraExtZoomTranslator *pZoomTranslator = NULL;

    // dlopen and dlsym here and if successful, create zoom translator object
    // if (success) {
    //     pZoomTranslator = new QCameraExtZoomTranslator();
    // }

    return pZoomTranslator;
}


/*===========================================================================
 * FUNCTION   : init
 *
 * DESCRIPTION: This function passes the initialization data to the zoom
 *              translation library.
 *
 *
 * PARAMETERS :
 *@initData   : Initialization data
 *
 * RETURN     :
 * NO_ERROR           : Success
 * INVALID_OPERATION  : Failure
 *
 *==========================================================================*/
int32_t QCameraExtZoomTranslator::init(
        __unused zoom_trans_init_data initData)
{
    int32_t rc = INVALID_OPERATION;

    // Pass the initData to the zoom translation lib and if the operation succeeds,
    // set rc to NO_ERROR. Set member variable mInitSuccess to true.

    return rc;
}


/*===========================================================================
 * FUNCTION   : getZoomValues
 *
 * DESCRIPTION: This function passes the user zoom to the zoom translation lib and
 *              gets back wide and tele zoom values corresponding to that user zoom.
 *
 *
 * PARAMETERS :
 *@userZoom   : User zoom (zoom index into the zoom table)
 *@wideZoom   : Zoom for wide camera (zoom index into the zoom table)
 *@teleZoom   : Zoom for tele camera (zoom index into the zoom table)
 *
 * RETURN     :
 * NO_ERROR           : Success
 * INVALID_OPERATION  : Failure
 *
 *==========================================================================*/
int32_t QCameraExtZoomTranslator::getZoomValues(
        __unused uint32_t  userZoom,
        __unused uint32_t *wideZoom,
        __unused uint32_t *teleZoom)
{
    int32_t rc = INVALID_OPERATION;

    // Pass the userzoom to the zoom translation lib to return wideZoom and teleZoom values.
    // If the operation succeeds, set rc to NO_ERROR.

    return rc;
}


/*===========================================================================
 * FUNCTION   : deInit
 *
 * DESCRIPTION: This function de-initializes zoom translation lib.
 *
 *
 * PARAMETERS : None
 *
 * RETURN     :
 * NO_ERROR           : Success
 * INVALID_OPERATION  : Failure
 *
 *==========================================================================*/
int32_t QCameraExtZoomTranslator::deInit()
{
    int32_t rc = INVALID_OPERATION;

    if (mInitSuccess) {
        // Deinit the zoom translation lib and if successful, set rc to NO_ERROR.
        // Do not dlclose here. dlclose in the destructor
    }

    return rc;
}


/*===========================================================================
 * FUNCTION   : isInitialized
 *
 * DESCRIPTION: Check if the zoom translator is initialized successfully
 *
 *
 * PARAMETERS : None
 *
 * RETURN     :
 * true       : Initialized successfully
 * false      : Not initialized
 *
 *==========================================================================*/
bool QCameraExtZoomTranslator::isInitialized()
{
    return mInitSuccess;
}

}; // namespace qcamera
