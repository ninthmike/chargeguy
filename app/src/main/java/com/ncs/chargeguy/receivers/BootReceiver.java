package com.ncs.chargeguy.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.util.Trace;

/**
 * Created by Mercury on 03/11/15.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Trace.warn("BootReceiver got an event, scheduling all alarms");
        Singleton.get().setupAndLoadVehicles(context);

        if (Singleton.get().hasVehicles()) {
            ScheduledAlarmReceiver.scheduleAllAlarms(context);
        }
    }
}
