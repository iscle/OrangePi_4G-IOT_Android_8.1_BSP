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

public abstract class AbstractPlayMusicHelper extends AbstractStandardAppHelper {

    public AbstractPlayMusicHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: PlayMusic is open and the navigation bar is visible.
     * <br/>
     * This method will open the navigation bar, press "My Library," and navigate to the songs tab.
     * This method blocks until the process is complete.
     */
    public abstract void goToTab(String tabTitle);

    /**
     * Setup expectations: PlayMusic is open and the navigation bar is visible.
     * <br/>
     * This method will open the navigation bar, press "Home".
     * This method blocks until the process is complete.
     */
    public abstract void goToHome();


    /**
     * Setup expectations: PlayMusic is open and in Listen Now.
     * <br/>
     * This method will open the first available thumbnail and play the first visible radio.
     * This method blocks until the process is complete.
     */
    public abstract void playAnyRadioStation();


    /**
     * Clicks on the 'Skip Ad' button on a video ad if it is visible.
     * This method blocks until the process is complete.
     *
     * @return true if an ad is dismissed, false otherwise.
     */
    public abstract boolean dismissAd();

    /**
     * Setup expectations: PlayMusic is open and the navigation bar is visible.
     * <br/>
     * This method will navigate to the Albums tab, select the album, and then select the song. The
     * method will block until the song is playing.
     */
    public abstract void selectSong(String album, String song);

    /**
     * Setup expectations: PlayMusic is open and the navigation bar is visible.
     * <br/>
     * This method will navigate to the Library tab, select the Album tab, and then select the
     * album. The method will block until the song is playing.
     */
    public void selectAlbum(String album) {
        throw new UnsupportedOperationException(
                "Cannot select a single album as playback for Google Play Music.");
    }


    /**
     * Setup expectations: PlayMusic is open with a song playing.
     * <br/>
     * This method will pause the song and block until the song is paused.
     */
    public abstract void pauseSong();

    /**
     * Setup expectations: PlayMusic is open with a song paused.
     * <br/>
     * This method will play the song and block until the song is playing.
     */
    public abstract void playSong();

    /**
     * Setup expectations: PlayMusic is open with a song playing the controls minimized.
     * <br/>
     * This method will press the header and block until the song is expanded.
     */
    public abstract void expandMediaControls();

    /**
     * Setup expectations: PlayMusic is open and on the Songs library tab
     * <br/>
     * This method will press the "Shuffle All" button and block until the song is playing.
     */
    public abstract void pressShuffleAll();

    /**
     * Setup expectations: PlayMusic is open with a song open and expanded.
     * <br/>
     * This method will press the repeat button and cycle to the next state. Unfortunately, the
     * limitations of the Accessibility for Play Music means that we cannot tell what state it
     * currently is in.
     */
    public abstract void pressRepeat();
}
