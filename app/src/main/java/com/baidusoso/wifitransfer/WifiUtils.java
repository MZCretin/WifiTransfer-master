package com.baidusoso.wifitransfer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * Created by masel on 2016/10/10.
 */

public class WifiUtils {

    public static String getWifiIp(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifimanager.getConnectionInfo();
        if (wifiInfo != null) {
            return intToIp(wifiInfo.getIpAddress());
        }
        return null;
    }

    private static String intToIp(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }

    public static int getWifiState(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            return wifiManager.getWifiState();
        }
        return WifiManager.WIFI_STATE_DISABLED;
    }

    public static NetworkInfo.State getWifiConnectState(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWiFiNetworkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (mWiFiNetworkInfo != null) {
            return mWiFiNetworkInfo.getState();
        }
        return NetworkInfo.State.DISCONNECTED;
    }
}
