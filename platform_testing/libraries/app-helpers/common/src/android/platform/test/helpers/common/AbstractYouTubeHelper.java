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

public abstract class AbstractYouTubeHelper extends AbstractStandardAppHelper {

    public enum VideoQuality {
        QUALITY_AUTO ("Auto"),
        QUALITY_144p ("144p"),
        QUALITY_240p ("240p"),
        QUALITY_360p ("360p"),
        QUALITY_480p ("480p"),
        QUALITY_720p ("720p"),
        QUALITY_1080p("1080p");

        private final String text;

        VideoQuality(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    };

    public AbstractYouTubeHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: YouTube app is open.
     *
     * This method keeps pressing the back button until YouTube is on the home page.
     */
    public abstract void goToHomePage();

    /**
     * Setup expectations: YouTube is on the home page.
     *
     * This method scrolls to the top of the home page and clicks the search button.
     */
    public abstract void goToSearchPage();

    /**
     * Setup expectations: YouTube is on the non-fullscreen video player page.
     *
     * This method changes the video player to fullscreen mode. Has no effect if the video player
     * is already in fullscreen mode.
     */
    public abstract void goToFullscreenMode();

    /**
     * Setup expectations: YouTube is on the home page.
     *
     * This method selects a video on the home page and blocks until the video is playing.
     */
    public abstract void playHomePageVideo();

    /**
     * Setup expectations: YouTube is on the search results page.
     *
     * This method selects a search result video and blocks until the video is playing.
     */
    public abstract void playSearchResultPageVideo();

    /**
     * Setup expectations: Recently opened a video in the YouTube app.
     *
     * This method blocks until the video has loaded.
     *
     * @param timeout wait timeout in milliseconds
     * @return true if video loaded within the timeout, false otherwise
     */
    public abstract boolean waitForVideoToLoad(long timeout);

    /**
     * Setup expectations: Recently initiated a search query in the YouTube app.
     *
     * This method blocks until search results appear.
     *
     * @param timeout wait timeout in milliseconds
     * @return true if search results appeared within timeout, false otherwise
     */
    public abstract boolean waitForSearchResults(long timeout);

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * This method changes the video quality of the current video.
     *
     * @param quality   the desired video quality
     * @see AbstractYouTubeHelper.VideoQuality
     */
    public abstract void setVideoQuality(VideoQuality quality);

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * This method pauses the video if it is playing.
     */
    public abstract void pauseVideo();

    /**
     * Setup expectations: YouTube is on the video player page.
     *
     * This method resumes the video if it is paused.
     */
    public abstract void resumeVideo();
}