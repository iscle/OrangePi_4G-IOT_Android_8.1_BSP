package android.accounts.cts;

import android.accounts.AccountsException;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;

import junit.framework.TestCase;

public class ExceptionTest extends TestCase {

    private String message = "Message";
    private Throwable cause = new Throwable("Throwable casue");

    public void testAccountsException() {
        new AccountsException();
    }

    public void testAccountsExceptionWithMessage() {
        new AccountsException(message);
    }

    public void testAccountsExceptionWithThrowableCause() {
        new AccountsException(cause);
    }

    public void testAccountsExceptionWithMessageAndThrowableCause() {
        new AccountsException(message, cause);
    }

    public void testNetworkErrorException() {
        new NetworkErrorException();
    }

    public void testNetworkErrorExceptionWithMessage() {
        new NetworkErrorException(message);
    }

    public void testNetworkErrorExceptionWithThrowableCause() {
        new NetworkErrorException(cause);
    }

    public void testNetworkErrorExceptionWithMessageAndThrowableCause() {
        new NetworkErrorException(message, cause);
    }

    public void testAuthenticatorException() {
        new AuthenticatorException();
    }

    public void testAuthenticatorExceptionWithMessage() {
        new AuthenticatorException(message);
    }

    public void testAuthenticatorExceptionWithThrowableCause() {
        new AuthenticatorException(cause);
    }

    public void testAuthenticatorExceptionWithMessageAndThrowableCause() {
        new AuthenticatorException(message, cause);
    }

    public void testOperationCanceledException() {
        new OperationCanceledException();
    }

    public void testOperationCanceledExceptionWithMessage() {
        new OperationCanceledException(message);
    }

    public void testOperationCanceledExceptionWithThrowableCause() {
        new OperationCanceledException(cause);
    }

    public void testOperationCanceledExceptionWithMessageAndThrowableCause() {
        new OperationCanceledException(message, cause);
    }
}
