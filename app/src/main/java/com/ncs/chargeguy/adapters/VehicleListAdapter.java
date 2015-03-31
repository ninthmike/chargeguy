package com.ncs.chargeguy.adapters;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ncs.chargeguy.R;
import com.ncs.chargeguy.TypefaceHelper;
import com.ncs.chargeguy.model.Singleton;
import com.ncs.chargeguy.model.Vehicle;
import com.ncs.chargeguy.util.Trace;

import java.util.List;

/**
 * Created by Mercury on 03/09/15.
 */
public class VehicleListAdapter extends BaseAdapter {
    @Override
    public int getCount() {
        return Singleton.get().getVehicles().size();
    }

    @Override
    public Object getItem(int position) {
        return Singleton.get().getVehicles().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            Context context = parent.getContext();
            Vehicle vehicle = (Vehicle) getItem(position);
            boolean newView = false;

            if (convertView == null) {
                newView = true;
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.vehicle_list_item, null, false);
            }

            // Find the views:
            TextView displayName = (TextView) convertView.findViewById(R.id.display_name);
            TextView rangeValue = (TextView) convertView.findViewById(R.id.range_value);
            TextView chargeValue = (TextView) convertView.findViewById(R.id.charge_value);
            TextView chargeTimeLeft = (TextView) convertView.findViewById(R.id.charge_time_left);

            // Style it:
            if (newView) {
                TypefaceHelper.setTextViewTypeface(convertView.findViewById(R.id.range_header), TypefaceHelper.TYPEFACE_MEDIUM);
                TypefaceHelper.setTextViewTypeface(convertView.findViewById(R.id.charge_header), TypefaceHelper.TYPEFACE_MEDIUM);
                TypefaceHelper.setTextViewTypeface(displayName, TypefaceHelper.TYPEFACE_MEDIUM);
                TypefaceHelper.setTextViewTypeface(rangeValue, TypefaceHelper.TYPEFACE_BOOK);
                TypefaceHelper.setTextViewTypeface(chargeValue, TypefaceHelper.TYPEFACE_BOOK);
                TypefaceHelper.setTextViewTypeface(chargeTimeLeft, TypefaceHelper.TYPEFACE_BOOK);
            }

            // Load in data:
            displayName.setText(vehicle.displayName);
            rangeValue.setText(vehicle.getRangeText(context));
            chargeValue.setText(Html.fromHtml(vehicle.getChargeText(context, true)));

            if (vehicle.isCharging()) {
                chargeTimeLeft.setVisibility(View.VISIBLE);
                chargeTimeLeft.setText(vehicle.getChargeTimeLeftText(context, false));
            }
            else {
                chargeTimeLeft.setVisibility(View.GONE);
            }

            return convertView;
        }
        catch (Exception ex) {
            Trace.error(ex, "VLA.getView");
            return null;
        }
    }
}
