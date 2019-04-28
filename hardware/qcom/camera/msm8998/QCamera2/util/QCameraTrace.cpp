/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

// Camera dependencies
#include <stdlib.h>
#include <pthread.h>

#include "QCameraTrace.h"

#define CAMSCOPE_MEMSTORE_SIZE 0x00100000 // 1MB

volatile uint32_t kpi_camscope_flags = 0;
volatile uint32_t kpi_camscope_frame_count = 0;

static const char * camscope_filenames[CAMSCOPE_SECTION_SIZE] = {
    "/data/misc/camera/camscope_mmcamera.bin",
    "/data/misc/camera/camscope_hal.bin",
    "/data/misc/camera/camscope_jpeg.bin"
};

static FILE * camscope_fd[CAMSCOPE_SECTION_SIZE];
static uint32_t camscope_num_bytes_stored[CAMSCOPE_SECTION_SIZE];
static char * camscope_memstore[CAMSCOPE_SECTION_SIZE];
static pthread_mutex_t camscope_mutex[CAMSCOPE_SECTION_SIZE];

/* camscope_init:
 *
 *  @camscope_section: camscope section where this function is occurring
 *
 *  Initializes the CameraScope tool functionality
 *
 *  Return: N/A
 */
void camscope_init(camscope_section_type camscope_section) {
    pthread_mutex_init(&(camscope_mutex[camscope_section]), NULL);
    if (camscope_fd[camscope_section] == NULL) {
        if(camscope_memstore[camscope_section] == NULL) {
            camscope_memstore[camscope_section] =
                (char *)malloc(CAMSCOPE_MEMSTORE_SIZE);
            if (camscope_memstore[camscope_section] == NULL) {
              CLOGE(CAM_NO_MODULE, "Failed to allocate camscope memstore"
                    "with size %d\n", CAMSCOPE_MEMSTORE_SIZE);
            }
        }
        camscope_fd[camscope_section] =
            fopen(camscope_filenames[camscope_section], "ab");
    }
}

/* camscope_flush:
 *
 *  @camscope_section: camscope section where this function is occurring
 *
 *  Flushes the camscope memstore to the file system
 *
 *  Return: N/A
 */
static void camscope_flush(camscope_section_type camscope_section) {
    if (camscope_fd[camscope_section] != NULL &&
        camscope_memstore[camscope_section] != NULL) {
        fwrite(camscope_memstore[camscope_section], sizeof(char),
               camscope_num_bytes_stored[camscope_section],
               camscope_fd[camscope_section]);
        camscope_num_bytes_stored[camscope_section] = 0;
    }
}

/* camscope_destroy:
 *
 *  @camscope_section: camscope section where this function is occurring
 *
 *  Flushes any remaining data to the file system and cleans up CameraScope
 *
 *  Return: N/A
 */
void camscope_destroy(camscope_section_type camscope_section) {
    if (camscope_fd[camscope_section] != NULL) {
        pthread_mutex_lock(&(camscope_mutex[camscope_section]));
        if(camscope_memstore[camscope_section] != NULL) {
            camscope_flush(camscope_section);
            free(camscope_memstore[camscope_section]);
            camscope_memstore[camscope_section] = NULL;
        }
        fclose(camscope_fd[camscope_section]);
        camscope_fd[camscope_section] = NULL;
        pthread_mutex_unlock(&(camscope_mutex[camscope_section]));
    }
    pthread_mutex_destroy(&(camscope_mutex[camscope_section]));
}

/* camscope_reserve:
 *
 *  @camscope_section:     camscope section where this function is occurring
 *  @num_bytes_to_reserve: number in bytes to reserve on the memstore
 *
 *  Reserves a number of bytes on the memstore flushing to the
 *  file system if remaining space is insufficient
 *
 *  Return: number of bytes successfully reserved on the memstore
 */
uint32_t camscope_reserve(camscope_section_type camscope_section,
                                 uint32_t num_bytes_to_reserve) {
    uint32_t bytes_reserved = 0;
    if (camscope_fd[camscope_section] != NULL &&
        num_bytes_to_reserve <= CAMSCOPE_MEMSTORE_SIZE) {
        int32_t size = CAMSCOPE_MEMSTORE_SIZE -
               camscope_num_bytes_stored[camscope_section] -
               num_bytes_to_reserve;
        if (size < 0) {
            camscope_flush(camscope_section);
        }
        bytes_reserved = num_bytes_to_reserve;
    }
    return bytes_reserved;
}

/* camscope_store_data:
 *
 *  @camscope_section: camscope section where this function is occurring
 *  @data:             data to be stored
 *  @size:             size of data to be stored
 *
 *  Store the data to the memstore and calculate remaining space
 *
 *  Return: N/A
 */
void camscope_store_data(camscope_section_type camscope_section,
                       void* data, uint32_t size) {
    if(camscope_memstore[camscope_section] != NULL) {
        memcpy(camscope_memstore[camscope_section] +
               camscope_num_bytes_stored[camscope_section], (char*)data, size);
        camscope_num_bytes_stored[camscope_section] += size;
    }
}

/* camscope_mutex_lock:
 *
 *  @camscope_section: camscope section where this function is occurring
 *
 *  Lock the camscope mutex lock for the given camscope section
 *
 *  Return: N/A
 */
void camscope_mutex_lock(camscope_section_type camscope_section) {
    pthread_mutex_lock(&(camscope_mutex[camscope_section]));
}

/* camscope_mutex_unlock:
 *
 *  @camscope_section: camscope section where this function is occurring
 *
 *  Unlock the camscope mutex lock for the given camscope section
 *
 *  Return: N/A
 */
void camscope_mutex_unlock(camscope_section_type camscope_section) {
    pthread_mutex_unlock(&(camscope_mutex[camscope_section]));
}
