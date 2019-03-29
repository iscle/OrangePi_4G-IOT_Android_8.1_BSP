/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.verifier.notifications;

import com.android.cts.verifier.R;

import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConditionProviderVerifierActivity extends InteractiveVerifierActivity
        implements Runnable {
    protected static final String CP_PACKAGE = "com.android.cts.verifier";
    protected static final String CP_PATH = CP_PACKAGE +
            "/com.android.cts.verifier.notifications.MockConditionProvider";

    @Override
    int getTitleResource() {
        return R.string.cp_test;
    }

    @Override
    int getInstructionsResource() {
        return R.string.cp_info;
    }

    // Test Setup

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        List<InteractiveTestCase> tests = new ArrayList<>(9);
        tests.add(new IsEnabledTest());
        tests.add(new ServiceStartedTest());
        tests.add(new CreateAutomaticZenRuleTest());
        tests.add(new UpdateAutomaticZenRuleTest());
        tests.add(new GetAutomaticZenRuleTest());
        tests.add(new GetAutomaticZenRulesTest());
        tests.add(new SubscribeAutomaticZenRuleTest());
        tests.add(new DeleteAutomaticZenRuleTest());
        tests.add(new UnsubscribeAutomaticZenRuleTest());
        tests.add(new IsDisabledTest());
        tests.add(new ServiceStoppedTest());
        return tests;
    }

    protected class IsEnabledTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createSettingsItem(parent, R.string.cp_enable_service);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        void test() {
            Intent settings = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            if (settings.resolveActivity(mPackageManager) == null) {
                logFail("no settings activity");
                status = FAIL;
            } else {
                if (mNm.isNotificationPolicyAccessGranted()) {
                    status = PASS;
                } else {
                    status = WAIT_FOR_USER;
                }
                next();
            }
        }

        void tearDown() {
            // wait for the service to start
            delay();
        }
    }

    protected class ServiceStartedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_service_started);
        }

        @Override
        void test() {
            MockConditionProvider.probeConnected(mContext,
                    new MockConditionProvider.BooleanResultCatcher() {
                        @Override
                        public void accept(boolean result) {
                            if (result) {
                                status = PASS;
                            } else {
                                logFail();
                                status = RETEST;
                                delay();
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class CreateAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_create_rule);
        }

        @Override
        void test() {
            long now = System.currentTimeMillis();
            AutomaticZenRule ruleToCreate =
                    createRule("Rule", "value", NotificationManager.INTERRUPTION_FILTER_ALARMS);
            id = mNm.addAutomaticZenRule(ruleToCreate);

            if (!TextUtils.isEmpty(id)) {
                status = PASS;
            } else {
                logFail();
                status = FAIL;
            }
            next();
        }

        @Override
        void tearDown() {
            if (id != null) {
                mNm.removeAutomaticZenRule(id);
            }
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class UpdateAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_update_rule);
        }

        @Override
        void setUp() {
            id = mNm.addAutomaticZenRule(createRule("BeforeUpdate", "beforeValue",
                    NotificationManager.INTERRUPTION_FILTER_ALARMS));
            status = READY;
            delay();
        }

        @Override
        void test() {
            AutomaticZenRule updated = mNm.getAutomaticZenRule(id);
            updated.setName("AfterUpdate");
            updated.setConditionId(MockConditionProvider.toConditionId("afterValue"));
            updated.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);

            try {
                boolean success = mNm.updateAutomaticZenRule(id, updated);
                if (success && updated.equals(mNm.getAutomaticZenRule(id))) {
                    status = PASS;
                } else {
                    logFail();
                    status = FAIL;
                }
            } catch (Exception e) {
                logFail("update failed", e);
                status = FAIL;
            }
            next();
        }

        @Override
        void tearDown() {
            if (id != null) {
                mNm.removeAutomaticZenRule(id);
            }
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class SubscribeAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;
        private AutomaticZenRule ruleToCreate;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_subscribe_rule);
        }

        @Override
        void setUp() {
            ruleToCreate = createRule("RuleSubscribe", "Subscribevalue",
                    NotificationManager.INTERRUPTION_FILTER_ALARMS);
            id = mNm.addAutomaticZenRule(ruleToCreate);
            status = READY;
            delay();
        }

        @Override
        void test() {

            MockConditionProvider.probeSubscribe(mContext,
                    new MockConditionProvider.ParcelableListResultCatcher() {
                        @Override
                        public void accept(List<Parcelable> result) {
                            boolean foundMatch = false;
                            for (Parcelable p : result) {
                                Uri uri = (Uri) p;
                                if (ruleToCreate.getConditionId().equals(uri)) {
                                    foundMatch = true;
                                    break;
                                }
                            }
                            if (foundMatch) {
                                status = PASS;
                            } else {
                                logFail();
                                status = RETEST;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            if (id != null) {
                mNm.removeAutomaticZenRule(id);
            }
            MockConditionProvider.resetData(mContext);
            // wait for intent to move through the system
            delay();
        }
    }

    private class GetAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;
        private AutomaticZenRule ruleToCreate;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_get_rule);
        }

        @Override
        void setUp() {
            ruleToCreate = createRule("RuleGet", "valueGet",
                    NotificationManager.INTERRUPTION_FILTER_ALARMS);
            id = mNm.addAutomaticZenRule(ruleToCreate);
            status = READY;
            delay();
        }

        @Override
        void test() {
            AutomaticZenRule queriedRule = mNm.getAutomaticZenRule(id);
            if (queriedRule != null
                    && ruleToCreate.getName().equals(queriedRule.getName())
                    && ruleToCreate.getOwner().equals(queriedRule.getOwner())
                    && ruleToCreate.getConditionId().equals(queriedRule.getConditionId())
                    && ruleToCreate.isEnabled() == queriedRule.isEnabled()) {
                status = PASS;
            } else {
                logFail();
                status = FAIL;
            }
            next();
        }

        @Override
        void tearDown() {
            if (id != null) {
                mNm.removeAutomaticZenRule(id);
            }
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class GetAutomaticZenRulesTest extends InteractiveTestCase {
        private List<String> ids = new ArrayList<>();
        private AutomaticZenRule rule1;
        private AutomaticZenRule rule2;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_get_rules);
        }

        @Override
        void setUp() {
            rule1 = createRule("Rule1", "value1", NotificationManager.INTERRUPTION_FILTER_ALARMS);
            rule2 = createRule("Rule2", "value2", NotificationManager.INTERRUPTION_FILTER_NONE);
            ids.add(mNm.addAutomaticZenRule(rule1));
            ids.add(mNm.addAutomaticZenRule(rule2));
            status = READY;
            delay();
        }

        @Override
        void test() {
            Map<String, AutomaticZenRule> rules = mNm.getAutomaticZenRules();

            if (rules == null || rules.size() != 2) {
                logFail();
                status = FAIL;
                next();
                return;
            }

            for (AutomaticZenRule createdRule : rules.values()) {
                if (!compareRules(createdRule, rule1) && !compareRules(createdRule, rule2)) {
                    logFail();
                    status = FAIL;
                    break;
                }
            }
            status = PASS;
            next();
        }

        @Override
        void tearDown() {
            for (String id : ids) {
                mNm.removeAutomaticZenRule(id);
            }
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class DeleteAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_delete_rule);
        }

        @Override
        void test() {
            AutomaticZenRule ruleToCreate = createRule("RuleDelete", "Deletevalue",
                    NotificationManager.INTERRUPTION_FILTER_ALARMS);
            id = mNm.addAutomaticZenRule(ruleToCreate);

            if (id != null) {
                if (mNm.removeAutomaticZenRule(id)) {
                    if (mNm.getAutomaticZenRule(id) == null) {
                        status = PASS;
                    } else {
                        logFail();
                        status = FAIL;
                    }
                } else {
                    logFail();
                    status = FAIL;
                }
            } else {
                logFail("Couldn't test rule deletion; creation failed.");
                status = FAIL;
            }
            next();
        }

        @Override
        void tearDown() {
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class UnsubscribeAutomaticZenRuleTest extends InteractiveTestCase {
        private String id = null;
        private AutomaticZenRule ruleToCreate;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_unsubscribe_rule);
        }

        @Override
        void setUp() {
            ruleToCreate = createRule("RuleUnsubscribe", "valueUnsubscribe",
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            id = mNm.addAutomaticZenRule(ruleToCreate);
            status = READY;
            delay();
        }

        @Override
        void test() {
            MockConditionProvider.probeSubscribe(mContext,
                    new MockConditionProvider.ParcelableListResultCatcher() {
                        @Override
                        public void accept(List<Parcelable> result) {
                            boolean foundMatch = false;
                            for (Parcelable p : result) {
                                Uri uri = (Uri) p;
                                if (ruleToCreate.getConditionId().equals(uri)) {
                                    foundMatch = true;
                                    break;
                                }
                            }
                            if (foundMatch) {
                                // Now that it's subscribed, remove the rule and verify that it
                                // unsubscribes.
                                mNm.removeAutomaticZenRule(id);
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    logFail("unexpected InterruptedException");
                                }
                                MockConditionProvider.probeSubscribe(mContext,
                                        new MockConditionProvider.ParcelableListResultCatcher() {
                                            @Override
                                            public void accept(List<Parcelable> result) {
                                                boolean foundMatch = false;
                                                for (Parcelable p : result) {
                                                    Uri uri = (Uri) p;
                                                    if (ruleToCreate.getConditionId().equals(uri)) {
                                                        foundMatch = true;
                                                        break;
                                                    }
                                                }
                                                if (foundMatch) {
                                                    logFail();
                                                    status = RETEST;
                                                } else {
                                                    status = PASS;
                                                }
                                                next();
                                            }
                                        });
                            } else {
                                logFail("Couldn't test unsubscribe; subscribe failed.");
                                status = RETEST;
                                next();
                            }
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.removeAutomaticZenRule(id);
            MockConditionProvider.resetData(mContext);
            // wait for intent to move through the system
            delay();
        }
    }

    private class IsDisabledTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createSettingsItem(parent, R.string.cp_disable_service);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        void test() {
            if (!mNm.isNotificationPolicyAccessGranted()) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }
            next();
        }

        @Override
        void tearDown() {
            MockConditionProvider.resetData(mContext);
            delay();
        }
    }

    private class ServiceStoppedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.cp_service_stopped);
        }

        @Override
        void test() {
            MockConditionProvider.probeConnected(mContext,
                    new MockConditionProvider.BooleanResultCatcher() {
                        @Override
                        public void accept(boolean result) {
                            if (result) {
                                logFail();
                                status = RETEST;
                                delay();
                            } else {
                                status = PASS;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockConditionProvider.resetData(mContext);
            // wait for intent to move through the system
            delay();
        }
    }

    private AutomaticZenRule createRule(String name, String queryValue, int status) {
        return new AutomaticZenRule(name,
                ComponentName.unflattenFromString(CP_PATH),
                MockConditionProvider.toConditionId(queryValue),
                status,
                true);
    }

    private boolean compareRules(AutomaticZenRule rule1, AutomaticZenRule rule2) {
        return rule1.isEnabled() == rule2.isEnabled()
                && Objects.equals(rule1.getName(), rule2.getName())
                && rule1.getInterruptionFilter() == rule2.getInterruptionFilter()
                && Objects.equals(rule1.getConditionId(), rule2.getConditionId())
                && Objects.equals(rule1.getOwner(), rule2.getOwner());
    }

    protected View createSettingsItem(ViewGroup parent, int messageId) {
        return createUserItem(parent, R.string.cp_start_settings, messageId);
    }

    public void launchSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
    }

    public void actionPressed(View v) {
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            int id = ((Integer) tag).intValue();
            if (id == R.string.cp_start_settings) {
                launchSettings();
            } else if (id == R.string.attention_ready) {
                mCurrentTest.status = READY;
                next();
            }
        }
    }
}
