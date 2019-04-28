package com.android.carrierconfig;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CarrierConfigManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import junit.framework.AssertionFailedError;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class CarrierConfigTest extends InstrumentationTestCase {

    /**
     * Iterate over all XML files in assets/ and ensure they parse without error.
     */
    public void testAllFilesParse() {
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser) throws XmlPullParserException, IOException {
                PersistableBundle b = DefaultCarrierConfigService.readConfigFromXml(parser,
                        new CarrierIdentifier("001", "001", "Test", "001001123456789", "", ""));
                assertNotNull("got null bundle", b);
            }
        });
    }

    /**
     * Check that the config bundles in XML files have valid filter attributes.
     * This checks the attribute names only.
     */
    public void testFilterValidAttributes() {
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser) throws XmlPullParserException, IOException {
                int event;
                while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
                    if (event == XmlPullParser.START_TAG
                            && "carrier_config".equals(parser.getName())) {
                        for (int i = 0; i < parser.getAttributeCount(); ++i) {
                            String attribute = parser.getAttributeName(i);
                            switch (attribute) {
                                case "mcc":
                                case "mnc":
                                case "gid1":
                                case "gid2":
                                case "spn":
                                case "imsi":
                                case "device":
                                    break;
                                default:
                                    fail("Unknown attribute '" + attribute
                                            + "' at " + parser.getPositionDescription());
                                    break;
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Tests that the variable names in each XML file match actual keys in CarrierConfigManager.
     */
    public void testVariableNames() {
        final Set<String> varXmlNames = getCarrierConfigXmlNames();
        // organize them into sets by type or unknown
        forEachConfigXml(new ParserChecker() {
            public void check(XmlPullParser parser) throws XmlPullParserException, IOException {
                int event;
                while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
                    if (event == XmlPullParser.START_TAG) {
                        switch (parser.getName()) {
                            case "int-array":
                            case "string-array":
                                // string-array and int-array require the 'num' attribute
                                final String varNum = parser.getAttributeValue(null, "num");
                                assertNotNull("No 'num' attribute in array: "
                                        + parser.getPositionDescription(), varNum);
                            case "int":
                            case "long":
                            case "boolean":
                            case "string":
                                // NOTE: This doesn't check for other valid Bundle values, but it
                                // is limited to the key types in CarrierConfigManager.
                                final String varName = parser.getAttributeValue(null, "name");
                                assertNotNull("No 'name' attribute: "
                                        + parser.getPositionDescription(), varName);
                                assertTrue("Unknown variable: '" + varName
                                        + "' at " + parser.getPositionDescription(),
                                        varXmlNames.contains(varName));
                                // TODO: Check that the type is correct.
                                break;
                            case "carrier_config_list":
                            case "item":
                            case "carrier_config":
                                // do nothing
                                break;
                            default:
                                fail("unexpected tag: '" + parser.getName()
                                        + "' at " + parser.getPositionDescription());
                                break;
                        }
                    }
                }
            }
        });
    }

    /**
     * Utility for iterating over each XML document in the assets folder.
     *
     * This can be used with {@link #forEachConfigXml} to run checks on each XML document.
     * {@link #check} should {@link #fail} if the test does not pass.
     */
    private interface ParserChecker {
        void check(XmlPullParser parser) throws XmlPullParserException, IOException;
    }

    /**
     * Utility for iterating over each XML document in the assets folder.
     */
    private void forEachConfigXml(ParserChecker checker) {
        AssetManager assetMgr = getInstrumentation().getTargetContext().getAssets();
        try {
            String[] files = assetMgr.list("");
            assertNotNull("failed to list files", files);
            assertTrue("no files", files.length > 0);
            for (String fileName : files) {
                try {
                    if (!fileName.startsWith("carrier_config_")) continue;

                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                    XmlPullParser parser = factory.newPullParser();
                    parser.setInput(assetMgr.open(fileName), "utf-8");

                    checker.check(parser);

                } catch (Throwable e) {
                    throw new AssertionError("Problem in " + fileName + ": " + e.getMessage(), e);
                }
            }
            // Check vendor.xml too
            try {
                Resources res = getInstrumentation().getTargetContext().getResources();
                checker.check(res.getXml(R.xml.vendor));
            } catch (Throwable e) {
                throw new AssertionError("Problem in vendor.xml: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            fail(e.toString());
        }
    }

    /**
     * Get the set of config variable names, as used in XML files.
     */
    private Set<String> getCarrierConfigXmlNames() {
        // get values of all KEY_ members of CarrierConfigManager
        Field[] fields = CarrierConfigManager.class.getDeclaredFields();
        HashSet<String> varXmlNames = new HashSet<>();
        for (Field f : fields) {
            if (!f.getName().startsWith("KEY_")) continue;
            if ((f.getModifiers() & Modifier.STATIC) == 0) {
                fail("non-static key in CarrierConfigManager: " + f.toString());
            }
            try {
                String value = (String) f.get(null);
                varXmlNames.add(value);
            }
            catch (IllegalAccessException e) {
                throw new AssertionError("Failed to get config key: " + e.getMessage(), e);
            }
        }
        assertTrue("Found zero keys", varXmlNames.size() > 0);
        return varXmlNames;
    }
}
