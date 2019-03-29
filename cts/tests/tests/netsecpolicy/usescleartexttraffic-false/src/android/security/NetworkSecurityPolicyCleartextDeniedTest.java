package android.security;

public class NetworkSecurityPolicyCleartextDeniedTest extends NetworkSecurityPolicyTestBase {

    public NetworkSecurityPolicyCleartextDeniedTest() {
        super(false // expect cleartext traffic to be blocked
                );
    }
}
