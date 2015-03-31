package com.ncs.chargeguy.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.activities.CarsActivity;
import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.receivers.ScheduledAlarmReceiver;
import com.ncs.chargeguy.util.Keys;
import com.ncs.chargeguy.util.Trace;

public class LiveMonitorService extends IntentService {
    private final static int MAX_RUNTIME = 1000 * 60 * 60 * 3;             // Stop after 3 hours to avoid hammering Tesla's servers.
    private final static int LIVEMONITOR_REFRESH_TIME = 1000 * 5;          // Check the data model every 5 seconds.
    private final static int ID_NOTIFICATION = 666;                        // Evil AI will kill us all!

    // Bunch of cheezy static fields to make the service look stateful:
    //

    private static Vehicle vehicle = null;
    private static String lastChargeState;
    private static PendingIntent notificationIntent;
    private static PendingIntent alarmIntent;
    private static long startedTimestamp;
    private static long lastChargeUpdate;

    public static Vehicle getVehicle() {
        return LiveMonitorService.vehicle;
    }

    public static void startMonitoring(Context context, Vehicle vehicle) {
        // Save these off in case we get booted out of memory before polling again:
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edits = prefs.edit();
        edits.putLong(Keys.PREF_MONITOR_VEHICLE_ID, vehicle.id);
        edits.putLong(Keys.PREF_MONITOR_TIMESTAMP, SystemClock.elapsedRealtime());
        edits.putString(Keys.PREF_MONITOR_LAST_CHARGE_STATE, vehicle.chargingState);
        edits.commit();

        LiveMonitorService.vehicle = vehicle;
        LiveMonitorService.lastChargeState = vehicle.chargingState;
        LiveMonitorService.startedTimestamp =SystemClock.elapsedRealtime();
        LiveMonitorService.lastChargeUpdate = vehicle.lastChargeUpdate;

        Intent resultIntent = new Intent(context, CarsActivity.class);
        resultIntent.putExtra(CarsActivity.EXTRA_SHOW_VEHICLE, vehicle.id);
        String uniqueAction = String.format("com.ncs.chargeguy.action_monitor_%d", vehicle.id);
        resultIntent.setAction(uniqueAction);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(CarsActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        LiveMonitorService.notificationIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        updateNotification(context, false, vehicle.isChargeComplete(), null);

        if (!vehicle.isChargeComplete()) {
            alarmIntent = ScheduledAlarmReceiver.createVehicleAlarmIntent(context, vehicle, ScheduledAlarmReceiver.ALARMTYPE_MONITOR);
            scheduleNextPoll(context);
        }
        else {
            Trace.debug("Skipping monitor alarm, car is charged - what is the operator doing?");

            // Why did we even bother with this?  Leave the notification there so the operator
            // can dismiss it.

            stopMonitoring(context, false);
        }
    }

    public static void stopMonitoring(Context context, boolean alsoYankNotification) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor edits = prefs.edit();
        edits.remove(Keys.PREF_MONITOR_TIMESTAMP);
        edits.remove(Keys.PREF_MONITOR_VEHICLE_ID);
        edits.commit();

        removeNotification(context);
    }

    private static void removeNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(ID_NOTIFICATION);
    }

    private static void updateNotification(Context context, boolean alsoAlert, boolean allowDismiss, String errorText) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        Uri notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        String title = String.format(context.getString(R.string.monitor_notification_title), vehicle.displayName);
        String message = vehicle.getChargeText(context, false);

        if (vehicle.isCharging()) {
            // Stick the time remaining on there, too.
            message = String.format(context.getString(R.string.monitor_charging_template), message, vehicle.getChargeTimeLeftText(context, true));
        }

        if (errorText != null) {
            message = errorText;
        }

        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle(title);
        builder.setContentText(message);
        builder.setContentIntent(notificationIntent);
        builder.setCategory(Notification.CATEGORY_ALARM);
        builder.setOngoing(!allowDismiss);

        if (alsoAlert) {
            builder.setSound(notifySound);
        }

        Notification note = builder.build();

        if (alsoAlert) {
            note.defaults |= Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(ID_NOTIFICATION, note);
    }

    private static void scheduleNextPoll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + LIVEMONITOR_REFRESH_TIME, alarmIntent);
    }

    // Stub constructor fun time.

    public LiveMonitorService() {
        super("LiveMonitorService");
    }

    // Alarm guy sent this over, do your thing.
    //

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        long vehicleID = prefs.getLong(Keys.PREF_MONITOR_VEHICLE_ID, 0);

        if (vehicleID == 0) {
            Trace.warn("LMS.onHandleIntent invoked, but no vehicle preference - guess we're done");
        }
        else {
            vehicle = Singleton.get().getVehicleById(vehicleID);
            startedTimestamp = prefs.getLong(Keys.PREF_MONITOR_TIMESTAMP, 0);

            // If we've been turned off, then quietly go away.
            if (vehicle != null) {
                try {
                    processMonitorPoll();
                } catch (Exception ex) {
                    Trace.error(ex, "Exception in background monitoring");
                }
            }
            else {
                Trace.warn("LMS.onHandleIntent invoked, but vehicle id %d is no longer present - guess we're done", vehicleID);
            }
        }

        ScheduledAlarmReceiver.completeWakefulIntent(intent);
    }

    void processMonitorPoll() {
        boolean shouldContinue = true;

        // First up, see if we've been running too long.
        if (SystemClock.elapsedRealtime() - startedTimestamp >= MAX_RUNTIME) {
            shouldContinue = false;
            updateNotification(getApplicationContext(), true, true, getString(R.string.monitor_timeout));
        }
        else {
            // See if the vehicle is stale.  The GUI may be present and updating the model,
            // so let's not fire off spurious requests if our data is fresh.

            if (vehicle.shouldRefreshChargeState()) {
                vehicle.updateChargeStateSync(getApplicationContext());
            }

            // The data is fresh, let's do our thing.

            if (vehicle.lastChargeUpdate != lastChargeUpdate) {
                boolean changed = false;

                // Check to see if the status changed, so we make the noise.
                if (vehicle.chargingState != null && lastChargeState == null ||
                    vehicle.chargingState == null && lastChargeState != null ||
                    !vehicle.chargingState.equals(lastChargeState)) {
                    changed = true;
                }

                if (vehicle.isChargeComplete()) {
                    shouldContinue = false;
                }

                updateNotification(getApplicationContext(), changed, !shouldContinue, null);

                // And remember this for next time.
                lastChargeState = vehicle.chargingState;
                lastChargeUpdate = vehicle.lastChargeUpdate;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                prefs.edit().putString(Keys.PREF_MONITOR_LAST_CHARGE_STATE, lastChargeState).commit();
            }

            // If it's complete, let's also stop monitoring now.
        }

        if (shouldContinue) {
            scheduleNextPoll(getApplicationContext());
        }
        else {
            // We're not monitoring any more, bye bye.
            vehicle = null;
        }
    }
}
