package android.security;

public class NetworkSecurityPolicyCleartextUnspecifiedTest extends NetworkSecurityPolicyTestBase {

    public NetworkSecurityPolicyCleartextUnspecifiedTest() {
        super(true // expect cleartext traffic to be permitted
                );
    }
}
