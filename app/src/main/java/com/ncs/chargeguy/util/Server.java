package com.ncs.chargeguy.util;

import android.content.Context;
import android.os.AsyncTask;

import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Mercury on 03/09/15.
 */
public class Server {
    public final static String REQUEST_AUTH = "/oauth/token";
    public final static String REQUEST_VEHICLES = "/api/1/vehicles";
    public final static String REQUEST_CHARGE_TEMPLATE = "/api/1/vehicles/%d/data_request/charge_state";
    public final static String REQUEST_GUI_TEMPLATE = "/api/1/vehicles/%d/data_request/gui_settings";

    private final static boolean SHOW_VERBOSE_DEBUG = false;
    private final static String REMOTE_SERVER = "https://owner-api.teslamotors.com";

    // Simple obfuscation to conceal clientid/clientsecret from kids running string-dumping analysis.
    //

    private final static String CID_OBF = "2f9c239b9b9c9b22292223909c909298229f9b2326269f23929f9822962623299023999c96982f9c929f9699939f9c9b9026929f9b2c9f23269922999f239b2f";
    private final static String CSEC_OBF = "29959f22939c2626232c2998262f2f9923959f9b9c9c93962999939c93922298999090969f922c959292982f23952f922f952290929596952622269b2c969690";

    Context context;
    ServerRequestContainer container;

    public static interface ServerRequestContainer {
        public void serverRequestCompleted(boolean success, String request, Server server, String note);
    }

    public Server(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public void setContainer(ServerRequestContainer container) {
        this.container = container;
    }

    public void abandon() {
        container = null;
    }

    public void startAuth(String email, String password) {
        ArrayList<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();

        params.add(new BasicNameValuePair("grant_type", "password"));
        params.add(new BasicNameValuePair(Keys.dos("29242b2f2a5cfd2b2c"), Keys.dos(CID_OBF)));
        params.add(new BasicNameValuePair(Keys.dos("29242b2f2a5cfd592f29562f5c"), Keys.dos(CSEC_OBF)));
        params.add(new BasicNameValuePair("email", email));
        params.add(new BasicNameValuePair("password", password));

        fireSimpleRequest(REQUEST_AUTH, params);
    }

    public void startGetVehicles() {
        fireSimpleRequest(REQUEST_VEHICLES, null);
    }

    public void startGetChargeState(Vehicle vehicle) {
        String page = String.format(REQUEST_CHARGE_TEMPLATE, vehicle.id);
        ServerRequestTask task = new ServerRequestTask(this, page, REQUEST_CHARGE_TEMPLATE, null);
        task.setTag(vehicle);
        task.execute();
    }

    // This method blocks the current thread, do not call it from the UI thread!
    //

    public ServerRequestTask getChargeStateSync(Vehicle vehicle) {
        String page = String.format(REQUEST_CHARGE_TEMPLATE, vehicle.id);
        ServerRequestTask task = new ServerRequestTask(this, page, REQUEST_CHARGE_TEMPLATE, null);
        task.setTag(vehicle);
        task.doServerRequest();
        task.notifyOwner();

        return task;
    }

    public void startGetGUIPrefs(Vehicle vehicle) {
        String page = String.format(REQUEST_GUI_TEMPLATE, vehicle.id);
        ServerRequestTask task = new ServerRequestTask(this, page, REQUEST_GUI_TEMPLATE, null);
        task.setTag(vehicle);
        task.execute();
    }

    private void fireSimpleRequest(String page, List<BasicNameValuePair> params) {
        ServerRequestTask task = new ServerRequestTask(this, page, page, params);
        task.execute();
    }

    private void serverRequestGood(ServerRequestTask task, String requestName, JSONObject jsonObject, List<BasicNameValuePair> params) throws JSONException {
        Trace.debug("Server.serverRequest good, name = \"%s\"", requestName);
        if (requestName.equals(REQUEST_AUTH)) {
            Trace.debug("Auth request has returned, yay!");
            String token = jsonObject.getString("access_token");
            int expires = jsonObject.getInt("expires_in");

            Singleton.get().storeLogin(context, token, expires, getParamValue(params, "email"), getParamValue(params, "password"));
        }

        if (requestName.equals(REQUEST_VEHICLES)) {
            JSONArray jsonVehicles = jsonObject.getJSONArray("response");
            ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();

            for (int i = 0; i < jsonVehicles.length(); i++) {
                JSONObject jsonVehicle = jsonVehicles.getJSONObject(i);
                Vehicle vehicle = new Vehicle();

                vehicle.id = jsonVehicle.getLong("id");
                vehicle.displayName = jsonVehicle.getString("display_name");
                vehicle.vehicleId = jsonVehicle.getLong("vehicle_id");
                vehicle.state = jsonVehicle.getString("state");

                if (vehicle.displayName == null || vehicle.displayName.length() == 0) {
                    vehicle.displayName = "Unnamed Model S";
                }

                vehicles.add(vehicle);
                Trace.debug("Added vehicle: %s", vehicle.toString());
            }

            Singleton.get().setVehicles(vehicles);
            Singleton.get().saveVehicles(context);
        }

        if (requestName.equals(REQUEST_CHARGE_TEMPLATE)) {
            JSONObject jsonResponse = jsonObject.getJSONObject("response");
            Vehicle vehicle = (Vehicle) task.getTag();
            vehicle.chargingState = jsonResponse.getString("charging_state");
            vehicle.batteryRange = jsonResponse.getDouble("battery_range");
            vehicle.batteryLevel = jsonResponse.getDouble("battery_level");
            vehicle.chargerVoltage = getDoubleOrZero(jsonResponse, "charger_voltage");
            vehicle.chargeRate = getDoubleOrZero(jsonResponse, "charge_rate");
            vehicle.chargerActualCurrent = getDoubleOrZero(jsonResponse, "charger_actual_current");
            vehicle.timeToFullCharge = getDoubleOrZero(jsonResponse, "time_to_full_charge");
            vehicle.idealBatteryRange = jsonResponse.getDouble("ideal_battery_range");
            vehicle.lastChargeUpdate = new Date().getTime();

            Trace.debug("Updated vehicle charge info: state=%s, range=%f", vehicle.chargingState, vehicle.batteryRange);

            Singleton.get().setListDirty();
        }

        if (requestName.equals(REQUEST_GUI_TEMPLATE)) {
            JSONObject jsonResponse = jsonObject.getJSONObject("response");
            Vehicle vehicle = (Vehicle) task.getTag();
            vehicle.guiRangeDisplay = jsonResponse.getString("gui_range_display");
            vehicle.chargeRateUnits = jsonResponse.getString("gui_charge_rate_units");
            vehicle.guiDistanceUnits = jsonResponse.getString("gui_distance_units");
            vehicle.lastGUIPrefUpdate = new Date().getTime();

            Trace.debug("Updated vehicle GUI pref info: rangeDisplay=%s", vehicle.guiRangeDisplay);

            Singleton.get().setListDirty();
        }

        if (container != null) {
            Trace.debug("Notifying container of finished request");
            container.serverRequestCompleted(true, requestName, this, null);
        }
    }

    private double getDoubleOrZero(JSONObject json, String fieldName) throws JSONException {
        if (json.isNull(fieldName)) {
            return 0.0;
        }
        else {
            return json.getDouble(fieldName);
        }
    }

    private void serverRequestFailed(ServerRequestTask task, String requestName, String errorText) {
        Trace.debug("serverRequest failed, name = \"%s\"", requestName);

        if (container != null) {
            container.serverRequestCompleted(false, requestName, this, errorText);
        }
    }

    private String getParamValue(List<BasicNameValuePair> params, String name) {
        for (BasicNameValuePair param : params) {
            if (param.getName().equals(name)) {
                return param.getValue();
            }
        }

        Trace.error(new Throwable(), "can't find param named %s in post data, this is not good", name);
        return null;
    }

    public static class ServerRequestTask extends AsyncTask<Void, Void, Integer> {
        public final List<BasicNameValuePair> postParams;
        public final Server owner;
        public final String page;
        public final String requestName;

        public JSONObject jsonResult = null;
        public String stringResult = null;        // This would be some kind of error.
        public boolean success = false;
        public Object tag = null;

        public ServerRequestTask(Server owner, String page, String requestName, List<BasicNameValuePair> postParams) {
            this.page = page;
            this.requestName = requestName;
            this.owner = owner;
            this.postParams = postParams;
        }

        public void setTag(Object o) {
            this.tag = o;
        }

        public Object getTag() {
            return tag;
        }

        @Override
        protected void onPostExecute(Integer result) {
            Trace.debug("onPostExecute");
            notifyOwner();
        }

        public void notifyOwner() {
            Trace.debug("notifyOwner, success = %s", success);
            if (success) {
                try {
                    owner.serverRequestGood(this, requestName, jsonResult, postParams);
                    return;
                }
                catch (Exception ex) {
                    Trace.error(ex, "Parsing server response");
                    stringResult = "Unable to parse response from Tesla - !?";
                }
            }

            // If we're here, then either the request failed or the parsing failed.
            owner.serverRequestFailed(this, requestName, stringResult);
        }

        public void doServerRequest() {
            String fullURL = REMOTE_SERVER + page;

            try {
                HttpClient httpClient = new DefaultHttpClient();
                HttpUriRequest request;

                if (postParams == null) {
                    HttpGet getRequest = new HttpGet(fullURL);
                    request = getRequest;

                    if (SHOW_VERBOSE_DEBUG){
                        Trace.debug("GET: %s", fullURL);
                    }
                }
                else {
                    HttpPost postRequest = new HttpPost(fullURL);
                    postRequest.setEntity(new UrlEncodedFormEntity(postParams));
                    request = postRequest;

                    if (SHOW_VERBOSE_DEBUG){
                        Trace.debug("POST: %s", fullURL);

                        for (BasicNameValuePair param : postParams) {
                            Trace.debug("%s = \"%s\"", param.getName(), param.getValue());
                        }
                    }
                }

                if (!request.equals(REQUEST_AUTH)) {
                    request.addHeader("Authorization", "Bearer " + Singleton.get().getStoredAccessToken(owner.getContext()));
                }

                request.setHeader("User-Agent", "ChargeGuy/Android");

                HttpResponse response = httpClient.execute(request);

                if (response.getStatusLine().getStatusCode() == 401) {
                    throw new Exception("401 unauthorized, oh no!");
                }

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new Exception(String.format("HTTP Server Error %d (%s)", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
                }

                String contents = slurpResponseToString(response.getEntity().getContent());

                if (SHOW_VERBOSE_DEBUG) {
                    Trace.debug("Response: \"%s\"", contents);
                }

                JSONObject json = new JSONObject(contents);
                this.jsonResult = json;
                success = true;
            }
            catch (Exception ex) {
                Trace.error(ex, "Exception in server request for: %s", fullURL);
                this.stringResult = ex.getMessage();
                this.success = false;
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            doServerRequest();

            return null;
        }

        private static String slurpResponseToString(InputStream content) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(content, "UTF-8"));
            String sResponse;
            StringBuilder s = new StringBuilder();

            while ((sResponse = reader.readLine()) != null) {
                s.append(sResponse);
            }

            return s.toString();
        }
    }
}
