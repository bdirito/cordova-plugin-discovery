package com.scott.plugin;

import android.content.Context;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
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

    private void createServiceObjWithXMLData(String url, final JSONObject jsonObj) {

        SyncHttpClient syncRequest = new SyncHttpClient();
        syncRequest.get(mContext.getApplicationContext(), url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject device = jsonObj;
                    device.put("xml", new String(responseBody));
                    String location = device.get("LOCATION").toString();
                    if (!deviceIps.contains(location)) {
                        mDeviceList.put(device);
                        deviceIps.add(location);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                LOG.e(TAG, responseBody.toString());
            }
        });
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

        // Listen for NOTIFY multi-cast packets
        MulticastSocket multicast = null;
        try {
            multicast = new MulticastSocket(SSDP_PORT);
            multicast.setReuseAddress(true);
            multicast.joinGroup(InetAddress.getByName(SSDP_IP));
            multicast.setSoTimeout(TIMEOUT);

            DatagramPacket receivePacket;
            while (true) {
                try {
                    receivePacket = new DatagramPacket(new byte[1536], 1536);
                    multicast.receive(receivePacket);
                    String message = new String(receivePacket.getData());
                    try {
                        String ntValue = parseHeaderValue(message, "NT");
                        if (!service.equals(ntValue)) {
                            continue;
                        }
                        JSONObject device = new JSONObject();
                        device.put("USN", parseHeaderValue(message, "USN"));
                        device.put("LOCATION", parseHeaderValue(message, "Location"));
                        // yes this is the nt value but keep the api of the plugin to js the same
                        device.put("ST", ntValue);
                        device.put("Server", parseHeaderValue(message, "Server"));
                        createServiceObjWithXMLData(parseHeaderValue(message, "LOCATION"), device);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } catch (SocketTimeoutException e) {
                    callbackContext.success(mDeviceList);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            if (multicast != null) {
                multicast.leaveGroup(InetAddress.getByName(SSDP_IP));
                multicast.disconnect();
                multicast.close();
            }
        }
    }

}
