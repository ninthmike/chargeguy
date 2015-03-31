package com.ncs.chargeguy.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.services.ChargeCheckService;
import com.ncs.chargeguy.services.LiveMonitorService;
import com.ncs.chargeguy.util.Server;
import com.ncs.chargeguy.util.Trace;

import java.util.Calendar;

/**
 * Created by Mercury on 03/11/15.
 */
public class ScheduledAlarmReceiver extends WakefulBroadcastReceiver {
    private final static int SECONDS_PER_DAY = 60 * 60 * 24;

    public final static String EXTRA_ALARM_TYPE = "com.ncs.chargeguy.alarm_type";
    public final static String EXTRA_VEHICLE_ID = "com.ncs.chargeguy.vehicle_id";

    public final static String ALARMTYPE_PLUGCHECK = "com.ncs.chargeguy.action_plugcheck";
    public final static String ALARMTYPE_RANGECHECK = "com.ncs.chargeguy.action_range";
    public final static String ALARMTYPE_MONITOR = "com.ncs.chargeguy.monitor";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Load up what we need.
        Singleton.get().setupAndLoadVehicles(context);

        if (!intent.hasExtra(EXTRA_ALARM_TYPE) || !intent.hasExtra(EXTRA_VEHICLE_ID)) {
            Trace.error("Intent is missing our extras, wtf?  Bailing out.");
            return;
        }

        long vehicleID = intent.getLongExtra(EXTRA_VEHICLE_ID, 0);
        Vehicle vehicle = Singleton.get().getVehicleById(vehicleID);
        String alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE);

        if (vehicle == null) {
            Trace.error("Can't find vehicle by id, maybe it was removed?  ID = %d",vehicleID);
            return;
        }

        // Special case for live monitoring alarms: just call the intentservice and bail.
        if (alarmType.equals(ALARMTYPE_MONITOR)) {
            Intent service = new Intent(context, LiveMonitorService.class);
            startWakefulService(context, service);
        }
        else {
            // Ok, definitely schedule everything up again:
            scheduleAllAlarms(context, vehicle, alarmType);

            if (alarmType.equals(ALARMTYPE_RANGECHECK) && vehicle.rangeCheckMiles == 0) {
                Trace.error("Can't do rangecheck alarm with zero miles, user must have interrupted setup");
                return;
            }

            // Ok, validation is good.  Load up the intent service action and let 'em have at it:

            Trace.warn("Process this: alarmtype=%s, vehicle.dn=%s", alarmType, vehicle.displayName);
            Intent service = new Intent(context, ChargeCheckService.class);
            service.putExtra(EXTRA_ALARM_TYPE, alarmType);
            service.putExtra(EXTRA_VEHICLE_ID, vehicleID);

            startWakefulService(context, service);
        }
    }

    public static void scheduleAllAlarms(Context context) {
        scheduleAllAlarms(context, null, null);
    }

    public static void scheduleAllAlarms(Context context, Vehicle currentAlarmVehicle, String currentAlarmType) {
        Trace.debug("SAS.scheduleAllAlarms");

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (Vehicle car : Singleton.get().getVehicles()) {
            PendingIntent plugCheckIntent = createVehicleAlarmIntent(context, car, ALARMTYPE_PLUGCHECK);
            PendingIntent rangeCheckIntent = createVehicleAlarmIntent(context, car, ALARMTYPE_RANGECHECK);

            // Remove any existing alarms for the vehicle:
            manager.cancel(plugCheckIntent);
            manager.cancel(rangeCheckIntent);

            // Zero miles for plug check == check for plug, regardless of range.
            if (car.plugCheckEnabled) {
                scheduleAlarm(manager, plugCheckIntent, car.plugCheckTime, car == currentAlarmVehicle && currentAlarmType.equals(ALARMTYPE_PLUGCHECK));
            }

            // Zero miles for range check == can't do it, operator didn't enter required data.
            if (car.rangeCheckEnabled && car.rangeCheckMiles > 0) {
                scheduleAlarm(manager, rangeCheckIntent, car.rangeCheckTime, car == currentAlarmVehicle && currentAlarmType.equals(ALARMTYPE_RANGECHECK));
            }
        }
    }

    public static void removeAllAlarms(Context context) {
        Trace.debug("SAS.removeAllAlarms");

        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (Vehicle car : Singleton.get().getVehicles()) {
            PendingIntent plugCheckIntent = createVehicleAlarmIntent(context, car, ALARMTYPE_PLUGCHECK);
            PendingIntent rangeCheckIntent = createVehicleAlarmIntent(context, car, ALARMTYPE_RANGECHECK);
            PendingIntent monitorIntent = createVehicleAlarmIntent(context, car, ALARMTYPE_MONITOR);

            // Remove any existing alarms for the vehicle:
            manager.cancel(plugCheckIntent);
            manager.cancel(rangeCheckIntent);
            manager.cancel(monitorIntent);
        }
    }


    private static void scheduleAlarm(AlarmManager manager, PendingIntent intent, int secondsFromMidnight, boolean forceTomorrow) {
        Calendar now = Calendar.getInstance();
        int hourNow = now.get(Calendar.HOUR_OF_DAY);
        int minuteNow = now.get(Calendar.MINUTE);

        int secondsNow = 3600 * hourNow + 60 * minuteNow + now.get(Calendar.SECOND);
        int secondsUntilAlarm = secondsFromMidnight - secondsNow;

        // If we're scheduling an alarm for a time that has already passed today,
        // then this will happen.  Or we might want to force it tomorrow, if we're
        // re-scheduling the current alarm to avoid accidentally scheduling it for
        // in 10 milliseconds or something.

        if (secondsUntilAlarm < 0 || forceTomorrow) {
            secondsUntilAlarm += SECONDS_PER_DAY;
        }

        long millisUntilAlarm = secondsUntilAlarm * 1000;

        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + millisUntilAlarm, intent);
    }

    public static PendingIntent createVehicleAlarmIntent(Context context, Vehicle vehicle, String alarmType) {
        String uniqueAction;   // Keep Android from re-using the intents when only the extras have changed.

        Intent intent = new Intent(context, ScheduledAlarmReceiver.class);
        intent.putExtra(EXTRA_VEHICLE_ID, vehicle.id);
        intent.putExtra(EXTRA_ALARM_TYPE, alarmType);

        uniqueAction = String.format("com.ncs.chargeguy.action_%s_%d", alarmType, vehicle.id);
        intent.setAction(uniqueAction);

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }
}
