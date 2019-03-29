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

package android.location.cts.asn1.base;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Collection;

/**
 * A UTCTime is a string representation for a UTC timestamp
 *
 * <p>Implements ASN.1 functionality.
 *
 */
public class Asn1UTCTime extends Asn1Object {
  private static final Collection<Asn1Tag> possibleFirstTags =
      ImmutableList.of(Asn1Tag.UTC_TIME);

  private int year;
  private int month;
  private int day;
  private int hour;
  private int minute;
  private int second;
  private DecimalFormat twoDigit = new DecimalFormat("00");
  private DecimalFormat fourDigit = new DecimalFormat("0000");

  public Asn1UTCTime() {
  }

  public static Collection<Asn1Tag> getPossibleFirstTags() {
    return possibleFirstTags;
  }

  public void assignTo(Asn1UTCTime other) {
    year = other.year;
    month = other.month;
    day = other.day;
    hour = other.hour;
    minute = other.minute;
    second = other.second;
  }

  public int getYear() {
    return year;
  }

  public void setYear(int newYear) {
    year = newYear;
  }

  public int getMonth() {
    return month;
  }

  public void setMonth(int newMonth) {
    month = newMonth;
  }

  public int getDay() {
    return day;
  }

  public void setDay(int newDay) {
    day = newDay;
  }

  public int getHour() {
    return hour;
  }

  public void setHour(int newHour) {
    hour = newHour;
  }

  public int getMinute() {
    return minute;
  }

  public void setMinute(int newMinute) {
    minute = newMinute;
  }

  public int getSecond() {
    return second;
  }

  public void setSecond(int newSecond) {
    second = newSecond;
  }

  private Asn1IA5String encodeToIA5String() {
    StringBuilder builder = new StringBuilder();
    builder.append(twoDigit.format(year % 100));
    builder.append(twoDigit.format(month));
    builder.append(twoDigit.format(day));
    builder.append(twoDigit.format(hour % 100));
    builder.append(twoDigit.format(minute));
    builder.append(twoDigit.format(second));
    builder.append("Z");
    Asn1IA5String result = new Asn1IA5String();
    result.setMaxSize(255);
    result.setValue(builder.toString());
    return result;
  }

  public String toHumanReadableString() {
    StringBuilder builder = new StringBuilder();
    builder.append(fourDigit.format(year));
    builder.append('-');
    builder.append(twoDigit.format(month));
    builder.append('-');
    builder.append(twoDigit.format(day));
    builder.append(' ');
    builder.append(twoDigit.format(hour));
    builder.append(':');
    builder.append(twoDigit.format(minute));
    builder.append(':');
    builder.append(twoDigit.format(second));
    return builder.toString();
  }

  @Override Asn1Tag getDefaultTag() {
    return Asn1Tag.UTC_TIME;
  }

  @Override int getBerValueLength() {
    return encodeToIA5String().getBerValueLength();
  }

  @Override void encodeBerValue(ByteBuffer buf) {
    encodeToIA5String().encodeBerValue(buf);
  }

  @Override public void decodeBerValue(ByteBuffer buf) {
    Asn1IA5String result = new Asn1IA5String();
    result.setMaxSize(255);
    result.decodeBerValue(buf);
    retrieveResult(result);
  }

  @Override
  public Iterable<BitStream> encodePerAligned() {
    Asn1IA5String result = encodeToIA5String();
    return result.encodePerAligned();
  }

  @Override
  public Iterable<BitStream> encodePerUnaligned() {
    Asn1IA5String result = encodeToIA5String();
    return result.encodePerUnaligned();
  }

  // The format definition of UTCTime:
  //
  // http://www.obj-sys.com/asn1tutorial/node15.html
  // http://www.obj-sys.com/asn1tutorial/node14.html
  //
  // We currently only support "[YY]YYMMDDHHMM[SS[Z]]"
  private void retrieveResult(Asn1IA5String str) {
    String result = str.getValue();
    int yearLength = 0;
    // If the result has trailing 'Z', remove it.
    if (result.charAt(result.length() - 1) == 'Z') {
      result = result.substring(0, result.length() - 1);
    }
    boolean hasSecond = true;
    switch (result.length()) {
      case 10:
        hasSecond = false;
        // Fall-through
      case 12:
        yearLength = 2;
        break;
      case 14:
        yearLength = 4;
        break;
      default:
        throw new IllegalArgumentException("malformed UTCTime format: " + result);
    }
    year = Integer.parseInt(result.substring(0, yearLength));
    // Two-digit year's range is from 1954 to 2053.
    if (yearLength == 2) {
      if (year > 53) {
        year += 1900;
      } else {
        year += 2000;
      }
    }
    month = Integer.parseInt(result.substring(yearLength, yearLength + 2));
    day = Integer.parseInt(result.substring(yearLength + 2, yearLength + 4));
    hour = Integer.parseInt(result.substring(yearLength + 4, yearLength + 6));
    minute = Integer.parseInt(result.substring(yearLength + 6, yearLength + 8));
    if (hasSecond) {
      second = Integer.parseInt(result.substring(yearLength + 8, yearLength + 10));
    } else {
      second = 0;
    }
  }

  @Override
  public void decodePerAligned(BitStreamReader reader) {
    Asn1IA5String result = new Asn1IA5String();
    result.setMaxSize(255);
    result.decodePerAligned(reader);
    retrieveResult(result);
  }

  @Override
  public void decodePerUnaligned(BitStreamReader reader) {
    Asn1IA5String result = new Asn1IA5String();
    result.setMaxSize(255);
    result.decodePerUnaligned(reader);
    retrieveResult(result);
  }
}
