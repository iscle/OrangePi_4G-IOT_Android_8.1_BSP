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

package android.system.helpers;

import android.content.Context;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.system.helpers.CommandsHelper;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.Assert;

/**
 * Implement common helper methods for user.
 */
public class UserHelper {
    private static final String TAG = UserHelper.class.getSimpleName();
    private static UserHelper sInstance = null;
    private Context mContext = null;

    public static final int INVALID_USER_ID = -1;


    public UserHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    public static UserHelper getInstance() {
        if (sInstance == null) {
            sInstance = new UserHelper();
        }
        return sInstance;
    }

    public UserManager getUserManager() {
        return (UserManager)mContext.getSystemService(Context.USER_SERVICE);
    }

    /**
     * Creates a test user
     * @return id for created secondary user
     */
    public int createSecondaryUser(String userName) {
        // Create user
        String cmdOut = CommandsHelper.execute("pm create-user " + userName);
        // Find user id from user-create output
        // output format : "Success: created user id 10"
        final Pattern pattern = Pattern.compile("Success: created user id (\\d+)");
        Matcher matcher = pattern.matcher(cmdOut);
        int userId = INVALID_USER_ID;
        if (matcher.find()) {
            userId = Integer.parseInt(matcher.group(1));
            Log.i(TAG, String.format("User Name:%s User ID:%d", userName, userId));
        }
        return userId;
    }

    /**
     * Returns id for first secondary user
     * @return userid
     */
    public int getSecondaryUserId() {
        String cmdOut = CommandsHelper.execute("pm list users");
        // Assume that the a user with ID 0 is a primary user. Otherwise secondary users
        final Pattern USERS_REGEX = Pattern.compile("UserInfo\\{([1-9]\\d*):[\\w\\s]+:(\\d+)\\}");
        Matcher matcher = USERS_REGEX.matcher(cmdOut);
        int userId = INVALID_USER_ID;
        if (matcher.find()) {
            userId = Integer.parseInt(matcher.group(1));    // 1 = id 2 = flag
            Log.i(TAG, String.format("The userId is %d", userId));
        }
        return userId;
    }

    public void removeSecondaryUser(int userId) {
        int prevUserCount = getUserCount();
        CommandsHelper.execute("pm remove-user " + userId);
        Assert.assertTrue("User hasn't been removed", getUserCount() == (prevUserCount - 1));
    }

    public int getUserCount() {
        return getUserManager().getUserCount();
    }
}