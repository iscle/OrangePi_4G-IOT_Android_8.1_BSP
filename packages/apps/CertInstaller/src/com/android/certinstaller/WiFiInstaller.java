package com.android.certinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.hotspot2.ConfigParser;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An Activity for provisioning a Hotspot 2.0 Release 1 configuration.
 */
public class WiFiInstaller extends Activity {

    private static final String TAG = "WifiInstaller";
    private static final String NETWORK_NAME = "network_name";
    private static final String INSTALL_STATE = "install_state";
    public static final int INSTALL_SUCCESS = 2;
    public static final int INSTALL_FAIL = 1;
    public static final int INSTALL_FAIL_NO_WIFI = 0;
    PasspointConfiguration mPasspointConfig;
    WifiManager mWifiManager;
    boolean doNotInstall;

    @Override
    protected void onCreate(Bundle savedStates) {
        super.onCreate(savedStates);

        Bundle bundle = getIntent().getExtras();
        String uriString = bundle.getString(CertInstallerMain.WIFI_CONFIG_FILE);
        String mimeType = bundle.getString(CertInstallerMain.WIFI_CONFIG);
        byte[] data = bundle.getByteArray(CertInstallerMain.WIFI_CONFIG_DATA);

        Log.d(TAG, "WiFi data for " + CertInstallerMain.WIFI_CONFIG + ": " +
                mimeType + " is " + (data != null ? data.length : "-"));

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mPasspointConfig = ConfigParser.parsePasspointConfig(mimeType, data);
        dropFile(Uri.parse(uriString), getApplicationContext());

        if (mPasspointConfig == null) {
            Log.w(TAG, "failed to build passpoint configuration");
            doNotInstall = true;
        } else if (mPasspointConfig.getHomeSp() == null) {
            Log.w(TAG, "Passpoint profile missing HomeSP information");
            doNotInstall = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        createMainDialog();
    }

    /**
     * Create a dialog that guides the user through Hotspot 2.0 Release 1 configuration file
     * installation.
     */
    private void createMainDialog() {
        Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View layout = getLayoutInflater().inflate(R.layout.wifi_main_dialog, null);
        builder.setView(layout);

        TextView text = (TextView) layout.findViewById(R.id.wifi_info);
        if (!doNotInstall) {
            text.setText(String.format(getResources().getString(R.string.wifi_installer_detail),
                    mPasspointConfig.getHomeSp().getFriendlyName()));

            builder.setTitle(mPasspointConfig.getHomeSp().getFriendlyName());
            builder.setIcon(res.getDrawable(R.drawable.signal_wifi_4_bar_lock_black_24dp));

            builder.setPositiveButton(R.string.wifi_install_label,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(WiFiInstaller.this, getString(R.string.wifi_installing_label),
                            Toast.LENGTH_LONG).show();
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            boolean success = true;
                            try {
                                mWifiManager.addOrUpdatePasspointConfiguration(mPasspointConfig);
                            } catch (RuntimeException rte) {
                                Log.w(TAG, "Caught exception while installing wifi config: " +
                                           rte, rte);
                                success = false;
                            }
                            if (success) {
                                Intent intent = new Intent(getApplicationContext(),
                                        CredentialsInstallDialog.class);
                                intent.putExtra(NETWORK_NAME,
                                        mPasspointConfig.getHomeSp().getFriendlyName());
                                intent.putExtra(INSTALL_STATE, INSTALL_SUCCESS);
                                startActivity(intent);
                            } else {
                                Intent intent = new Intent(getApplicationContext(),
                                        CredentialsInstallDialog.class);
                                intent.putExtra(INSTALL_STATE, INSTALL_FAIL);
                                startActivity(intent);
                            }
                            finish();
                        }
                    });
                    dialog.dismiss();
                }
            });

            builder.setNegativeButton(R.string.wifi_cancel_label, new
                    DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
        } else {
            text.setText(getResources().getString(R.string.wifi_installer_download_error));
            builder.setPositiveButton(R.string.done_label, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    finish();
                }
            });
        }
        builder.create().show();
    }

    /**
     * Delete the file specified by the given URI.
     *
     * @param uri The URI of the file
     * @param context The context of the current application
     */
    private static void dropFile(Uri uri, Context context) {
      try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
          DocumentsContract.deleteDocument(context.getContentResolver(), uri);
        } else {
          context.getContentResolver().delete(uri, null, null);
        }
      } catch (Exception e) {
        Log.e(TAG, "could not delete document " + uri);
      }
    }
}
