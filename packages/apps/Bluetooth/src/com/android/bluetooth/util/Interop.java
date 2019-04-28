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

package com.android.bluetooth.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized Bluetooth Interoperability workaround utilities and database.
 * This is the Java version. An analagous native version can be found
 * in /system/bt/devices/include/interop_database.h.
 */
public class Interop {

  /**
   * Simple interop entry consisting of a workarond id (see below)
   * and a (partial or complete) Bluetooth device address string
   * to match against.
   */
  private static class Entry {
    String address;
    int workaround_id;

    public Entry(int workaround_id, String address) {
      this.workaround_id = workaround_id;
      this.address = address;
    }
  }

  /**
   * The actual "database" of interop entries.
   */
  private static List<Entry> entries = null;

  /**
   * Workaround ID for deivces which do not accept non-ASCII
   * characters in SMS messages.
   */
  public static final int INTEROP_MAP_ASCIIONLY = 1;

  /**
   * Initializes the interop datbase with the relevant workaround
   * entries.
   * When adding entries, please provide a description for each
   * device as to what problem the workaround addresses.
   */
  private static void lazyInitInteropDatabase() {
    if (entries != null) return;
    entries = new ArrayList<Entry>();

    /** Mercedes Benz NTG 4.5 does not handle non-ASCII characters in SMS */
    entries.add(new Entry(INTEROP_MAP_ASCIIONLY, "00:26:e8"));
  }

  /**
   * Checks wheter a given device identified by |address| is a match
   * for a given workaround identified by |workaround_id|.
   * Return true if the address matches, false otherwise.
   */
  public static boolean matchByAddress(int workaround_id, String address) {
    if (address == null || address.isEmpty()) return false;

    lazyInitInteropDatabase();
    for (Entry entry : entries) {
      if (entry.workaround_id == workaround_id &&
          entry.address.startsWith(address.toLowerCase())) {
        return true;
      }
    }

    return false;
  }
}
