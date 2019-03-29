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
package com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils;

import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Imports an .obj file into an ObjectData class so that it can be rendered by OpenGl. Based on the
 * .obj importer from Rajawali.
 */
public class ObjImporter {
    protected static final String TAG = "ObjImporter";
    protected static final String VERTEX = "v";
    protected static final String FACE = "f";
    protected static final String NORMAL = "vn";

    protected static class ObjIndexData {

        public ArrayList<Integer> vertexIndices;
        public ArrayList<Integer> texCoordIndices;
        public ArrayList<Integer> colorIndices;
        public ArrayList<Integer> normalIndices;

        public ObjIndexData() {
            vertexIndices = new ArrayList<Integer>();
            texCoordIndices = new ArrayList<Integer>();
            colorIndices = new ArrayList<Integer>();
            normalIndices = new ArrayList<Integer>();
        }
    }

    public static class ObjectData {
        float[] mVertexData;
        float[] mNormalsData;
        int[] mIndicesData;

        protected ObjectData(float[] vertexData, float[] normalsData, int[] indicesData) {
            mVertexData = vertexData;
            mNormalsData = normalsData;
            mIndicesData = indicesData;
        }

        public float[] getVertexData() {
            return mVertexData;
        }

        public float[] getNormalsData() {
            return mNormalsData;
        }

        public int[] getIndicesData() {
            return mIndicesData;
        }
    }

    public static ObjectData parse(Resources mResources, int mResourceId) {
        BufferedReader buffer = null;
        InputStream fileIn = mResources.openRawResource(mResourceId);
        buffer = new BufferedReader(new InputStreamReader(fileIn));
        String line;
        ObjIndexData currObjIndexData = new ObjIndexData();

        ArrayList<Float> vertices = new ArrayList<Float>();
        ArrayList<Float> texCoords = new ArrayList<Float>();
        ArrayList<Float> normals = new ArrayList<Float>();

        try {
            while ((line = buffer.readLine()) != null) {
                // Skip comments and empty lines.
                if (line.length() == 0 || line.charAt(0) == '#')
                    continue;
                StringTokenizer parts = new StringTokenizer(line, " ");
                int numTokens = parts.countTokens();

                if (numTokens == 0)
                    continue;
                String type = parts.nextToken();

                if (type.equals(VERTEX)) {
                    vertices.add(Float.parseFloat(parts.nextToken()));
                    vertices.add(Float.parseFloat(parts.nextToken()));
                    vertices.add(Float.parseFloat(parts.nextToken()));
                } else if (type.equals(FACE)) {
                    boolean isQuad = numTokens == 5;
                    int[] quadvids = new int[4];
                    int[] quadtids = new int[4];
                    int[] quadnids = new int[4];

                    boolean emptyVt = line.indexOf("//") > -1;
                    if (emptyVt) line = line.replace("//", "/");

                    parts = new StringTokenizer(line);

                    parts.nextToken();
                    StringTokenizer subParts = new StringTokenizer(parts.nextToken(), "/");
                    int partLength = subParts.countTokens();

                    boolean hasuv = partLength >= 2 && !emptyVt;
                    boolean hasn = partLength == 3 || (partLength == 2 && emptyVt);
                    int idx;

                    for (int i = 1; i < numTokens; i++) {
                        if (i > 1)
                            subParts = new StringTokenizer(parts.nextToken(), "/");
                        idx = Integer.parseInt(subParts.nextToken());

                        if (idx < 0) idx = (vertices.size() / 3) + idx;
                        else idx -= 1;
                        if (!isQuad)
                            currObjIndexData.vertexIndices.add(idx);
                        else
                            quadvids[i - 1] = idx;
                        if (hasuv) {
                            idx = Integer.parseInt(subParts.nextToken());
                            if (idx < 0) idx = (texCoords.size() / 2) + idx;
                            else idx -= 1;
                            if (!isQuad)
                                currObjIndexData.texCoordIndices.add(idx);
                            else
                                quadtids[i - 1] = idx;
                        }
                        if (hasn) {
                            idx = Integer.parseInt(subParts.nextToken());
                            if (idx < 0) idx = (normals.size() / 3) + idx;
                            else idx -= 1;
                            if (!isQuad)
                                currObjIndexData.normalIndices.add(idx);
                            else
                                quadnids[i - 1] = idx;
                        }
                    }

                    if (isQuad) {
                        int[] indices = new int[]{0, 1, 2, 0, 2, 3};

                        for (int i = 0; i < 6; ++i) {
                            int index = indices[i];
                            currObjIndexData.vertexIndices.add(quadvids[index]);
                            currObjIndexData.texCoordIndices.add(quadtids[index]);
                            currObjIndexData.normalIndices.add(quadnids[index]);
                        }
                    }
                } else if (type.equals(NORMAL)) {
                    normals.add(Float.parseFloat(parts.nextToken()));
                    normals.add(Float.parseFloat(parts.nextToken()));
                    normals.add(Float.parseFloat(parts.nextToken()));
                }
            }

            buffer.close();
        } catch (IOException e) {
            Log.e(TAG, "failed to parse", e);
        }

        int i;
        float[] aVertices = new float[currObjIndexData.vertexIndices.size() * 3];
        float[] aNormals = new float[currObjIndexData.normalIndices.size() * 3];
        int[] aIndices = new int[currObjIndexData.vertexIndices.size()];

        for (i = 0; i < currObjIndexData.vertexIndices.size(); ++i) {
            int faceIndex = currObjIndexData.vertexIndices.get(i) * 3;
            int vertexIndex = i * 3;
            try {
                aVertices[vertexIndex] = vertices.get(faceIndex);
                aVertices[vertexIndex + 1] = vertices.get(faceIndex + 1);
                aVertices[vertexIndex + 2] = vertices.get(faceIndex + 2);
                aIndices[i] = i;
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.d(TAG, "Obj array index out of bounds: " + vertexIndex + ", " + faceIndex);
            }
        }
        for (i = 0; i < currObjIndexData.normalIndices.size(); ++i) {
            int normalIndex = currObjIndexData.normalIndices.get(i) * 3;
            int ni = i * 3;
            if (normals.size() == 0) {
                Log.e(TAG, "There are no normals specified for this model. " +
                        "Please re-export with normals.");
                throw new RuntimeException("[" + TAG + "] There are no normals specified " +
                        "for this model. Please re-export with normals.");
            }
            aNormals[ni] = normals.get(normalIndex);
            aNormals[ni + 1] = normals.get(normalIndex + 1);
            aNormals[ni + 2] = normals.get(normalIndex + 2);
        }

        return new ObjectData(aVertices, aNormals, aIndices);
    }
}
