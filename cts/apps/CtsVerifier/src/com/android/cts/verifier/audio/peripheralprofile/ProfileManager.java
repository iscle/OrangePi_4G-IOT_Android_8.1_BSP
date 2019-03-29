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

import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ProfileManager {
    private static final String mBuiltInprofiles =
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>" +
            "<ProfileList Version=\"1.0.0\">" +
              "<PeripheralProfile ProfileName=\"Headset\" ProfileDescription=\"Microsoft LX-3000\" ProductName=\"USB-Audio - Microsoft LifeChat LX-3000\">" +
                "<OutputDevInfo ChanCounts=\"2\" ChanPosMasks=\"12\" ChanIndexMasks=\"3\" Encodings=\"2\" SampleRates=\"44100,48000\" />" +
                "<InputDevInfo ChanCounts=\"1,2\" ChanPosMasks=\"12,16\" ChanIndexMasks=\"1\" Encodings=\"2\" SampleRates=\"44100,48000\" />" +
                "<ButtonInfo HasBtnA=\"0\" HasBtnB=\"1\" HasBtnC=\"1\" HasBtnD=\"0\" />" +
            "</PeripheralProfile>" +
            "<PeripheralProfile ProfileName=\"Audio Interface\" ProfileDescription=\"Presonus AudioVox 44VSL\" ProductName=\"USB-Audio - AudioBox 44 VSL\">" +
              "<OutputDevInfo ChanCounts=\"2,4\" ChanPosMasks=\"12\" ChanIndexMasks=\"15\" Encodings=\"4\" SampleRates=\"44100,48000,88200,96000\" />" +
              "<InputDevInfo ChanCounts=\"1,2,4\" ChanPosMasks=\"12,16\" ChanIndexMasks=\"15\" Encodings=\"4\" SampleRates=\"44100,48000,88200,96000\" />" +
            "</PeripheralProfile>" +
            "<PeripheralProfile ProfileName=\"AudioBox 22VSL\" ProfileDescription=\"Presonus AudioBox 22VSL\" ProductName=\"USB-Audio - AudioBox 22 VSL\">" +
              "<OutputDevInfo ChanCounts=\"2\" ChanPosMasks=\"12\" ChanIndexMasks=\"3\" Encodings=\"4\" SampleRates=\"44100,48000,88200,96000\" />" +
              "<InputDevInfo ChanCounts=\"1,2\" ChanPosMasks=\"12,16\" ChanIndexMasks=\"3\" Encodings=\"4\" SampleRates=\"44100,48000,88200,96000\" />" +
            "</PeripheralProfile>" +
            "<PeripheralProfile ProfileName=\"AudioBox USB\" ProfileDescription=\"Presonus AudioBox USB\" ProductName=\"USB-Audio - AudioBox USB\">" +
              "<OutputDevInfo ChanCounts=\"2\" ChanPosMasks=\"12\" ChanIndexMasks=\"3\" Encodings=\"4\" SampleRates=\"44100,48000\" />" +
              "<InputDevInfo ChanCounts=\"1,2\" ChanPosMasks=\"12,16\" ChanIndexMasks=\"3\" Encodings=\"4\" SampleRates=\"44100,48000\" />" +
            "</PeripheralProfile>" +
            "<PeripheralProfile ProfileName=\"gen1-headset\" ProfileDescription=\"Reference USB Headset\" ProductName=\"USB-Audio - Skylab\">" +
            "<OutputDevInfo ChanCounts=\"2\" ChanPosMasks=\"12\" ChanIndexMasks=\"3\" Encodings=\"2,4\" SampleRates=\"8000,16000,32000,44100,48000\" />" +
            "<InputDevInfo ChanCounts=\"1,2\" ChanPosMasks=\"12,16\" ChanIndexMasks=\"1\" Encodings=\"2\" SampleRates=\"8000,16000,32000,44100,48000\" />" +
            "<ButtonInfo HasBtnA=\"1\" HasBtnB=\"1\" HasBtnC=\"1\" HasBtnD=\"1\" />" +
          "</PeripheralProfile>" +
          "<PeripheralProfile ProfileName=\"mir\" ProfileDescription=\"Reference USB Dongle\" ProductName=\"USB-Audio - USB Audio\">" +
            "<OutputDevInfo ChanCounts=\"2\" ChanPosMasks=\"12\" ChanIndexMasks=\"3\" Encodings=\"4\" SampleRates=\"48000\" />" +
          "</PeripheralProfile>" +
          "</ProfileList>";

    // XML Tags and Attributes
    private final static String kTag_ProfileList = "ProfileList";
    private final static String kAttrName_Version = "Version";
    private final static String kValueStr_Version = "1.0.0";

    private final ArrayList<PeripheralProfile> mProfiles =
        new ArrayList<PeripheralProfile>();

    private PeripheralProfile mParsingProfile = null;

    public boolean addProfile(PeripheralProfile profile) {
        mProfiles.add(profile);

        return true;
    }

    private class ProfileLoader extends DefaultHandler {
        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
            if (localName.equals(kTag_ProfileList)) {
                // Maybe check the version here.
            } else if (localName.equals(PeripheralProfile.kTag_Profile)){
                mParsingProfile = new PeripheralProfile(null, null, null, null, null);
                mParsingProfile.startElement(namespaceURI, localName, qName, atts);
            } else {
                mParsingProfile.startElement(namespaceURI, localName, qName, atts);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals(kTag_ProfileList)) {
                // Version Checking here maybe?
            } else if (localName.equals(PeripheralProfile.kTag_Profile)){
                mProfiles.add(mParsingProfile);
                mParsingProfile = null;
            }
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public boolean loadProfiles(InputStream inStream) {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp;
        try {
            sp = spf.newSAXParser();
            XMLReader xr = sp.getXMLReader();
            xr.setContentHandler(new ProfileLoader());
            xr.parse(new InputSource(inStream));
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    public boolean loadProfiles(String profilesXML) {
        mProfiles.clear();

        return loadProfiles(new ByteArrayInputStream(profilesXML.getBytes()));
    }

    public boolean loadProfiles() {
        return loadProfiles(mBuiltInprofiles);
    }

    //
    // Access
    //
    public ArrayList<PeripheralProfile> getProfiles() { return mProfiles; }

    public int getNumProfiles() {
        return mProfiles.size();
    }
    public PeripheralProfile getProfile(int index) {
        return mProfiles.get(index);
    }

    @Nullable
    public PeripheralProfile getProfile(String productName) {
        for(PeripheralProfile profile : mProfiles) {
            if (productName.equals(profile.getProductName())) {
                return profile;
            }
        }
        return null;
    }
}
