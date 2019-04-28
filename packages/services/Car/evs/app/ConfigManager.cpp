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
#include "ConfigManager.h"

#include "json/json.h"

#include <fstream>
#include <math.h>
#include <assert.h>


static const float kDegreesToRadians = M_PI / 180.0f;


static float normalizeToPlusMinus180degrees(float theta) {
    const float wraps = floor((theta+180.0f) / 360.0f);
    return theta - wraps*360.0f;
}


static bool readChildNodeAsFloat(const char* groupName,
                                 const Json::Value& parentNode,
                                 const char* childName,
                                 float* value) {
    // Must have a place to put the value!
    assert(value);

    Json::Value childNode = parentNode[childName];
    if (!childNode.isNumeric()) {
        printf("Missing or invalid field %s in record %s", childName, groupName);
        return false;
    }

    *value = childNode.asFloat();
    return true;
}


bool ConfigManager::initialize(const char* configFileName)
{
    bool complete = true;

    // Set up a stream to read in the input file
    std::ifstream configStream(configFileName);

    // Parse the stream into JSON objects
    Json::Reader reader;
    Json::Value rootNode;
    bool parseOk = reader.parse(configStream, rootNode, false /* don't need comments */);
    if (!parseOk) {
        printf("Failed to read configuration file %s\n", configFileName);
        printf("%s\n", reader.getFormatedErrorMessages().c_str());
        return false;
    }


    //
    // Read car information
    //
    {
        Json::Value car = rootNode["car"];
        if (!car.isObject()) {
            printf("Invalid configuration format -- we expect a car description\n");
            return false;
        }
        complete &= readChildNodeAsFloat("car", car, "width",       &mCarWidth);
        complete &= readChildNodeAsFloat("car", car, "wheelBase",   &mWheelBase);
        complete &= readChildNodeAsFloat("car", car, "frontExtent", &mFrontExtent);
        complete &= readChildNodeAsFloat("car", car, "rearExtent",  &mRearExtent);
    }


    //
    // Read display layout information
    //
    {
        Json::Value displayNode = rootNode["display"];
        if (!displayNode.isObject()) {
            printf("Invalid configuration format -- we expect a display description\n");
            return false;
        }
        complete &= readChildNodeAsFloat("display", displayNode, "frontRange", &mFrontRangeInCarSpace);
        complete &= readChildNodeAsFloat("display", displayNode, "rearRange",  &mRearRangeInCarSpace);
    }


    //
    // Car top view texture properties for top down view
    //
    {
        Json::Value graphicNode = rootNode["graphic"];
        if (!graphicNode.isObject()) {
            printf("Invalid configuration format -- we expect a graphic description\n");
            return false;
        }
        complete &= readChildNodeAsFloat("graphic", graphicNode, "frontPixel", &mCarGraphicFrontPixel);
        complete &= readChildNodeAsFloat("display", graphicNode, "rearPixel",  &mCarGraphicRearPixel);
    }


    //
    // Read camera information
    // NOTE:  Missing positions and angles are not reported, but instead default to zero
    //
    {
        Json::Value cameraArray = rootNode["cameras"];
        if (!cameraArray.isArray()) {
            printf("Invalid configuration format -- we expect an array of cameras\n");
            return false;
        }

        mCameras.reserve(cameraArray.size());
        for (auto&& node: cameraArray) {
            // Get data from the configuration file
            Json::Value nameNode = node.get("cameraId", "MISSING");
            const char *cameraId = nameNode.asCString();

            Json::Value usageNode = node.get("function", "");
            const char *function = usageNode.asCString();

            float yaw   = node.get("yaw", 0).asFloat();
            float pitch = node.get("pitch", 0).asFloat();
            float hfov  = node.get("hfov", 0).asFloat();
            float vfov  = node.get("vfov", 0).asFloat();

            // Wrap the direction angles to be in the 180deg to -180deg range
            // Rotate 180 in yaw if necessary to flip the pitch into the +/-90degree range
            pitch = normalizeToPlusMinus180degrees(pitch);
            if (pitch > 90.0f) {
                yaw += 180.0f;
                pitch = 180.0f - pitch;
            }
            if (pitch < -90.0f) {
                yaw += 180.0f;
                pitch = -180.0f + pitch;
            }
            yaw = normalizeToPlusMinus180degrees(yaw);

            // Range check the FOV values to ensure they are postive and less than 180degrees
            if (hfov > 179.0f) {
                printf("Pathological horizontal field of view %f clamped to 179 degrees\n", hfov);
                hfov = 179.0f;
            }
            if (hfov < 1.0f) {
                printf("Pathological horizontal field of view %f clamped to 1 degree\n", hfov);
                hfov = 1.0f;
            }
            if (vfov > 179.0f) {
                printf("Pathological horizontal field of view %f clamped to 179 degrees\n", vfov);
                vfov = 179.0f;
            }
            if (vfov < 1.0f) {
                printf("Pathological horizontal field of view %f clamped to 1 degree\n", vfov);
                vfov = 1.0f;
            }

            // Store the camera info (converting degrees to radians in the process)
            CameraInfo info;
            info.position[0] = node.get("x", 0).asFloat();
            info.position[1] = node.get("y", 0).asFloat();
            info.position[2] = node.get("z", 0).asFloat();
            info.yaw         = yaw   * kDegreesToRadians;
            info.pitch       = pitch * kDegreesToRadians;
            info.hfov        = hfov  * kDegreesToRadians;
            info.vfov        = vfov  * kDegreesToRadians;
            info.cameraId    = cameraId;
            info.function    = function;

            mCameras.push_back(info);
        }
    }

    // If we got this far, we were successful as long as we found all our child fields
    return complete;
}
