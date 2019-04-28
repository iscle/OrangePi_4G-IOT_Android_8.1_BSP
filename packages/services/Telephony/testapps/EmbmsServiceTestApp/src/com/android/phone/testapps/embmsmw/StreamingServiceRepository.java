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
 * limitations under the License
 */

package com.android.phone.testapps.embmsmw;

import android.net.Uri;
import android.telephony.mbms.StreamingServiceInfo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class StreamingServiceRepository {
    private static final String STREAMING_SCHEME = "stream";
    private static final String STREAMING_URI_SSP_PREFIX = "identifier/";

    private static int sServiceIdCounter = 0;
    private static final Map<String, StreamingServiceInfo> sIdToServiceInfo =
            new HashMap<>();
    private static final Map<String, Uri> sIdToStreamingUri = new HashMap<>();

    static {
        fetchStreamingServices();
    }

    public static List<StreamingServiceInfo> getStreamingServicesForClasses(
            List<String> serviceClasses) {
        return sIdToServiceInfo.values().stream()
                .filter((info) -> serviceClasses.contains(info.getServiceClassName()))
                .collect(Collectors.toList());
    }

    public static Uri getUriForService(String serviceId) {
        if (sIdToStreamingUri.containsKey(serviceId)) {
            return sIdToStreamingUri.get(serviceId);
        }
        return null;
    }

    public static StreamingServiceInfo getStreamingServiceInfoForId(String serviceId) {
        return sIdToServiceInfo.getOrDefault(serviceId, null);
    }

    private static void createStreamingService(String className) {
        sServiceIdCounter++;
        String id = "StreamingServiceId[" + sServiceIdCounter + "]";
        Map<Locale, String> localeDict = new HashMap<Locale, String>() {{
                put(Locale.US, "Entertainment Source " + sServiceIdCounter);
                put(Locale.CANADA, "Entertainment Source, eh?" + sServiceIdCounter);
        }};
        List<Locale> locales = new ArrayList<Locale>() {{
                add(Locale.CANADA);
                add(Locale.US);
        }};
        StreamingServiceInfo info = new StreamingServiceInfo(localeDict, className, locales,
                id, new Date(System.currentTimeMillis() - 10000),
                new Date(System.currentTimeMillis() + 10000));
        sIdToServiceInfo.put(id, info);
        sIdToStreamingUri.put(id, Uri.fromParts(STREAMING_SCHEME,
                STREAMING_URI_SSP_PREFIX + sServiceIdCounter,
                null));
    }

    private static void fetchStreamingServices() {
        createStreamingService("Class1");
        createStreamingService("Class2");
        createStreamingService("Class3");
        createStreamingService("Class4");
        createStreamingService("Class5");
        createStreamingService("Class6");
    }

    // Do not instantiate
    private StreamingServiceRepository() {}
}
