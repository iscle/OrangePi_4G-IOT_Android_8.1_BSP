package android.signature.cts;

/**
 * Define the type of the signature check failures.
 */
public enum FailureType {
    MISSING_CLASS,
    MISSING_INTERFACE,
    MISSING_METHOD,
    MISSING_FIELD,
    MISMATCH_CLASS,
    MISMATCH_INTERFACE,
    MISMATCH_INTERFACE_METHOD,
    MISMATCH_METHOD,
    MISMATCH_FIELD,
    UNEXPECTED_CLASS,
    CAUGHT_EXCEPTION,
}
