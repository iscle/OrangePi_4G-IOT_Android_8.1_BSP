/*
 * Copyright (C) 2013-2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TFA_98XX_H
#define TFA_98XX_H

#ifdef SMART_PA_TFA_98XX_SUPPORTED
int audio_extn_tfa_98xx_enable_speaker(void);
void audio_extn_tfa_98xx_disable_speaker(snd_device_t snd_device);
void audio_extn_tfa_98xx_set_mode();
void audio_extn_tfa_98xx_set_mode_bt(void);
void audio_extn_tfa_98xx_update(void);
void audio_extn_tfa_98xx_set_voice_vol(float vol);
int audio_extn_tfa_98xx_init(struct audio_device *adev);
void audio_extn_tfa_98xx_deinit(void);
bool audio_extn_tfa_98xx_is_supported(void);
#else
#define audio_extn_tfa_98xx_enable_speaker(void)                (0)
#define audio_extn_tfa_98xx_disable_speaker(snd_device)         (0)
#define audio_extn_tfa_98xx_set_mode()                          (0)
#define audio_extn_tfa_98xx_set_mode_bt()                       (0)
#define audio_extn_tfa_98xx_update(void)                        (0)
#define audio_extn_tfa_98xx_set_voice_vol(vol)                  (0)
#define audio_extn_tfa_98xx_init(adev)                          (0)
#define audio_extn_tfa_98xx_deinit(void)                        (0)
#define audio_extn_tfa_98xx_is_supported(void)                  (false)
#endif

#endif /* TFA_98XX_H */
