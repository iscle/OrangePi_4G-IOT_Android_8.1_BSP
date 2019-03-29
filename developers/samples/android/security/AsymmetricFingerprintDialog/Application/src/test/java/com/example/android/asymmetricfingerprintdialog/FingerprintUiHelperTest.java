package com.example.android.asymmetricfingerprintdialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FingerprintUiHelper}.
 */
// Hide lint warnings about missing USE_FINGERPRINT permission that is granted at app install time.
@SuppressWarnings("MissingPermission")
@RunWith(MockitoJUnitRunner.class)
public class FingerprintUiHelperTest {

    private static final int ERROR_MSG_ID = 1;
    private static final CharSequence ERR_STRING = "ERROR_STRING";
    private static final int HINT_COLOR = 10;

    @Mock private FingerprintManager mockFingerprintManager;
    @Mock private ImageView mockIcon;
    @Mock private TextView mockTextView;
    @Mock private FingerprintUiHelper.Callback mockCallback;
    @Mock private FingerprintManager.CryptoObject mockCryptoObject;
    @Mock private Resources mockResources;

    @Captor private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;

    @InjectMocks private FingerprintUiHelper.FingerprintUiHelperBuilder mockBuilder;

    private FingerprintUiHelper mFingerprintUiHelper;

    @Before
    public void setUp() {
        mFingerprintUiHelper = mockBuilder.build(mockIcon, mockTextView, mockCallback);

        when(mockFingerprintManager.isHardwareDetected()).thenReturn(true);
        when(mockFingerprintManager.hasEnrolledFingerprints()).thenReturn(true);
        when(mockTextView.getResources()).thenReturn(mockResources);
        when(mockResources.getColor(R.color.hint_color, null)).thenReturn(HINT_COLOR);
    }

    @Test
    public void testStartListening_fingerprintAuthAvailable() {
        mFingerprintUiHelper.startListening(mockCryptoObject);

        verify(mockFingerprintManager).authenticate(eq(mockCryptoObject),
                isA(CancellationSignal.class), eq(0), eq(mFingerprintUiHelper),
                any(Handler.class));
        verify(mockIcon).setImageResource(R.drawable.ic_fp_40px);
    }

    @Test
    public void testStartListening_fingerprintAuthNotAvailable() {
        when(mockFingerprintManager.isHardwareDetected()).thenReturn(false);

        mFingerprintUiHelper.startListening(mockCryptoObject);

        verify(mockFingerprintManager, never()).authenticate(
                any(FingerprintManager.CryptoObject.class), any(CancellationSignal.class), eq(0),
                any(FingerprintUiHelper.class), any(Handler.class));
    }

    @Test
    public void testOnAuthenticationError() {
        mFingerprintUiHelper.mSelfCancelled = false;

        mFingerprintUiHelper.onAuthenticationError(ERROR_MSG_ID, ERR_STRING);

        verify(mockIcon).setImageResource(R.drawable.ic_fingerprint_error);
        verify(mockTextView).removeCallbacks(mFingerprintUiHelper.mResetErrorTextRunnable);
        verify(mockTextView).postDelayed(mFingerprintUiHelper.mResetErrorTextRunnable,
                FingerprintUiHelper.ERROR_TIMEOUT_MILLIS);
        verify(mockIcon).postDelayed(mRunnableArgumentCaptor.capture(),
                eq(FingerprintUiHelper.ERROR_TIMEOUT_MILLIS));

        mRunnableArgumentCaptor.getValue().run();

        verify(mockCallback).onError();
    }

    @Test
    public void testOnAuthenticationSucceeded() {
        mFingerprintUiHelper.onAuthenticationSucceeded(null);

        verify(mockIcon).setImageResource(R.drawable.ic_fingerprint_success);
        verify(mockTextView).removeCallbacks(mFingerprintUiHelper.mResetErrorTextRunnable);
        verify(mockIcon).postDelayed(mRunnableArgumentCaptor.capture(),
                eq(FingerprintUiHelper.SUCCESS_DELAY_MILLIS));

        mRunnableArgumentCaptor.getValue().run();

        verify(mockCallback).onAuthenticated();
    }
}
