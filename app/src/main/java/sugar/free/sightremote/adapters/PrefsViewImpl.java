package sugar.free.sightremote.adapters;

import android.content.Context;
import android.support.annotation.NonNull;

import sugar.free.sightparser.Pref;
import sugar.free.sightparser.handling.SightServiceConnector;

import static sugar.free.sightparser.Pref.CHANGE_PREFS_SPECIAL_CASE;
import static sugar.free.sightparser.Pref.CHANGE_PREFS_SPECIAL_CASE_DELIMITER;

/**
 * Created by jamorham on 01/02/2018.
 */

public class PrefsViewImpl extends ObservableArrayMapNoNotify<String, Boolean> implements PrefsView {
    private final Pref pref;
    private final SightServiceConnector connector;

    public PrefsViewImpl(Context context, String prefix, SightServiceConnector connector) {
        this.pref = Pref.get(context, prefix);
        this.connector = connector;
    }

    public boolean getbool(String name) {
        return pref.getBooleanDefaultFalse(name);
    }

    public void setbool(String name, boolean value) {
        pref.setBoolean(name, value);
        put(name, value);

        // send to the service process
        try {
            if (connector != null) {
                connector.setAuthorized(CHANGE_PREFS_SPECIAL_CASE + CHANGE_PREFS_SPECIAL_CASE_DELIMITER
                        + name + CHANGE_PREFS_SPECIAL_CASE_DELIMITER + value, false);
            }
        } catch (NullPointerException e) {
            android.util.Log.e("PrefsView", "Caught unfortunate race condition with connector " + e);
        }
    }

    public void togglebool(String name) {
        setbool(name, !getbool(name));
    }

    @NonNull
    @Override
    public Boolean get(Object key) {
        Boolean value = super.get(key);
        if (value == null) {
            value = getbool((String) key);
            super.putNoNotify((String) key, value);
        }
        return value;
    }

}
