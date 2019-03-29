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

package com.android.cts.verifier.audio.peripheralprofile;

import android.media.AudioDeviceInfo;
import android.support.annotation.NonNull;

import com.android.cts.verifier.audio.peripheralprofile.ListsHelper;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class PeripheralProfile extends DefaultHandler {
    private String mProfileName;
    private String mProfileDescription;

    private String mProductName = "";    // From AudioDeviceInfo

    public class ProfileAttributes {
        public int[] mChannelCounts;
        public int[] mChannelIndexMasks;
        public int[] mChannelPositionMasks;
        public int[] mEncodings;
        public int[] mSampleRates;
    }

    ProfileAttributes mOutputAttributes;
    ProfileAttributes mInputAttributes;

    ProfileButtonAttributes mButtonAttributes;

    //
    // Accessors
    //
    public String getName() { return mProfileName; }
    public String getDescription() { return mProfileDescription; }
    public String getProductName() { return mProductName; }

    public ProfileAttributes getOutputAttributes() {
        return mOutputAttributes;
    }
    public ProfileAttributes getInputAttributes() {
        return mInputAttributes;
    }
    public ProfileButtonAttributes getButtonAttributes() {
        return mButtonAttributes;
    }

    @Override
    public String toString() { return mProfileName; }

    public PeripheralProfile(String profileName, String profileDescription,
                             AudioDeviceInfo outDeviceInfo,
                             AudioDeviceInfo inDeviceInfo,
                             ProfileButtonAttributes buttonAttributes) {
        mProfileName = profileName;
        mProfileDescription = profileDescription;

        if (outDeviceInfo != null) {
            mProductName = outDeviceInfo.getProductName().toString();

            mOutputAttributes = new ProfileAttributes();
            mOutputAttributes.mChannelCounts =
                outDeviceInfo.getChannelCounts();
            mOutputAttributes.mChannelIndexMasks =
                outDeviceInfo.getChannelIndexMasks();
            mOutputAttributes.mChannelPositionMasks =
                outDeviceInfo.getChannelMasks();
            mOutputAttributes.mEncodings = outDeviceInfo.getEncodings();
            mOutputAttributes.mSampleRates = outDeviceInfo.getSampleRates();
        } else {
            mOutputAttributes = null;
        }

        if (inDeviceInfo != null) {
            mProductName = outDeviceInfo.getProductName().toString();

            mInputAttributes = new ProfileAttributes();
            mInputAttributes.mChannelCounts = inDeviceInfo.getChannelCounts();
            mInputAttributes.mChannelIndexMasks = inDeviceInfo.getChannelIndexMasks();
            mInputAttributes.mChannelPositionMasks = inDeviceInfo.getChannelMasks();
            mInputAttributes.mEncodings = inDeviceInfo.getEncodings();
            mInputAttributes.mSampleRates = inDeviceInfo.getSampleRates();
        } else {
            mInputAttributes = null;
        }

        mButtonAttributes = buttonAttributes;
    }

    public static boolean matches(ProfileAttributes attribs, AudioDeviceInfo deviceInfo) {
        boolean match =
            ListsHelper.isMatch(deviceInfo.getChannelCounts(), attribs.mChannelCounts) &&
            ListsHelper.isMatch(deviceInfo.getChannelIndexMasks(), attribs.mChannelIndexMasks) &&
            ListsHelper.isMatch(deviceInfo.getChannelMasks(), attribs.mChannelPositionMasks) &&
            ListsHelper.isMatch(deviceInfo.getEncodings(), attribs.mEncodings) &&
            ListsHelper.isMatch(deviceInfo.getSampleRates(), attribs.mSampleRates);
        return match;
    }

    //
    // Peripheral (XML) Loading
    //
    private static int[] parseIntList(String intList) {
        String[] strings = intList.split(",");
        int[] ints = new int[strings.length];
        for (int index = 0; index < strings.length; index++) {
            ints[index] = Integer.parseInt(strings[index]);
        }
        return ints;
    }

    // XML Tags
    public static final String kTag_Profile = "PeripheralProfile";
    public static final String kTag_OutputDevInfo = "OutputDevInfo";
    public static final String kTag_InputDevInfo = "InputDevInfo";
    public static final String kTag_ButtonInfo = "ButtonInfo";

    // XML Attributes
    //  - Attributes for Profile Tag
    private static final String kAttr_ProfileName = "ProfileName";
    private static final String kAttr_ProfileDescription = "ProfileDescription";
    private static final String kAttr_Product = "ProductName";

    //  - Attributes for DevInfo tags
    private static final String kAttr_ChanCounts = "ChanCounts";
    private static final String kAttr_ChanPosMasks = "ChanPosMasks";
    private static final String kAttr_ChanIndexMasks = "ChanIndexMasks";
    private static final String kAttr_Encodings = "Encodings";
    private static final String kAttr_SampleRates = "SampleRates";
    private static final String kAttr_HasBtnA = "HasBtnA";
    private static final String kAttr_HasBtnB = "HasBtnB";
    private static final String kAttr_HasBtnC = "HasBtnC";
    private static final String kAttr_HasBtnD = "HasBtnD";

    private void parseProfileAttributes(ProfileAttributes attribs, String elementName,
                                        Attributes xmlAtts) {
        attribs.mChannelCounts = parseIntList(xmlAtts.getValue(kAttr_ChanCounts));
        attribs.mChannelPositionMasks = parseIntList(xmlAtts.getValue(kAttr_ChanPosMasks));
        attribs.mChannelIndexMasks = parseIntList(xmlAtts.getValue(kAttr_ChanIndexMasks));
        attribs.mEncodings = parseIntList(xmlAtts.getValue(kAttr_Encodings));
        attribs.mSampleRates = parseIntList(xmlAtts.getValue(kAttr_SampleRates));
    }

    private void parseProfileButtons(ProfileButtonAttributes buttonAttributes, String elementName,
                                     Attributes xmlAtts) {
        buttonAttributes.mHasBtnA = Integer.parseInt(xmlAtts.getValue(kAttr_HasBtnA)) == 1;
        buttonAttributes.mHasBtnB = Integer.parseInt(xmlAtts.getValue(kAttr_HasBtnB)) == 1;
        buttonAttributes.mHasBtnC = Integer.parseInt(xmlAtts.getValue(kAttr_HasBtnC)) == 1;
        buttonAttributes.mHasBtnD = Integer.parseInt(xmlAtts.getValue(kAttr_HasBtnD)) == 1;
    }

    //
    // org.xml.sax.helpers.DefaultHandler overrides
    //
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
        if (qName.equals(kTag_Profile)) {
            mProfileName = atts.getValue(kAttr_ProfileName);
            mProfileDescription = atts.getValue(kAttr_ProfileDescription);
            mProductName = atts.getValue(kAttr_Product);
        } else if (qName.equals(kTag_OutputDevInfo)) {
            mOutputAttributes = new ProfileAttributes();
            parseProfileAttributes(mOutputAttributes, localName, atts);
        } else if (qName.equals(kTag_InputDevInfo)) {
            mInputAttributes = new ProfileAttributes();
            parseProfileAttributes(mInputAttributes, localName, atts);
        } else if (qName.equals(kTag_ButtonInfo)) {
            mButtonAttributes = new ProfileButtonAttributes();
            parseProfileButtons(mButtonAttributes, localName, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
    }
}
