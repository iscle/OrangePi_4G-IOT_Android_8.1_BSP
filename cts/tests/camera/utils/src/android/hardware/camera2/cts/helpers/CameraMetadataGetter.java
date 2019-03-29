/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.camera2.cts.helpers;

import static com.android.ex.camera2.blocking.BlockingStateCallback.*;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.TonemapCurve;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.util.Range;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingStateCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility class to dump the camera metadata.
 */
public final class CameraMetadataGetter implements AutoCloseable {
    private static final String TAG = CameraMetadataGetter.class.getSimpleName();
    private static final int CAMERA_CLOSE_TIMEOUT_MS = 5000;
    public static final int[] TEMPLATE_IDS = {
        CameraDevice.TEMPLATE_PREVIEW,
        CameraDevice.TEMPLATE_STILL_CAPTURE,
        CameraDevice.TEMPLATE_RECORD,
        CameraDevice.TEMPLATE_VIDEO_SNAPSHOT,
        CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
        CameraDevice.TEMPLATE_MANUAL,
    };
    private CameraManager mCameraManager;
    private BlockingStateCallback mCameraListener;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    private static class MetadataEntry {
        public MetadataEntry(String k, Object v) {
            key = k;
            value = v;
        }

        public String key;
        public Object value;
    }

    public CameraMetadataGetter(CameraManager cameraManager) {
        if (cameraManager == null) {
            throw new IllegalArgumentException("can not create an CameraMetadataGetter object"
                    + " with null CameraManager");
        }

        mCameraManager = cameraManager;

        mCameraListener = new BlockingStateCallback();
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public String getCameraInfo() {
        StringBuffer cameraInfo = new StringBuffer("{\"CameraStaticMetadata\":{");
        CameraCharacteristics staticMetadata;
        String[] cameraIds;
        try {
            cameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to get camera ids, skip this info, error: " + e.getMessage());
            return "";
        }
        for (String id : cameraIds) {
            String value = null;
            try {
                staticMetadata = mCameraManager.getCameraCharacteristics(id);
                value = serialize(staticMetadata).toString();
            } catch (CameraAccessException e) {
                Log.e(TAG,
                        "Unable to get camera camera static info, skip this camera, error: "
                                + e.getMessage());
            }
            cameraInfo.append("\"camera" + id + "\":"); // Key
            cameraInfo.append(value); // Value
            // If not last, print "," // Separator
            if (!id.equals(cameraIds[cameraIds.length - 1])) {
                cameraInfo.append(",");
            }
        }
        cameraInfo.append("}}");

        return cameraInfo.toString();
    }

    public JSONObject getCameraInfo(String cameraId) {
        JSONObject staticMetadata = null;
        try {
            staticMetadata = serialize(mCameraManager.getCameraCharacteristics(cameraId));
        } catch (CameraAccessException e) {
            Log.e(TAG,
                    "Unable to get camera camera static info, skip this camera, error: "
                            + e.getMessage());
        }
        return staticMetadata;
    }

    public JSONObject[] getCaptureRequestTemplates(String cameraId) {
        JSONObject[] templates = new JSONObject[TEMPLATE_IDS.length];
        CameraDevice camera = null;
        try {
            camera = (new BlockingCameraManager(mCameraManager)).openCamera(cameraId,
                            mCameraListener, mHandler);
            for (int i = 0; i < TEMPLATE_IDS.length; i++) {
                CaptureRequest.Builder request;
                try {
                    request = camera.createCaptureRequest(TEMPLATE_IDS[i]);
                    templates[i] = serialize(request.build());
                } catch (Exception e) {
                    Log.e(TAG, "Unable to create template " + TEMPLATE_IDS[i]
                                    + " because of error " + e.getMessage());
                    templates[i] = null;
                }
            }
            return templates;
        } catch (CameraAccessException | BlockingOpenException e) {
            Log.e(TAG, "Unable to open camera " + cameraId + " because of error "
                            + e.getMessage());
            return new JSONObject[0];
        } finally {
            if (camera != null) {
                camera.close();
            }
        }
    }

    public String getCaptureRequestTemplates() {
        StringBuffer templates = new StringBuffer("{\"CameraRequestTemplates\":{");
        String[] cameraIds;
        try {
            cameraIds = mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to get camera ids, skip this info, error: " + e.getMessage());
            return "";
        }
        CameraDevice camera = null;
        for (String id : cameraIds) {
            try {
                try {
                    camera = (new BlockingCameraManager(mCameraManager)).openCamera(id,
                                    mCameraListener, mHandler);
                } catch (CameraAccessException | BlockingOpenException e) {
                    Log.e(TAG, "Unable to open camera " + id + " because of error "
                                    + e.getMessage());
                    continue;
                }

                for (int i = 0; i < TEMPLATE_IDS.length; i++) {
                    String value = null;
                    CaptureRequest.Builder request;
                    try {
                        request = camera.createCaptureRequest(TEMPLATE_IDS[i]);
                        value = serialize(request.build()).toString();
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to create template " + TEMPLATE_IDS[i]
                                        + " because of error " + e.getMessage());
                    }
                    templates.append("\"Camera" + id + "CaptureTemplate" +
                                    TEMPLATE_IDS[i] + "\":");
                    templates.append(value);
                    if (!id.equals(cameraIds[cameraIds.length - 1]) ||
                                    i < (TEMPLATE_IDS.length - 1)) {
                        templates.append(",");
                    }
                }
            } finally {
                if (camera != null) {
                    camera.close();
                    mCameraListener.waitForState(STATE_CLOSED, CAMERA_CLOSE_TIMEOUT_MS);
                }
            }
        }

        templates.append("}}");
        return templates.toString();
    }

    /*
     * Cleanup the resources.
     */
    @Override
    public void close() throws Exception {
        mHandlerThread.quitSafely();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @SuppressWarnings("unchecked")
    private static Object serializeRational(Rational rat) throws org.json.JSONException {
        JSONObject ratObj = new JSONObject();
        ratObj.put("numerator", rat.getNumerator());
        ratObj.put("denominator", rat.getDenominator());
        return ratObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeSize(Size size) throws org.json.JSONException {
        JSONObject sizeObj = new JSONObject();
        sizeObj.put("width", size.getWidth());
        sizeObj.put("height", size.getHeight());
        return sizeObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeSizeF(SizeF size) throws org.json.JSONException {
        JSONObject sizeObj = new JSONObject();
        sizeObj.put("width", size.getWidth());
        sizeObj.put("height", size.getHeight());
        return sizeObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeRect(Rect rect) throws org.json.JSONException {
        JSONObject rectObj = new JSONObject();
        rectObj.put("left", rect.left);
        rectObj.put("right", rect.right);
        rectObj.put("top", rect.top);
        rectObj.put("bottom", rect.bottom);
        return rectObj;
    }

    private static Object serializePoint(Point point) throws org.json.JSONException {
        JSONObject pointObj = new JSONObject();
        pointObj.put("x", point.x);
        pointObj.put("y", point.y);
        return pointObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeFace(Face face)
                    throws org.json.JSONException {
        JSONObject faceObj = new JSONObject();
        faceObj.put("bounds", serializeRect(face.getBounds()));
        faceObj.put("score", face.getScore());
        faceObj.put("id", face.getId());
        faceObj.put("leftEye", serializePoint(face.getLeftEyePosition()));
        faceObj.put("rightEye", serializePoint(face.getRightEyePosition()));
        faceObj.put("mouth", serializePoint(face.getMouthPosition()));
        return faceObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeStreamConfigurationMap(
                    StreamConfigurationMap map)
                    throws org.json.JSONException {
        // TODO: Serialize the rest of the StreamConfigurationMap fields.
        JSONObject mapObj = new JSONObject();
        JSONArray cfgArray = new JSONArray();
        int fmts[] = map.getOutputFormats();
        if (fmts != null) {
            for (int fi = 0; fi < Array.getLength(fmts); fi++) {
                Size sizes[] = map.getOutputSizes(fmts[fi]);
                if (sizes != null) {
                    for (int si = 0; si < Array.getLength(sizes); si++) {
                        JSONObject obj = new JSONObject();
                        obj.put("format", fmts[fi]);
                        obj.put("width", sizes[si].getWidth());
                        obj.put("height", sizes[si].getHeight());
                        obj.put("input", false);
                        obj.put("minFrameDuration",
                                        map.getOutputMinFrameDuration(fmts[fi], sizes[si]));
                        cfgArray.put(obj);
                    }
                }
            }
        }
        mapObj.put("availableStreamConfigurations", cfgArray);
        return mapObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeMeteringRectangle(MeteringRectangle rect)
                    throws org.json.JSONException {
        JSONObject rectObj = new JSONObject();
        rectObj.put("x", rect.getX());
        rectObj.put("y", rect.getY());
        rectObj.put("width", rect.getWidth());
        rectObj.put("height", rect.getHeight());
        rectObj.put("weight", rect.getMeteringWeight());
        return rectObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializePair(Pair pair)
                    throws org.json.JSONException {
        JSONArray pairObj = new JSONArray();
        pairObj.put(pair.first);
        pairObj.put(pair.second);
        return pairObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeRange(Range range)
                    throws org.json.JSONException {
        JSONArray rangeObj = new JSONArray();
        rangeObj.put(range.getLower());
        rangeObj.put(range.getUpper());
        return rangeObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeColorSpaceTransform(ColorSpaceTransform xform)
                    throws org.json.JSONException {
        JSONArray xformObj = new JSONArray();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                xformObj.put(serializeRational(xform.getElement(col, row)));
            }
        }
        return xformObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeTonemapCurve(TonemapCurve curve)
                    throws org.json.JSONException {
        JSONObject curveObj = new JSONObject();
        String names[] = {
                        "red", "green", "blue" };
        for (int ch = 0; ch < 3; ch++) {
            JSONArray curveArr = new JSONArray();
            int len = curve.getPointCount(ch);
            for (int i = 0; i < len; i++) {
                curveArr.put(curve.getPoint(ch, i).x);
                curveArr.put(curve.getPoint(ch, i).y);
            }
            curveObj.put(names[ch], curveArr);
        }
        return curveObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeRggbChannelVector(RggbChannelVector vec)
                    throws org.json.JSONException {
        JSONArray vecObj = new JSONArray();
        vecObj.put(vec.getRed());
        vecObj.put(vec.getGreenEven());
        vecObj.put(vec.getGreenOdd());
        vecObj.put(vec.getBlue());
        return vecObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeBlackLevelPattern(BlackLevelPattern pat)
                    throws org.json.JSONException {
        int patVals[] = new int[4];
        pat.copyTo(patVals, 0);
        JSONArray patObj = new JSONArray();
        patObj.put(patVals[0]);
        patObj.put(patVals[1]);
        patObj.put(patVals[2]);
        patObj.put(patVals[3]);
        return patObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeLocation(Location loc)
                    throws org.json.JSONException {
        return loc.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object serializeLensShadingMap(LensShadingMap map)
            throws org.json.JSONException {
        JSONArray mapObj = new JSONArray();
        for (int row = 0; row < map.getRowCount(); row++) {
            for (int col = 0; col < map.getColumnCount(); col++) {
                for (int ch = 0; ch < 4; ch++) {
                    mapObj.put(map.getGainFactor(ch, col, row));
                }
            }
        }
        return mapObj;
    }

    private static String getKeyName(Object keyObj) {
        if (keyObj.getClass() == CaptureResult.Key.class
                || keyObj.getClass() == TotalCaptureResult.class) {
            return ((CaptureResult.Key) keyObj).getName();
        } else if (keyObj.getClass() == CaptureRequest.Key.class) {
            return ((CaptureRequest.Key) keyObj).getName();
        } else if (keyObj.getClass() == CameraCharacteristics.Key.class) {
            return ((CameraCharacteristics.Key) keyObj).getName();
        }

        throw new IllegalArgumentException("Invalid key object");
    }

    private static Object getKeyValue(CameraMetadata md, Object keyObj) {
        if (md.getClass() == CaptureResult.class || md.getClass() == TotalCaptureResult.class) {
            return ((CaptureResult) md).get((CaptureResult.Key) keyObj);
        } else if (md.getClass() == CaptureRequest.class) {
            return ((CaptureRequest) md).get((CaptureRequest.Key) keyObj);
        } else if (md.getClass() == CameraCharacteristics.class) {
            return ((CameraCharacteristics) md).get((CameraCharacteristics.Key) keyObj);
        }

        throw new IllegalArgumentException("Invalid key object");
    }

    @SuppressWarnings("unchecked")
    private static MetadataEntry serializeEntry(Type keyType, Object keyObj, CameraMetadata md) {
        String keyName = getKeyName(keyObj);

        try {
            Object keyValue = getKeyValue(md, keyObj);
            if (keyValue == null) {
                return new MetadataEntry(keyName, JSONObject.NULL);
            } else if (keyType == Float.class) {
                // The JSON serializer doesn't handle floating point NaN or Inf.
                if (((Float) keyValue).isInfinite() || ((Float) keyValue).isNaN()) {
                    Log.w(TAG, "Inf/NaN floating point value serialized: " + keyName);
                    return null;
                }
                return new MetadataEntry(keyName, keyValue);
            } else if (keyType == Integer.class || keyType == Long.class || keyType == Byte.class ||
                    keyType == Boolean.class || keyType == String.class) {
                return new MetadataEntry(keyName, keyValue);
            } else if (keyType == Rational.class) {
                return new MetadataEntry(keyName, serializeRational((Rational) keyValue));
            } else if (keyType == Size.class) {
                return new MetadataEntry(keyName, serializeSize((Size) keyValue));
            } else if (keyType == SizeF.class) {
                return new MetadataEntry(keyName, serializeSizeF((SizeF) keyValue));
            } else if (keyType == Rect.class) {
                return new MetadataEntry(keyName, serializeRect((Rect) keyValue));
            } else if (keyType == Face.class) {
                return new MetadataEntry(keyName, serializeFace((Face) keyValue));
            } else if (keyType == StreamConfigurationMap.class) {
                return new MetadataEntry(keyName,
                        serializeStreamConfigurationMap((StreamConfigurationMap) keyValue));
            } else if (keyType instanceof ParameterizedType &&
                    ((ParameterizedType) keyType).getRawType() == Range.class) {
                return new MetadataEntry(keyName, serializeRange((Range) keyValue));
            } else if (keyType == ColorSpaceTransform.class) {
                return new MetadataEntry(keyName,
                        serializeColorSpaceTransform((ColorSpaceTransform) keyValue));
            } else if (keyType == MeteringRectangle.class) {
                return new MetadataEntry(keyName,
                        serializeMeteringRectangle((MeteringRectangle) keyValue));
            } else if (keyType == Location.class) {
                return new MetadataEntry(keyName,
                        serializeLocation((Location) keyValue));
            } else if (keyType == RggbChannelVector.class) {
                return new MetadataEntry(keyName,
                        serializeRggbChannelVector((RggbChannelVector) keyValue));
            } else if (keyType == BlackLevelPattern.class) {
                return new MetadataEntry(keyName,
                        serializeBlackLevelPattern((BlackLevelPattern) keyValue));
            } else if (keyType == TonemapCurve.class) {
                return new MetadataEntry(keyName,
                        serializeTonemapCurve((TonemapCurve) keyValue));
            } else if (keyType == Point.class) {
                return new MetadataEntry(keyName,
                        serializePoint((Point) keyValue));
            } else if (keyType == LensShadingMap.class) {
                return new MetadataEntry(keyName,
                        serializeLensShadingMap((LensShadingMap) keyValue));
            } else {
                Log.w(TAG, String.format("Serializing unsupported key type: " + keyType));
                return null;
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("JSON error for key: " + keyName + ": ", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static MetadataEntry serializeArrayEntry(Type keyType, Object keyObj,
            CameraMetadata md) {
        String keyName = getKeyName(keyObj);
        try {
            Object keyValue = getKeyValue(md, keyObj);
            if (keyValue == null) {
                return new MetadataEntry(keyName, JSONObject.NULL);
            }
            int arrayLen = Array.getLength(keyValue);
            Type elmtType = ((GenericArrayType) keyType).getGenericComponentType();
            if (elmtType == int.class || elmtType == float.class || elmtType == byte.class ||
                    elmtType == long.class || elmtType == double.class
                    || elmtType == boolean.class) {
                return new MetadataEntry(keyName, new JSONArray(keyValue));
            } else if (elmtType == Rational.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRational((Rational) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Size.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeSize((Size) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Rect.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRect((Rect) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Face.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeFace((Face) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == StreamConfigurationMap.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeStreamConfigurationMap(
                            (StreamConfigurationMap) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType instanceof ParameterizedType &&
                    ((ParameterizedType) elmtType).getRawType() == Range.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRange((Range) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType instanceof ParameterizedType &&
                    ((ParameterizedType) elmtType).getRawType() == Pair.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializePair((Pair) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == MeteringRectangle.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeMeteringRectangle(
                            (MeteringRectangle) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Location.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeLocation((Location) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == RggbChannelVector.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRggbChannelVector(
                            (RggbChannelVector) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == BlackLevelPattern.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeBlackLevelPattern(
                            (BlackLevelPattern) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Point.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializePoint((Point) Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else {
                Log.w(TAG, String.format("Serializing unsupported array type: " + elmtType));
                return null;
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("JSON error for key: " + keyName + ": ", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static JSONObject serialize(CameraMetadata md) {
        JSONObject jsonObj = new JSONObject();
        Field[] allFields = md.getClass().getDeclaredFields();
        if (md.getClass() == TotalCaptureResult.class) {
            allFields = CaptureResult.class.getDeclaredFields();
        }
        for (Field field : allFields) {
            if (Modifier.isPublic(field.getModifiers()) &&
                    Modifier.isStatic(field.getModifiers()) &&
                            (field.getType() == CaptureRequest.Key.class
                            || field.getType() == CaptureResult.Key.class
                            || field.getType() == TotalCaptureResult.Key.class
                            || field.getType() == CameraCharacteristics.Key.class)
                    &&
                    field.getGenericType() instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) field.getGenericType();
                Type[] argTypes = paramType.getActualTypeArguments();
                if (argTypes.length > 0) {
                    try {
                        Type keyType = argTypes[0];
                        Object keyObj = field.get(md);
                        MetadataEntry entry;
                        if (keyType instanceof GenericArrayType) {
                            entry = serializeArrayEntry(keyType, keyObj, md);
                        } else {
                            entry = serializeEntry(keyType, keyObj, md);
                        }

                        // TODO: Figure this weird case out.
                        // There is a weird case where the entry is non-null but
                        // the toString
                        // of the entry is null, and if this happens, the
                        // null-ness spreads like
                        // a virus and makes the whole JSON object null from the
                        // top level down.
                        // Not sure if it's a bug in the library or I'm just not
                        // using it right.
                        // Workaround by checking for this case explicitly and
                        // not adding the
                        // value to the jsonObj when it is detected.
                        if (entry != null && entry.key != null && entry.value != null
                                && entry.value.toString() == null) {
                            Log.w(TAG, "Error encountered serializing value for key: "
                                    + entry.key);
                        } else if (entry != null) {
                            jsonObj.put(entry.key, entry.value);
                        } else {
                            // Ignore.
                        }
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(
                                "Access error for field: " + field + ": ", e);
                    } catch (org.json.JSONException e) {
                        throw new IllegalStateException(
                                "JSON error for field: " + field + ": ", e);
                    }
                }
            }
        }
        return jsonObj;
    }
}
