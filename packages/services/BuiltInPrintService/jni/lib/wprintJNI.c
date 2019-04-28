/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2014-2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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

#include <jni.h>
#include "lib_wprint.h"
#include "wprint_debug.h"
#include <errno.h>
#include "../plugins/wprint_mupdf.h"

#define TAG "wprintJNI"

#define MAX_NUM_PAGES 2000

static jclass _LocalJobParamsClass;
static jfieldID _LocalJobParamsField__borderless;
static jfieldID _LocalJobParamsField__duplex;
static jfieldID _LocalJobParamsField__media_size;
static jfieldID _LocalJobParamsField__media_type;
static jfieldID _LocalJobParamsField__media_tray;
static jfieldID _LocalJobParamsField__color_space;
static jfieldID _LocalJobParamsField__render_flags;
static jfieldID _LocalJobParamsField__num_copies;
static jfieldID _LocalJobParamsField__page_range;
static jfieldID _LocalJobParamsField__print_resolution;
static jfieldID _LocalJobParamsField__printable_width;
static jfieldID _LocalJobParamsField__printable_height;
static jfieldID _LocalJobParamsField__page_width;
static jfieldID _LocalJobParamsField__page_height;
static jfieldID _LocalJobParamsField__page_margin_top;
static jfieldID _LocalJobParamsField__page_margin_left;
static jfieldID _LocalJobParamsField__page_margin_right;
static jfieldID _LocalJobParamsField__page_margin_bottom;
static jfieldID _LocalJobParamsField__job_margin_top;
static jfieldID _LocalJobParamsField__job_margin_left;
static jfieldID _LocalJobParamsField__job_margin_right;
static jfieldID _LocalJobParamsField__job_margin_bottom;
static jfieldID _LocalJobParamsField__fit_to_page;
static jfieldID _LocalJobParamsField__fill_page;
static jfieldID _LocalJobParamsField__auto_rotate;
static jfieldID _LocalJobParamsField__portrait_mode;
static jfieldID _LocalJobParamsField__landscape_mode;
static jfieldID _LocalJobParamsField__nativeData;
static jfieldID _LocalJobParamsField__document_category;
static jfieldID _LocalJobParamsField__alignment;
static jfieldID _LocalJobParamsField__document_scaling;
static jfieldID _LocalJobParamsField__job_name;
static jfieldID _LocalJobParamsField__job_originating_user_name;
static jfieldID _LocalJobParamsField__pdf_render_resolution;

static jclass _LocalPrinterCapabilitiesClass;
static jfieldID _LocalPrinterCapabilitiesField__name;
static jfieldID _LocalPrinterCapabilitiesField__path;
static jfieldID _LocalPrinterCapabilitiesField__uuid;
static jfieldID _LocalPrinterCapabilitiesField__location;
static jfieldID _LocalPrinterCapabilitiesField__duplex;
static jfieldID _LocalPrinterCapabilitiesField__borderless;
static jfieldID _LocalPrinterCapabilitiesField__color;
static jfieldID _LocalPrinterCapabilitiesField__isSupported;
static jfieldID _LocalPrinterCapabilitiesField__mediaDefault;
static jfieldID _LocalPrinterCapabilitiesField__supportedMediaTypes;
static jfieldID _LocalPrinterCapabilitiesField__supportedMediaSizes;
static jfieldID _LocalPrinterCapabilitiesField__nativeData;

static jclass _JobCallbackClass;
static jobject _callbackReceiver;
static jmethodID _JobCallbackMethod__jobCallback;

static jclass _JobCallbackParamsClass;
static jmethodID _JobCallbackParamsMethod__init;
static jfieldID _JobCallbackParamsField__jobId;
static jfieldID _JobCallbackParamsField__jobState;
static jfieldID _JobCallbackParamsField__jobDoneResult;
static jfieldID _JobCallbackParamsField__blockedReasons;

static jclass _PrintServiceStringsClass;
static jfieldID _PrintServiceStringsField__JOB_STATE_QUEUED;
static jfieldID _PrintServiceStringsField__JOB_STATE_RUNNING;
static jfieldID _PrintServiceStringsField__JOB_STATE_BLOCKED;
static jfieldID _PrintServiceStringsField__JOB_STATE_DONE;
static jfieldID _PrintServiceStringsField__JOB_STATE_OTHER;
static jfieldID _PrintServiceStringsField__JOB_DONE_OK;
static jfieldID _PrintServiceStringsField__JOB_DONE_ERROR;
static jfieldID _PrintServiceStringsField__JOB_DONE_CANCELLED;
static jfieldID _PrintServiceStringsField__JOB_DONE_CORRUPT;
static jfieldID _PrintServiceStringsField__JOB_DONE_OTHER;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__OFFLINE;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__BUSY;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__CANCELLED;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_PAPER;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_INK;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_TONER;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__JAMMED;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__DOOR_OPEN;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__SERVICE_REQUEST;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_INK;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_TONER;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__REALLY_LOW_ON_INK;
static jfieldID _PrintServiceStringsField__BLOCKED_REASON__UNKNOWN;
static jfieldID _PrintServiceStringsField__ALIGNMENT__CENTER;
static jfieldID _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL;
static jfieldID _PrintServiceStringsField__ALIGNMENT__CENTER_VERTICAL;
static jfieldID _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL_ON_ORIENTATION;

// Global so it can be used in PDF render code
JavaVM *_JVM = NULL;

static jstring _fakeDir;

int g_API_version = 0;

/*
 * Convert char * to a java object
 */
static void stringToJava(JNIEnv *env, jobject obj, jfieldID id, const char *str);

/*
 * Retuns if the mime type is MIME_TYPE_PDF
 */
static bool _is_pdf_doc(const char *mime_type, const char *pathname) {
    if (mime_type == NULL || pathname == NULL) {
        return false;
    }

    if (strcmp(mime_type, MIME_TYPE_PDF) == 0) {
        return true;
    }

    return false;
}

/*
 * Returns if the string is numeric
 */
static int _isNumeric(const char *s) {
    if (s == NULL || *s == '\0' || isspace(*s)) {
        return 0;
    }
    char *p;
    strtod(s, &p);
    return *p == '\0';
}

/*
 * Outputs the number of pages in a pdf to page_count. Returns False if an error ocurred
 */
static bool _get_pdf_page_count(const char *mime_type, int *page_count, const char *pathname) {
    *page_count = 0;

    if (!_is_pdf_doc(mime_type, pathname)) {
        return false;
    }

    pdf_render_ifc_t *pdf_render_ifc = create_pdf_render_ifc();
    *page_count = pdf_render_ifc->openDocument(pdf_render_ifc, pathname);
    pdf_render_ifc->destroy(pdf_render_ifc);

    LOGI("pdf page count for %s: %d", pathname, *page_count);
    if (*page_count < 0) {
        LOGE("page count error");
        *page_count = 0;
    }
    return true;
}

/*
 * Reorders pdf pages before sending to the printer. In general the last page is printed first.
 * Removes pages from pages_ary if they are not in the specified range.
 */
static bool _order_pdf_pages(int num_pages, int *pages_ary, int *num_index,
        char *page_range_split) {
    bool succeeded = false;
    char num_begin_ary[5] = "";
    char num_end_ary[5] = "";
    int num_counter = 0;
    bool dash_encountered = false;
    int range_count = 0;

    // initialize to 0
    memset(num_begin_ary, 0, 5);
    memset(num_end_ary, 0, 5);

    for (range_count = 0; range_count < (int) strlen(page_range_split); range_count++) {
        // skip spaces
        if (!isspace(page_range_split[range_count])) {
            // store first number found in range in num_begin_ary
            // and second number (after the dash '-') in num_end_ary
            // skip the dash ('-') character
            if (page_range_split[range_count] == '-') {
                dash_encountered = true;
                num_counter = 0;
                continue;
            }

            if (!dash_encountered) {
                num_begin_ary[num_counter++] = page_range_split[range_count];
            } else {
                num_end_ary[num_counter++] = page_range_split[range_count];
            }
        }
    }

    // fill in first cell of end num with 0 so array has a valid number
    if (!dash_encountered) {
        num_end_ary[0] = '0';
    }

    // make sure numeric values are stored in num_begin_ary and num_end_ary
    if (_isNumeric(num_begin_ary) && _isNumeric(num_end_ary)) {
        // convert to integers
        int num_begin = atoi(num_begin_ary);
        int num_end = atoi(num_end_ary);

        // if ending number was 0, there was no range, only a single page number
        // so, set it to the value of the beginning number
        if (num_end == 0) {
            num_end = num_begin;
        }

        // make sure beginning and ending numbers are at least 1
        if (num_begin > 0 && num_end > 0) {
            // make sure the beginning and ending numbers are not greater than the page count
            if (num_begin <= num_pages && num_end <= num_pages) {
                if (num_end >= num_begin) {
                    // make sure the upper bound does not exceed the number of pages
                    if (num_end > num_pages) {
                        num_end = num_pages;
                    }
                    // store range in pages_ary in ascending order
                    int count = 0;
                    for (count = *num_index; count <= (*num_index + num_end - num_begin); count++) {
                        *(pages_ary + count) = num_begin++;
                        *num_index += 1;
                    }
                } else {
                    // reverse order
                    // make sure the upper bound does not exceed the number of pages
                    if (num_begin > num_pages) {
                        num_begin = num_pages;
                    }
                    // store range in pages_ary in descending order
                    int count = 0;
                    for (count = *num_index; count <= *num_index + num_begin - num_end; count++) {
                        *(pages_ary + count) = num_begin--;
                        *num_index += 1;
                    }
                }
                succeeded = true;
            } else {
                LOGE("_order_pdf_pages(), ERROR: first and/or last numbers are not greater than "
                        "%d: first num=%d, second num=%d", num_pages, num_begin, num_end);
            }
        } else {
            LOGE("_order_pdf_pages(), ERROR: first and/or last numbers are not greater than 0: "
                    "first num=%d, second num=%d", num_begin, num_end);
        }
    } else {
        LOGE("_order_pdf_pages(), ERROR: first and/or last numbers are not numeric: first num=%s, "
                "second num=%s", num_begin_ary, num_end_ary);
    }
    return succeeded;
}

/*
 * Outputs page range of a pdf to page_range_str
 */
static void _get_pdf_page_range(JNIEnv *env, jobject javaJobParams, int *pages_ary, int num_pages,
        int *num_index, char *page_range_str) {
    char *page_range = NULL;
    jstring pageRangeObject = (jstring) (*env)->GetObjectField(env, javaJobParams,
            _LocalJobParamsField__page_range);
    if (pageRangeObject) {
        int page_range_size = (*env)->GetStringLength(env, pageRangeObject);
        const jbyte *pageRange = (jbyte *) (*env)->GetStringUTFChars(env, pageRangeObject, 0);
        if (strcmp((char *) pageRange, "") != 0) {
            page_range = (char *) malloc(page_range_size + 1);
            memset(page_range, 0, page_range_size + 1);
            strncpy(page_range, (char *) pageRange, page_range_size);

            // no empty strings
            if (strcmp(page_range, "") == 0) {
                free(page_range);
                page_range = NULL;
            }

            (*env)->ReleaseStringUTFChars(env, pageRangeObject, (const char *) pageRange);
            LOGD("_get_pdf_page_range(), page_range from JNI environment=%s", page_range);
        }
    }

    if (!page_range) {
        page_range = (char *) malloc(MAX_NUM_PAGES + 1);
        memset(page_range, 0, MAX_NUM_PAGES + 1);

        snprintf(page_range_str, MAX_NUM_PAGES, "1-%d", num_pages);
        snprintf(page_range, MAX_NUM_PAGES, "1-%d", num_pages);
    } else {
        strncpy(page_range_str, page_range, MAX_NUM_PAGES);
    }

    LOGD("_get_pdf_page_range(), range: %s, pages in document: %d", page_range_str, num_pages);

    // get the first token in page_range_str
    memset(pages_ary, 0, MAX_NUM_PAGES);
    char *page_range_split = strtok(page_range, ",");
    while (page_range_split != NULL) {
        if (!_order_pdf_pages(num_pages, pages_ary, num_index, page_range_split)) {
            snprintf(page_range_str, MAX_NUM_PAGES, "1-%d", num_pages);
            LOGD("_get_pdf_page_range(), setting page_range to: %s", page_range_str);
            _order_pdf_pages(num_pages, pages_ary, num_index, page_range_str);
            break;
        }

        // get next range token
        page_range_split = strtok(NULL, ",");
    }

    if (page_range) {
        free(page_range);
    }
}

/*
 * Sends a pdf to a printer
 */
static jint _print_pdf_pages(wJob_t job_handle, printer_capabilities_t *printer_cap,
        duplex_t duplex, char *pathname, int num_index, int *pages_ary) {
    int num_pages = num_index;

    // now, print the pages
    int page_index;
    jint result = ERROR;

    // print forward direction if printer prints pages face down; otherwise print backward
    // NOTE: last page is sent from calling function
    if (printer_cap->faceDownTray || duplex) {
        LOGD("_print_pdf_pages(), pages print face down or duplex, printing in normal order");
        page_index = 0;
        while (page_index < num_pages) {
            LOGD("_print_pdf_pages(), PRINTING PDF: %d", *(pages_ary + page_index));
            result = wprintPage(job_handle, *(pages_ary + page_index++), pathname, false, true,
                    0, 0, 0, 0);

            if (result != OK) {
                break;
            }
        }
    } else {
        LOGI("   _print_pdf_pages(), pages print face up, printing in reverse");
        page_index = num_pages - 1;
        while (page_index >= 0) {
            LOGD("_print_pdf_pages(), PRINTING PDF: %s, page: %d", pathname,
                    *(pages_ary + page_index));
            result = wprintPage(job_handle, *(pages_ary + page_index--), pathname, false, true,
                    0, 0, 0, 0);
            if (result != OK) {
                break;
            }
        }
    }

    LOGI("   _print_pdf_pages(), printing result: %s", result == OK ? "OK" : "ERROR");
    return result;
}

/*
 * Initialize JNI. Maps java values to jni values.
 */
static void _initJNI(JNIEnv *env, jobject callbackReceiver, jstring fakeDir) {
    _fakeDir = (jstring) (*env)->NewGlobalRef(env, fakeDir);

    // fill out static accessors for wPrintJobParameters
    _LocalJobParamsClass = (jclass) (*env)->NewGlobalRef(
            env, (*env)->FindClass(env, "com/android/bips/jni/LocalJobParams"));
    _LocalJobParamsField__borderless = (*env)->GetFieldID(env, _LocalJobParamsClass, "borderless",
            "I");
    _LocalJobParamsField__duplex = (*env)->GetFieldID(env, _LocalJobParamsClass, "duplex", "I");
    _LocalJobParamsField__media_size = (*env)->GetFieldID(env, _LocalJobParamsClass, "media_size",
            "I");
    _LocalJobParamsField__media_type = (*env)->GetFieldID(env, _LocalJobParamsClass, "media_type",
            "I");
    _LocalJobParamsField__media_tray = (*env)->GetFieldID(env, _LocalJobParamsClass, "media_tray",
            "I");
    _LocalJobParamsField__color_space = (*env)->GetFieldID(env, _LocalJobParamsClass, "color_space",
            "I");
    _LocalJobParamsField__render_flags = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "render_flags", "I");
    _LocalJobParamsField__num_copies = (*env)->GetFieldID(env, _LocalJobParamsClass, "num_copies",
            "I");
    _LocalJobParamsField__page_range = (*env)->GetFieldID(env, _LocalJobParamsClass, "page_range",
            "Ljava/lang/String;");
    _LocalJobParamsField__print_resolution = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "print_resolution", "I");
    _LocalJobParamsField__printable_width = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "printable_width", "I");
    _LocalJobParamsField__printable_height = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "printable_height", "I");
    _LocalJobParamsField__page_width = (*env)->GetFieldID(env, _LocalJobParamsClass, "page_width",
            "F");
    _LocalJobParamsField__page_height = (*env)->GetFieldID(env, _LocalJobParamsClass, "page_height",
            "F");
    _LocalJobParamsField__page_margin_top = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "page_margin_top", "F");
    _LocalJobParamsField__page_margin_left = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "page_margin_left", "F");
    _LocalJobParamsField__page_margin_right = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "page_margin_right", "F");
    _LocalJobParamsField__page_margin_bottom = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "page_margin_bottom", "F");
    _LocalJobParamsField__nativeData = (*env)->GetFieldID(env, _LocalJobParamsClass, "nativeData",
            "[B");
    _LocalJobParamsField__fit_to_page = (*env)->GetFieldID(env, _LocalJobParamsClass, "fit_to_page",
            "Z");
    _LocalJobParamsField__fill_page = (*env)->GetFieldID(env, _LocalJobParamsClass, "fill_page",
            "Z");
    _LocalJobParamsField__auto_rotate = (*env)->GetFieldID(env, _LocalJobParamsClass, "auto_rotate",
            "Z");
    _LocalJobParamsField__portrait_mode = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "portrait_mode", "Z");
    _LocalJobParamsField__landscape_mode = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "landscape_mode", "Z");
    _LocalJobParamsField__document_category = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "document_category",
            "Ljava/lang/String;");
    _LocalJobParamsField__alignment = (*env)->GetFieldID(env, _LocalJobParamsClass, "alignment",
            "I");
    _LocalJobParamsField__job_margin_top = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "job_margin_top", "F");
    _LocalJobParamsField__job_margin_left = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "job_margin_left", "F");
    _LocalJobParamsField__job_margin_right = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "job_margin_right", "F");
    _LocalJobParamsField__job_margin_bottom = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "job_margin_bottom", "F");
    _LocalJobParamsField__document_scaling = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "document_scaling", "Z");
    _LocalJobParamsField__job_name = (*env)->GetFieldID(env, _LocalJobParamsClass, "job_name",
            "Ljava/lang/String;");
    _LocalJobParamsField__job_originating_user_name = (*env)->GetFieldID(
            env, _LocalJobParamsClass, "job_originating_user_name", "Ljava/lang/String;");
    _LocalJobParamsField__pdf_render_resolution = (*env)->GetFieldID(env, _LocalJobParamsClass,
            "pdf_render_resolution", "I");

    // fill out static accessors for LocalPrinterCapabilities
    _LocalPrinterCapabilitiesClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(
            env, "com/android/bips/jni/LocalPrinterCapabilities"));
    _LocalPrinterCapabilitiesField__path = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "path", "Ljava/lang/String;");
    _LocalPrinterCapabilitiesField__name = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "name", "Ljava/lang/String;");
    _LocalPrinterCapabilitiesField__uuid = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "uuid", "Ljava/lang/String;");
    _LocalPrinterCapabilitiesField__location = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "location", "Ljava/lang/String;");
    _LocalPrinterCapabilitiesField__duplex = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "duplex", "Z");
    _LocalPrinterCapabilitiesField__borderless = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "borderless", "Z");
    _LocalPrinterCapabilitiesField__color = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "color", "Z");
    _LocalPrinterCapabilitiesField__isSupported = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "isSupported", "Z");
    _LocalPrinterCapabilitiesField__mediaDefault = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "mediaDefault", "Ljava/lang/String;");
    _LocalPrinterCapabilitiesField__supportedMediaTypes = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "supportedMediaTypes", "[I");
    _LocalPrinterCapabilitiesField__supportedMediaSizes = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "supportedMediaSizes", "[I");
    _LocalPrinterCapabilitiesField__nativeData = (*env)->GetFieldID(
            env, _LocalPrinterCapabilitiesClass, "nativeData", "[B");

    _JobCallbackParamsClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(
            env, "com/android/bips/jni/JobCallbackParams"));
    _JobCallbackParamsMethod__init = (*env)->GetMethodID(env, _JobCallbackParamsClass,
            "<init>", "()V");
    _JobCallbackParamsField__jobId = (*env)->GetFieldID(env, _JobCallbackParamsClass, "jobId",
            "I");
    _JobCallbackParamsField__jobState = (*env)->GetFieldID(
            env, _JobCallbackParamsClass, "jobState", "Ljava/lang/String;");
    _JobCallbackParamsField__jobDoneResult = (*env)->GetFieldID(
            env, _JobCallbackParamsClass, "jobDoneResult", "Ljava/lang/String;");
    _JobCallbackParamsField__blockedReasons = (*env)->GetFieldID(
            env, _JobCallbackParamsClass, "blockedReasons", "[Ljava/lang/String;");

    if (callbackReceiver) {
        _callbackReceiver = (jobject) (*env)->NewGlobalRef(env, callbackReceiver);
    }
    if (_callbackReceiver) {
        _JobCallbackClass = (jclass) (*env)->NewGlobalRef(env, (*env)->GetObjectClass(
                env, _callbackReceiver));
        _JobCallbackMethod__jobCallback = (*env)->GetMethodID(
                env, _JobCallbackClass, "jobCallback",
                "(ILcom/android/bips/jni/JobCallbackParams;)V");
    } else {
        _callbackReceiver = 0;
    }

    _PrintServiceStringsClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(
            env, "com/android/bips/jni/BackendConstants"));
    _PrintServiceStringsField__JOB_STATE_QUEUED = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_STATE_QUEUED", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_STATE_RUNNING = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_STATE_RUNNING", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_STATE_BLOCKED = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_STATE_BLOCKED", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_STATE_DONE = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_STATE_DONE", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_STATE_OTHER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_STATE_OTHER", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_DONE_OK = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_DONE_OK", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_DONE_ERROR = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_DONE_ERROR", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_DONE_CANCELLED = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_DONE_CANCELLED", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_DONE_CORRUPT = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_DONE_CORRUPT", "Ljava/lang/String;");
    _PrintServiceStringsField__JOB_DONE_OTHER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "JOB_DONE_OTHER", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__OFFLINE = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__OFFLINE", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__BUSY = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__BUSY", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__CANCELLED = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__CANCELLED", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_PAPER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__OUT_OF_PAPER", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_INK = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__OUT_OF_INK", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_TONER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__OUT_OF_TONER", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__JAMMED = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__JAMMED", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__DOOR_OPEN = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__DOOR_OPEN", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__SERVICE_REQUEST = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__SERVICE_REQUEST",
            "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_INK = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__LOW_ON_INK", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_TONER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__LOW_ON_TONER", "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__REALLY_LOW_ON_INK = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__REALLY_LOW_ON_INK",
            "Ljava/lang/String;");
    _PrintServiceStringsField__BLOCKED_REASON__UNKNOWN = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "BLOCKED_REASON__UNKNOWN", "Ljava/lang/String;");

    _PrintServiceStringsField__ALIGNMENT__CENTER = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "ALIGN_CENTER", "I");
    _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "ALIGN_CENTER_HORIZONTAL", "I");
    _PrintServiceStringsField__ALIGNMENT__CENTER_VERTICAL = (*env)->GetStaticFieldID(
            env, _PrintServiceStringsClass, "ALIGN_CENTER_VERTICIAL", "I");
    _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL_ON_ORIENTATION =
            (*env)->GetStaticFieldID(env, _PrintServiceStringsClass,
                    "ALIGN_CENTER_HORIZONTAL_ON_ORIENTATION", "I");

    pdf_render_init(env);
}

/*
 * Converts java printer caps to c and saves them to wprintPrinterCaps
 */
static int _convertPrinterCaps_to_C(JNIEnv *env, jobject javaPrinterCaps,
        printer_capabilities_t *wprintPrinterCaps) {
    if (!javaPrinterCaps || !wprintPrinterCaps) {
        return ERROR;
    }

    jbyteArray nativeDataObject = (jbyteArray) (*env)->GetObjectField(
            env, javaPrinterCaps, _LocalPrinterCapabilitiesField__nativeData);
    if (!nativeDataObject) {
        return ERROR;
    }
    jbyte *nativeDataPtr = (*env)->GetByteArrayElements(env, nativeDataObject, NULL);
    memcpy(wprintPrinterCaps, (const void *) nativeDataPtr, sizeof(printer_capabilities_t));
    (*env)->ReleaseByteArrayElements(env, nativeDataObject, nativeDataPtr, JNI_ABORT);

    return OK;
}

/*
 * Converts printer caps to java and saves them to javaPrinterCaps
 */
static int _convertPrinterCaps_to_Java(JNIEnv *env, jobject javaPrinterCaps,
        const printer_capabilities_t *wprintPrinterCaps) {
    if (!javaPrinterCaps || !wprintPrinterCaps) {
        return ERROR;
    }

    int arrayCreated = 0;
    jbyteArray nativeDataObject = (jbyteArray) (*env)->GetObjectField(
            env, javaPrinterCaps, _LocalPrinterCapabilitiesField__nativeData);
    if (!nativeDataObject) {
        arrayCreated = 1;
        nativeDataObject = (*env)->NewByteArray(env, sizeof(printer_capabilities_t));
    }

    jbyte *nativeDataPtr = (*env)->GetByteArrayElements(env, nativeDataObject, NULL);
    memcpy((void *) nativeDataPtr, wprintPrinterCaps, sizeof(printer_capabilities_t));
    (*env)->ReleaseByteArrayElements(env, nativeDataObject, nativeDataPtr, 0);

    if (arrayCreated) {
        (*env)->SetObjectField(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__nativeData,
                nativeDataObject);
        (*env)->DeleteLocalRef(env, nativeDataObject);
    }

    (*env)->SetBooleanField(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__duplex,
            (jboolean) wprintPrinterCaps->duplex);
    (*env)->SetBooleanField(env, javaPrinterCaps,
            _LocalPrinterCapabilitiesField__borderless,
            (jboolean) wprintPrinterCaps->borderless);
    (*env)->SetBooleanField(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__color,
            (jboolean) wprintPrinterCaps->color);
    (*env)->SetBooleanField(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__isSupported,
            (jboolean) wprintPrinterCaps->isSupported);

    stringToJava(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__mediaDefault,
            wprintPrinterCaps->mediaDefault);
    stringToJava(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__path,
            wprintPrinterCaps->printerUri);
    stringToJava(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__name,
            wprintPrinterCaps->name);
    stringToJava(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__uuid,
            wprintPrinterCaps->uuid);
    stringToJava(env, javaPrinterCaps, _LocalPrinterCapabilitiesField__location,
            wprintPrinterCaps->location);

    jintArray intArray;
    int *intArrayPtr;
    int index;

    intArray = (*env)->NewIntArray(env, wprintPrinterCaps->numSupportedMediaTypes);
    intArrayPtr = (*env)->GetIntArrayElements(env, intArray, NULL);
    for (index = 0; index < wprintPrinterCaps->numSupportedMediaTypes; index++) {
        intArrayPtr[index] = (int) wprintPrinterCaps->supportedMediaTypes[index];
    }
    (*env)->ReleaseIntArrayElements(env, intArray, intArrayPtr, 0);
    (*env)->SetObjectField(env, javaPrinterCaps,
            _LocalPrinterCapabilitiesField__supportedMediaTypes, intArray);
    (*env)->DeleteLocalRef(env, intArray);

    intArray = (*env)->NewIntArray(env, wprintPrinterCaps->numSupportedMediaSizes);
    intArrayPtr = (*env)->GetIntArrayElements(env, intArray, NULL);
    for (index = 0; index < wprintPrinterCaps->numSupportedMediaSizes; index++) {
        intArrayPtr[index] = (int) wprintPrinterCaps->supportedMediaSizes[index];
    }
    (*env)->ReleaseIntArrayElements(env, intArray, intArrayPtr, 0);
    (*env)->SetObjectField(env, javaPrinterCaps,
            _LocalPrinterCapabilitiesField__supportedMediaSizes, intArray);
    (*env)->DeleteLocalRef(env, intArray);

    int count;
    for (count = index = 0; index < (sizeof(int) * 8); index++) {
        if ((wprintPrinterCaps->supportedInputMimeTypes & (1 << index)) != 0) {
            count++;
        }
    }

    return OK;
}

/*
 * Converts str to a java string
 */
static void stringToJava(JNIEnv *env, jobject obj, jfieldID id, const char *str) {
    jstring jStr;

    // If null, copy an empty string
    if (!str) str = "";

    jStr = (*env)->NewStringUTF(env, str);
    (*env)->SetObjectField(env, obj, id, jStr);
    (*env)->DeleteLocalRef(env, jStr);
}

/*
 * Converts javaJobParams to C and saves them to wprintJobParams
 */
static int _convertJobParams_to_C(JNIEnv *env, jobject javaJobParams,
        wprint_job_params_t *wprintJobParams) {
    if (!javaJobParams || !wprintJobParams) {
        return ERROR;
    }

    jbyteArray nativeDataObject = (jbyteArray) (*env)->GetObjectField(
            env, javaJobParams, _LocalJobParamsField__nativeData);
    if (nativeDataObject == 0) {
        return ERROR;
    }

    jbyte *nativeDataPtr = (*env)->GetByteArrayElements(env, nativeDataObject, NULL);
    memcpy(wprintJobParams, (const void *) nativeDataPtr, sizeof(wprint_job_params_t));
    (*env)->ReleaseByteArrayElements(env, nativeDataObject, nativeDataPtr, JNI_ABORT);

    wprintJobParams->media_size = (media_size_t) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__media_size);
    wprintJobParams->media_type = (media_type_t) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__media_type);
    wprintJobParams->duplex = (duplex_t) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__duplex);
    wprintJobParams->color_space = (color_space_t) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__color_space);
    wprintJobParams->media_tray = (media_tray_t) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__media_tray);
    wprintJobParams->num_copies = (unsigned int) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__num_copies);
    wprintJobParams->borderless = (bool) (*env)->GetIntField(env, javaJobParams,
            _LocalJobParamsField__borderless);
    wprintJobParams->render_flags = (unsigned int) (*env)->GetIntField(
            env, javaJobParams, _LocalJobParamsField__render_flags);
    wprintJobParams->pdf_render_resolution =
            (unsigned int) (*env)->GetIntField(env, javaJobParams,
                    _LocalJobParamsField__pdf_render_resolution);
    // job margin setting
    wprintJobParams->job_top_margin = (float) (*env)->GetFloatField(
            env, javaJobParams, _LocalJobParamsField__job_margin_top);
    wprintJobParams->job_left_margin = (float) (*env)->GetFloatField(
            env, javaJobParams, _LocalJobParamsField__job_margin_left);
    wprintJobParams->job_right_margin = (float) (*env)->GetFloatField(
            env, javaJobParams, _LocalJobParamsField__job_margin_right);
    wprintJobParams->job_bottom_margin = (float) (*env)->GetFloatField(
            env, javaJobParams, _LocalJobParamsField__job_margin_bottom);

    if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__portrait_mode)) {
        wprintJobParams->render_flags |= RENDER_FLAG_PORTRAIT_MODE;
    } else if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__landscape_mode)) {
        wprintJobParams->render_flags |= RENDER_FLAG_LANDSCAPE_MODE;
    } else if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__auto_rotate)) {
        wprintJobParams->render_flags |= RENDER_FLAG_AUTO_ROTATE;
    }
    if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__fill_page)) {
        wprintJobParams->render_flags |= AUTO_SCALE_RENDER_FLAGS;
    } else if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__fit_to_page)) {
        wprintJobParams->render_flags |= AUTO_FIT_RENDER_FLAGS;
        if ((*env)->GetBooleanField(env, javaJobParams, _LocalJobParamsField__document_scaling)) {
            wprintJobParams->render_flags |= RENDER_FLAG_DOCUMENT_SCALING;
        }
    }

    int alignment = ((*env)->GetIntField(env, javaJobParams, _LocalJobParamsField__alignment));
    if (alignment != 0) {
        wprintJobParams->render_flags &= ~(RENDER_FLAG_CENTER_VERTICAL |
                RENDER_FLAG_CENTER_HORIZONTAL |
                RENDER_FLAG_CENTER_ON_ORIENTATION);
        if (alignment & ((*env)->GetStaticIntField(
                env, _PrintServiceStringsClass,
                _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL))) {
            wprintJobParams->render_flags |= RENDER_FLAG_CENTER_HORIZONTAL;
        }
        if (alignment & ((*env)->GetStaticIntField(
                env, _PrintServiceStringsClass,
                _PrintServiceStringsField__ALIGNMENT__CENTER_VERTICAL))) {
            wprintJobParams->render_flags |= RENDER_FLAG_CENTER_VERTICAL;
        }
        if (alignment & ((*env)->GetStaticIntField(
                env, _PrintServiceStringsClass,
                _PrintServiceStringsField__ALIGNMENT__CENTER_HORIZONTAL_ON_ORIENTATION))) {
            wprintJobParams->render_flags |= RENDER_FLAG_CENTER_ON_ORIENTATION;
        }
        if ((alignment & ((*env)->GetStaticIntField(
                env, _PrintServiceStringsClass, _PrintServiceStringsField__ALIGNMENT__CENTER))) ==
                ((*env)->GetStaticIntField(env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__ALIGNMENT__CENTER))) {
            wprintJobParams->render_flags &= ~RENDER_FLAG_CENTER_ON_ORIENTATION;
            wprintJobParams->render_flags |= (RENDER_FLAG_CENTER_VERTICAL |
                    RENDER_FLAG_CENTER_HORIZONTAL);
        }
    }

    jstring docCategory = (jstring) (*env)->GetObjectField(env, javaJobParams,
            _LocalJobParamsField__document_category);
    if (docCategory != NULL) {
        const char *category = (*env)->GetStringUTFChars(env, docCategory, NULL);
        if (category != NULL) {
            strncpy(wprintJobParams->docCategory, category,
                    sizeof(wprintJobParams->docCategory) - 1);
            (*env)->ReleaseStringUTFChars(env, docCategory, category);
        }
    }
    // job name
    jstring jobName = (jstring) (*env)->GetObjectField(env, javaJobParams,
            _LocalJobParamsField__job_name);
    if (jobName != NULL) {
        const char *name = (*env)->GetStringUTFChars(env, jobName, NULL);
        if (name != NULL) {
            strncpy(wprintJobParams->job_name, name, sizeof(wprintJobParams->job_name) - 1);
            (*env)->ReleaseStringUTFChars(env, jobName, name);
        }
    }
    // job originating user name
    jstring jobOriginatingUserName = (jstring) (*env)->GetObjectField(
            env, javaJobParams, _LocalJobParamsField__job_originating_user_name);
    if (jobOriginatingUserName != NULL) {
        const char *name = (*env)->GetStringUTFChars(env, jobOriginatingUserName, NULL);
        if (name != NULL) {
            strncpy(wprintJobParams->job_originating_user_name, name,
                    sizeof(wprintJobParams->job_originating_user_name) - 1);
            (*env)->ReleaseStringUTFChars(env, jobOriginatingUserName, name);
        }
    }

    free(wprintJobParams->page_range);
    wprintJobParams->page_range = NULL;
    jstring pageRangeObject = (jstring) (*env)->GetObjectField(env, javaJobParams,
            _LocalJobParamsField__page_range);
    if (pageRangeObject) {
        int page_range_size = (*env)->GetStringLength(env, pageRangeObject);
        const jbyte *pageRange = (jbyte *) (*env)->GetStringUTFChars(env, pageRangeObject, 0);
        if (strcmp((char *) pageRange, "") != 0) {
            wprintJobParams->page_range = (char *) malloc(page_range_size + 1);
            memset(wprintJobParams->page_range, 0, page_range_size + 1);
            strncpy(wprintJobParams->page_range, (char *) pageRange, page_range_size);

            (*env)->ReleaseStringUTFChars(env, pageRangeObject, (const char *) pageRange);
        }
    }

    return OK;
}

/*
 * Converts wprintJobParams to java and saves them to javaJobParams
 */
static int _covertJobParams_to_Java(JNIEnv *env, jobject javaJobParams,
        wprint_job_params_t *wprintJobParams) {
    if (!javaJobParams || !wprintJobParams) {
        return ERROR;
    }

    jbyteArray nativeDataObject = (jbyteArray) (*env)->GetObjectField(
            env, javaJobParams, _LocalJobParamsField__nativeData);
    if (!nativeDataObject) {
        nativeDataObject = (*env)->NewByteArray(env, sizeof(wprint_job_params_t));
        (*env)->SetObjectField(env, javaJobParams, _LocalJobParamsField__nativeData,
                nativeDataObject);
        nativeDataObject = (jbyteArray) (*env)->GetObjectField(env, javaJobParams,
                _LocalJobParamsField__nativeData);
    }

    jbyte *nativeDataPtr = (*env)->GetByteArrayElements(env, nativeDataObject, NULL);
    memcpy((void *) nativeDataPtr, wprintJobParams, sizeof(wprint_job_params_t));
    (*env)->ReleaseByteArrayElements(env, nativeDataObject, nativeDataPtr, 0);

    // update job parameters
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__media_size,
            (int) wprintJobParams->media_size);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__media_type,
            (int) wprintJobParams->media_type);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__duplex,
            (int) wprintJobParams->duplex);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__color_space,
            (int) wprintJobParams->color_space);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__media_tray,
            (int) wprintJobParams->media_tray);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__num_copies,
            (int) wprintJobParams->num_copies);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__borderless,
            (int) wprintJobParams->borderless);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__render_flags,
            (int) wprintJobParams->render_flags);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__pdf_render_resolution,
            wprintJobParams->pdf_render_resolution);
    (*env)->SetBooleanField(env, javaJobParams, _LocalJobParamsField__fit_to_page,
            (jboolean) ((wprintJobParams->render_flags & AUTO_FIT_RENDER_FLAGS) ==
                    AUTO_FIT_RENDER_FLAGS));
    (*env)->SetBooleanField(env, javaJobParams, _LocalJobParamsField__fill_page,
            (jboolean) ((wprintJobParams->render_flags & AUTO_SCALE_RENDER_FLAGS) ==
                    AUTO_SCALE_RENDER_FLAGS));
    (*env)->SetBooleanField(env, javaJobParams, _LocalJobParamsField__auto_rotate,
            (jboolean) ((wprintJobParams->render_flags & RENDER_FLAG_AUTO_ROTATE) != 0));
    (*env)->SetBooleanField(env, javaJobParams, _LocalJobParamsField__portrait_mode, (jboolean) (
            (wprintJobParams->render_flags & RENDER_FLAG_PORTRAIT_MODE) != 0));
    (*env)->SetBooleanField(env, javaJobParams, _LocalJobParamsField__landscape_mode, (jboolean) (
            (wprintJobParams->render_flags & RENDER_FLAG_LANDSCAPE_MODE) != 0));

    // update the printable area & DPI information
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__print_resolution,
            (int) wprintJobParams->pixel_units);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__printable_width,
            (int) wprintJobParams->width);
    (*env)->SetIntField(env, javaJobParams, _LocalJobParamsField__printable_height,
            (int) wprintJobParams->height);

    // update the page size information
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_width,
            wprintJobParams->page_width);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_height,
            wprintJobParams->page_height);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_margin_top,
            wprintJobParams->page_top_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_margin_left,
            wprintJobParams->page_left_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_margin_right,
            wprintJobParams->page_right_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__page_margin_bottom,
            wprintJobParams->page_bottom_margin);

    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__job_margin_top,
            wprintJobParams->job_top_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__job_margin_left,
            wprintJobParams->job_left_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__job_margin_right,
            wprintJobParams->job_right_margin);
    (*env)->SetFloatField(env, javaJobParams, _LocalJobParamsField__job_margin_bottom,
            wprintJobParams->job_bottom_margin);

    return OK;
}

/*
 * Handles print job callbacks. Handles job states and blocked reasons
 */
static void _wprint_callback_fn(wJob_t job_handle, void *param) {
    jstring jStr;
    wprint_job_callback_params_t *cb_param = (wprint_job_callback_params_t *) param;
    if (!cb_param) {
        return;
    }

    int needDetach = 0;
    JNIEnv *env;
    if ((*_JVM)->GetEnv(_JVM, (void **) &env, JNI_VERSION_1_6) < 0) {
        needDetach = 1;
        if ((*_JVM)->AttachCurrentThread(_JVM, &env, NULL) < 0) {
            return;
        }
    }

    jobject callbackParams = (*env)->NewObject(env, _JobCallbackParamsClass,
            _JobCallbackParamsMethod__init);
    if (callbackParams != 0) {
        switch (cb_param->state) {
            case JOB_QUEUED:
                jStr = (jstring) (*env)->GetStaticObjectField(
                        env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__JOB_STATE_QUEUED);
                break;
            case JOB_RUNNING:
                jStr = (jstring) (*env)->GetStaticObjectField(
                        env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__JOB_STATE_RUNNING);
                break;
            case JOB_BLOCKED:
                jStr = (jstring) (*env)->GetStaticObjectField(
                        env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__JOB_STATE_BLOCKED);
                break;
            case JOB_DONE:
                jStr = (jstring) (*env)->GetStaticObjectField(
                        env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__JOB_STATE_DONE);
                break;
            default:
                jStr = (jstring) (*env)->GetStaticObjectField(
                        env, _PrintServiceStringsClass,
                        _PrintServiceStringsField__JOB_STATE_OTHER);
                break;
        }
        (*env)->SetObjectField(env, callbackParams, _JobCallbackParamsField__jobState, jStr);

        if (cb_param->state == JOB_DONE) {
            switch (cb_param->job_done_result) {
                case OK:
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__JOB_DONE_OK);
                    break;
                case ERROR:
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__JOB_DONE_ERROR);
                    break;
                case CANCELLED:
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__JOB_DONE_CANCELLED);
                    break;
                case CORRUPT:
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__JOB_DONE_CORRUPT);
                    break;
                default:
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__JOB_DONE_OTHER);
                    break;
            }

            (*env)->SetObjectField(env, callbackParams,
                    _JobCallbackParamsField__jobDoneResult, jStr);
        }

        int i, count;
        for (count = i = 0; i < PRINT_STATUS_MAX_STATE; i++) {
            if (cb_param->blocked_reasons & (1 << i)) {
                count++;
            }
        }

        if (count > 0) {
            jStr = (*env)->NewStringUTF(env, "");
            jobjectArray stringArray = (*env)->NewObjectArray(env, count, (*env)->FindClass(
                    env, "java/lang/String"), jStr);
            (*env)->DeleteLocalRef(env, jStr);

            unsigned int blocked_reasons = cb_param->blocked_reasons;
            for (count = i = 0; i < PRINT_STATUS_MAX_STATE; i++) {
                jStr = NULL;

                if ((blocked_reasons & (1 << i)) == 0) {
                    jStr = NULL;
                } else if (blocked_reasons & BLOCKED_REASON_UNABLE_TO_CONNECT) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__OFFLINE);
                } else if (blocked_reasons & BLOCKED_REASON_BUSY) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__BUSY);
                } else if (blocked_reasons & BLOCKED_REASONS_CANCELLED) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__CANCELLED);
                } else if (blocked_reasons & BLOCKED_REASON_JAMMED) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__JAMMED);
                } else if (blocked_reasons & BLOCKED_REASON_OUT_OF_PAPER) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_PAPER);
                } else if (blocked_reasons & BLOCKED_REASON_OUT_OF_INK) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_INK);
                } else if (blocked_reasons & BLOCKED_REASON_OUT_OF_TONER) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__OUT_OF_TONER);
                } else if (blocked_reasons & BLOCKED_REASON_DOOR_OPEN) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__DOOR_OPEN);
                } else if (blocked_reasons & BLOCKED_REASON_SVC_REQUEST) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__SERVICE_REQUEST);
                } else if (blocked_reasons & BLOCKED_REASON_LOW_ON_INK) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_INK);
                } else if (blocked_reasons & BLOCKED_REASON_LOW_ON_TONER) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__LOW_ON_TONER);
                } else if (blocked_reasons &
                        BLOCKED_REASON_PRINT_STATUS_VERY_LOW_ON_INK) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__REALLY_LOW_ON_INK);
                } else if (blocked_reasons & BLOCKED_REASON_UNKNOWN) {
                    jStr = (jstring) (*env)->GetStaticObjectField(
                            env, _PrintServiceStringsClass,
                            _PrintServiceStringsField__BLOCKED_REASON__UNKNOWN);
                }

                blocked_reasons &= ~(1 << i);
                if (jStr != 0) {
                    (*env)->SetObjectArrayElement(env, stringArray, count++, jStr);
                }
            }

            (*env)->SetObjectField(env, callbackParams, _JobCallbackParamsField__blockedReasons,
                    stringArray);
        }

        (*env)->SetIntField(env, callbackParams, _JobCallbackParamsField__jobId,
                (jint) job_handle);
        (*env)->CallVoidMethod(env, _callbackReceiver, _JobCallbackMethod__jobCallback,
                (jint) job_handle, callbackParams);
        (*env)->DeleteLocalRef(env, callbackParams);
    }

    if (needDetach) {
        (*_JVM)->DetachCurrentThread(_JVM);
    }
}

/*
 * Initialize wprint JNI
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeInit(
        JNIEnv *env, jobject obj, jobject callbackReceiver, jstring fakeDir,
        jint apiVersion) {
    LOGI("nativeInit JNIenv is %p", env);
    int result;

    // Setup the global JavaVM reference first.
    (*env)->GetJavaVM(env, &_JVM);

    // Initialize the Android API version value
    g_API_version = apiVersion;

    _initJNI(env, callbackReceiver, fakeDir);

    // initialize wprint library
    result = wprintInit();

    // return the result
    return result;
}

/*
 * Copies a given string and returns the copy
 */
static char *copyToNewString(JNIEnv *env, jstring source) {
    const char *fromJava;
    char *newString;

    fromJava = (*env)->GetStringUTFChars(env, source, NULL);
    if (fromJava == NULL) return NULL;

    newString = (char *) malloc(strlen(fromJava) + 1);
    strcpy(newString, fromJava);
    (*env)->ReleaseStringUTFChars(env, source, fromJava);

    return newString;
}

/*
 * JNI call to wprint to get capabilities. Returns caps converted to java.
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeGetCapabilities(
        JNIEnv *env, jobject obj, jstring address, jint port, jstring httpResource,
        jstring uriScheme, jlong timeout, jobject printerCaps) {
    jint result;
    printer_capabilities_t caps;
    wprint_connect_info_t connect_info;

    connect_info.printer_addr = copyToNewString(env, address);
    connect_info.uri_path = copyToNewString(env, httpResource);
    connect_info.uri_scheme = copyToNewString(env, uriScheme);
    connect_info.port_num = port;
    connect_info.timeout = timeout;

    LOGI("nativeGetCapabilities for %s JNIenv is %p", connect_info.printer_addr, env);

    // This call may take a while, and the JNI may be torn down when we return
    result = wprintGetCapabilities(&connect_info, &caps);

    if (connect_info.printer_addr) free((char *) connect_info.printer_addr);
    if (connect_info.uri_path) free((char *) connect_info.uri_path);
    if (connect_info.uri_scheme) free((char *) connect_info.uri_scheme);

    if (!wprintIsRunning() && result == 0) {
        result = ERROR;
    }

    // additional IPP checks
    if (result == 0) {
        if (caps.isSupported && (caps.ippVersionMajor < 1)) {
            caps.isSupported = 0;
        }
        _convertPrinterCaps_to_Java(env, printerCaps, &caps);
    }

    return result;
}

/*
 * JNI call to wprint to get default job params. Returns job params converted to java.
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeGetDefaultJobParameters(
        JNIEnv *env, jobject obj, jobject jobParams) {
    LOGI("nativeGetDefaultJobParameters, JNIenv is %p", env);
    jint result;
    wprint_job_params_t params;

    result = wprintGetDefaultJobParams(&params);

    _covertJobParams_to_Java(env, jobParams, &params);
    return result;
}

/*
 * JNI call to wprint to get final job params. Returns final params converted to java.
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeGetFinalJobParameters(
        JNIEnv *env, jobject obj, jobject jobParams, jobject printerCaps) {
    LOGI("nativeGetFinalJobParameters, JNIenv is %p", env);
    jint result;
    wprint_job_params_t params;
    printer_capabilities_t caps;

    _convertJobParams_to_C(env, jobParams, &params);
    _convertPrinterCaps_to_C(env, printerCaps, &caps);

    LOGD("nativeGetFinalJobParameters: After _convertJobParams_to_C: res=%d, name=%s",
            params.pdf_render_resolution, params.job_name);
    result = wprintGetFinalJobParams(&params, &caps);

    _covertJobParams_to_Java(env, jobParams, &params);
    return result;
}

/*
 * JNI call to wprint to start a print job. Takes connection params, job params, caps, and file
 * array to complete the job
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeStartJob(
        JNIEnv *env, jobject obj, jstring address, jint port, jstring mimeType, jobject jobParams,
        jobject printerCaps, jobject fileArray, jstring jobDebugDir, jstring scheme) {
    LOGI("nativeStartJob, JNIenv is %p", env);
    jint result = ERROR;
    wJob_t job_handle = ERROR;
    bool hasFiles = false;

    wprint_job_params_t params;
    printer_capabilities_t caps;

    _convertJobParams_to_C(env, jobParams, &params);
    _convertPrinterCaps_to_C(env, printerCaps, &caps);

    LOGD("nativeStartJob: After _convertJobParams_to_C: res=%d, name=%s",
            params.pdf_render_resolution, params.job_name);

    const char *addressStr = (*env)->GetStringUTFChars(env, address, NULL);
    const char *mimeTypeStr = (*env)->GetStringUTFChars(env, mimeType, NULL);
    const char *dataDirStr = (*env)->GetStringUTFChars(env, _fakeDir, NULL);
    const char *schemeStr = (*env)->GetStringUTFChars(env, scheme, NULL);

    jsize len = 0;
    jobjectArray array;

    if (fileArray) {
        array = (jobjectArray) fileArray;
        len = (*env)->GetArrayLength(env, array);
        hasFiles = (len > 0);
    }

    int index = 0, pageIndex, incrementor;
    int page_range_arr[len];

    // Initialize page_range_arr (address defect reported by Coverity scans)
    memset((char *) page_range_arr, 0, sizeof(int) * len);

    int pdf_pages_ary[len];
    int pages_ary[len][MAX_NUM_PAGES];

    if (hasFiles) {
        result = OK;
        for (pageIndex = 0; ((result == OK) && (pageIndex < len)); pageIndex++) {
            jstring page = (jstring) (*env)->GetObjectArrayElement(env, array, pageIndex);
            const char *pageStr = (*env)->GetStringUTFChars(env, page, NULL);
            if (pageStr == NULL) {
                result = ERROR;
            } else {
                int page_count = 0;
                if (_get_pdf_page_count(mimeTypeStr, &page_count, pageStr)) {
                    pdf_pages_ary[pageIndex] = page_count;
                    page_range_arr[pageIndex] = 0;
                    char page_range_str[MAX_NUM_PAGES];
                    memset(page_range_str, 0, MAX_NUM_PAGES);
                    _get_pdf_page_range(env, jobParams, &pages_ary[pageIndex][0],
                            pdf_pages_ary[pageIndex], &page_range_arr[pageIndex], page_range_str);
                }
            }
            (*env)->ReleaseStringUTFChars(env, page, pageStr);
        }

        jstring page = (jstring) (*env)->GetObjectArrayElement(env, array, index);
        const char *pageStr = (*env)->GetStringUTFChars(env, page, NULL);
        if (pageStr == NULL) {
            result = ERROR;
        }

        if (len == 1) {
            if (_is_pdf_doc((char *) mimeTypeStr, (char *) pageStr)) {
                if (page_range_arr[0] == 1) {
                    LOGI("smart duplex, disabling duplex");
                    params.duplex = DUPLEX_MODE_NONE;
                }
            } else {
                LOGI("smart duplex, disabling duplex");
                params.duplex = DUPLEX_MODE_NONE;
            }
        }

        (*env)->ReleaseStringUTFChars(env, page, pageStr);
        const char *jobDebugDirStr = NULL;
        if (jobDebugDir != NULL) {
            jobDebugDirStr = (*env)->GetStringUTFChars(env, jobDebugDir, NULL);
        }
        result = wprintStartJob(addressStr, port, &params, &caps, (char *) mimeTypeStr,
                (char *) dataDirStr, _wprint_callback_fn, jobDebugDirStr, schemeStr);
        if (result == ERROR) {
            LOGE("failed to start job: error code :%d", errno);
        }
        if ((jobDebugDir != NULL) && (jobDebugDirStr != NULL)) {
            (*env)->ReleaseStringUTFChars(env, jobDebugDir, jobDebugDirStr);
        }
    } else {
        LOGE("empty file list");
    }
    if (result != ERROR) {
        job_handle = (wJob_t) result;

        // register job handle with service
        if (caps.faceDownTray || params.duplex) {
            index = 0;
            incrementor = 1;
        } else {
            index = len - 1;
            incrementor = -1;
        }

        result = OK;
        for (pageIndex = 1; ((result == OK) && (pageIndex <= len)); pageIndex++) {
            jstring page = (jstring) (*env)->GetObjectArrayElement(env, array, index);
            const char *pageStr = (*env)->GetStringUTFChars(env, page, NULL);
            if (pageStr == NULL) {
                result = ERROR;
            } else {
                if (_is_pdf_doc((char *) mimeTypeStr, (char *) pageStr)) {
                    result = _print_pdf_pages(job_handle, &caps, params.duplex, (char *) pageStr,
                            page_range_arr[index], pages_ary[index]);
                } else {
                    result = wprintPage(job_handle, pageIndex, (char *) pageStr, false, false,
                            0, 0, 0, 0);
                }
            }
            (*env)->ReleaseStringUTFChars(env, page, pageStr);
            index += incrementor;
        }

        wprintPage(job_handle, pageIndex, NULL, true, false, 0, 0, 0, 0);
        if (result != OK) {
            LOGE("failed to add some pages, aborting job");
            wprintCancelJob(job_handle);
            wprintEndJob(job_handle);
            job_handle = ERROR;
        }
    }

    (*env)->ReleaseStringUTFChars(env, mimeType, mimeTypeStr);
    (*env)->ReleaseStringUTFChars(env, address, addressStr);
    (*env)->ReleaseStringUTFChars(env, _fakeDir, dataDirStr);
    (*env)->ReleaseStringUTFChars(env, scheme, schemeStr);
    return job_handle;
}

/*
 * JNI call to wprint to end a print job
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeEndJob(
        JNIEnv *env, jobject obj, jint job_handle) {
    LOGI("nativeEndJob, JNIenv is %p", env);
    return wprintEndJob((wJob_t) job_handle);
}

/*
 * JNI call to wprint to cancel a print job
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeCancelJob(
        JNIEnv *env, jobject obj, jint job_handle) {
    LOGI("nativeCancelJob, JNIenv is %p", env);
    return wprintCancelJob((wJob_t) job_handle);
}

/*
 * JNI call to wprint to exit
 */
JNIEXPORT jint JNICALL Java_com_android_bips_ipp_Backend_nativeExit(JNIEnv *env, jobject obj) {
    LOGI("nativeExit, JNIenv is %p", env);

    (*env)->DeleteGlobalRef(env, _LocalJobParamsClass);
    (*env)->DeleteGlobalRef(env, _LocalPrinterCapabilitiesClass);
    (*env)->DeleteGlobalRef(env, _JobCallbackParamsClass);
    if (_callbackReceiver) {
        (*env)->DeleteGlobalRef(env, _callbackReceiver);
    }
    if (_JobCallbackClass) {
        (*env)->DeleteGlobalRef(env, _JobCallbackClass);
    }
    (*env)->DeleteGlobalRef(env, _fakeDir);
    (*env)->DeleteGlobalRef(env, _PrintServiceStringsClass);

    pdf_render_deinit(env);
    return wprintExit();
}

/*
 * Sets app name/version and os name
 */
JNIEXPORT void JNICALL Java_com_android_bips_ipp_Backend_nativeSetSourceInfo(
        JNIEnv *env, jobject obj, jstring appName, jstring appVersion, jstring osName) {
    LOGI("nativeSetSourceInfo, JNIenv is %p", env);
    const char *appNameStr = (*env)->GetStringUTFChars(env, appName, NULL);
    const char *appVersionStr = (*env)->GetStringUTFChars(env, appVersion, NULL);
    const char *osNameStr = (*env)->GetStringUTFChars(env, osName, NULL);
    wprintSetSourceInfo(appNameStr, appVersionStr, osNameStr);
    (*env)->ReleaseStringUTFChars(env, appName, appNameStr);
    (*env)->ReleaseStringUTFChars(env, appVersion, appVersionStr);
    (*env)->ReleaseStringUTFChars(env, osName, osNameStr);
}