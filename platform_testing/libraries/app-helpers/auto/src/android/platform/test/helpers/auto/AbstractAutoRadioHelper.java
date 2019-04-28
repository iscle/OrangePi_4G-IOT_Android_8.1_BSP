
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

public abstract class AbstractAutoRadioHelper extends AbstractStandardAppHelper {

    public AbstractAutoRadioHelper(Instrumentation instr) {
        super(instr);
    }

    /**
     * Setup expectations: Radio app is open
     *
     * This method is used to play Radio.
     */
    public abstract void playRadio();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to pause radio.
     */
    public abstract void pauseRadio();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to select next station.
     */
    public abstract void clickNextStation();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to select previous station.
     */
    public abstract void clickPreviousStation();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to save current station.
     */
    public abstract void saveCurrentStation();

    /**
     * Setup expectations: Radio app is open
     *
     * This method is used to unsave current station.
     */
    public abstract void unsaveCurrentStation();

    /**
     * Setup expectations: Radio app is open
     *
     * This method is used to open saved station list.
     */
    public abstract void openSavedStationList();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to select AM from menu.
     */
    public abstract void clickAmFromMenu();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to select FM from menu.
     */
    public abstract void clickFmFromMenu();

    /**
     * Setup expectations: Radio app is open.
     *
     * This method is used to tune station manually.
     *
     * @param stationType - to select AM or FM.
     * @param band        - band to tune in.
     */
    public abstract void setStation(String stationType, double band);

    /**
     * Setup expectations: Radio app is open.
     *
     * @return to get current playing station band with Am or Fm.
     */
    public abstract String getStationBand();

    /**
     * Setup expectations: Radio app is open and
     * Favourite/saved station list should be open.
     *
     * This method is used to pause radio from current station card
     */
    public abstract void pauseCurrentStationCard();

    /**
     * Setup expectations: Radio app is open and
     * Favourite/saved station list should be open
     *
     * This method is used to play radio from current station card
     */
    public abstract void playCurrentStationCard();

    /**
     * Setup expectations: Radio app is open and
     * Favourite/saved station list should be open
     *
     * This method is used to exit current station card
     */
    public abstract void exitCurrentStationCard();

    /**
     * Setup expectations: Radio app is open and
     * Favourite/saved station list should be open
     *
     * This method is used to find and play the provided channelName from the list of saved stations
     *
     * @param channelName : the channel name to be played
     */
    public abstract void playFavoriteStation(String channelName);
}
