package com.ncs.chargeguy.model;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.receivers.ScheduledAlarmReceiver;
import com.ncs.chargeguy.util.Server;
import com.ncs.chargeguy.util.Trace;

import java.util.Date;

/**
 * Created by Mercury on 03/09/15.
 */
public class Vehicle {
    // For knowing when to ask for charge state again:
    private final static int FOREGROUND_REFRESH_TIME = 1000 * 15;
    private final static int CHARGE_REFRESH_TIME = 1000 * 5;
    private final static int UNKNOWN_REFRESH_TIME = 1000 * 2;

    // For synchronous requests:
    private final static int MAX_RETRIES = 5;
    private final static int RETRY_DELAY_MS = 1000 * 10;

    private final static int GUI_STALE_TIME_MS = 1000 * 60 * 60 * 24;   // Only check this once a day.

    // Stuff we get from vehicles query:
    //

    public String displayName;
    public long id;
    public long vehicleId;
    public String state;

    // Stuff we get from charge query:
    //

    public String chargingState;            // null (plugged in and waiting), Disconnected, Charging, or Stopped, NoPower
    public double chargerVoltage;           // Power!
    public double chargeRate;               // Mi/hr of charge going on right now.
    public double chargerActualCurrent;     // Other field needed for power, oops.
    public double batteryRange;             // Rated miles in the battery right now.
    public double idealBatteryRange;        // Ideal miles in the battery right now.
    public double batteryLevel;             // Percent in the battery right now.
    public double timeToFullCharge;         // How many hours until we're done?

    // Stuff we get from GUI prefs query:
    //

    public String guiDistanceUnits;         // km/hr or mi/hr
    public String guiRangeDisplay;          // Rated / Ideal
    public String chargeRateUnits;          // Units for charge rate, in case we ever need it.

    // Internal state:
    //

    public long lastChargeUpdate;           // getTime() of the last time we pulled charge status from Tesla's server.
    public long lastGUIPrefUpdate;          // getTime() of the last time we pulled GUI preferences from Tesla's server.

    // Stuff the operator can configure:
    //

    public boolean plugCheckEnabled = true;                 // We checking for the car to be plugged in?
    public int plugCheckTime = 68400;                       // Seconds after midnight when we check to see if the car is plugged.  (7pm default)
    public int plugCheckMiles = 150;                        // How many miles do we ignore the plug state above?

    public boolean rangeCheckEnabled = true;                // We checking for minimum range?
    public int rangeCheckTime = 79200;                      // Seconds after midnight when we check for minimum range.  (10pm default)
    public int rangeCheckMiles = 100;                       // How many rated miles do we need to be happy?
    public boolean extraNoisy = true;                       // Play our own sound clip at full volume, just to be a dick.

    // Methods.
    //

    public void markChargeStatusStale() {
        lastChargeUpdate = 0;
    }

    // How soon should we check this car again for charge status?  Default is a long time,
    // but we should check faster if charging and even faster if "unknown" charge state.

    public int getChargePollPeriod() {
        int period = FOREGROUND_REFRESH_TIME;

        // Maybe promote it to slightly faster speed when charging:
        if (isCharging()) {
            period = CHARGE_REFRESH_TIME;
        }

        // Maybe promote it to much faster speed when unknown: (wtf, Tesla?)
        if (!isChargeStateKnown()) {
            period = UNKNOWN_REFRESH_TIME;
        }

        return period;
    }

    public boolean shouldRefreshChargeState() {
        return new Date().getTime() - lastChargeUpdate > getChargePollPeriod();
    }

    public boolean isGUIPrefStale() {
        return new Date().getTime() - lastGUIPrefUpdate > GUI_STALE_TIME_MS;
    }

    @Override
    public String toString() {
        return String.format("Vehicle: dn=%s, id=%d, vid=%d, state=%s", displayName, id, vehicleId, state);
    }

    public boolean isRangeIdeal() {
        return guiRangeDisplay.equals("Ideal");
    }

    public boolean isDistanceUnitKm() {
        return guiDistanceUnits.equals("km/hr");
    }

    public boolean isChargeComplete() {
        return isChargeStateKnown() && chargingState.equals("Complete");
    }

    public boolean isCharging() {
        return isChargeStateKnown() && (chargingState.equals("Charging") || chargingState.equals("Starting"));
    }

    public boolean isPluggedIn() {
        return isChargeStateKnown() && (chargingState.equals("Connected") || chargingState.equals("Charging") || chargingState.equals("Stopped") || chargingState.equals("Complete"));
    }

    public boolean isChargeStateKnown() {
        return !(chargingState == null || chargingState.equals("null"));
    }

    // Get the numeric value of range according to use prefs (ideal/rated and mi/km).
    public double getRangeNumber() {

        double range = isRangeIdeal() ? idealBatteryRange : batteryRange;

        // Convert it to km if appropriate:
        if (isDistanceUnitKm()) {
            range /= .62137;
        }

        return range;
    }

    public String getRangeUnitsText(Context context) {
        Resources res = context.getResources();

        String template = res.getString(R.string.range_units_template);
        String unit = res.getString(isDistanceUnitKm() ? R.string.kilometers_short : R.string.miles_short);
        String rangeType = res.getString(isRangeIdeal() ? R.string.ideal : R.string.rated);

        return String.format(template, unit, rangeType);
    }

    public String getRangeText(Context context) {
        // Ok, first pick the mileage appropriate to the preference.
        double range = getRangeNumber();

        // Now make a nice string for it:
        Resources res = context.getResources();
        String template = res.getString(R.string.range_template);
        String unit = res.getString(isDistanceUnitKm() ? R.string.kilometers_short : R.string.miles_short);
        String rangeType = res.getString(isRangeIdeal() ? R.string.ideal : R.string.rated);

        return String.format(template, Math.round(range), unit, rangeType);
    }

    public String getChargeText(Context context, boolean includeMarkup) {
        Resources res = context.getResources();

        // First, check all the standard ones that have no parameters:

        if (chargingState == null || chargingState.equals("null")) {
            return res.getString(R.string.charge_status_null);
        }

        if (chargingState.equals("Disconnected")) {
            return res.getString(R.string.charge_status_disconnected);
        }

        if (chargingState.equals("Stopped")) {
            return res.getString(R.string.charge_status_stopped);
        }

        if (chargingState.equals("Starting")) {
            return res.getString(R.string.charge_status_starting);
        }

        if (chargingState.equals("Complete")) {
            return res.getString(R.string.charge_status_complete);
        }

        if (chargingState.equals("NoPower")) {
            return res.getString(R.string.charge_status_nopower);
        }

        // Ok, it's not one of those.  We *should* be charging.  Sanity check it here first,
        // in case there are other charge state values that we don't know about.

        if (!chargingState.equals("Charging")) {
            return String.format("!! Unknown: %s", chargingState);
        }

        // Ok, we are definitely charging.  Format it up nicely and send it off.

        double rate = chargeRate;
        String rateUnit = res.getString(R.string.charge_rate_mi);

        // Convert it to km if appropriate:

        if (isDistanceUnitKm()) {
            rate /= .62137;
            rateUnit = res.getString(R.string.charge_rate_km);
        }

        // Now make a nice string for it:

        String template = res.getString(includeMarkup ? R.string.charge_template_html : R.string.charge_template);

        return String.format(template, Math.round(rate), rateUnit, (int) chargerVoltage, (int) chargerActualCurrent);
    }

    public String getChargeTimeLeftText(Context context, boolean shortForm) {
        int hours = (int) Math.floor(timeToFullCharge);
        int minutes = (int) ((timeToFullCharge - hours) * 60);

        if (hours == 0 && minutes == 0) {
            return context.getString(R.string.remaining_unknown);
        }

        Resources res = context.getResources();
        String plural = res.getString(R.string.plural_suffix);

        if (hours > 0) {
            if (shortForm) {
                return String.format(res.getString(R.string.time_left_template_hours_short), hours, minutes);
            }
            else {
                return String.format(res.getString(R.string.time_left_template_hours), hours, hours == 1 ? "" : plural, minutes, minutes == 1 ? "" : plural);
            }
        }
        else {
            if (shortForm) {
                return String.format(res.getString(R.string.time_left_template_mins_short), minutes);
            }
            else {
                return String.format(res.getString(R.string.time_left_template_mins), minutes, minutes == 1 ? "" : plural);
            }
        }
    }

    // Go get the vehicle's charge status, retrying as necessary.
    //
    // WARNING: this method will block the current thread, possibly for a long time.  Do not call
    // on UI thread!

    public void updateChargeStateSync(Context context) {
        // First, clear it out.  This way we'll properly return null to
        // anyone who cares if we couldn't finish.

        chargingState = null;

        // Ok, now fire it up:

        int tries = 0;
        Server.ServerRequestTask task = null;
        Server server = new Server(context);

        while (tries < MAX_RETRIES) {
            task = server.getChargeStateSync(this);

            // Note: sometimes the request will succeed, but the charge state returned by the
            // server is null.  So simply checking the request for success is insufficient.
            // In those cases, ask again until we get a charge state.
            //

            if (task.success && isChargeStateKnown()) {
                // Awww, yeeaa.
                break;
            }

            Trace.debug("Couldn't get charge state, will retry (success = %s, known = %s)", task.success, isChargeStateKnown());
            try {
                Thread.sleep(RETRY_DELAY_MS);
            }
            catch (InterruptedException iex) {
                // No.
            }
        }
    }
}
