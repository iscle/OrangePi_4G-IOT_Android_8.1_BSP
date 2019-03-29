package android.security;

public class NetworkSecurityPolicyCleartextPermittedTest extends NetworkSecurityPolicyTestBase {

    public NetworkSecurityPolicyCleartextPermittedTest() {
        super(true // expect cleartext traffic to be permitted
                );
    }
}
