package com.ncs.chargeguy.services;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.activities.CarsActivity;
import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.receivers.ScheduledAlarmReceiver;
import com.ncs.chargeguy.util.Trace;

/**
 * Created by Mercury on 03/11/15.
 */
public class ChargeCheckService extends IntentService implements MediaPlayer.OnCompletionListener {

    private final static int ID_NOTIFICATION = 11;

    boolean mediaDone = false;

    public ChargeCheckService() {
        super("ChargeCheckService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Also, put the whole thing in a general catch block so that we can
        // properly complete the wakeful intent, even if it shit goes south.
        // Otherwise, the CPU will never sleep and that is very bad.
        //

        try {
            Vehicle vehicle = Singleton.get().getVehicleById(intent.getLongExtra(ScheduledAlarmReceiver.EXTRA_VEHICLE_ID, 0));
            String alarmType = intent.getStringExtra(ScheduledAlarmReceiver.EXTRA_ALARM_TYPE);

            vehicle.updateChargeStateSync(getApplicationContext());

            finishCheck(vehicle, alarmType);
        }
        catch (Exception ex) {
            Trace.error(ex, "Exception processing charge request!");
        }

        ScheduledAlarmReceiver.completeWakefulIntent(intent);
    }

    void finishCheck(Vehicle vehicle, String alarmType) {
        // Let's raise some notifications!  If we need to...
        //
        if (!vehicle.isChargeStateKnown()) {
            raiseFailedNotification(vehicle);
        }
        else {
            if (alarmType.equals(ScheduledAlarmReceiver.ALARMTYPE_PLUGCHECK)) {
                checkConnection(vehicle);
            }

            if (alarmType.equals(ScheduledAlarmReceiver.ALARMTYPE_RANGECHECK)) {
                checkRange(vehicle);
            }
        }

        // Save the list if we modified it.
        if (Singleton.get().isListDirty()) {
            Singleton.get().saveVehicles(getApplicationContext());
        }
    }

    void raiseFailedNotification(Vehicle vehicle) {
        raiseNotification(String.format(getString(R.string.notification_failed), vehicle.displayName), Notification.PRIORITY_HIGH, true);
    }

    void raiseNotification(String text, int priority, boolean playSound) {
        Trace.warn("Raise notification: %s", text);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
        Uri notifySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        builder.setContentText(text);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle(getString(R.string.notification_title));
        builder.setContentIntent(createLaunchIntent());
        builder.setCategory(Notification.CATEGORY_ALARM);

        if (playSound) {
            builder.setSound(notifySound);
        }

        builder.setPriority(priority);

        Notification note = builder.build();
        note.defaults |= Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS;

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(ID_NOTIFICATION, note);
    }

    void checkConnection(Vehicle vehicle) {
        // First, see if we can bail out because of range.
        if (vehicle.plugCheckMiles > 0 && vehicle.getRangeNumber() >= vehicle.plugCheckMiles) {
            Trace.debug("Vehicle has range above warning level, skipping connection check");
            return;
        }

        // Ok, now see if it's actually plugged in.
        if (vehicle.isPluggedIn()) {
            Trace.debug("Vehicle is connected, no need for warning, state = %s", vehicle.getChargeText(getApplicationContext(), false));
            return;
        }

        // Looks like we should complain.
        raiseNotification(String.format(getString(R.string.notification_disconnected), vehicle.displayName), Notification.PRIORITY_DEFAULT, true);
    }

    void checkRange(Vehicle vehicle) {
        // All we care about is range, so check it:
        if (vehicle.getRangeNumber() >= vehicle.rangeCheckMiles) {
            Trace.debug("Vehicle has range above warning level, nothing to see here");
            return;
        }

        // Looks like we should complain.
        raiseNotification(String.format(getString(R.string.notification_range), vehicle.displayName), Notification.PRIORITY_HIGH, !vehicle.extraNoisy);

        // If we're extra noisy, then queue that guy up and play it at max volume.  Also wait for it to finish.
        //

        if (vehicle.extraNoisy) {
            try {
                MediaPlayer player = new MediaPlayer();
                Resources res = getResources();
                AssetFileDescriptor afd = res.openRawResourceFd(R.raw.hullbreach);

                mediaDone = false;

                player.setAudioStreamType(AudioManager.STREAM_ALARM);
                player.setVolume(1.0f, 1.0f);
                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                player.setOnCompletionListener(this);
                player.prepare();
                player.start();

                int futility = 0;

                // Wait up to ten seconds for it to complete.
                while (futility < 4 * 10 && !mediaDone) {
                    Thread.sleep(250);

                    futility++;
                }

                player.release();
            }
            catch (Exception ex) {
                Trace.error(ex, "Exception setting off extra noisy notification");
            }
        }
    }

    PendingIntent createLaunchIntent() {
        Intent resultIntent = new Intent(this, CarsActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(CarsActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        return resultPendingIntent;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mediaDone = true;
    }
}
