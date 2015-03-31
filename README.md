# Chargeguy
Charge status monitoring tool for Tesla Model S and Android devices.

# Requirements
- Android device (4.0.4 or newer)
- Tesla Model S automobile

# Overview
ChargeGuy monitors your car on two scheduled alarms to check for the car to be plugged in and to have a minimum range, regardless of plug status.

In addition, ChargeGuy can monitor the car's charge status in real-time, notifying you immediately when it changes.

# Before submitting a pull request
Keep in mind that ChargeGuy is inherently based on bad design: we are polling Tesla's servers to make up for the fact that there is
no notification service for Android.  That's why the monitoring stops after a few hours and the alarms only fire twice per day (per car).

That means we shouldn't be hammering them with a ton of requests per hour, since each installation of ChargeGuy will multiply that.  So if you
add a bunch of stuff that queries Tesla every few minutes, I'm not going to merge it.  And if you do poll faster than regular for your own
use, be a sport and change the user-agent that is sent up in ServerRequestTask.doServerRequest.
That way Tesla can ban your app without impacting everyone else.

