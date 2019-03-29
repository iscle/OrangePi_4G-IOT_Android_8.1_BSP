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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.example.android.wearable.wear.messaging.chatlist.ChatListActivity;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;

/**
 * Activity authenticates via Google and mocks backend model with shared preferences.
 *
 * <p>The login flow:
 *
 * <p>On sign in: Create mProfile from the user details from their google account Check if the user
 * exists in our 'backend'
 *
 * <p>If the user does not exist: Add them to our 'backend' Once a user has been established, then
 * reset the UI's state and launch the 'Main' Activity. In this case, we will go to the ChatActivity
 * and view the list of chats for the user.
 */
public class SignInActivity extends WearableActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "SignInActivity";

    /* request code for signing in with a google account */
    private static final int RC_SIGN_IN = 9001;

    private GoogleApiClient mGoogleApiClient;

    private SignInButton mSignInButton;

    private Profile mProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);
        setAmbientEnabled();

        // Configure Google Sign In
        GoogleSignInOptions.Builder builder =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail();
        GoogleSignInOptions gso = builder.build();
        mGoogleApiClient =
                new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                        .build();

        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignInButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent signInIntent =
                                Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                        startActivityForResult(signInIntent, RC_SIGN_IN);
                    }
                });
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleGoogleSigninResult(result);
        }
    }

    private void handleGoogleSigninResult(GoogleSignInResult result) {
        if (result.isSuccess()) {
            GoogleSignInAccount account = result.getSignInAccount();
            syncUserWithBackend(account);
        } else {
            // Google Sign-In failed
            Log.e(TAG, "Google Sign-In failed.");
            Toast.makeText(SignInActivity.this, R.string.google_signin_failed, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /*
     * Syncs with mock backend (shared preferences). You will want to put in your backend logic here.
     */
    private void syncUserWithBackend(GoogleSignInAccount acct) {
        Log.d(TAG, "syncUserWithBackend():" + acct.getId());

        mProfile = new Profile(acct);
        MockDatabase.getUser(this, mProfile.getId(), new SimpleRetrieveUserCallback(this));
    }

    private void setAuthFailedState() {
        if (mSignInButton != null) {
            mSignInButton.setEnabled(false);
        }
        Toast.makeText(SignInActivity.this, R.string.authentication_failed, Toast.LENGTH_SHORT)
                .show();
    }

    private void finishSuccessfulSignin() {
        if (mSignInButton != null) {
            mSignInButton.setEnabled(false);
        }

        Intent chatActivityIntent = new Intent(this, ChatListActivity.class);
        startActivity(chatActivityIntent);
        finish();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected()");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended(): connection to location client suspended: " + i);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show();
    }

    private class SimpleRetrieveUserCallback implements MockDatabase.RetrieveUserCallback {

        final Context mContext;

        private SimpleRetrieveUserCallback(Context context) {
            this.mContext = context;
        }

        @Override
        public void onUserRetrieved(Profile user) {
            if (user == null) {
                // User did not exists so create the user.
                MockDatabase.createUser(
                        mContext,
                        mProfile,
                        new MockDatabase.CreateUserCallback() {
                            @Override
                            public void onSuccess() {
                                finishSuccessfulSignin();
                            }

                            @Override
                            public void onError(Exception e) {
                                setAuthFailedState();
                            }
                        });
            } else {
                finishSuccessfulSignin();
            }
        }

        @Override
        public void error(Exception e) {
            setAuthFailedState();
        }
    }
}
