This package helps creating accounts with DPM.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED / DISALLOWED.


Note: AccountCheckHostSideTest should pass even with a pre-existing ALLOWED account.  Meaning, even
after you followed the below steps to add an ALLOWED account, AccountCheckHostSideTest should
still pass.

- Build
$ mmma -j cts/hostsidetests/devicepolicy/app/AccountCheck/Tester/

- Install
$ adb install  -r -g  ${ANDROID_PRODUCT_OUT}/data/app/CtsAccountCheckAuthAppTester/CtsAccountCheckAuthAppTester.apk


- Add an account with DEVICE_OR_PROFILE_OWNER_ALLOWED.
adb shell am startservice -a add_account \
    --esa features android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED \
    com.android.cts.devicepolicy.accountcheck.tester/.TestAuthenticator

- Add an account with DEVICE_OR_PROFILE_OWNER_DISALLOWED.
adb shell am startservice -a add_account \
    --esa features android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED \
    com.android.cts.devicepolicy.accountcheck.tester/.TestAuthenticator

- Verify
$ dumpsys-account
User UserInfo{0:Owner:13}:
  Accounts: 1
    Account {name=8894956487610:android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED, type=com.android.cts.devicepolicy.authcheck.tester}


