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

#ifndef DRM_HAL_VENDOR_MODULE_API_H
#define DRM_HAL_VENDOR_MODULE_API_H

#include <stdint.h>
#include <map>
#include <string>
#include <vector>

/**
 * The DRM and Crypto HALs interact with vendor-provided HAL implementations
 * that have DRM-specific capabilities. Since the VTS tests cannot contain
 * DRM-specific functionality, supporting modules are required to enable VTS
 * to validate HAL implementations in a generic way.  If the vendor-specific
 * VTS module is not provided for a given drm HAL implementation, only very
 * small subset of functionality can be verified.
 *
 * As an example, a DRM HAL implementation interacts with a DRM-specific
 * license server to obtain licenses for decrypting content.  The DRM HAL
 * implementation generates a key request message, delivers it to the server
 * and receives a key response message which is then loaded into the HAL. Once
 * the keys are loaded, the Crypto HAL decryption functionality and performance
 * and other associated APIs can be tested by the common VTS test suite.
 *
 * Vendor-specific VTS modules are shared libraries used by the DRM VTS test.
 * They provide a set of functions to support VTS testing of the DRM HAL module.
 *
 * The modules are placed in a common location on the file system. The VTS test
 * scans through all vendor-provided support libraries and runs the VTS test
 * suite on each library that is found.
 *
 * The vendor-specific module exposes an extern “C” vendorModuleFactory()
 * function that returns a DrmHalVTSVendorModule instance. DrmHalVTSVendorModule
 * instances are versioned, where each version is represented by subclass of
 * DrmHalVTSVendorModule that corresponds to the API version. For example, a
 * vendor-specific module that implements version 1 of the API would return a
 * DrmHalVTSVendorModule_V1 from the vendorModuleFactory() function.
 */

class DrmHalVTSVendorModule;

extern "C" {
/**
 * The factory method for creating DrmHalVTSVendorModule instances. The returned
 * instance will be a subclass of DrmHalVTSVendorModule that corresponds to the
 * supported API version.
 */
DrmHalVTSVendorModule* vendorModuleFactory();
};

class DrmHalVTSVendorModule {
   public:
    DrmHalVTSVendorModule() : installed(true) {}
    virtual ~DrmHalVTSVendorModule() {}

    /**
     * Return the vendor-specific module API version. The version is an integer
     * value with initial version 1. The API version indicates which subclass
     * version DrmHalVTSVendorModule this instance is.
     */
    virtual uint32_t getAPIVersion() const = 0;

    /**
     * Return the UUID for the DRM HAL implementation. Protection System
     * Specific
     * UUID (see http://dashif.org/identifiers/protection/)
     */
    virtual std::vector<uint8_t> getUUID() const = 0;

    /**
     * Return the service name for the DRM HAL implementation. If the hal is a
     * legacy
     * drm plugin, i.e. not running as a HIDL service, return the empty string.
     */
    virtual std::string getServiceName() const = 0;

    /**
     * Set a flag in the vendor module to indicate whether or not the drm
     * scheme corresponding to this module is installed on the device.
     */
    void setInstalled(bool flag) {installed = flag;}
    bool isInstalled() const {return installed;}

   private:
    bool installed;
    DrmHalVTSVendorModule(const DrmHalVTSVendorModule&) = delete;
    void operator=(const DrmHalVTSVendorModule&) = delete;
};

/**
 * API Version 1.  This is the baseline version that supports a minimal set
 * of VTS tests.
 */
class DrmHalVTSVendorModule_V1 : public DrmHalVTSVendorModule {
   public:
    DrmHalVTSVendorModule_V1() {}
    virtual ~DrmHalVTSVendorModule_V1() {}

    virtual uint32_t getAPIVersion() const { return 1; }

    /**
     * Handle a provisioning request. This function will be called if the HAL
     * module's getProvisionRequest returns a provision request.  The vendor
     * module should process the provisioning request, either by sending it
     * to a provisioning server, or generating a mock response.  The resulting
     * provisioning response is returned to the VTS test.
     *
     * @param provisioningRequest the provisioning request recieved from
     * the DRM HAL
     * @param url the default url the HAL implementation provided with the
     * provisioning request
     * @return the generated provisioning response
     */
    virtual std::vector<uint8_t> handleProvisioningRequest(
            const std::vector<uint8_t>& provisioningRequest,
            const std::string& url) = 0;

    /**
     * Content configuration specifies content-specific parameters associated
     * with a key request/response transaction. It allows the VTS test to
     * request keys and use them to perform decryption.
     */
    struct ContentConfiguration {
        /**
         * Assign a name for this configuration that will be referred to
         * in log messages.
         */
        const std::string name;

        /**
         * Server to use when requesting a key response.  This url will be
         * passed as a parameter to the vendor vts module along with the
         * key request to perform the key request transaction.
         */
        const std::string serverUrl;

        /**
         * Initialization data provided to getKeyRequest, e.g. PSSH for CENC
         * content
         */
        const std::vector<uint8_t> initData;

        /**
         *  Mime type provided to getKeyRequest, e.g. "video/mp4", or "cenc"
         */
        const std::string mimeType;

        /**
         * Optional parameters to be associated with the key request
         */
        const std::map<std::string, std::string> optionalParameters;

        /**
         *  Define license policy attributes for the content configuration.
         *  These attributes can affect which tests are able to be applied.
         */
        struct Policy {
            /**
             * Indicate if the license policy allows offline playback.
             * Content configurated with this policy supports KeyType::OFFLINE
             * key requests/responses. A vendor module should provide at least
             * one content configuration where allowOffline is true if the drm
             * scheme supports offline content.
             */
            bool allowOffline;
        } policy;

        /**
         * The keys that will be available once the keys are loaded
         */
        struct Key {
            /**
             * Indicate if the key content is configured to require secure
             * buffers, where the output buffers are protected and cannot be
             * accessed by the non-secure cpu. A vendor module should provide
             * at least one content configurations where isSecure is false, to
             * allow decrypt result verification tests to be run.
             */
            bool isSecure;

            /**
             * A key ID identifies a key to use for decryption
             */
            const std::vector<uint8_t> keyId;

            /**
             * The clear content key is provided to generate expected values for
             * validating decryption.
             */
            const std::vector<uint8_t> clearContentKey;
        };
        std::vector<Key> keys;
    };

    /**
     * Return a list of content configurations that can be exercised by the
     * VTS test.
     */
    virtual std::vector<ContentConfiguration>
            getContentConfigurations() const = 0;

    /**
     * Handle a key request. This function will be called if the HAL
     * module's getKeyRequest returns a key request.  The vendor
     * module should process the key request, either by sending it
     * to a license server, or by generating a mock response.  The resulting
     * key response is returned to the VTS test.
     *
     * @param keyRequest the key request recieved from the DRM HAL
     * @param serverUrl the url of the key server that was supplied
     * by the ContentConfiguration
     * @return the generated key response
     */
    virtual std::vector<uint8_t> handleKeyRequest(
            const std::vector<uint8_t>& keyRequest,
            const std::string& serverUrl) = 0;
};

#endif  // DRM_HAL_VENDOR_MODULE_API_H
