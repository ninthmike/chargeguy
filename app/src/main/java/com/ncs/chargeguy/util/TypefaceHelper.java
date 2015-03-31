package com.ncs.chargeguy;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.support.v7.app.ActionBarActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.View;
import android.widget.TextView;

import com.ncs.chargeguy.util.CustomTypefaceSpan;

import java.util.HashMap;

/***
 * Lightweight helper methods to simplify applying custom fonts to various
 * controls on Android versions less than 4.1.
 *
 * @author Michael Donnelly
 *
 */
public final class TypefaceHelper {
    public static final String TYPEFACE_BOOK = "book";
    public static String TYPEFACE_BOOKITALIC = "bookitalic";
    public static String TYPEFACE_BOLD = "bold";
    public static String TYPEFACE_MEDIUM = "medium";
    public static String TYPEFACE_MEDIUMITALIC = "mediumitalic";
    private static HashMap<String, Typeface> typefaces = null;

    public static void setTextViewTypeface(Activity activity, int id, String typefaceName) {
        setTextViewTypeface(activity.findViewById(id), typefaceName);
    }
    public static void setTextViewTypeface(View theView, String typefaceName) {
        if (typefaces == null) {
            setupTypefaces(theView.getContext().getAssets());
        }

        ((TextView) theView).setTypeface(typefaces.get(typefaceName));
    }

    public static void styleActionBarTitle(ActionBarActivity activity, int resID) {
        styleActionBarTitle(activity, activity.getString(resID));
    }

    public static void styleActionBarTitle(ActionBarActivity activity, String newTitle) {
        if (typefaces == null) {
            setupTypefaces(activity.getAssets());
        }

        SpannableString s = new SpannableString(newTitle);

        s.setSpan(new CustomTypefaceSpan("dobra", typefaces.get(TypefaceHelper.TYPEFACE_MEDIUM)),
                                          0, s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        activity.getSupportActionBar().setTitle(s);
    }

    private static void setupTypefaces(AssetManager assets) {
        if (typefaces != null) {   // Already did it.
            return;
        }

        typefaces = new HashMap<String, Typeface>();
        typefaces.put(TYPEFACE_BOOK, Typeface.createFromAsset(assets, "Dobra-Book.otf"));
        typefaces.put(TYPEFACE_BOOKITALIC, Typeface.createFromAsset(assets, "Dobra-Book-Italic.otf"));
        typefaces.put(TYPEFACE_BOLD, Typeface.createFromAsset(assets, "Dobra-Bold.otf"));
        typefaces.put(TYPEFACE_MEDIUM, Typeface.createFromAsset(assets, "Dobra-Medium.otf"));
        typefaces.put(TYPEFACE_MEDIUMITALIC, Typeface.createFromAsset(assets, "Dobra-Medium-Italic.otf"));
    }
}
