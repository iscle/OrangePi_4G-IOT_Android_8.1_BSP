/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEO_LOG_H__
#define __MIX_VIDEO_LOG_H__
#include <mixlog.h>

#ifdef MIX_LOG_ENABLE
#define LOG_V(format, ...) mix_log(MIX_VIDEO_COMP, MIX_LOG_LEVEL_VERBOSE, format, ##__VA_ARGS__)
#define LOG_I(format, ...) mix_log(MIX_VIDEO_COMP, MIX_LOG_LEVEL_INFO, format, ##__VA_ARGS__)
#define LOG_W(format, ...) mix_log(MIX_VIDEO_COMP, MIX_LOG_LEVEL_WARNING, format, ##__VA_ARGS__)
#define LOG_E(format, ...) mix_log(MIX_VIDEO_COMP, MIX_LOG_LEVEL_ERROR, format, ##__VA_ARGS__)
#else
#define LOG_V(format, ...)
#define LOG_I(format, ...)
#define LOG_W(format, ...)
#define LOG_E(format, ...)
#endif

#endif /*  __MIX_VIDEO_LOG_H__ */
