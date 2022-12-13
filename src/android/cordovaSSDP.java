package com.scott.plugin;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Scanner;

import cz.msebera.android.httpclient.Header;

public class cordovaSSDP extends CordovaPlugin {

    private static final String TAG = "scott.plugin.cordovaSSDP";
    private JSONArray mDeviceList;
    private Context mContext;
    private HashSet<String> deviceIps;

    public cordovaSSDP(Context context){
        mContext = context;
    }

    public static String parseHeaderValue(String content, String headerName) {
        Scanner s = new Scanner(content);
        s.nextLine();
        while (s.hasNextLine()) {
            String line = s.nextLine();
            int index = line.indexOf(':');

            if (index == -1) {
                return null;
            }
            String header = line.substring(0, index);
            if (headerName.equalsIgnoreCase(header.trim())) {
                return line.substring(index + 1).trim();
            }
        }
        return null;
    }

    public void search(String service, CallbackContext callbackContext) throws IOException {
        final int SSDP_PORT = 1900;
        final String SSDP_IP = "239.255.255.250";
        // max-age is observed to be 15; We 'should' get everything broadcasting at no more then 7.5s
        // apart. Due to the way we are doing things we will wait between TIMEOUT and TIMEOUT * 2
        int TIMEOUT = 7500;


        // Clear the cached Device List every time a new search is called
        mDeviceList = new JSONArray();
        deviceIps = new HashSet<>();

        // https://stackoverflow.com/questions/16730711/get-my-wifi-ip-address-android
        final WifiManager wifiManager = (WifiManager) this.mContext.getSystemService(Context.WIFI_SERVICE);

        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock(getClass().getSimpleName());
        if (!multicastLock.isHeld()) {
            multicastLock.acquire();
        }

        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByAddress(ipByteArray);
        } catch (UnknownHostException ex) {
            LOG.e(TAG, "Unable to get host address.");
        }
        inetAddress = InetAddress.getByName("0.0.0.0");
        System.out.println("inet address is");
        System.out.println(inetAddress);

        // Listen for NOTIFY multi-cast packets
        MulticastSocket multicast = null;
        try {
            multicast = new MulticastSocket(new InetSocketAddress(inetAddress, SSDP_PORT));
            multicast.setReuseAddress(true);
            //multicast.bind(new InetSocketAddress(inetAddress, SSDP_PORT));
            multicast.joinGroup(InetAddress.getByName(SSDP_IP));
            multicast.setSoTimeout(TIMEOUT);

            DatagramPacket receivePacket;
            long startTime = System.currentTimeMillis();
            long endTime = startTime + TIMEOUT;
            while (System.currentTimeMillis() < endTime) {
                try {
                    receivePacket = new DatagramPacket(new byte[1536], 1536);
                    multicast.receive(receivePacket);
                    String message = new String(receivePacket.getData());
                    try {
                        String ntValue = parseHeaderValue(message, "NT");
                        if (!service.equals(ntValue)) {
                            continue;
                        }
                        System.out.println(message);
                        String location = parseHeaderValue(message, "Location");
                        JSONObject device = new JSONObject();
                        device.put("USN", parseHeaderValue(message, "USN"));
                        device.put("LOCATION", location);
                        // yes this is the nt value but keep the api of the plugin to js the same
                        device.put("ST", ntValue);
                        device.put("Server", parseHeaderValue(message, "Server"));
                        if (!deviceIps.contains(location)) {
                            mDeviceList.put(device);
                            deviceIps.add(location);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            callbackContext.success(mDeviceList);

            if (multicast != null) {
                multicast.leaveGroup(InetAddress.getByName(SSDP_IP));
                multicast.disconnect();
                multicast.close();
            }
            if (multicastLock.isHeld()) {
                multicastLock.release();
            }
        }
    }

}
