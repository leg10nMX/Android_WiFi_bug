package com.example.leg10n.wifitest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import static android.content.ContentValues.TAG;

/**
 * Listens for 3 broadcasted custom wifi actions:
 * <p>
 * - {@link #ACTION_WIFI_ON} - {@link #ACTION_WIFI_OFF} - {@link #ACTION_CONNECT_TO_WIFI}
 * <p>
 * These actions are custom and can be replaced with any other string. To test these custom actions
 * you can do the following
 * <p>
 * <code>
 *
 * adb shell am broadcast -a android.intent.action.WIFI_ON
 *
 * adb shell am broadcast -a android.intent.action.WIFI_OFF
 *
 * adb shell am broadcast -a android.intent.action.CONNECT_TO_WIFI -e ssid {ssid} -e password {pwd}
 *
 * </code>
 */
public class WifiConnectionReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "WifiConnectionReceiver";
    /**
     * Notifies the receiver to turn wifi on
     */
    private static final String ACTION_WIFI_ON = "android.intent.action.WIFI_ON";

    /**
     * Notifies the receiver to turn wifi off
     */
    private static final String ACTION_WIFI_OFF = "android.intent.action.WIFI_OFF";

    /**
     * Notifies the receiver to connect to a specified wifi
     */
    private static final String ACTION_CONNECT_TO_WIFI = "android.intent.action.CONNECT_TO_WIFI";

    private WifiManager wifiManager;

    public WifiConnectionReceiver() {
    }

    String ssid;
    public void onReceive(Context c, Intent intent) {
        Log.d(TAG, "onReceive() called with: intent = [" + intent + "]");

        wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);

        final String action = intent.getAction();

        if (!isTextNullOrEmpty(action)) {
            switch (action) {
                case WifiManager.WIFI_STATE_CHANGED_ACTION:

                    bindToNetwork(c);
                    break;
                case ACTION_WIFI_ON:
                    // Turns wifi on
                    wifiManager.setWifiEnabled(true);
                    break;
                case ACTION_WIFI_OFF:
                    // Turns wifi off
                    wifiManager.setWifiEnabled(false);
                    break;
                case ACTION_CONNECT_TO_WIFI:
                    // Connects to a specific wifi network
                    final String networkSSID = intent.getStringExtra("ssid");
                    final String networkPassword = intent.getStringExtra("password");

                    this.ssid = networkSSID;
                    if (!isTextNullOrEmpty(networkSSID)) {
                        connectToWifi(networkSSID, networkPassword);
                    } else {
                        Log.e(TAG, "onReceive: cannot use " + ACTION_CONNECT_TO_WIFI +
                                "without passing in a proper wifi SSID and password.");
                    }
                    break;
            }
        }
    }

    private boolean isTextNullOrEmpty(final String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Connect to the specified wifi network.
     *
     * @param networkSSID     - The wifi network SSID
     * @param networkPassword - the wifi password
     */
    private void connectToWifi(final String networkSSID, final String networkPassword) {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = String.format("\"%s\"", networkSSID);
        if (networkPassword != null) {
            conf.preSharedKey = String.format("\"%s\"", networkPassword);
        } else {
            conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int netId = wifiManager.addNetwork(conf);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
    }

    @NonNull
    public static IntentFilter getIntentFilterForWifiConnectionReceiver() {
        final IntentFilter randomIntentFilter = new IntentFilter(ACTION_WIFI_ON);
        randomIntentFilter.addAction(ACTION_WIFI_OFF);
        randomIntentFilter.addAction(ACTION_CONNECT_TO_WIFI);
        randomIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        return randomIntentFilter;
    }

    void bindToNetwork(Context _context) {
        ConnectivityManager cm = (ConnectivityManager) _context.getSystemService(Context
                .CONNECTIVITY_SERVICE);
        // Find the network we just got an address for
        Network[] networks = cm.getAllNetworks();
        Network foundNetwork = null;
        for (Network n : networks) {
            NetworkInfo info = cm.getNetworkInfo(n);
            if (info != null &&
                    info.getType() == ConnectivityManager.TYPE_WIFI &&
                    info.isConnectedOrConnecting() &&
                    info.getExtraInfo() == this.ssid) {
                foundNetwork = n;
                break;
            }
        }

        if (foundNetwork != null) {
            Log.d(LOG_TAG, "foundNetwork:" + foundNetwork + ", info:" + cm.getNetworkInfo(foundNetwork));
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
            boolean bound = cm.bindProcessToNetwork(foundNetwork);
            Log.d(LOG_TAG, "bindProcessToNetwork returns " + bound);

            // Android 9 on some phones has issues with this check
            if (Build.VERSION.SDK_INT < 28) {
                // On some devices the returned result from bindProcessToNetwork doesn't
                // reflect real bound status, say the result is not reliable enough.
                // Specifically, successful bound may return false, and
                // failed bound may return true. Actually there is a logcat output that
                // indicates if the bind was eventually successful or not. something like this:
                // 05-04 13:47:18.021 16998-17161/com.aylanetworks.agilelink V/NetdClient: setNetworkForSocket: netId=133, socketFd=103
                // Because of this, we'll wait up to 5 seconds for the bound network becomes
                // active, otherwise succeeding network calls, such as fetchDeviceDetail(), may
                // end up with network error, TimeoutError e.g.
                long MAX_TIME = TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
                long start = System.nanoTime();
                while (!foundNetwork.equals(cm.getActiveNetwork()) &&
                        (System.nanoTime() - start < MAX_TIME)) {
                    try {
                        Thread.sleep(500);
                        Log.i(LOG_TAG, "Waiting for bound network becomes active...");
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "Interrupted in bindToNetwork");
                        break;
                    }
                }

                if (!foundNetwork.equals(cm.getActiveNetwork())) {
                    Log.e(LOG_TAG,"Could not bind process to device's network");
                } else {
                    Log.d(LOG_TAG, "bound network becomes active");
                }
            }

            Log.i(LOG_TAG, "foundNetwork:" + foundNetwork + ", info:" +
                    cm.getNetworkInfo(foundNetwork));

        } else {
            Log.e(LOG_TAG, "No network found!");
        }
    }
}
