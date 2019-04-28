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

#define LOG_TAG "ValidateAudioConfig"
#include <utils/Log.h>

#define LIBXML_SCHEMAS_ENABLED
#include <libxml/xmlschemastypes.h>
#define LIBXML_XINCLUDE_ENABLED
#include <libxml/xinclude.h>

#include <memory>
#include <string>

#include "ValidateXml.h"

namespace android {
namespace hardware {
namespace audio {
namespace common {
namespace test {
namespace utility {

/** Map libxml2 structures to their corresponding deleters. */
template <class T>
constexpr void (*xmlDeleter)(T* t);
template <>
constexpr auto xmlDeleter<xmlSchema> = xmlSchemaFree;
template <>
constexpr auto xmlDeleter<xmlDoc> = xmlFreeDoc;
template <>
constexpr auto xmlDeleter<xmlSchemaParserCtxt> = xmlSchemaFreeParserCtxt;
template <>
constexpr auto xmlDeleter<xmlSchemaValidCtxt> = xmlSchemaFreeValidCtxt;

/** @return a unique_ptr with the correct deleter for the libxml2 object. */
template <class T>
constexpr auto make_xmlUnique(T* t) {
    // Wrap deleter in lambda to enable empty base optimization
    auto deleter = [](T* t) { xmlDeleter<T>(t); };
    return std::unique_ptr<T, decltype(deleter)>{t, deleter};
}

/** Class that handles libxml2 initialization and cleanup. NOT THREAD SAFE*/
struct Libxml2Global {
    Libxml2Global() {
        xmlLineNumbersDefault(1);  // Better error message
        xmlSetGenericErrorFunc(this, errorCb);
    }
    ~Libxml2Global() {
        // TODO: check if all those cleanup are needed
        xmlSetGenericErrorFunc(nullptr, nullptr);
        xmlSchemaCleanupTypes();
        xmlCleanupParser();
        xmlCleanupThreads();
    }

    const std::string& getErrors() { return errors; }

   private:
    static void errorCb(void* ctxt, const char* msg, ...) {
        auto* self = static_cast<Libxml2Global*>(ctxt);
        va_list args;
        va_start(args, msg);

        char* formatedMsg;
        if (vasprintf(&formatedMsg, msg, args) >= 0) {
            LOG_PRI(ANDROID_LOG_ERROR, LOG_TAG, "%s", formatedMsg);
            self->errors += "Error: ";
            self->errors += formatedMsg;
        }
        free(formatedMsg);

        va_end(args);
    }
    std::string errors;
};

::testing::AssertionResult validateXml(const char* xmlFilePathExpr, const char* xsdFilePathExpr,
                                       const char* xmlFilePath, const char* xsdFilePath) {
    Libxml2Global libxml2;

    auto context = [&]() {
        return std::string() + "    While validating: " + xmlFilePathExpr +
               "\n          Which is: " + xmlFilePath + "\nAgainst the schema: " + xsdFilePathExpr +
               "\n          Which is: " + xsdFilePath + "Libxml2 errors\n" + libxml2.getErrors();
    };

    auto schemaParserCtxt = make_xmlUnique(xmlSchemaNewParserCtxt(xsdFilePath));
    auto schema = make_xmlUnique(xmlSchemaParse(schemaParserCtxt.get()));
    if (schema == nullptr) {
        return ::testing::AssertionFailure() << "Failed to parse schema (xsd)\n" << context();
    }

    auto doc = make_xmlUnique(xmlReadFile(xmlFilePath, nullptr, 0));
    if (doc == nullptr) {
        return ::testing::AssertionFailure() << "Failed to parse xml\n" << context();
    }

    if (xmlXIncludeProcess(doc.get()) == -1) {
        return ::testing::AssertionFailure() << "Failed to resolve xincludes in xml\n" << context();
    }

    auto schemaCtxt = make_xmlUnique(xmlSchemaNewValidCtxt(schema.get()));
    int ret = xmlSchemaValidateDoc(schemaCtxt.get(), doc.get());
    if (ret > 0) {
        return ::testing::AssertionFailure() << "xml is not valid according to the xsd.\n"
                                             << context();
    }
    if (ret < 0) {
        return ::testing::AssertionFailure() << "Internal or API error\n" << context();
    }

    return ::testing::AssertionSuccess();
}

}  // utility
}  // test
}  // common
}  // audio
}  // test
}  // utility
