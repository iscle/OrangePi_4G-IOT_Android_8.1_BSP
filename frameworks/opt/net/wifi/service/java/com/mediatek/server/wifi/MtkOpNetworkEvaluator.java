package com.mediatek.server.wifi;

import android.annotation.Nullable;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.util.Log;
import android.util.Pair;

import com.android.server.wifi.*;
import com.android.server.wifi.util.TelephonyUtil;

import com.mediatek.common.wifi.IWifiFwkExt;
import com.mediatek.server.wifi.MtkOpFwkExtManager.AutoConnectManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MtkOpNetworkEvaluator implements WifiNetworkSelector.NetworkEvaluator {
    private static final String NAME = "MtkOpNetworkEvaluator";
    private final Context mContext;
    private final WifiConfigManager mWifiConfigManager;

    public MtkOpNetworkEvaluator(Context context, WifiConfigManager configManager) {
        mContext = context;
        mWifiConfigManager = configManager;
    }

    /**
      * Get the evaluator name.
      */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Update the evaluator.
     *
     * Certain evaluators have to be updated with the new scan results. For example
     * the ExternalScoreEvalutor needs to refresh its Score Cache.
     *
     * @param scanDetails    a list of scan details constructed from the scan results
     */
    @Override
    public void update(List<ScanDetail> scanDetails) {
        // do nothing
    }

    /**
     * Evaluate all the networks from the scan results.
     *
     * @param scanDetails    a list of scan details constructed from the scan results
     * @param currentNetwork configuration of the current connected network
     *                       or null if disconnected
     * @param currentBssid   BSSID of the current connected network or null if
     *                       disconnected
     * @param connected      a flag to indicate if WifiStateMachine is in connected
     *                       state
     * @param untrustedNetworkAllowed a flag to indidate if untrusted networks like
     *                                ephemeral networks are allowed
     * @param connectableNetworks     a list of the ScanDetail and WifiConfiguration
     *                                pair which is used by the WifiLastResortWatchdog
     * @return configuration of the chosen network;
     *         null if no network in this category is available.
     */
    @Override
    @Nullable
    public WifiConfiguration evaluateNetworks(List<ScanDetail> scanDetails,
                    WifiConfiguration currentNetwork, String currentBssid,
                    boolean connected, boolean untrustedNetworkAllowed,
                    List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks) {
        MtkOpFwkExtManager.log(NAME + ".evaluateNetworks");
        for (ScanDetail detail : scanDetails) {
            MtkOpFwkExtManager.log(detail.toString());
        }
        // select network based on OP policy
        IWifiFwkExt ext = MtkOpFwkExtManager.getOpExt();
        ScanResult scanResultCandidate = null;
        WifiConfiguration candidate = null;

        if (ext.hasNetworkSelection() == IWifiFwkExt.OP_01) {
            MtkOpFwkExtManager.log(NAME + ".evaluateNetworks: IWifiFwkExt.OP_01");
            AutoConnectManager acm = MtkOpFwkExtManager.getACM();
            // 1. notify user if necessary
            // init this flag
            acm.setShowReselectDialog(false);
            if (acm.getScanForWeakSignal()) {
                // this scan is triggered due to weak signal, auto connect
                // a valid network/show reselection dialog
                acm.showReselectionDialog();
            }
            // reset this record which contains the last disconnected network
            // (aosp disconnection/disconnection due to poor RSSI)
            acm.clearDisconnectNetworkId();

            // 2. select a network based on priority
            List<Integer> disconnectNetworks = acm.getDisconnectNetworks();
            for (ScanDetail scanDetail : scanDetails) {
                ScanResult scanResult = scanDetail.getScanResult();
                int candidateIdOfScanResult = WifiConfiguration.INVALID_NETWORK_ID;

                // One ScanResult can be associated with more than one networks, hence we calculate all
                // the scores and use the highest one as the ScanResult's score.
                // This two-level-loop strucure is inherited from AOSP's SavedNetworkEvaluator. Please
                // notice currently we can only get single configuration from
                // getConfiguredNetworkForScanDetailAndCache() thus the inner loop makes no sense.
                List<WifiConfiguration> associatedConfigurations = null;
                WifiConfiguration associatedConfiguration =
                        mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(scanDetail);

                if (associatedConfiguration == null) {
                    continue;
                } else {
                    associatedConfigurations =
                        new ArrayList<>(Arrays.asList(associatedConfiguration));
                }

                for (WifiConfiguration network : associatedConfigurations) {
                    /**
                     * Ignore Passpoint and Ephemeral networks. They are configured networks,
                     * but without being persisted to the storage. They are evaluated by
                     * {@link PasspointNetworkEvaluator} and {@link ScoredNetworkEvaluator}
                     * respectively.
                     */
                    if (network.isPasspoint() || network.isEphemeral()) {
                        continue;
                    }

                    WifiConfiguration.NetworkSelectionStatus status =
                            network.getNetworkSelectionStatus();
                    status.setSeenInLastQualifiedNetworkSelection(true);

                    if (!status.isNetworkEnabled()) {
                        continue;
                    } else if (network.BSSID != null &&  !network.BSSID.equals("any")
                            && !network.BSSID.equals(scanResult.BSSID)) {
                        // App has specified the only BSSID to connect for this
                        // configuration. So only the matching ScanResult can be a candidate.
                        MtkOpFwkExtManager.log(
                            "Network " + WifiNetworkSelector.toNetworkString(network)
                                + " has specified BSSID " + network.BSSID + ". Skip "
                                + scanResult.BSSID);
                        continue;
                    } else if (TelephonyUtil.isSimConfig(network)
                            && !mWifiConfigManager.isSimPresent()) {
                        // Don't select if security type is EAP SIM/AKA/AKA' when SIM is not present.
                        continue;
                    }

                    if (disconnectNetworks == null ||
                        !disconnectNetworks.contains(network.networkId)) {
                        if (candidate == null || candidate.priority < network.priority) {
                            // Ascending : potentialCandidate < network
                            // skip setting of highestScoreOfScanResult because we use candidate
                            // directly
                            mWifiConfigManager.setNetworkCandidateScanResult(
                                    network.networkId,
                                    scanResult,
                                    Integer.MAX_VALUE - network.priority/* map priority to score */);
                            candidateIdOfScanResult = network.networkId;
                            // Reload the network config with the updated info.
                            candidate =
                                mWifiConfigManager.getConfiguredNetwork(candidateIdOfScanResult);
                        }
                    }
                }

                if (connectableNetworks != null) {
                    connectableNetworks.add(Pair.create(scanDetail,
                            mWifiConfigManager.getConfiguredNetwork(candidateIdOfScanResult)));
                }
            }

            if (scanResultCandidate == null) {
                MtkOpFwkExtManager.log("did not see any good candidates.");
            }
            MtkOpFwkExtManager.log("Candidate: " + candidate);
            if (candidate != null && candidate.priority > 0) {
                return candidate;
            } else {
                // If priority is 0, then it means settings has not set any priorities.
                // Fall back to AOSP evaluator.
                return null;
            }
        } else {
            return null;
        }

    }
}
