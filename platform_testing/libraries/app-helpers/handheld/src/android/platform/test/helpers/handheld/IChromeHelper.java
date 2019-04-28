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
import android.support.test.uiautomator.Direction;
import java.lang.IllegalArgumentException;

public interface IChromeHelper extends IStandardAppHelper {
  public enum MenuItem {
    NEW_TAB("New tab"),
    DOWNLOADS("Downloads"),
    HISTORY("History"),
    SETTINGS("Settings");

    private String mDisplayName;

    MenuItem(String displayName) {
      mDisplayName = displayName;
    }

    @Override
    public String toString() {
      return mDisplayName;
    }
  }

  public enum ClearRange {
    PAST_HOUR("past hour"),
    PAST_DAY("past day"),
    PAST_WEEK("past week"),
    LAST_4_WEEKS("last 4 weeks"),
    BEGINNING_OF_TIME("beginning of time");

    private String mDisplayName;

    ClearRange(String displayName) {
      mDisplayName = displayName;
    }

    @Override
    public String toString() {
      return mDisplayName;
    }
  }

  /**
   * Setup expectations: Chrome is open and on a standard page, i.e. a tab is open.
   *
   * This method will open the URL supplied and block until the page is open.
   */
  public abstract void openUrl(String url);

  /**
   * Setup expectations: Chrome is open on a page.
   *
   * This method will scroll the page as directed and block until idle.
   */
  public abstract void flingPage(Direction dir);

  /**
   * Setup expectations: Chrome is open on a page.
   *
   * This method will open the overload menu, indicated by three dots and block until open.
   */
  public abstract void openMenu();

  /**
   * Setup expectations: Chrome is open on a page and menu is opened.
   *
   * This method will open provided item in the menu.
   */
  public abstract void openMenuItem(IChromeHelper.MenuItem menuItem);

  /**
   * Setup expectations: Chrome is open on a page and the tabs are treated as apps.
   *
   * This method will change the settings to treat tabs inside of Chrome and block until Chrome is
   * open on the original tab.
   */
  public abstract void mergeTabs();

  /**
   * Setup expectations: Chrome is open on a page and the tabs are merged.
   *
   * This method will change the settings to treat tabs outside of Chrome and block until Chrome
   * is open on the original tab.
   */
  public abstract void unmergeTabs();

  /**
   * Setup expectations: Chrome is open on a page.
   *
   * This method will reload the page by clicking the refresh button, and block until the page
   * is reopened.
   */
  public abstract void reloadPage();

  /**
   * Setup expectations: Chrome is open on a page.
   *
   * This method is getter for contentDescription of Tab elements.
   */
  public abstract String getTabDescription();

  /**
   * Setup expectations: Chrome is open on a History page.
   *
   * This method clears browser history for provided period of time.
   */
  public abstract void clearBrowsingData(IChromeHelper.ClearRange range);

  /**
   * Setup expectations: Chrome is open on a Downloads page.
   *
   * This method checks header is displayed on Downloads page.
   */
  public abstract void checkIfDownloadsOpened();

  /**
   * Setup expectations: Chrome is open on a Settings page.
   *
   * This method clicks on Privacy setting on Settings page.
   */
  public abstract void openPrivacySettings();
}