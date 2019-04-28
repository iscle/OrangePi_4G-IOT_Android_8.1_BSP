/**********************************************************************
* Copyright (C) 2014 Intel Corporation. All rights reserved.

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at

* http://www.apache.org/licenses/LICENSE-2.0

* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**********************************************************************/

#ifndef __DRM_PR_API_H__
#define __DRM_PR_API_H__

/*!
 * Defines
 */
#define DRM_SECURE_CLOCK_FLAG_RESET     (1)

struct drm_nalu_headers
{
   uint32_t frame_size;
   uint32_t parse_size;
   uint8_t  *p_enc_ciphertext;
   uint32_t hdrs_buf_len;
   uint8_t  *p_hdrs_buf;
};

/*!
 *@brief Returns NALU header
 *
 */
uint32_t drm_pr_return_naluheaders(uint32_t session_id,
                                   struct drm_nalu_headers *nalu_info);

/*!
 *@brief Returns SRTC time
 *
 */
uint32_t drm_pr_get_srtc_time(uint32_t  *time,
                              uint32_t  *flags);
#endif
