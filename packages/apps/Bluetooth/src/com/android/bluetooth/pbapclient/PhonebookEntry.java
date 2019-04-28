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
package com.android.bluetooth.pbapclient;

import com.android.vcard.VCardEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 *  A simpler more public version of VCardEntry.
 */
public class PhonebookEntry {
    public static class Name {
        public String family;
        public String given;
        public String middle;
        public String prefix;
        public String suffix;

        public Name() { }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Name)) {
                return false;
            }

            Name n = ((Name) o);
            return (family == n.family || family != null && family.equals(n.family)) &&
                    (given == n.given ||  given != null && given.equals(n.given)) &&
                    (middle == n.middle || middle != null && middle.equals(n.middle)) &&
                    (prefix == n.prefix || prefix != null && prefix.equals(n.prefix)) &&
                    (suffix == n.suffix || suffix != null && suffix.equals(n.suffix));
        }

        @Override
        public int hashCode() {
            int result = 23 * (family == null ? 0 : family.hashCode());
            result = 23 * result + (given == null ? 0 : given.hashCode());
            result = 23 * result + (middle == null ? 0 : middle.hashCode());
            result = 23 * result + (prefix == null ? 0 : prefix.hashCode());
            result = 23 * result + (suffix == null ? 0 : suffix.hashCode());
            return result;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Name: { family: ");
            sb.append(family);
            sb.append(" given: ");
            sb.append(given);
            sb.append(" middle: ");
            sb.append(middle);
            sb.append(" prefix: ");
            sb.append(prefix);
            sb.append(" suffix: ");
            sb.append(suffix);
            sb.append(" }");
            return sb.toString();
        }
    }

    public static class Phone {
        public int type;
        public String number;

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Phone)) {
                return false;
            }

            Phone p = (Phone) o;
            return (number == p.number || number != null && number.equals(p.number))
                    && type == p.type;
        }

        @Override
        public int hashCode() {
            return 23 * type + number.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(" Phone: { number: ");
            sb.append(number);
            sb.append(" type: " + type);
            sb.append(" }");
            return sb.toString();
        }
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof PhonebookEntry) {
            return equals((PhonebookEntry) object);
        }
        return false;
    }

    public PhonebookEntry() {
        name = new Name();
        phones = new ArrayList<Phone>();
    }

    public PhonebookEntry(VCardEntry v) {
        name = new Name();
        phones = new ArrayList<Phone>();

        VCardEntry.NameData n = v.getNameData();
        name.family = n.getFamily();
        name.given = n.getGiven();
        name.middle = n.getMiddle();
        name.prefix = n.getPrefix();
        name.suffix = n.getSuffix();

        List<VCardEntry.PhoneData> vp = v.getPhoneList();
        if (vp == null || vp.isEmpty()) {
            return;
        }

        for (VCardEntry.PhoneData p : vp) {
            Phone phone = new Phone();
            phone.type = p.getType();
            phone.number = p.getNumber();
            phones.add(phone);
        }
    }

    private boolean equals(PhonebookEntry p) {
        return name.equals(p.name) && phones.equals(p.phones);
    }

    @Override
    public int hashCode() {
        return name.hashCode() + 23 * phones.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PhonebookEntry { id: ");
        sb.append(id);
        sb.append(" ");
        sb.append(name.toString());
        sb.append(phones.toString());
        sb.append(" }");
        return sb.toString();
    }

    public Name name;
    public List<Phone> phones;
    public String id;
}
