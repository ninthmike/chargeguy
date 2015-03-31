package com.ncs.chargeguy.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ncs.chargeguy.util.Keys;
import com.ncs.chargeguy.util.Trace;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Mercury on 03/09/15.
 */
public class Singleton {
    private final static String VEHICLES_SAVE_NAME = "vehicles.json";
    private final static int OLD_TOKEN_TIME = 60 * 60 * 24 * 3;   // Within three days of expiring: do it again.

    private static Singleton me;

    public static void setup(Context context) {
        if (me != null) {   // We are already set up, bail out.
            return;
        }

        me = new Singleton();
    }

    public static void setupAndLoadVehicles(Context context) {
        setup(context);

        if (!me.hasVehicles()) {
            me.loadVehicles(context);
        }
    }

    public static Singleton get() {
        return me;
    }

    ArrayList<Vehicle> vehicles;
    boolean listDirty = false;

    ////////////////////////////////////////////

    public boolean isListDirty() {
        return listDirty;
    }

    public void setListDirty() {
        listDirty = true;
    }

    public void setVehicles(ArrayList<Vehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public boolean hasVehicles() {
        return vehicles != null;
    }

    public ArrayList<Vehicle> getVehicles() {
        return vehicles;
    }

    public Vehicle getVehicleById(long id) {
        if (vehicles == null) {
            // Lana: Noope.
            return null;
        }

        for (Vehicle one : vehicles) {
            if (one.id == id) {
                return one;
            }
        }

        return null;
    }

    public void markAllVehiclesChargeStatusStale() {
        for (Vehicle vehicle : Singleton.get().getVehicles()) {
            vehicle.markChargeStatusStale();
        }
    }

    public void loadVehicles(Context context) {
        try {
            FileInputStream inStream = context.openFileInput(VEHICLES_SAVE_NAME);
            String jsonBlob = slurpFileToString(inStream);

            Gson gson = new Gson();
            vehicles = gson.fromJson(jsonBlob, new TypeToken<ArrayList<Vehicle>>() {}.getType());

            listDirty = false;
        }
        catch (FileNotFoundException fex) {
            Trace.debug("First run, no save file, oh well");
            vehicles = null;
        }
        catch (IOException iox) {
            Trace.error(iox, "wtf?");
        }
    }

    private static String slurpFileToString(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();

        String oneLine = r.readLine();

        while (oneLine != null) {
            sb.append(oneLine);
            sb.append('\r');
            oneLine = r.readLine();
        }

        r.close();
        in.close();

        return sb.toString();
    }

    public void saveVehicles(Context context) {
        if (vehicles == null || vehicles.size() == 0) {
            Trace.error("!! Attempt to save empty vehicles list, bailing out");
            return;
        }

        try {
            FileOutputStream outStream = context.openFileOutput(VEHICLES_SAVE_NAME, Context.MODE_PRIVATE);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outStream));
            w.write(new Gson().toJson(vehicles));
            w.flush();
            w.close();

            listDirty = false;
        } catch (Exception ex) {
            Trace.error(ex, "saveVehicles");
        }
    }

    public void clearVehicles(Context context) {
        vehicles = null;
        context.deleteFile(VEHICLES_SAVE_NAME);
    }

    public boolean hasLogin(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.contains(Keys.PREF_USERNAME);
    }

    public void clearLogin(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edits = prefs.edit();

        edits.remove(Keys.PREF_USERNAME);
        edits.remove(Keys.PREF_PASSWORD);
        edits.remove(Keys.PREF_TOKEN);
        edits.remove(Keys.PREF_TOKEN_EXPIRES);

        edits.commit();

        clearVehicles(context);
    }

    public void storeLogin(Context context, String accessToken, int expires, String email, String password) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edits = prefs.edit();
        long expireTime = (new Date().getTime() / 1000) + expires;

        edits.putString(Keys.PREF_USERNAME, email);
        edits.putString(Keys.PREF_PASSWORD, password);
        edits.putString(Keys.PREF_TOKEN, accessToken);
        edits.putLong(Keys.PREF_TOKEN_EXPIRES, expireTime);

        edits.commit();
    }

    public boolean isAccessTokenOld(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long expires = prefs.getLong(Keys.PREF_TOKEN_EXPIRES, 0);
        long rightNow = new Date().getTime() / 1000;
        long secondsLeft = expires - rightNow;

        return secondsLeft < OLD_TOKEN_TIME;
    }

    public String getStoredAccessToken(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_TOKEN, null);
    }

    public String getStoredUsername(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_USERNAME, null);
    }

    public String getStoredPassword(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(Keys.PREF_PASSWORD, null);
    }
}
