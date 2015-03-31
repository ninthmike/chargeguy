package com.ncs.chargeguy.activities;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.TypefaceHelper;
import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.receivers.ScheduledAlarmReceiver;
import com.ncs.chargeguy.services.LiveMonitorService;

/**
 * Created by Mercury on 03/10/15.
 */
public class AlarmActivity extends ActionBarActivity implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {
    private final static int FLASH_SPEED_MS = 400;

    boolean showingRedBolt = true;
    Vehicle vehicle;
    CheckBox plugCheckEnable;
    TextView plugCheckTime;
    EditText plugCheckRange;
    CheckBox rangeCheckEnable;
    TextView rangeCheckTime;
    EditText rangeCheckRange;
    CheckBox rangeCheckExtraNoisy;
    int lastTimeClicked;
    Handler handler = new Handler();

    Runnable flashBolt = new Runnable() {
        @Override
        public void run() {
            if (!isMonitorOnThisVehicle()) {   // Oops, we're all done.
                showingRedBolt = true;
                invalidateOptionsMenu();
            }
            else {
                showingRedBolt = !showingRedBolt;
                invalidateOptionsMenu();
                handler.postDelayed(flashBolt, FLASH_SPEED_MS);

            }
        }
    };

    private final static String EXTRA_VEHICLE_INDEX = "com.ncs.chargeguy.vehicle_index";

    public static void startActivity(Context context, int vehicleIndex) {
        Intent i = new Intent(context, AlarmActivity.class);
        i.putExtra(EXTRA_VEHICLE_INDEX, vehicleIndex);
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TextView plugCheckUnits;
        TextView rangeCheckUnits;

        super.onCreate(savedInstanceState);
        int vehicleIndex = getIntent().getIntExtra(EXTRA_VEHICLE_INDEX, -1);
        vehicle = Singleton.get().getVehicles().get(vehicleIndex);

        TypefaceHelper.styleActionBarTitle(this, vehicle.displayName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.alarm);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // Grab some references to hold:
        plugCheckEnable = (CheckBox) findViewById(R.id.plugcheck_enabled);
        plugCheckRange = (EditText) findViewById(R.id.plugcheck_range_value);
        plugCheckTime = (TextView) findViewById(R.id.plugcheck_time_value);
        plugCheckUnits = (TextView) findViewById(R.id.plugcheck_range_units);
        rangeCheckEnable = (CheckBox) findViewById(R.id.rangecheck_enabled);
        rangeCheckRange = (EditText) findViewById(R.id.rangecheck_range_value);
        rangeCheckTime = (TextView) findViewById(R.id.rangecheck_time_value);
        rangeCheckUnits = (TextView) findViewById(R.id.rangecheck_range_units);
        rangeCheckExtraNoisy = (CheckBox) findViewById(R.id.rangecheck_extranoisy);

        // Style up a bunch of stuff:
        TypefaceHelper.setTextViewTypeface(plugCheckEnable, TypefaceHelper.TYPEFACE_MEDIUM);
        TypefaceHelper.setTextViewTypeface(plugCheckTime, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(plugCheckRange, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(plugCheckUnits, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(this, R.id.plugcheck_time_header, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(rangeCheckEnable, TypefaceHelper.TYPEFACE_MEDIUM);
        TypefaceHelper.setTextViewTypeface(rangeCheckTime, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(rangeCheckRange, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(rangeCheckUnits, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(this, R.id.rangecheck_time_header, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(rangeCheckExtraNoisy, TypefaceHelper.TYPEFACE_BOOK);

        // Listen to this, if you can:
        plugCheckEnable.setOnCheckedChangeListener(this);
        plugCheckTime.setOnClickListener(this);
        rangeCheckEnable.setOnCheckedChangeListener(this);
        rangeCheckTime.setOnClickListener(this);
        rangeCheckExtraNoisy.setOnCheckedChangeListener(this);

        // Assign values to fields:
        plugCheckUnits.setText(vehicle.getRangeUnitsText(this));
        setTimeTextView(plugCheckTime, vehicle.plugCheckTime);
        setRangeEditText(plugCheckRange, vehicle.plugCheckMiles);
        plugCheckEnable.setChecked(vehicle.plugCheckEnabled);

        rangeCheckUnits.setText(vehicle.getRangeUnitsText(this));
        setTimeTextView(rangeCheckTime, vehicle.rangeCheckTime);
        setRangeEditText(rangeCheckRange, vehicle.rangeCheckMiles);
        rangeCheckEnable.setChecked(vehicle.rangeCheckEnabled);
        rangeCheckExtraNoisy.setChecked(vehicle.extraNoisy);

        // Update the various chunks of enabled/disabled fields according to user options:
        enableControls();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.alarms, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem monitor = menu.findItem(R.id.action_watch);

        if (showingRedBolt) {
            monitor.setIcon(R.drawable.ic_redbolt);
        }
        else {
            monitor.setIcon(R.mipmap.ic_launcher);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_watch:
                if (LiveMonitorService.getVehicle() == vehicle) {
                    showStopMonitorDialog();
                }
                else {
                    if (LiveMonitorService.getVehicle() != null) {
                        showOtherMonitorDialog();
                    }
                    else {
                        showStartMonitorDialog();
                    }
                }
                break;

            case android.R.id.home:
                if (canSave()) {
                    finish();
                }
                break;
        }

        return true;
    }

    void showStopMonitorDialog() {
        LiveMonitorService.stopMonitoring(this, true);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setTitle(R.string.monitor_dialog_title);
        b.setMessage(R.string.monitor_dialog_stopmessage);
        b.setPositiveButton(android.R.string.ok, null);
        b.create().show();
    }

    void showStartMonitorDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setTitle(R.string.monitor_dialog_title);
        b.setMessage(R.string.monitor_dialog_message);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        LiveMonitorService.startMonitoring(AlarmActivity.this, vehicle);
                        CarsActivity.quickFinish = true;
                        finish();
                        dialog.dismiss();
                    }
                });
        b.setNegativeButton(android.R.string.cancel, null);
        b.create().show();
    }

    void showOtherMonitorDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setTitle(R.string.monitor_dialog_title);
        b.setMessage(R.string.monitor_dialog_othermessage);
        b.setPositiveButton(android.R.string.ok, null);
        b.create().show();
    }

    void setTimeTextView(TextView target, int timeValueInSeconds) {
        int hours = timeValueInSeconds / 3600;
        int minutes = (timeValueInSeconds % 3600) / 60;
        String contents;

        if (DateFormat.is24HourFormat(this)) {
            contents = String.format("%02d:%02d", hours, minutes);
        }
        else {
            String ampm = getString(R.string.am);

            // Demote 1300+ to pm.
            if (hours > 12) {
                hours -= 12;
                ampm = getString(R.string.pm);
            }

            // Promote midnight to 12am.
            if (hours == 0) {
                hours = 12;
            }

            contents = String.format("%02d:%02d %s", hours, minutes, ampm);
        }

        target.setText(contents);
    }

    void setRangeEditText(EditText edit, int numericValue) {
        if (numericValue == 0) {
            edit.setText("");
        }
        else {
            edit.setText(Integer.toString(numericValue));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isMonitorOnThisVehicle()) {
            handler.postDelayed(flashBolt, FLASH_SPEED_MS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        handler.removeCallbacks(flashBolt);

        // Grab numeric fields that weren't picked up by event handlers.
        try {
            vehicle.plugCheckMiles = Integer.parseInt(plugCheckRange.getText().toString());
        }
        catch (NumberFormatException nex) {
            // If we failed, just set it to zero.  Alarm logic can handle it.
            vehicle.plugCheckMiles = 0;
        }

        try {
            vehicle.rangeCheckMiles = Integer.parseInt(rangeCheckRange.getText().toString());
        }
        catch (NumberFormatException nex) {
            // If we failed, just set it to zero.  Alarm logic can handle it.
            vehicle.rangeCheckMiles = 0;
        }

        if (Singleton.get().isListDirty()) {
            Singleton.get().saveVehicles(this);
        }

        ScheduledAlarmReceiver.scheduleAllAlarms(this);
    }

    @Override
    public void onBackPressed() {
        if (canSave()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.plugcheck_enabled:
                vehicle.plugCheckEnabled = isChecked;
                enableControls();
                Singleton.get().setListDirty();
                break;

            case R.id.rangecheck_enabled:
                vehicle.rangeCheckEnabled = isChecked;
                enableControls();
                Singleton.get().setListDirty();
                break;

            case R.id.rangecheck_extranoisy:
                vehicle.extraNoisy = isChecked;
                Singleton.get().setListDirty();
                break;
        }
    }

    void enableControls() {
        findViewById(R.id.plugcheck_range_value).setEnabled(vehicle.plugCheckEnabled);
        findViewById(R.id.plugcheck_time_value).setEnabled(vehicle.plugCheckEnabled);
        findViewById(R.id.rangecheck_range_value).setEnabled(vehicle.rangeCheckEnabled);
        findViewById(R.id.rangecheck_time_value).setEnabled(vehicle.rangeCheckEnabled);
        findViewById(R.id.rangecheck_extranoisy).setEnabled(vehicle.rangeCheckEnabled);
    }

    void openTimePicker(final int lastTimeClicked, int existingValue) {
        this.lastTimeClicked = lastTimeClicked;
        TimePickerDialog picker;
        int hour = existingValue / 3600;
        int minute = (existingValue % 3600) / 60;

        picker = new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker timePicker, int selectedHour, int selectedMinute) {
                Singleton.get().setListDirty();
                int newValue = selectedHour * 3600 + selectedMinute * 60;

                if (lastTimeClicked == R.id.plugcheck_time_value) {
                    vehicle.plugCheckTime = newValue;
                    setTimeTextView(plugCheckTime, vehicle.plugCheckTime);
                }

                if (lastTimeClicked == R.id.rangecheck_time_value) {
                    vehicle.rangeCheckTime = newValue;
                    setTimeTextView(rangeCheckTime, vehicle.rangeCheckTime);
                }
            }
        }, hour, minute, DateFormat.is24HourFormat(this));

        picker.setTitle(getString(R.string.select_time));
        picker.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.plugcheck_time_value:
                openTimePicker(R.id.plugcheck_time_value, vehicle.plugCheckTime);
                break;

            case R.id.rangecheck_time_value:
                openTimePicker(R.id.rangecheck_time_value, vehicle.rangeCheckTime);
                break;
        }
    }

    boolean canSave() {
        if (vehicle.rangeCheckEnabled) {
            String milesValue = rangeCheckRange.getText().toString();
            boolean showDialog = false;

            try {
                if (milesValue.length() == 0 || Integer.parseInt(milesValue) == 0) {
                    showDialog = true;
                }
            }
            catch (NumberFormatException nex) {
                showDialog = true;
            }

            if (showDialog) {
                rangeCheckRange.requestFocus();
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setCancelable(false);
                b.setTitle(R.string.range_miles_req_title);
                b.setPositiveButton(android.R.string.ok, null);
                b.setMessage(R.string.range_miles_req_message);
                b.create().show();
                return false;
            }
        }
        return true;
    }

    boolean isMonitorOnThisVehicle() {
        return LiveMonitorService.getVehicle() == vehicle;
    }


}
