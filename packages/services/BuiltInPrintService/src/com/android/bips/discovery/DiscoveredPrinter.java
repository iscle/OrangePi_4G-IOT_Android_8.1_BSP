/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.discovery;

import android.content.Context;
import android.net.Uri;
import android.print.PrinterId;
import android.printservice.PrintService;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.bips.R;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

/** Represents a network-visible printer */
public class DiscoveredPrinter {
    /** UUID (RFC4122) uniquely identifying the print service, or null if not reported */
    public final Uri uuid;

    /** User-visible name for the print service */
    public final String name;

    /** Location of the device or null of not reported */
    public final String location;

    /** Resource path at which the print service can be reached */
    public final Uri path;

    /** Lazily-created printer id. */
    private PrinterId mPrinterId;

    /**
     * Construct minimal information about a network printer
     *
     * @param uuid     Unique identification of the network printer, if known
     * @param name     Self-identified printer or service name
     * @param path     Network path at which the printer is currently available
     * @param location Self-advertised location of the printer, if known
     */
    public DiscoveredPrinter(Uri uuid, String name, Uri path, String location) {
        this.uuid = uuid;
        this.name = name;
        this.path = path;
        this.location = location;
    }

    /** Construct an object based on field values of an JSON object found next in the JsonReader */
    public DiscoveredPrinter(JsonReader reader) throws IOException {
        String printerName = null, location = null;
        Uri uuid = null, path = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String itemName = reader.nextName();
            switch (itemName) {
                case "uuid":
                    uuid = Uri.parse(reader.nextString());
                    break;
                case "name":
                    printerName = reader.nextString();
                    break;
                case "path":
                    path = Uri.parse(reader.nextString());
                    break;
                case "location":
                    location = reader.nextString();
                    break;
            }
        }
        reader.endObject();

        if (printerName == null || path == null) throw new IOException("Missing name or path");
        this.uuid = uuid;
        this.name = printerName;
        this.path = path;
        this.location = location;
    }

    /**
     * Return the best URI describing this printer: from the UUID (if present) or
     * from the path (if UUID is missing)
     */
    public Uri getUri() {
        return uuid != null ? uuid : path;
    }

    /** Return a generated printer ID based on uuid or (if uuid is missing) its path */
    public PrinterId getId(PrintService printService) {
        if (mPrinterId == null) {
            mPrinterId = printService.generatePrinterId(getUri().toString());
        }
        return mPrinterId;
    }

    /** Return a friendly description string including host and (if present) location */
    public String getDescription(Context context) {
        String host = path.getHost().replaceAll(":[0-9]+", "");
        String description;
        if (!TextUtils.isEmpty(location)) {
            description = context.getString(R.string.printer_description, host, location);
        } else {
            description = host;
        }
        return description;
    }

    /** Writes all serializable fields into JSON form */
    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("name").value(name);
        writer.name("path").value(path.toString());
        if (uuid != null) {
            writer.name("uuid").value(uuid.toString());
        }
        if (!TextUtils.isEmpty(location)) {
            writer.name("location").value(location);
        }
        writer.endObject();
    }

    /** Combine the best (longest) elements of this record and another into a merged record */
    DiscoveredPrinter bestOf(DiscoveredPrinter other) {
        return new DiscoveredPrinter(uuid, longest(name, other.name), path,
                longest(location, other.location));
    }

    private static String longest(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.length() > b.length() ? a : b;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DiscoveredPrinter)) return false;
        DiscoveredPrinter other = (DiscoveredPrinter) obj;
        return Objects.equals(uuid, other.uuid) &&
                Objects.equals(name, other.name) &&
                Objects.equals(path, other.path) &&
                Objects.equals(location, other.location);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + name.hashCode();
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        result = 31 * result + path.hashCode();
        result = 31 * result + (location != null ? location.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            write(new JsonWriter(sw));
        } catch (IOException ignored) {
        }
        return "DiscoveredPrinter" + sw.toString();
    }
}