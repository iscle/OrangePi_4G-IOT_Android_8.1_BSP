/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
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
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.util.Range;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Class to deal with serializing and deserializing between JSON and Camera2 objects.
 */
public class ItsSerializer {
    public static final String TAG = ItsSerializer.class.getSimpleName();

    private static class MetadataEntry {
        public MetadataEntry(String k, Object v) {
            key = k;
            value = v;
        }
        public String key;
        public Object value;
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
        if (face.getLeftEyePosition() != null) {
            faceObj.put("leftEye", serializePoint(face.getLeftEyePosition()));
        }
        if (face.getRightEyePosition() != null) {
            faceObj.put("rightEye", serializePoint(face.getRightEyePosition()));
        }
        if (face.getMouthPosition() != null) {
            faceObj.put("mouth", serializePoint(face.getMouthPosition()));
        }
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
                        obj.put("width",sizes[si].getWidth());
                        obj.put("height", sizes[si].getHeight());
                        obj.put("input", false);
                        obj.put("minFrameDuration",
                                map.getOutputMinFrameDuration(fmts[fi],sizes[si]));
                        cfgArray.put(obj);
                    }
                }
                sizes = map.getHighResolutionOutputSizes(fmts[fi]);
                if (sizes != null) {
                    for (int si = 0; si < Array.getLength(sizes); si++) {
                        JSONObject obj = new JSONObject();
                        obj.put("format", fmts[fi]);
                        obj.put("width",sizes[si].getWidth());
                        obj.put("height", sizes[si].getHeight());
                        obj.put("input", false);
                        obj.put("minFrameDuration",
                                map.getOutputMinFrameDuration(fmts[fi],sizes[si]));
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
                xformObj.put(serializeRational(xform.getElement(col,row)));
            }
        }
        return xformObj;
    }

    @SuppressWarnings("unchecked")
    private static Object serializeTonemapCurve(TonemapCurve curve)
            throws org.json.JSONException {
        JSONObject curveObj = new JSONObject();
        String names[] = {"red", "green", "blue"};
        for (int ch = 0; ch < 3; ch++) {
            JSONArray curveArr = new JSONArray();
            int len = curve.getPointCount(ch);
            for (int i = 0; i < len; i++) {
                curveArr.put(curve.getPoint(ch,i).x);
                curveArr.put(curve.getPoint(ch,i).y);
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

    private static String getKeyName(Object keyObj) throws ItsException {
        if (keyObj.getClass() == CaptureResult.Key.class
                || keyObj.getClass() == TotalCaptureResult.class) {
            return ((CaptureResult.Key)keyObj).getName();
        } else if (keyObj.getClass() == CaptureRequest.Key.class) {
            return ((CaptureRequest.Key)keyObj).getName();
        } else if (keyObj.getClass() == CameraCharacteristics.Key.class) {
            return ((CameraCharacteristics.Key)keyObj).getName();
        }
        throw new ItsException("Invalid key object");
    }

    private static Object getKeyValue(CameraMetadata md, Object keyObj) throws ItsException {
        if (md.getClass() == CaptureResult.class || md.getClass() == TotalCaptureResult.class) {
            return ((CaptureResult)md).get((CaptureResult.Key)keyObj);
        } else if (md.getClass() == CaptureRequest.class) {
            return ((CaptureRequest)md).get((CaptureRequest.Key)keyObj);
        } else if (md.getClass() == CameraCharacteristics.class) {
            return ((CameraCharacteristics)md).get((CameraCharacteristics.Key)keyObj);
        }
        throw new ItsException("Invalid key object");
    }

    @SuppressWarnings("unchecked")
    private static MetadataEntry serializeEntry(Type keyType, Object keyObj, CameraMetadata md)
            throws ItsException {
        String keyName = getKeyName(keyObj);

        try {
            Object keyValue = getKeyValue(md, keyObj);
            if (keyValue == null) {
                return new MetadataEntry(keyName, JSONObject.NULL);
            } else if (keyType == Float.class) {
                // The JSON serializer doesn't handle floating point NaN or Inf.
                if (((Float)keyValue).isInfinite() || ((Float)keyValue).isNaN()) {
                    Logt.w(TAG, "Inf/NaN floating point value serialized: " + keyName);
                    return null;
                }
                return new MetadataEntry(keyName, keyValue);
            } else if (keyType == Integer.class || keyType == Long.class || keyType == Byte.class ||
                       keyType == Boolean.class || keyType == String.class) {
                return new MetadataEntry(keyName, keyValue);
            } else if (keyType == Rational.class) {
                return new MetadataEntry(keyName, serializeRational((Rational)keyValue));
            } else if (keyType == Size.class) {
                return new MetadataEntry(keyName, serializeSize((Size)keyValue));
            } else if (keyType == SizeF.class) {
                return new MetadataEntry(keyName, serializeSizeF((SizeF)keyValue));
            } else if (keyType == Rect.class) {
                return new MetadataEntry(keyName, serializeRect((Rect)keyValue));
            } else if (keyType == Face.class) {
                return new MetadataEntry(keyName, serializeFace((Face)keyValue));
            } else if (keyType == StreamConfigurationMap.class) {
                return new MetadataEntry(keyName,
                        serializeStreamConfigurationMap((StreamConfigurationMap)keyValue));
            } else if (keyType instanceof ParameterizedType &&
                    ((ParameterizedType)keyType).getRawType() == Range.class) {
                return new MetadataEntry(keyName, serializeRange((Range)keyValue));
            } else if (keyType == ColorSpaceTransform.class) {
                return new MetadataEntry(keyName,
                        serializeColorSpaceTransform((ColorSpaceTransform)keyValue));
            } else if (keyType == MeteringRectangle.class) {
                return new MetadataEntry(keyName,
                        serializeMeteringRectangle((MeteringRectangle)keyValue));
            } else if (keyType == Location.class) {
                return new MetadataEntry(keyName,
                        serializeLocation((Location)keyValue));
            } else if (keyType == RggbChannelVector.class) {
                return new MetadataEntry(keyName,
                        serializeRggbChannelVector((RggbChannelVector)keyValue));
            } else if (keyType == BlackLevelPattern.class) {
                return new MetadataEntry(keyName,
                        serializeBlackLevelPattern((BlackLevelPattern)keyValue));
            } else if (keyType == TonemapCurve.class) {
                return new MetadataEntry(keyName,
                        serializeTonemapCurve((TonemapCurve)keyValue));
            } else if (keyType == Point.class) {
                return new MetadataEntry(keyName,
                        serializePoint((Point)keyValue));
            } else if (keyType == LensShadingMap.class) {
                return new MetadataEntry(keyName,
                        serializeLensShadingMap((LensShadingMap)keyValue));
            } else {
                Logt.w(TAG, String.format("Serializing unsupported key type: " + keyType));
                return null;
            }
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error for key: " + keyName + ": ", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static MetadataEntry serializeArrayEntry(Type keyType, Object keyObj, CameraMetadata md)
            throws ItsException {
        String keyName = getKeyName(keyObj);
        try {
            Object keyValue = getKeyValue(md, keyObj);
            if (keyValue == null) {
                return null;
            }
            int arrayLen = Array.getLength(keyValue);
            Type elmtType = ((GenericArrayType)keyType).getGenericComponentType();
            if (elmtType == int.class  || elmtType == float.class || elmtType == byte.class ||
                elmtType == long.class || elmtType == double.class || elmtType == boolean.class) {
                return new MetadataEntry(keyName, new JSONArray(keyValue));
            } else if (elmtType == Rational.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRational((Rational)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Size.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeSize((Size)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Rect.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRect((Rect)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Face.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeFace((Face)Array.get(keyValue, i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == StreamConfigurationMap.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeStreamConfigurationMap(
                            (StreamConfigurationMap)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType instanceof ParameterizedType &&
                    ((ParameterizedType)elmtType).getRawType() == Range.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRange((Range)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType instanceof ParameterizedType &&
                    ((ParameterizedType)elmtType).getRawType() == Pair.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializePair((Pair)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == MeteringRectangle.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeMeteringRectangle(
                            (MeteringRectangle)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Location.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeLocation((Location)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == RggbChannelVector.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeRggbChannelVector(
                            (RggbChannelVector)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == BlackLevelPattern.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializeBlackLevelPattern(
                            (BlackLevelPattern)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else if (elmtType == Point.class) {
                JSONArray jsonArray = new JSONArray();
                for (int i = 0; i < arrayLen; i++) {
                    jsonArray.put(serializePoint((Point)Array.get(keyValue,i)));
                }
                return new MetadataEntry(keyName, jsonArray);
            } else {
                Logt.w(TAG, String.format("Serializing unsupported array type: " + elmtType));
                return null;
            }
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error for key: " + keyName + ": ", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static JSONObject serialize(CameraMetadata md)
            throws ItsException {
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
                      || field.getType() == CameraCharacteristics.Key.class) &&
                    field.getGenericType() instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType)field.getGenericType();
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
                        // There is a weird case where the entry is non-null but the toString
                        // of the entry is null, and if this happens, the null-ness spreads like
                        // a virus and makes the whole JSON object null from the top level down.
                        // Not sure if it's a bug in the library or I'm just not using it right.
                        // Workaround by checking for this case explicitly and not adding the
                        // value to the jsonObj when it is detected.
                        if (entry != null && entry.key != null && entry.value != null
                                          && entry.value.toString() == null) {
                            Logt.w(TAG, "Error encountered serializing value for key: " + entry.key);
                        } else if (entry != null) {
                            jsonObj.put(entry.key, entry.value);
                        } else {
                            // Ignore.
                        }
                    } catch (IllegalAccessException e) {
                        throw new ItsException(
                                "Access error for field: " + field + ": ", e);
                    } catch (org.json.JSONException e) {
                        throw new ItsException(
                                "JSON error for field: " + field + ": ", e);
                    }
                }
            }
        }
        return jsonObj;
    }

    @SuppressWarnings("unchecked")
    public static CaptureRequest.Builder deserialize(CaptureRequest.Builder mdDefault,
            JSONObject jsonReq) throws ItsException {
        try {
            Logt.i(TAG, "Parsing JSON capture request ...");

            // Iterate over the CaptureRequest reflected fields.
            CaptureRequest.Builder md = mdDefault;
            Field[] allFields = CaptureRequest.class.getDeclaredFields();
            for (Field field : allFields) {
                if (Modifier.isPublic(field.getModifiers()) &&
                        Modifier.isStatic(field.getModifiers()) &&
                        field.getType() == CaptureRequest.Key.class &&
                        field.getGenericType() instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType)field.getGenericType();
                    Type[] argTypes = paramType.getActualTypeArguments();
                    if (argTypes.length > 0) {
                        CaptureRequest.Key key = (CaptureRequest.Key)field.get(md);
                        String keyName = key.getName();
                        Type keyType = argTypes[0];

                        // For each reflected CaptureRequest entry, look inside the JSON object
                        // to see if it is being set. If it is found, remove the key from the
                        // JSON object. After this process, there should be no keys left in the
                        // JSON (otherwise an invalid key was specified).

                        if (jsonReq.has(keyName) && !jsonReq.isNull(keyName)) {
                            if (keyType instanceof GenericArrayType) {
                                Type elmtType =
                                        ((GenericArrayType)keyType).getGenericComponentType();
                                JSONArray ja = jsonReq.getJSONArray(keyName);
                                Object val[] = new Object[ja.length()];
                                for (int i = 0; i < ja.length(); i++) {
                                    if (elmtType == int.class) {
                                        Array.set(val, i, ja.getInt(i));
                                    } else if (elmtType == byte.class) {
                                        Array.set(val, i, (byte)ja.getInt(i));
                                    } else if (elmtType == float.class) {
                                        Array.set(val, i, (float)ja.getDouble(i));
                                    } else if (elmtType == long.class) {
                                        Array.set(val, i, ja.getLong(i));
                                    } else if (elmtType == double.class) {
                                        Array.set(val, i, ja.getDouble(i));
                                    } else if (elmtType == boolean.class) {
                                        Array.set(val, i, ja.getBoolean(i));
                                    } else if (elmtType == String.class) {
                                        Array.set(val, i, ja.getString(i));
                                    } else if (elmtType == Size.class){
                                        JSONObject obj = ja.getJSONObject(i);
                                        Array.set(val, i, new Size(
                                                obj.getInt("width"), obj.getInt("height")));
                                    } else if (elmtType == Rect.class) {
                                        JSONObject obj = ja.getJSONObject(i);
                                        Array.set(val, i, new Rect(
                                                obj.getInt("left"), obj.getInt("top"),
                                                obj.getInt("bottom"), obj.getInt("right")));
                                    } else if (elmtType == Rational.class) {
                                        JSONObject obj = ja.getJSONObject(i);
                                        Array.set(val, i, new Rational(
                                                obj.getInt("numerator"),
                                                obj.getInt("denominator")));
                                    } else if (elmtType == RggbChannelVector.class) {
                                        JSONArray arr = ja.getJSONArray(i);
                                        Array.set(val, i, new RggbChannelVector(
                                                (float)arr.getDouble(0),
                                                (float)arr.getDouble(1),
                                                (float)arr.getDouble(2),
                                                (float)arr.getDouble(3)));
                                    } else if (elmtType == ColorSpaceTransform.class) {
                                        JSONArray arr = ja.getJSONArray(i);
                                        Rational xform[] = new Rational[9];
                                        for (int j = 0; j < 9; j++) {
                                            xform[j] = new Rational(
                                                    arr.getJSONObject(j).getInt("numerator"),
                                                    arr.getJSONObject(j).getInt("denominator"));
                                        }
                                        Array.set(val, i, new ColorSpaceTransform(xform));
                                    } else if (elmtType == MeteringRectangle.class) {
                                        JSONObject obj = ja.getJSONObject(i);
                                        Array.set(val, i, new MeteringRectangle(
                                                obj.getInt("x"),
                                                obj.getInt("y"),
                                                obj.getInt("width"),
                                                obj.getInt("height"),
                                                obj.getInt("weight")));
                                    } else {
                                        throw new ItsException(
                                                "Failed to parse key from JSON: " + keyName);
                                    }
                                }
                                if (val != null) {
                                    Logt.i(TAG, "Set: "+keyName+" -> "+Arrays.toString(val));
                                    md.set(key, val);
                                    jsonReq.remove(keyName);
                                }
                            } else {
                                Object val = null;
                                if (keyType == Integer.class) {
                                    val = jsonReq.getInt(keyName);
                                } else if (keyType == Byte.class) {
                                    val = (byte)jsonReq.getInt(keyName);
                                } else if (keyType == Double.class) {
                                    val = jsonReq.getDouble(keyName);
                                } else if (keyType == Long.class) {
                                    val = jsonReq.getLong(keyName);
                                } else if (keyType == Float.class) {
                                    val = (float)jsonReq.getDouble(keyName);
                                } else if (keyType == Boolean.class) {
                                    val = jsonReq.getBoolean(keyName);
                                } else if (keyType == String.class) {
                                    val = jsonReq.getString(keyName);
                                } else if (keyType == Size.class) {
                                    JSONObject obj = jsonReq.getJSONObject(keyName);
                                    val = new Size(
                                            obj.getInt("width"), obj.getInt("height"));
                                } else if (keyType == Rect.class) {
                                    JSONObject obj = jsonReq.getJSONObject(keyName);
                                    val = new Rect(
                                            obj.getInt("left"), obj.getInt("top"),
                                            obj.getInt("right"), obj.getInt("bottom"));
                                } else if (keyType == Rational.class) {
                                    JSONObject obj = jsonReq.getJSONObject(keyName);
                                    val = new Rational(obj.getInt("numerator"),
                                                       obj.getInt("denominator"));
                                } else if (keyType == RggbChannelVector.class) {
                                    JSONObject obj = jsonReq.optJSONObject(keyName);
                                    JSONArray arr = jsonReq.optJSONArray(keyName);
                                    if (arr != null) {
                                        val = new RggbChannelVector(
                                                (float)arr.getDouble(0),
                                                (float)arr.getDouble(1),
                                                (float)arr.getDouble(2),
                                                (float)arr.getDouble(3));
                                    } else if (obj != null) {
                                        val = new RggbChannelVector(
                                                (float)obj.getDouble("red"),
                                                (float)obj.getDouble("greenEven"),
                                                (float)obj.getDouble("greenOdd"),
                                                (float)obj.getDouble("blue"));
                                    } else {
                                        throw new ItsException("Invalid RggbChannelVector object");
                                    }
                                } else if (keyType == ColorSpaceTransform.class) {
                                    JSONArray arr = jsonReq.getJSONArray(keyName);
                                    Rational a[] = new Rational[9];
                                    for (int i = 0; i < 9; i++) {
                                        a[i] = new Rational(
                                                arr.getJSONObject(i).getInt("numerator"),
                                                arr.getJSONObject(i).getInt("denominator"));
                                    }
                                    val = new ColorSpaceTransform(a);
                                } else if (keyType instanceof ParameterizedType &&
                                        ((ParameterizedType)keyType).getRawType() == Range.class &&
                                        ((ParameterizedType)keyType).getActualTypeArguments().length == 1 &&
                                        ((ParameterizedType)keyType).getActualTypeArguments()[0] == Integer.class) {
                                    JSONArray arr = jsonReq.getJSONArray(keyName);
                                    val = new Range<Integer>(arr.getInt(0), arr.getInt(1));
                                } else {
                                    throw new ItsException(
                                            "Failed to parse key from JSON: " +
                                            keyName + ", " + keyType);
                                }
                                if (val != null) {
                                    Logt.i(TAG, "Set: " + keyName + " -> " + val);
                                    md.set(key ,val);
                                    jsonReq.remove(keyName);
                                }
                            }
                        }
                    }
                }
            }

            // Ensure that there were no invalid keys in the JSON request object.
            if (jsonReq.length() != 0) {
                throw new ItsException("Invalid JSON key(s): " + jsonReq.toString());
            }

            Logt.i(TAG, "Parsing JSON capture request completed");
            return md;
        } catch (java.lang.IllegalAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<CaptureRequest.Builder> deserializeRequestList(
            CameraDevice device, JSONObject jsonObjTop, String requestKey)
            throws ItsException {
        try {
            List<CaptureRequest.Builder> requests = null;
            JSONArray jsonReqs = jsonObjTop.getJSONArray(requestKey);
            requests = new LinkedList<CaptureRequest.Builder>();
            for (int i = 0; i < jsonReqs.length(); i++) {
                CaptureRequest.Builder templateReq = device.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                requests.add(
                    deserialize(templateReq, jsonReqs.getJSONObject(i)));
            }
            return requests;
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
    }
}
