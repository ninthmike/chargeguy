package com.ncs.chargeguy.util;

import java.io.UnsupportedEncodingException;

/**
 * Created by Mercury on 03/09/15.
 */
public class Keys {
    public final static String PREF_USERNAME = "username";
    public final static String PREF_PASSWORD = "password";
    public final static String PREF_TOKEN = "token";
    public final static String PREF_TOKEN_EXPIRES = "token_expires";
    public final static String PREF_SHOWED_INTRO = "showed_intro";
    public final static String PREF_MONITOR_VEHICLE_ID = "monitor_vehicle_id";
    public final static String PREF_MONITOR_TIMESTAMP = "monitor_timestamp";
    public final static String PREF_MONITOR_LAST_CHARGE_STATE = "monitor_last_charge_state";


    private static String stringDictionary = null;
    public static String dos(String encrypted) {

        // Set up our matching dictionary if we haven't yet:
        //

        if (stringDictionary == null) {
            createStringDictionary();
        }

        // Now decrypt it to a hex string with the proper values replaced:
        //

        StringBuilder hex = new StringBuilder();

        for (char c : encrypted.toCharArray()) {
            int intValue;

            for (intValue = 0; intValue < 16; intValue++) {
                if (stringDictionary.charAt(intValue) == c) {
                    break;
                }
            }

            hex.append(Integer.toHexString(intValue));
        }

        // Final step: decode the hex into an actual string.
        //

        byte[] bytes = new byte[hex.length() / 2];

        for (int i = 0; i < hex.length(); i += 2) {
            int intValue = Integer.parseInt(hex.substring(i, i + 2), 16);
            bytes[i / 2] = (byte) intValue;
        }

        try {
            return new String(bytes, "ascii");
        }
        catch (UnsupportedEncodingException uex) {   // Oh, come on.
            return null;
        }
    }

    private static void createStringDictionary() {
        StringBuilder sb = new StringBuilder();
        int currentValue = 0;

        for (int i = 0; i < 16; i++) {
            int thisValue = currentValue % 16;
            sb.append(Integer.toHexString(thisValue));
            currentValue += 3;
        }

        stringDictionary = sb.toString();
    }
}
