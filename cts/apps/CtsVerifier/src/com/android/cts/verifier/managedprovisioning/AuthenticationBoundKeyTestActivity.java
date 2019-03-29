package com.android.cts.verifier.managedprovisioning;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * Test device credential-bound keys in work profile.
 * Currently there are two types, one is keys bound to lockscreen passwords which can be configured
 * to remain available within a certain timeout after the latest successful user authentication.
 * The other is keys bound to fingerprint authentication which require explicit fingerprint
 * authentication before they can be accessed.
 */
public class AuthenticationBoundKeyTestActivity extends DialogTestListActivity {

    public static final String ACTION_AUTH_BOUND_KEY_TEST =
            "com.android.cts.verifier.managedprovisioning.action.AUTH_BOUND_KEY_TEST";

    private static final int AUTHENTICATION_DURATION_SECONDS = 5;
    private static final String LOCKSCREEN_KEY_NAME = "mp_lockscreen_key";
    private static final String FINGERPRINT_KEY_NAME = "mp_fingerprint_key";
    private static final byte[] SECRET_BYTE_ARRAY = new byte[] {1, 2, 3, 4, 5, 6};
    private static final int CONFIRM_CREDENTIALS_REQUEST_CODE = 1;
    private static final int FINGERPRINT_PERMISSION_REQUEST_CODE = 0;

    private static final int LOCKSCREEN = 1;
    private static final int FINGERPRINT = 2;

    private static final String KEYSTORE_NAME = "AndroidKeyStore";
    private static final String CIPHER_TRANSFORMATION =  KeyProperties.KEY_ALGORITHM_AES + "/"
            + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7;


    private KeyguardManager mKeyguardManager;
    private FingerprintManager mFingerprintManager;
    private boolean mFingerprintSupported;

    private DialogTestListItem mLockScreenBoundKeyTest;
    private DialogTestListItem mFingerprintBoundKeyTest;

    private Cipher mFingerprintCipher;

    public AuthenticationBoundKeyTestActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod_auth_bound_key,
                R.string.provisioning_byod_auth_bound_key_info,
                R.string.provisioning_byod_auth_bound_key_instruction);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        mFingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
        mFingerprintSupported = mFingerprintManager != null
                && mFingerprintManager.isHardwareDetected();
        // Need to have valid mFingerprintSupported value before calling super.onCreate() because
        // mFingerprintSupported is used in setupTests() which gets called by super.onCreate().
        super.onCreate(savedInstanceState);

        mPrepareTestButton.setText(R.string.provisioning_byod_auth_bound_key_set_up);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
        });
        if (mFingerprintSupported) {
            requestPermissions(new String[] {Manifest.permission.USE_FINGERPRINT},
                    FINGERPRINT_PERMISSION_REQUEST_CODE);
        }
    }

    private class LockscreenCountDownTester extends CountDownTimer {

        private Toast mToast;

        public LockscreenCountDownTester() {
            // Wait for AUTHENTICATION_DURATION_SECONDS so the key is evicted before the real test.
            super(AUTHENTICATION_DURATION_SECONDS * 1000, 1000);
            mToast = Toast.makeText(AuthenticationBoundKeyTestActivity.this, "", Toast.LENGTH_SHORT);
        }

        @Override
        public void onFinish() {
            mToast.cancel();
            if (tryEncryptWithLockscreenKey()) {
                showToast("Test failed. Key accessible without auth.");
                setTestResult(mLockScreenBoundKeyTest, TestResult.TEST_RESULT_FAILED);
            } else {
                // Start the Confirm Credentials screen.
                Intent intent = mKeyguardManager.createConfirmDeviceCredentialIntent(null, null);
                if (intent != null) {
                    startActivityForResult(intent, CONFIRM_CREDENTIALS_REQUEST_CODE);
                } else {
                    showToast("Test failed. No lockscreen password exists.");
                    setTestResult(mLockScreenBoundKeyTest, TestResult.TEST_RESULT_FAILED);
                }
            }
        }

        @Override
        public void onTick(long millisUntilFinished) {
            mToast.setText(String.format("Lockscreen challenge start in %d seconds..",
                    millisUntilFinished / 1000));
            mToast.show();
        }
    }


    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        mLockScreenBoundKeyTest = new DialogTestListItem(this,
                R.string.provisioning_byod_lockscreen_bound_key,
                "BYOD_LockScreenBoundKeyTest") {

            @Override
            public void performTest(DialogTestListActivity activity) {
                if (checkPreconditions()) {
                    createKey(LOCKSCREEN);
                    new LockscreenCountDownTester().start();
                }
            }
        };
        adapter.add(mLockScreenBoundKeyTest);
        if (mFingerprintSupported) {
            AuthenticationBoundKeyTestActivity that = this;
            mFingerprintBoundKeyTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_fingerprint_bound_key,
                    "BYOD_FingerprintBoundKeyTest") {

                @Override
                public void performTest(DialogTestListActivity activity) {
                    if (checkPreconditions()) {
                        createKey(FINGERPRINT);
                        mFingerprintCipher = initFingerprintEncryptionCipher();
                        if (tryEncryptWithFingerprintKey(mFingerprintCipher)) {
                            showToast("Test failed. Key accessible without auth.");
                            setTestResult(mFingerprintBoundKeyTest, TestResult.TEST_RESULT_FAILED);
                        } else {
                            FingerprintAuthDialogFragment fadf =
                                    new FingerprintAuthDialogFragment();
                            fadf.setActivity(that);
                            fadf.show(getFragmentManager(),"fingerprint_dialog");
                        }
                    }
                }
            };
            adapter.add(mFingerprintBoundKeyTest);
        }
    }

    private boolean checkPreconditions() {
        if (!mKeyguardManager.isKeyguardSecure()) {
            showToast("Please set a lockscreen password.");
            return false;
        } else if (mFingerprintSupported && !mFingerprintManager.hasEnrolledFingerprints()) {
            showToast("Please enroll a fingerprint.");
            return false;
        } else {
            return true;
        }
    }

    private String getKeyName(int testType) {
        return testType == LOCKSCREEN ? LOCKSCREEN_KEY_NAME : FINGERPRINT_KEY_NAME;
    }
    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with device credentials.
     */
    private void createKey(int testType) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
            keyStore.load(null);
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            KeyGenParameterSpec.Builder builder;
            builder = new KeyGenParameterSpec.Builder(getKeyName(testType),
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            if (testType == LOCKSCREEN) {
                // Require that the user unlocked the lockscreen in the last 5 seconds
                builder.setUserAuthenticationValidityDurationSeconds(
                        AUTHENTICATION_DURATION_SECONDS);
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME);
            keyGenerator.init(builder.build());
            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException | KeyStoreException
                | CertificateException | IOException e) {
            throw new RuntimeException("Failed to create a symmetric key", e);
        }
    }

    private SecretKey loadSecretKey(int testType) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_NAME);
            keyStore.load(null);
            return (SecretKey) keyStore.getKey(getKeyName(testType), null);
        } catch (UnrecoverableKeyException  | CertificateException |KeyStoreException | IOException
                | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to load a symmetric key", e);
        }
    }

    private boolean tryEncryptWithLockscreenKey() {
        try {
            // Try encrypting something, it will only work if the user authenticated within
            // the last AUTHENTICATION_DURATION_SECONDS seconds.
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadSecretKey(LOCKSCREEN));
            cipher.doFinal(SECRET_BYTE_ARRAY);
            return true;
        } catch (UserNotAuthenticatedException e) {
            // User is not authenticated, let's authenticate with device credentials.
            return false;
        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated.
            createKey(LOCKSCREEN);
            showToast("Set up lockscreen after test ran. Retry the test.");
            return false;
        } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException
                | NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Encrypt with lockscreen-bound key failed", e);
        }
    }

    private Cipher initFingerprintEncryptionCipher() {
        try {
            Cipher cipher =  Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadSecretKey(FINGERPRINT));
            return cipher;
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            return null;
        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            createKey(FINGERPRINT);
            showToast("Set up lockscreen after test ran. Retry the test.");
            return null;
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Init cipher with fingerprint-bound key failed", e);
        }
    }

    private boolean tryEncryptWithFingerprintKey(Cipher cipher) {

        try {
            cipher.doFinal(SECRET_BYTE_ARRAY);
            return true;
        } catch (IllegalBlockSizeException e) {
            // Cannot encrypt, key is unavailable
            return false;
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONFIRM_CREDENTIALS_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    if (tryEncryptWithLockscreenKey()) {
                        setTestResult(mLockScreenBoundKeyTest, TestResult.TEST_RESULT_PASSED);
                    } else {
                        showToast("Test failed. Key not accessible after auth");
                        setTestResult(mLockScreenBoundKeyTest, TestResult.TEST_RESULT_FAILED);
                    }
                } else {
                    showToast("Lockscreen challenge canceled.");
                    setTestResult(mLockScreenBoundKeyTest, TestResult.TEST_RESULT_FAILED);
                }
                break;
            default:
                super.handleActivityResult(requestCode, resultCode, data);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    static public class FingerprintAuthDialogFragment extends DialogFragment {

        private AuthenticationBoundKeyTestActivity mActivity;
        private CancellationSignal mCancellationSignal;
        private FingerprintManager mFingerprintManager;
        private FingerprintManagerCallback mFingerprintManagerCallback;
        private boolean mSelfCancelled;

        class FingerprintManagerCallback extends FingerprintManager.AuthenticationCallback {
            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {
                if (!mSelfCancelled) {
                    showToast(errString.toString());
                }
            }

            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                showToast(helpString.toString());
            }

            @Override
            public void onAuthenticationFailed() {
                showToast(getString(R.string.sec_fp_auth_failed));
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                if (mActivity.tryEncryptWithFingerprintKey(mActivity.mFingerprintCipher)) {
                    showToast("Test passed.");
                    mActivity.setTestResult(mActivity.mFingerprintBoundKeyTest,
                            TestResult.TEST_RESULT_PASSED);
                } else {
                    showToast("Test failed. Key not accessible after auth");
                    mActivity.setTestResult(mActivity.mFingerprintBoundKeyTest,
                            TestResult.TEST_RESULT_FAILED);
                }
                FingerprintAuthDialogFragment.this.dismiss();
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            mCancellationSignal.cancel();
            mSelfCancelled = true;
        }

        private void setActivity(AuthenticationBoundKeyTestActivity activity) {
            mActivity = activity;
        }

        private void showToast(String message) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG)
                .show();
        }


        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            mCancellationSignal = new CancellationSignal();
            mSelfCancelled = false;
            mFingerprintManager =
                    (FingerprintManager) getContext().getSystemService(Context.FINGERPRINT_SERVICE);
            mFingerprintManagerCallback = new FingerprintManagerCallback();
            mFingerprintManager.authenticate(
                    new FingerprintManager.CryptoObject(mActivity.mFingerprintCipher),
                    mCancellationSignal, 0, mFingerprintManagerCallback, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.sec_fp_dialog_message);
            return builder.create();
        }

    }

}
