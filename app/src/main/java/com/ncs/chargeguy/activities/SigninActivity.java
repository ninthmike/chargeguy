package com.ncs.chargeguy.activities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.view.View.OnKeyListener;
import com.ncs.chargeguy.R;
import com.ncs.chargeguy.TypefaceHelper;
import com.ncs.chargeguy.util.Server;
import com.ncs.chargeguy.util.Trace;

/**
 * Created by Mercury on 03/09/15.
 */
public class SigninActivity extends ActionBarActivity implements View.OnClickListener, Server.ServerRequestContainer {
    EditText emailView;
    EditText passwordView;
    Server server;
    ProgressDialog progress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signin);

        progress = new ProgressDialog(this);
        progress.setCancelable(false);
        progress.setMessage(getString(R.string.signing_in));
        server = new Server(this);

        emailView = (EditText) findViewById(R.id.email);
        passwordView = (EditText) findViewById(R.id.password);
        TypefaceHelper.setTextViewTypeface(this, R.id.signin_header, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(this, R.id.signin_button, TypefaceHelper.TYPEFACE_MEDIUM);
        TypefaceHelper.setTextViewTypeface(emailView, TypefaceHelper.TYPEFACE_BOOK);
        TypefaceHelper.setTextViewTypeface(passwordView, TypefaceHelper.TYPEFACE_BOOK);
        findViewById(R.id.signin_button).setOnClickListener(this);

        ((EditText) findViewById(R.id.password)).setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    fireSignin();
                    return true;
                } else {
                    return false;
                }
            }
        });

        TypefaceHelper.styleActionBarTitle(this, R.string.app_name);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.signin_button:
                fireSignin();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        server.setContainer(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        server.abandon();

        if (progress.isShowing()) {
            progress.dismiss();
        }
    }

    void fireSignin() {
        String email = emailView.getText().toString().trim();
        String password = passwordView.getText().toString().trim();

        if (email.indexOf("@") == -1 || password.length() == 0) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setCancelable(false);
            b.setTitle(R.string.signin_failed);
            b.setMessage(R.string.signin_nofields_message);
            b.setPositiveButton(android.R.string.ok, null);
            b.create().show();
            return;
        }

        progress.show();
        server.startAuth(email, password);
    }

    @Override
    public void serverRequestCompleted(boolean success, String request, Server server, String note) {
        Trace.debug("serverRequestCompleted: %s / %s / %s", success, request, note);

        if (request.equals(Server.REQUEST_AUTH)) {
            if (!success) {
                Trace.debug("That was auth failure, damn!");
                progress.dismiss();

                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setCancelable(false);
                b.setTitle(R.string.signin_failed);
                b.setPositiveButton(android.R.string.ok, null);

                if (note.indexOf("401 unauthorized") > -1) {
                    b.setMessage(R.string.signin_unauth);
                } else {
                    b.setMessage(R.string.generic_server_failed_message);
                }

                b.create().show();
            }
            else {
                Trace.debug("That was auth success, getting vehicles list now");
                progress.setMessage(getString(R.string.getting_vehicles));
                server.startGetVehicles();
                // Ok, we auth'd.  Get the vehicles list and save it!
            }
        }

        if (request.equals(Server.REQUEST_VEHICLES)) {
            if (!success) {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setCancelable(false);
                b.setTitle(R.string.generic_server_failed_title);
                b.setPositiveButton(android.R.string.ok, null);
                b.setMessage(R.string.generic_server_failed_message);
                b.create().show();
            }
            else {
                progress.dismiss();

                // Server object saved the vehicles to disk/memory, so let's fire it up.
                startActivity(new Intent(this, CarsActivity.class));
                finish();
            }

        }
    }
}
