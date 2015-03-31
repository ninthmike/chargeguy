package com.ncs.chargeguy.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.TypefaceHelper;
import com.ncs.chargeguy.adapters.VehicleListAdapter;
import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.receivers.ScheduledAlarmReceiver;
import com.ncs.chargeguy.services.LiveMonitorService;
import com.ncs.chargeguy.util.ButteryProgressBar;
import com.ncs.chargeguy.util.CustomTypefaceSpan;
import com.ncs.chargeguy.util.Keys;
import com.ncs.chargeguy.util.Server;
import com.ncs.chargeguy.util.Trace;

public class CarsActivity extends ActionBarActivity implements Server.ServerRequestContainer, AdapterView.OnItemClickListener {
    public final static String EXTRA_SHOW_VEHICLE = "com.ncs.chargeguy.show_vehicle";

    private final static int UPDATE_CHECK_INTERVAL = 1000;   // Check the model every second to see if something is stale.
    private final static int ERROR_BACKOFF_MS = 5000;        // If Tesla returns a 503, wait this long before trying again.

    public static boolean quickFinish = false;   // Somebody may want us dead!

    ProgressDialog progress;
    Server server;
    ListView list;
    VehicleListAdapter adapter;
    ButteryProgressBar butter;
    Handler handler = new Handler();
    boolean requestFired = false;

    Runnable checkNextRequest = new Runnable() {
        @Override
        public void run() {
            fireNextRequest();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Trace.warn("CA.onCreate invoked");

        Singleton.setup(this);

        setContentView(R.layout.cars);

        butter = (ButteryProgressBar) findViewById(R.id.butter_progress);
        list = (ListView) findViewById(R.id.cars_list);
        list.setOnItemClickListener(this);

        server = new Server(this);
        progress = new ProgressDialog(this);
        progress.setCancelable(false);

        // If we came in via a notification tap (monitoring), then go directly to
        // that vehicle's alarm config.
        //

        if (getIntent().hasExtra(EXTRA_SHOW_VEHICLE)) {
            long id = getIntent().getLongExtra(EXTRA_SHOW_VEHICLE, 0);

            // Find the vehicle index so we can open the alarm guy:
            for (int i = 0; i < Singleton.get().getVehicles().size(); i++) {
                Vehicle one = Singleton.get().getVehicles().get(i);

                if (one.id == id) {
                    AlarmActivity.startActivity(this, i);
                    break;
                }
            }
        }

        TypefaceHelper.styleActionBarTitle(this, R.string.app_name);
    }

    @Override
    public void onResume() {
        super.onResume();

        // quickFinish is used when the alarm activity starts active monitoring.  In that case,
        // we're going to background, so let's just finish the activity here and let the operator
        // on his way.

        if (quickFinish) {
            quickFinish = false;
            finish();
            return;
        }

        server.setContainer(this);

        if (!Singleton.get().hasLogin(this)) {
            Trace.debug("No login present in CarsActivity.onResume, going to signin");
            startActivity(new Intent(this, SigninActivity.class));
            finish();
            return;
        }

        if (!Singleton.get().hasVehicles()) {
            Singleton.get().loadVehicles(this);
        }

        if (!Singleton.get().hasVehicles()) {
            Singleton.get().clearLogin(this);
            startActivity(new Intent(this, SigninActivity.class));
            finish();
            return;
        }

        Singleton.get().markAllVehiclesChargeStatusStale();

        if (Singleton.get().isAccessTokenOld(this)) {
            Trace.warn("Access token is old, refreshing it");
            progress.setMessage(getString(R.string.signing_in));
            progress.show();
            server.startAuth(Singleton.get().getStoredUsername(this), Singleton.get().getStoredPassword(this));
        }
        else {
            fireNextRequest();
        }

        ScheduledAlarmReceiver.scheduleAllAlarms(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        server.abandon();
        handler.removeCallbacks(checkNextRequest);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.cars, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_signout:
                ScheduledAlarmReceiver.removeAllAlarms(this);
                if (LiveMonitorService.getVehicle() != null) {
                    LiveMonitorService.stopMonitoring(this, true);
                }

                server.abandon();
                Singleton.get().clearLogin(this);
                startActivity(new Intent(this, SigninActivity.class));
                finish();
                break;
        }

        return true;
    }

    @Override
    public void serverRequestCompleted(boolean success, String request, Server server, String note) {
        if (request.equals(Server.REQUEST_AUTH)) {
            progress.dismiss();

            if (success) {
                fireNextRequest();
            }
            else {

                // Couldn't refresh token, this is bad.
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setCancelable(false);
                b.setTitle(R.string.signin_failed);
                b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Singleton.get().clearLogin(CarsActivity.this);
                        startActivity(new Intent(CarsActivity.this, SigninActivity.class));
                        finish();
                    }
                });

                b.setMessage(R.string.refresh_token_failed);
                b.create().show();
            }
        }

        if (request.equals(Server.REQUEST_GUI_TEMPLATE) || request.equals(Server.REQUEST_CHARGE_TEMPLATE)) {
            if (success) {
                fireNextRequest();
            }
            else {
                if (note.indexOf("HTTP Server Error 503") > -1) {
                    butter.setVisibility(View.INVISIBLE);
                    Trace.warn("503 returned by Tesla, will retry soon");

                    // This happens a lot, just silently ignore it.  The charge timer will become
                    // stale on a vehicle and cause another request to fire.

                    handler.postDelayed(checkNextRequest, ERROR_BACKOFF_MS);
                }
                else {
                    showNetworkError();
                }
            }
        }
    }

    void fireNextRequest() {
        if (!Singleton.get().hasVehicles()) {
            // Oops, must be signing out!
            return;
        }

        for (Vehicle car : Singleton.get().getVehicles()) {
            // See if the GUI pref is old for this vehicle.
            if (car.isGUIPrefStale()) {
                butter.setVisibility(View.VISIBLE);
                server.startGetGUIPrefs(car);
                requestFired = true;
                return;

            }

            // See if the charge status is old for this vehicle.
            if (car.shouldRefreshChargeState()) {
                butter.setVisibility(View.VISIBLE);
                server.startGetChargeState(car);
                requestFired = true;
                return;
            }
        }


        // Nothing to check?  Awesome.  Save the vehicles list if we did anything.
        if (Singleton.get().isListDirty()) {
            Singleton.get().saveVehicles(this);
        }

        if (requestFired) {
            requestFired = false;

            // Get rid of animated indicators that may be showing:
            butter.setVisibility(View.INVISIBLE);
            if (progress.isShowing()) {
                progress.dismiss();
            }

            // Also, show the list.
            if (adapter == null) {
                adapter = new VehicleListAdapter();
                list.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }

            // Throw up the intro dialog if the operator has never seen it, also:
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            if (!prefs.getBoolean(Keys.PREF_SHOWED_INTRO, false)) {
                prefs.edit().putBoolean(Keys.PREF_SHOWED_INTRO, true).commit();
                showIntroDialog();
            }
        }

        // Queue it up:
        handler.postDelayed(checkNextRequest, UPDATE_CHECK_INTERVAL);
    }

    void showNetworkError() {
        butter.setVisibility(View.INVISIBLE);
        progress.dismiss();
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setTitle(R.string.generic_server_failed_title);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fireNextRequest();
                        dialog.dismiss();
                    }
                });
        b.setMessage(R.string.generic_server_failed_message);
        b.create().show();
    }

    void showIntroDialog() {
        progress.dismiss();
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setTitle(R.string.intro_title);
        b.setPositiveButton(android.R.string.ok, null);
        b.setMessage(R.string.intro_message);
        b.create().show();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        AlarmActivity.startActivity(this, position);
    }
}
