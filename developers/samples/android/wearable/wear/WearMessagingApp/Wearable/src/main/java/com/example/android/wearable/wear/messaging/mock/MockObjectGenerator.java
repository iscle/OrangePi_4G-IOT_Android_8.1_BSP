/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.messaging.mock;

import android.support.annotation.NonNull;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.model.Profile;
import java.util.Arrays;
import java.util.List;

/** Helper methods to generate mock objects. */
public class MockObjectGenerator {

    /**
     * Returns a list of mocked contacts.
     *
     * @return a {@link List<Profile>} that can be used to mock out a user's contact list.
     */
    public static List<Profile> generateDefaultContacts() {

        Profile paul = buildProfile("1234", "Paul Saxman", "PaulSaxman@email.com", R.drawable.paul);

        Profile ben =
                buildProfile("2345", "Benjamin Baxter", "benjaminbaxter@email.com", R.drawable.ben);

        Profile jeremy =
                buildProfile("3456", "Jeremy Walker", "jeremywalker@email.com", R.drawable.jeremy);

        Profile jennifer =
                buildProfile(
                        "4567", "Jennifer Smith", "jennifersmith@email.com", R.drawable.jennifer);

        Profile android =
                buildProfile("5678", "Android", "android@email.com", R.drawable.android_logo);

        Profile lisa =
                buildProfile("6789", "Lisa Williams", "lisawilliams@email.com", R.drawable.lisa);

        Profile jane = buildProfile("7890", "Jane Doe", "janedoe@email.com", R.drawable.jane);

        return Arrays.asList(paul, jennifer, ben, lisa, jane, jeremy, android);
    }

    @NonNull
    private static Profile buildProfile(String id, String name, String email, int profileResource) {
        return new Profile.Builder()
                .id(id)
                .name(name)
                .email(email)
                .profileImageResource(profileResource)
                .lastUpdatedTime(System.currentTimeMillis())
                .build();
    }
}
