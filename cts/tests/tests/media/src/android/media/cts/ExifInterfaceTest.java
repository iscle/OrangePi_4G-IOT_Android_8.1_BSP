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

package android.media.cts;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.FileUtils;
import android.test.AndroidTestCase;
import android.util.Log;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Type;

import libcore.io.IoUtils;
import libcore.io.Streams;

public class ExifInterfaceTest extends AndroidTestCase {
    private static final String TAG = ExifInterface.class.getSimpleName();
    private static final boolean VERBOSE = false;  // lots of logging

    private static final double DIFFERENCE_TOLERANCE = .001;

    private static final String EXTERNAL_BASE_DIRECTORY = "/test/images/";

    // This base directory is needed for the files listed below.
    // These files will be available for download in Android O release.
    // Link: https://source.android.com/compatibility/cts/downloads.html#cts-media-files
    private static final String EXIF_BYTE_ORDER_II_JPEG = "image_exif_byte_order_ii.jpg";
    private static final String EXIF_BYTE_ORDER_MM_JPEG = "image_exif_byte_order_mm.jpg";
    private static final String LG_G4_ISO_800_DNG = "lg_g4_iso_800.dng";
    private static final String SONY_RX_100_ARW = "sony_rx_100.arw";
    private static final String CANON_G7X_CR2 = "canon_g7x.cr2";
    private static final String FUJI_X20_RAF = "fuji_x20.raf";
    private static final String NIKON_1AW1_NEF = "nikon_1aw1.nef";
    private static final String NIKON_P330_NRW = "nikon_p330.nrw";
    private static final String OLYMPUS_E_PL3_ORF = "olympus_e_pl3.orf";
    private static final String PANASONIC_GM5_RW2 = "panasonic_gm5.rw2";
    private static final String PENTAX_K5_PEF = "pentax_k5.pef";
    private static final String SAMSUNG_NX3000_SRW = "samsung_nx3000.srw";
    private static final String VOLANTIS_JPEG = "volantis.jpg";

    private static final String[] EXIF_TAGS = {
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_WHITE_BALANCE
    };

    private static class ExpectedValue {
        // Thumbnail information.
        public final boolean hasThumbnail;
        public final int thumbnailWidth;
        public final int thumbnailHeight;
        public final boolean isThumbnailCompressed;

        // GPS information.
        public final boolean hasLatLong;
        public final float latitude;
        public final float longitude;
        public final float altitude;

        // Values.
        public final String make;
        public final String model;
        public final float aperture;
        public final String dateTimeOriginal;
        public final float exposureTime;
        public final float flash;
        public final String focalLength;
        public final String gpsAltitude;
        public final String gpsAltitudeRef;
        public final String gpsDatestamp;
        public final String gpsLatitude;
        public final String gpsLatitudeRef;
        public final String gpsLongitude;
        public final String gpsLongitudeRef;
        public final String gpsProcessingMethod;
        public final String gpsTimestamp;
        public final int imageLength;
        public final int imageWidth;
        public final String iso;
        public final int orientation;
        public final int whiteBalance;

        private static String getString(TypedArray typedArray, int index) {
            String stringValue = typedArray.getString(index);
            if (stringValue == null || stringValue.equals("")) {
                return null;
            }
            return stringValue.trim();
        }

        public ExpectedValue(TypedArray typedArray) {
            int index = 0;

            // Reads thumbnail information.
            hasThumbnail = typedArray.getBoolean(index++, false);
            thumbnailWidth = typedArray.getInt(index++, 0);
            thumbnailHeight = typedArray.getInt(index++, 0);
            isThumbnailCompressed = typedArray.getBoolean(index++, false);

            // Reads GPS information.
            hasLatLong = typedArray.getBoolean(index++, false);
            latitude = typedArray.getFloat(index++, 0f);
            longitude = typedArray.getFloat(index++, 0f);
            altitude = typedArray.getFloat(index++, 0f);

            // Reads values.
            make = getString(typedArray, index++);
            model = getString(typedArray, index++);
            aperture = typedArray.getFloat(index++, 0f);
            dateTimeOriginal = getString(typedArray, index++);
            exposureTime = typedArray.getFloat(index++, 0f);
            flash = typedArray.getFloat(index++, 0f);
            focalLength = getString(typedArray, index++);
            gpsAltitude = getString(typedArray, index++);
            gpsAltitudeRef = getString(typedArray, index++);
            gpsDatestamp = getString(typedArray, index++);
            gpsLatitude = getString(typedArray, index++);
            gpsLatitudeRef = getString(typedArray, index++);
            gpsLongitude = getString(typedArray, index++);
            gpsLongitudeRef = getString(typedArray, index++);
            gpsProcessingMethod = getString(typedArray, index++);
            gpsTimestamp = getString(typedArray, index++);
            imageLength = typedArray.getInt(index++, 0);
            imageWidth = typedArray.getInt(index++, 0);
            iso = getString(typedArray, index++);
            orientation = typedArray.getInt(index++, 0);
            whiteBalance = typedArray.getInt(index++, 0);

            typedArray.recycle();
        }
    }

    private void printExifTagsAndValues(String fileName, ExifInterface exifInterface) {
        // Prints thumbnail information.
        if (exifInterface.hasThumbnail()) {
            byte[] thumbnailBytes = exifInterface.getThumbnailBytes();
            if (thumbnailBytes != null) {
                Log.v(TAG, fileName + " Thumbnail size = " + thumbnailBytes.length);
                Bitmap bitmap = exifInterface.getThumbnailBitmap();
                if (bitmap == null) {
                    Log.e(TAG, fileName + " Corrupted thumbnail!");
                } else {
                    Log.v(TAG, fileName + " Thumbnail size: " + bitmap.getWidth() + ", "
                            + bitmap.getHeight());
                }
            } else {
                Log.e(TAG, fileName + " Unexpected result: No thumbnails were found. "
                        + "A thumbnail is expected.");
            }
        } else {
            if (exifInterface.getThumbnailBytes() != null) {
                Log.e(TAG, fileName + " Unexpected result: A thumbnail was found. "
                        + "No thumbnail is expected.");
            } else {
                Log.v(TAG, fileName + " No thumbnail");
            }
        }

        // Prints GPS information.
        Log.v(TAG, fileName + " Altitude = " + exifInterface.getAltitude(.0));

        float[] latLong = new float[2];
        if (exifInterface.getLatLong(latLong)) {
            Log.v(TAG, fileName + " Latitude = " + latLong[0]);
            Log.v(TAG, fileName + " Longitude = " + latLong[1]);
        } else {
            Log.v(TAG, fileName + " No latlong data");
        }

        // Prints values.
        for (String tagKey : EXIF_TAGS) {
            String tagValue = exifInterface.getAttribute(tagKey);
            Log.v(TAG, fileName + " Key{" + tagKey + "} = '" + tagValue + "'");
        }
    }

    private void assertIntTag(ExifInterface exifInterface, String tag, int expectedValue) {
        int intValue = exifInterface.getAttributeInt(tag, 0);
        assertEquals(expectedValue, intValue);
    }

    private void assertFloatTag(ExifInterface exifInterface, String tag, float expectedValue) {
        double doubleValue = exifInterface.getAttributeDouble(tag, 0.0);
        assertEquals(expectedValue, doubleValue, DIFFERENCE_TOLERANCE);
    }

    private void assertStringTag(ExifInterface exifInterface, String tag, String expectedValue) {
        String stringValue = exifInterface.getAttribute(tag);
        if (stringValue != null) {
            stringValue = stringValue.trim();
        }
        stringValue = (stringValue == "") ? null : stringValue;

        assertEquals(expectedValue, stringValue);
    }

    private void compareWithExpectedValue(ExifInterface exifInterface,
            ExpectedValue expectedValue, String verboseTag) {
        if (VERBOSE) {
            printExifTagsAndValues(verboseTag, exifInterface);
        }
        // Checks a thumbnail image.
        assertEquals(expectedValue.hasThumbnail, exifInterface.hasThumbnail());
        if (expectedValue.hasThumbnail) {
            byte[] thumbnailBytes = exifInterface.getThumbnailBytes();
            assertNotNull(thumbnailBytes);
            Bitmap thumbnailBitmap = exifInterface.getThumbnailBitmap();
            assertNotNull(thumbnailBitmap);
            assertEquals(expectedValue.thumbnailWidth, thumbnailBitmap.getWidth());
            assertEquals(expectedValue.thumbnailHeight, thumbnailBitmap.getHeight());
            assertEquals(expectedValue.isThumbnailCompressed,
                    exifInterface.isThumbnailCompressed());
        } else {
            assertNull(exifInterface.getThumbnail());
        }

        // Checks GPS information.
        float[] latLong = new float[2];
        assertEquals(expectedValue.hasLatLong, exifInterface.getLatLong(latLong));
        if (expectedValue.hasLatLong) {
            assertEquals(expectedValue.latitude, latLong[0], DIFFERENCE_TOLERANCE);
            assertEquals(expectedValue.longitude, latLong[1], DIFFERENCE_TOLERANCE);
        }
        assertEquals(expectedValue.altitude, exifInterface.getAltitude(.0), DIFFERENCE_TOLERANCE);

        // Checks values.
        assertStringTag(exifInterface, ExifInterface.TAG_MAKE, expectedValue.make);
        assertStringTag(exifInterface, ExifInterface.TAG_MODEL, expectedValue.model);
        assertFloatTag(exifInterface, ExifInterface.TAG_F_NUMBER, expectedValue.aperture);
        assertStringTag(exifInterface, ExifInterface.TAG_DATETIME_ORIGINAL,
                expectedValue.dateTimeOriginal);
        assertFloatTag(exifInterface, ExifInterface.TAG_EXPOSURE_TIME, expectedValue.exposureTime);
        assertFloatTag(exifInterface, ExifInterface.TAG_FLASH, expectedValue.flash);
        assertStringTag(exifInterface, ExifInterface.TAG_FOCAL_LENGTH, expectedValue.focalLength);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_ALTITUDE, expectedValue.gpsAltitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_ALTITUDE_REF,
                expectedValue.gpsAltitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_DATESTAMP, expectedValue.gpsDatestamp);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LATITUDE, expectedValue.gpsLatitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LATITUDE_REF,
                expectedValue.gpsLatitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LONGITUDE, expectedValue.gpsLongitude);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_LONGITUDE_REF,
                expectedValue.gpsLongitudeRef);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_PROCESSING_METHOD,
                expectedValue.gpsProcessingMethod);
        assertStringTag(exifInterface, ExifInterface.TAG_GPS_TIMESTAMP, expectedValue.gpsTimestamp);
        assertIntTag(exifInterface, ExifInterface.TAG_IMAGE_LENGTH, expectedValue.imageLength);
        assertIntTag(exifInterface, ExifInterface.TAG_IMAGE_WIDTH, expectedValue.imageWidth);
        assertStringTag(exifInterface, ExifInterface.TAG_ISO_SPEED_RATINGS, expectedValue.iso);
        assertIntTag(exifInterface, ExifInterface.TAG_ORIENTATION, expectedValue.orientation);
        assertIntTag(exifInterface, ExifInterface.TAG_WHITE_BALANCE, expectedValue.whiteBalance);
    }

    private void testExifInterfaceCommon(String fileName, ExpectedValue expectedValue)
            throws IOException {
        File imageFile = new File(Environment.getExternalStorageDirectory(), fileName);
        String verboseTag = imageFile.getName();

        // Creates via path.
        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        assertNotNull(exifInterface);
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag);

        InputStream in = null;
        // Creates via InputStream.
        try {
            in = new BufferedInputStream(new FileInputStream(imageFile.getAbsolutePath()));
            exifInterface = new ExifInterface(in);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag);
        } finally {
            IoUtils.closeQuietly(in);
        }

        // Creates via FileDescriptor.
        FileDescriptor fd = null;
        try {
            fd = Os.open(imageFile.getAbsolutePath(), OsConstants.O_RDONLY, 0600);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        } finally {
            IoUtils.closeQuietly(fd);
        }
    }

    private void testSaveAttributes_withFileName(String fileName, ExpectedValue expectedValue)
            throws IOException {
        File imageFile = new File(Environment.getExternalStorageDirectory(), fileName);
        String verboseTag = imageFile.getName();

        ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        exifInterface.saveAttributes();
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag);

        // Test for modifying one attribute.
        String backupValue = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
        exifInterface.setAttribute(ExifInterface.TAG_MAKE, "abc");
        exifInterface.saveAttributes();
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals("abc", exifInterface.getAttribute(ExifInterface.TAG_MAKE));
        // Restore the backup value.
        exifInterface.setAttribute(ExifInterface.TAG_MAKE, backupValue);
        exifInterface.saveAttributes();
        exifInterface = new ExifInterface(imageFile.getAbsolutePath());
        compareWithExpectedValue(exifInterface, expectedValue, verboseTag);
    }

    private void testSaveAttributes_withFileDescriptor(String fileName, ExpectedValue expectedValue)
            throws IOException {
        File imageFile = new File(Environment.getExternalStorageDirectory(), fileName);
        String verboseTag = imageFile.getName();

        FileDescriptor fd = null;
        try {
            fd = Os.open(imageFile.getAbsolutePath(), OsConstants.O_RDWR, 0600);
            ExifInterface exifInterface = new ExifInterface(fd);
            exifInterface.saveAttributes();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag);

            // Test for modifying one attribute.
            String backupValue = exifInterface.getAttribute(ExifInterface.TAG_MAKE);
            exifInterface.setAttribute(ExifInterface.TAG_MAKE, "abc");
            exifInterface.saveAttributes();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            assertEquals("abc", exifInterface.getAttribute(ExifInterface.TAG_MAKE));
            // Restore the backup value.
            exifInterface.setAttribute(ExifInterface.TAG_MAKE, backupValue);
            exifInterface.saveAttributes();
            Os.lseek(fd, 0, OsConstants.SEEK_SET);
            exifInterface = new ExifInterface(fd);
            compareWithExpectedValue(exifInterface, expectedValue, verboseTag);
        } catch (ErrnoException e) {
            throw e.rethrowAsIOException();
        } finally {
            IoUtils.closeQuietly(fd);
        }
    }

    private void testExifInterfaceForJpeg(String fileName, int typedArrayResourceId)
            throws IOException {
        ExpectedValue expectedValue = new ExpectedValue(
                getContext().getResources().obtainTypedArray(typedArrayResourceId));

        // Test for reading from external data storage.
        fileName = EXTERNAL_BASE_DIRECTORY + fileName;
        testExifInterfaceCommon(fileName, expectedValue);

        // Test for saving attributes.
        testSaveAttributes_withFileName(fileName, expectedValue);
        testSaveAttributes_withFileDescriptor(fileName, expectedValue);
    }

    private void testExifInterfaceForRaw(String fileName, int typedArrayResourceId)
            throws IOException {
        ExpectedValue expectedValue = new ExpectedValue(
                getContext().getResources().obtainTypedArray(typedArrayResourceId));

        // Test for reading from external data storage.
        fileName = EXTERNAL_BASE_DIRECTORY + fileName;
        testExifInterfaceCommon(fileName, expectedValue);

        // Since ExifInterface does not support for saving attributes for RAW files, do not test
        // about writing back in here.
    }

    public void testReadExifDataFromExifByteOrderIIJpeg() throws Throwable {
        testExifInterfaceForJpeg(EXIF_BYTE_ORDER_II_JPEG, R.array.exifbyteorderii_jpg);
    }

    public void testReadExifDataFromExifByteOrderMMJpeg() throws Throwable {
        testExifInterfaceForJpeg(EXIF_BYTE_ORDER_MM_JPEG, R.array.exifbyteordermm_jpg);
    }

    public void testReadExifDataFromLgG4Iso800Dng() throws Throwable {
        testExifInterfaceForRaw(LG_G4_ISO_800_DNG, R.array.lg_g4_iso_800_dng);
    }

    public void testDoNotFailOnCorruptedImage() throws Throwable {
        // To keep the compatibility with old versions of ExifInterface, even on a corrupted image,
        // it shouldn't raise any exceptions except an IOException when unable to open a file.
        byte[] bytes = new byte[1024];
        try {
            new ExifInterface(new ByteArrayInputStream(bytes));
            // Always success
        } catch (IOException e) {
            fail("Should not reach here!");
        }
    }

    public void testReadExifDataFromVolantisJpg() throws Throwable {
        // Test if it is possible to parse the volantis generated JPEG smoothly.
        testExifInterfaceForJpeg(VOLANTIS_JPEG, R.array.volantis_jpg);
    }

    public void testReadExifDataFromSonyRX100Arw() throws Throwable {
        testExifInterfaceForRaw(SONY_RX_100_ARW, R.array.sony_rx_100_arw);
    }

    public void testReadExifDataFromCanonG7XCr2() throws Throwable {
        testExifInterfaceForRaw(CANON_G7X_CR2, R.array.canon_g7x_cr2);
    }

    public void testReadExifDataFromFujiX20Raf() throws Throwable {
        testExifInterfaceForRaw(FUJI_X20_RAF, R.array.fuji_x20_raf);
    }

    public void testReadExifDataFromNikon1AW1Nef() throws Throwable {
        testExifInterfaceForRaw(NIKON_1AW1_NEF, R.array.nikon_1aw1_nef);
    }

    public void testReadExifDataFromNikonP330Nrw() throws Throwable {
        testExifInterfaceForRaw(NIKON_P330_NRW, R.array.nikon_p330_nrw);
    }

    public void testReadExifDataFromOlympusEPL3Orf() throws Throwable {
        testExifInterfaceForRaw(OLYMPUS_E_PL3_ORF, R.array.olympus_e_pl3_orf);
    }

    public void testReadExifDataFromPanasonicGM5Rw2() throws Throwable {
        testExifInterfaceForRaw(PANASONIC_GM5_RW2, R.array.panasonic_gm5_rw2);
    }

    public void testReadExifDataFromPentaxK5Pef() throws Throwable {
        testExifInterfaceForRaw(PENTAX_K5_PEF, R.array.pentax_k5_pef);
    }

    public void testReadExifDataFromSamsungNX3000Srw() throws Throwable {
        testExifInterfaceForRaw(SAMSUNG_NX3000_SRW, R.array.samsung_nx3000_srw);
    }

    public void testSetDateTime() throws IOException {
        final String dateTimeValue = "2017:02:02 22:22:22";
        final String dateTimeOriginalValue = "2017:01:01 11:11:11";

        File srcFile = new File(Environment.getExternalStorageDirectory(),
                EXTERNAL_BASE_DIRECTORY + EXIF_BYTE_ORDER_II_JPEG);
        File imageFile = new File(Environment.getExternalStorageDirectory(),
                EXTERNAL_BASE_DIRECTORY + EXIF_BYTE_ORDER_II_JPEG + "_copied");

        FileUtils.copyFileOrThrow(srcFile, imageFile);
        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTimeValue);
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTimeOriginalValue);
        exif.saveAttributes();

        // Check that the DATETIME value is not overwritten by DATETIME_ORIGINAL's value.
        exif = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals(dateTimeValue, exif.getAttribute(ExifInterface.TAG_DATETIME));
        assertEquals(dateTimeOriginalValue, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));

        // Now remove the DATETIME value.
        exif.setAttribute(ExifInterface.TAG_DATETIME, null);
        exif.saveAttributes();

        // When the DATETIME has no value, then it should be set to DATETIME_ORIGINAL's value.
        exif = new ExifInterface(imageFile.getAbsolutePath());
        assertEquals(dateTimeOriginalValue, exif.getAttribute(ExifInterface.TAG_DATETIME));
        imageFile.delete();
    }
}
