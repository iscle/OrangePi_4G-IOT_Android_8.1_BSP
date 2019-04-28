/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#include <stdio.h>
#include "mixaudio.h"
#include "mixparams.h"
#include "mixacp.h"
#include "mixacpmp3.h"

void test_getversion()
{
  g_printf("Calling mixaudio_getversion...\n");
  {
    guint major = 0;
    guint minor = 0;
    MIX_RESULT ret = mix_audio_get_version(&major, &minor);
    if (MIX_SUCCEEDED(ret))
    {
      g_printf("MixAudio Version %u.%u\n", major, minor);
    }
    else
      g_printf("mixaudio_getversion() failed! Ret code : 0x%08x\n", ret);
  }
}

int main (int argc, char **argv)
{
  g_type_init();

  g_printf("Smoke test for MixAudio and structs\n");

  test_getversion();

  g_printf("Creating MixAudio...\n");
  MixAudio *ma = mix_audio_new();
  if (MIX_IS_AUDIO(ma))
  {
    g_printf("Successful.\n");

  }
  else
  {
    g_printf("Failed.\n");
  }

  g_printf("Creating MixAudioConfigParams...\n");
  MixAudioConfigParams *map = mix_acp_new();
  if (MIX_IS_AUDIOCONFIGPARAMS(map))
  {
    g_printf("Successful.\n");

    g_printf("Destroying MixAudioConfigParams...\n");
    mix_acp_unref(map);
    g_printf("Successful.\n");
  }
  else
  {
    g_printf("Failed.\n");
  }
  g_printf("Creating mp3 config params...\n");
  MixAudioConfigParamsMP3 *mp3 = mix_acp_mp3_new();
  
  mp3->CRC = 0;

  g_printf("Destroying MixAudio...\n");
  mix_audio_unref(ma);
  g_printf("Successful.\n");

  g_printf("Smoke completed.\n");
}


