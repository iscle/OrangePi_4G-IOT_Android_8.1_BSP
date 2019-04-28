package com.android.carrierconfig;

import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.CarrierService;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.internal.util.FastXmlSerializer;

/**
 * Provides network overrides for carrier configuration.
 *
 * The configuration available through CarrierConfigManager is a combination of default values,
 * default network overrides, and carrier overrides. The default network overrides are provided by
 * this service. For a given network, we look for a matching XML file in our assets folder, and
 * return the PersistableBundle from that file. Assets are preferred over Resources because resource
 * overlays only support using MCC+MNC and that doesn't work with MVNOs. The only resource file used
 * is vendor.xml, to provide vendor-specific overrides.
 */
public class DefaultCarrierConfigService extends CarrierService {

    private static final String SPN_EMPTY_MATCH = "null";

    private static final String TAG = "DefaultCarrierConfigService";

    private XmlPullParserFactory mFactory;

    public DefaultCarrierConfigService() {
        Log.d(TAG, "Service created");
        mFactory = null;
    }

    /**
     * Returns per-network overrides for carrier configuration.
     *
     * This returns a carrier config bundle appropriate for the given network by reading data from
     * files in our assets folder. First we look for a file named after the MCC+MNC of {@code id}
     * and then we read res/xml/vendor.xml. Both files may contain multiple bundles with filters on
     * them. All the matching bundles are flattened to return one carrier config bundle.
     */
    @Override
    public PersistableBundle onLoadConfig(CarrierIdentifier id) {
        Log.d(TAG, "Config being fetched");

        if (id == null) {
            return null;
        }


        PersistableBundle config = null;
        try {
            synchronized (this) {
                if (mFactory == null) {
                    mFactory = XmlPullParserFactory.newInstance();
                }
            }

            XmlPullParser parser = mFactory.newPullParser();
            String fileName = "carrier_config_" + id.getMcc() + id.getMnc() + ".xml";
            parser.setInput(getApplicationContext().getAssets().open(fileName), "utf-8");
            config = readConfigFromXml(parser, id);
        }
        catch (IOException | XmlPullParserException e) {
            Log.d(TAG, e.toString());
            // We can return an empty config for unknown networks.
            config = new PersistableBundle();
        }

        // Treat vendor.xml as if it were appended to the carrier config file we read.
        XmlPullParser vendorInput = getApplicationContext().getResources().getXml(R.xml.vendor);
        try {
            PersistableBundle vendorConfig = readConfigFromXml(vendorInput, id);
            config.putAll(vendorConfig);
        }
        catch (IOException | XmlPullParserException e) {
            Log.e(TAG, e.toString());
        }

        return config;
    }

    /**
     * Parses an XML document and returns a PersistableBundle.
     *
     * <p>This function iterates over each {@code <carrier_config>} node in the XML document and
     * parses it into a bundle if its filters match {@code id}. The format of XML bundles is defined
     * by {@link PersistableBundle#restoreFromXml}. All the matching bundles will be flattened and
     * returned as a single bundle.</p>
     *
     * <p>Here is an example document. The second bundle will be applied to the first only if the
     * GID1 is ABCD.
     * <pre>{@code
     * <carrier_config_list>
     *     <carrier_config>
     *         <boolean name="voicemail_notification_persistent_bool" value="true" />
     *     </carrier_config>
     *     <carrier_config gid1="ABCD">
     *         <boolean name="voicemail_notification_persistent_bool" value="false" />
     *     </carrier_config>
     * </carrier_config_list>
     * }</pre></p>
     *
     * @param parser an XmlPullParser pointing at the beginning of the document.
     * @param id the details of the SIM operator used to filter parts of the document
     * @return a possibly empty PersistableBundle containing the config values.
     */
    static PersistableBundle readConfigFromXml(XmlPullParser parser, CarrierIdentifier id)
            throws IOException, XmlPullParserException {
        PersistableBundle config = new PersistableBundle();

        if (parser == null) {
          return config;
        }

        // Iterate over each <carrier_config> node in the document and add it to the returned
        // bundle if its filters match.
        int event;
        while (((event = parser.next()) != XmlPullParser.END_DOCUMENT)) {
            if (event == XmlPullParser.START_TAG && "carrier_config".equals(parser.getName())) {
                // Skip this fragment if it has filters that don't match.
                if (!checkFilters(parser, id)) {
                    continue;
                }
                PersistableBundle configFragment = PersistableBundle.restoreFromXml(parser);
                config.putAll(configFragment);
            }
        }

        return config;
    }

    /**
     * Checks to see if an XML node matches carrier filters.
     *
     * <p>This iterates over the attributes of the current tag pointed to by {@code parser} and
     * checks each one against {@code id} or {@link Build.DEVICE}. Attributes that are not specified
     * in the node will not be checked, so a node with no attributes will always return true. The
     * supported filter attributes are,
     * <ul>
     *   <li>mcc: {@link CarrierIdentifier#getMcc}</li>
     *   <li>mnc: {@link CarrierIdentifier#getMnc}</li>
     *   <li>gid1: {@link CarrierIdentifier#getGid1}</li>
     *   <li>gid2: {@link CarrierIdentifier#getGid2}</li>
     *   <li>spn: {@link CarrierIdentifier#getSpn}</li>
     *   <li>imsi: {@link CarrierIdentifier#getImsi}</li>
     *   <li>device: {@link Build.DEVICE}</li>
     * </ul>
     * </p>
     *
     * <p>
     * The attributes imsi and spn can be expressed as regexp to filter on patterns.
     * The spn attribute can be set to the string "null" to allow matching against a SIM
     * with no spn set.
     * </p>
     *
     * @param parser an XmlPullParser pointing at a START_TAG with the attributes to check.
     * @param id the carrier details to check against.
     * @return false if any XML attribute does not match the corresponding value.
     */
    static boolean checkFilters(XmlPullParser parser, CarrierIdentifier id) {
        boolean result = true;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attribute = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            switch (attribute) {
                case "mcc":
                    result = result && value.equals(id.getMcc());
                    break;
                case "mnc":
                    result = result && value.equals(id.getMnc());
                    break;
                case "gid1":
                    result = result && value.equalsIgnoreCase(id.getGid1());
                    break;
                case "gid2":
                    result = result && value.equalsIgnoreCase(id.getGid2());
                    break;
                case "spn":
                    result = result && matchOnSP(value, id);
                    break;
                case "imsi":
                    result = result && matchOnImsi(value, id);
                    break;
                case "device":
                    result = result && value.equalsIgnoreCase(Build.DEVICE);
                    break;
                default:
                    Log.e(TAG, "Unknown attribute " + attribute + "=" + value);
                    result = false;
                    break;
            }
        }
        return result;
    }

    /**
     * Check to see if the IMSI expression from the XML matches the IMSI of the
     * Carrier.
     *
     * @param xmlImsi IMSI expression fetched from the resource XML
     * @param id Id of the evaluated CarrierIdentifier
     * @return true if the XML IMSI matches the IMSI of CarrierIdentifier, false
     *         otherwise.
     */
    static boolean matchOnImsi(String xmlImsi, CarrierIdentifier id) {
        boolean matchFound = false;

        String currentImsi = id.getImsi();
        // If we were able to retrieve current IMSI, see if it matches.
        if (currentImsi != null) {
            Pattern imsiPattern = Pattern.compile(xmlImsi, Pattern.CASE_INSENSITIVE);
            Matcher matcher = imsiPattern.matcher(currentImsi);
            matchFound = matcher.matches();
        }
        return matchFound;
    }

    /**
     * Check to see if the service provider name expression from the XML matches the
     * CarrierIdentifier.
     *
     * @param xmlSP SP expression fetched from the resource XML
     * @param id Id of the evaluated CarrierIdentifier
     * @return true if the XML SP matches the phone's SP, false otherwise.
     */
    static boolean matchOnSP(String xmlSP, CarrierIdentifier id) {
        boolean matchFound = false;

        String currentSP = id.getSpn();
        if (SPN_EMPTY_MATCH.equalsIgnoreCase(xmlSP)) {
            if (TextUtils.isEmpty(currentSP)) {
                matchFound = true;
            }
        } else if (currentSP != null) {
            Pattern spPattern = Pattern.compile(xmlSP, Pattern.CASE_INSENSITIVE);
            Matcher matcher = spPattern.matcher(currentSP);
            matchFound = matcher.matches();
        }
        return matchFound;
    }
}
