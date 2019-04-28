package android.platform.test.helpers;

/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.platform.test.helpers.IStandardAppHelper;

public interface IYouTubeGoHelper extends IStandardAppHelper {
  enum GoVideoQuality {
    STANDARD("medium"),
    BASIC("low");

    private String mDisplayName;

    GoVideoQuality(String displayName) {
      mDisplayName = displayName;
    }

    @Override
    public String toString() {
      return mDisplayName;
    }
  }

  /**
   * Setup expectations: YouTube app is open.
   * <p>
   * This method clicks on the first home page video.
   */
  void goToHomePage();

  /**
   * Setup expectations: YouTube is on the home page.
   * <p>
   * This method clicks on a video on the home page.
   */
  void clickOnHomePageVideo();

  /**
   * Setup expectations: YouTube app is open.
   * <p>
   * This method checks if video quality is the same as provided parameter and changes
   * to desired if not.
   *
   * @param quality the desired {@code IYouTubeGoHelper.GoVideoQuality}
   */
  void chooseVideoQuality(GoVideoQuality quality);

  /**
   * Setup expectations: YouTube app is open on Home page.
   * <p>
   * This method clicks on the first video on home page, sets video quality and
   * then clicks on the Play button.
   *
   * @param quality the desired {@code IYouTubeGoHelper.GoVideoQuality}
   */
  void playHomePageVideo(GoVideoQuality quality);

  /**
   * Setup expectations: YouTube is on the video player page.
   * <p>
   * This method pauses the video if it is playing.
   */
  void pauseVideo();

  /**
   * Setup expectations: YouTube is on the video player page.
   * <p>
   * This method resumes the video if it is paused.
   */
  void resumeVideo();

  /**
   * Setup expectations: YouTube is on the home page.
   * <p>
   * This method scrolls to the top of the home page and clicks the search button.
   */
  void goToSearchPage();

  /**
   * Setup expectations: YouTube is on the search page.
   * <p>
   * This method executes search query and checks the search results are displayed.
   */
  void searchVideo(String query);

  /**
   * Setup expectations: YouTube is on the search results page.
   * <p>
   * This method clicks on the first search result video,
   * selects video quality and presses play button.
   *
   *  @param quality the desired {@code IYouTubeGoHelper.GoVideoQuality}
   */
  void playSearchResultPageVideo(GoVideoQuality quality);

  /**
   * Setup expectations: YouTube is on the search results page.
   * <p>
   * This method clicks on the first video on the search results page.
   */
  void clickOnSearchResultPageVideo();

  /**
   * Setup expectations: Choose Video Quality pop up is open.
   * <p>
   * This method clicks on Play button on Choose Video Quality pop up.
   */
  void pressPlayButton();

  /**
   * Setup expectations: YouTube is on the non-fullscreen video player page.
   * <p>
   * This method changes the video player to fullscreen mode. Has no effect if the video player
   * is already in fullscreen mode.
   */
  void goToFullscreenMode();

  /**
   * Setup expectations: YouTube is on the video player page.
   * <p>
   * This method expands video's description.
   */
  void expandVideoDescription();

  /**
   * Setup expectations: YouTube is on the video player page.
   * <p>
   * This method waits for video is finished and clicks on replay button.
   *
   */
  void replayVideo();
}
