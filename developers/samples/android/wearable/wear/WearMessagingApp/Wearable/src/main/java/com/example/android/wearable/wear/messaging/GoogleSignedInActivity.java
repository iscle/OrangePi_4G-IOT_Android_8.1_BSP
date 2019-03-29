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
package com.example.android.wearable.wear.messaging;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.SharedPreferencesHelper;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;

/**
 * This activity should be extended for any activity that requires an authenticated user. This
 * activity handles the signin flow with Google signin.
 *
 * <p>When the activity starts, it will silently try to verify that the user is valid. If a user is
 * not signed in, it will redirect to a SignInActivity.
 *
 * <p>It also provides a hook for any sub class to get a reference to the user object {@link
 * #getUser()}
 */
public abstract class GoogleSignedInActivity extends WearableActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "GoogleSignedInActivity";

    protected GoogleApiClient mGoogleApiClient;
    protected GoogleSignInAccount mGoogleSignInAccount;
    private String mUserIdToken;
    private Profile mUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAmbientEnabled();

        // Try to get the user if they don't exist, return to sign in.
        mUser = SharedPreferencesHelper.readUserFromJsonPref(this);
        if (mUser == null) {
            Log.e(TAG, "User is not stored locally");
            onGoogleSignInFailure();
        }

        setupGoogleApiClient();
    }

    /* gives a handle to the user object for the sub-activities */
    protected Profile getUser() {
        return mUser;
    }

    /** Configures the GoogleApiClient used for sign in. Requests scopes profile and email. */
    protected void setupGoogleApiClient() {
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestProfile()
                        .requestEmail()
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .build();

        mGoogleApiClient =
                new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                        .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected(): refreshing sign in");
        refreshSignIn();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended(): connection to location client suspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection Failed.");
    }

    /**
     * Handles sign in result and gets the UserIdToken.
     *
     * @param result sign in result
     */
    protected void handleSignInResult(GoogleSignInResult result) {
        if (result != null && result.isSuccess()) {
            mGoogleSignInAccount = result.getSignInAccount();
            if (mGoogleSignInAccount != null) {
                mUserIdToken = mGoogleSignInAccount.getIdToken();
                Log.d(TAG, "Google sign in success " + mUserIdToken);
            }
        } else if (result != null && !result.isSuccess()) {
            Log.d(TAG, "Google sign in failure: " + result.getStatus());
            onGoogleSignInFailure();
        } else {
            Log.d(TAG, "Google sign in result is null");
            onGoogleSignInFailure();
        }
    }

    protected void onGoogleSignInFailure() {
        // If sign in fails, ask them to sign in
        Intent signinIntent = new Intent(this, SignInActivity.class);
        startActivity(signinIntent);
    }

    /** Silently signs in. */
    private void refreshSignIn() {
        OptionalPendingResult<GoogleSignInResult> pendingResult =
                Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (pendingResult.isDone()) {
            handleSignInResult(pendingResult.get());
        } else {
            pendingResult.setResultCallback(
                    new ResultCallback<GoogleSignInResult>() {
                        @Override
                        public void onResult(@NonNull GoogleSignInResult googleSignInResult) {
                            handleSignInResult(googleSignInResult);
                        }
                    });
        }
    }
}
