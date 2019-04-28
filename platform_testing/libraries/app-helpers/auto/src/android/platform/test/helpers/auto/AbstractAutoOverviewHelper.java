/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.platform.test.helpers;

import android.app.Instrumentation;

public abstract class AbstractAutoOverviewHelper extends AbstractStandardAppHelper{

    public AbstractAutoOverviewHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: in home (overview) screen.
     *
     * This method is used to open settings.
     *
     */
    public abstract void openSettings();

    /**
     * Setup expectations: in home (overview) screen.
     *
     * This method is used to start voice assistant.
     *
     */
    public abstract void startVoiceAssistant();

    /**
     * Setup expectations:
     * 1.Play media from Media player/ Radio.
     * 2.Select Home button.
     * 3.Media/Radio card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to click next track/next station on Media/Radio card.
     *
     */
    public abstract void clickNextTrack();

    /**
     * Setup expectations:
     * 1.Play media from Media player/ Radio.
     * 2.Select Home button.
     * 3.Media/Radio card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to click previous track/previous station on Media/Radio card.
     *
     */
    public abstract void clickPreviousTrack();

    /**
     * Setup expectations:
     * 1.Play media from Media player/ Radio.
     * 2.Select Home button.
     * 3.Media/Radio card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to play media on Media/Radio card.
     *
     */
    public abstract void playMedia();

    /**
     * Setup expectations:
     * 1.Play media from Media player/ Radio.
     * 2.Select Home button.
     * 3.Media/Radio card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to pause media on Media/Radio card.
     *
     */
    public abstract void pauseMedia();

    /**
     * Setup expectations:
     * 1.Play media from Media player/ Radio.
     * 2.Select Home button.
     * 3.Media/Radio card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to open active media card app.
     * ( Radio,Bluetooth Media, Local Media player).
     *
     */
    public abstract void openMediaApp();

    /**
     * Setup expectations:
     * 1.Dial call from Dial app .
     * 2.Select Home button.
     * 3.Dial card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to end call on Dial card.
     *
     */
    public abstract void endCall();

    /**
     * Setup expectations:
     * 1.Dial call from Dial app .
     * 2.Select Home button.
     * 3.Dial card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to mute on going call.
     *
     */
    public abstract void muteCall();

    /**
     * Setup expectations:
     * 1.Dial call from Dial app and end call.
     * 2.Select Home button.
     * 3.Recent Dial card shown in home (overview) screen.
     *
     * else throws UnknownUiException if element not found.
     *
     * This method is used to dial recent call activity.
     *
     */
    public abstract void dialRecentCall();

}
