package sugar.free.sightremote.adapters;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.view.View;
import android.widget.AdapterView;

import java.text.MessageFormat;

import sugar.free.sightparser.Pref;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightremote.BR;
import sugar.free.sightremote.R;

import static sugar.free.sightparser.Pref.CHANGE_PREFS_SPECIAL_CASE;
import static sugar.free.sightparser.Pref.CHANGE_PREFS_SPECIAL_CASE_DELIMITER;

/**
 * Created by jamorham on 20/02/2018.
 */

public class SlidingWindowViewHelper extends BaseObservable {

    private static final String PREF_SLIDING_WINDOW_MULTIPLIER = "sliding_window_multiplier";
    private static final String PREF_SLIDING_WINDOW_PERIOD = "sliding_window_period";
    private static final String PREF_SLIDING_WINDOW_UNITS = "sliding_window_units";
    private static final String PREF_SLIDING_WINDOW_MS = "sliding-window-ms";
    private static final String PREF_SLIDING_WINDOW_LIMIT = "sliding-window-limit";
    private static final boolean d = false;
    private Pref pref;
    private SightServiceConnector connector;
    private String description;
    private long periodMultiplier;
    private int[] periodValues;
    private String[] periodNames;
    private long period;
    private double units;
    private Context context;
    private long last_ms = -1;
    private double last_total = -1;

    public SlidingWindowViewHelper(Context context, String prefix, SightServiceConnector connector) {
        this.context = context;
        this.pref = Pref.get(context, prefix);
        this.connector = connector;

        this.periodMultiplier = pref.getLong(PREF_SLIDING_WINDOW_MULTIPLIER, 3600000);
        this.period = pref.getLong(PREF_SLIDING_WINDOW_PERIOD, 1);
        this.units = pref.getDouble("sliding_window_units", 0.3);

        periodValues = context.getResources().getIntArray(R.array.PeriodsValues);
        periodNames = context.getResources().getStringArray(R.array.Periods);

        this.description = getDescription();
    }

    @Bindable
    public String getDescription() {
        updateService();
        return MessageFormat.format(
                context.getString(R.string.ask_approval_description_message_format)
                , this.units, this.period, this.periodMultiplier);
    }

    @Bindable
    public String getPeriod() {
        return "" + period;
    }

    public void setPeriod(String period) {
        try {
            this.period = Long.parseLong(period);
            if (this.period == 0) this.period = 1;
            pref.setLong(PREF_SLIDING_WINDOW_PERIOD, this.period);
        } catch (NumberFormatException e) {
            //
        }
        notifyPropertyChanged(BR.description);
    }

    @Bindable
    public String getUnits() {
        return "" + this.units;
    }

    public void setUnits(String units) {
        try {
            this.units = Double.parseDouble(units.trim().replace(",", "."));
            pref.setDouble(PREF_SLIDING_WINDOW_UNITS, this.units);
        } catch (NumberFormatException e) {
            //
        }
        notifyPropertyChanged(BR.description);
    }

    @Bindable
    public int getSelectedPeriodItem() {
        for (int i = 0; i < periodValues.length; i++) {
            if (periodValues[i] == periodMultiplier) return i;
        }
        throw new RuntimeException("Cant find period item");
    }

    public void onSelectedPeriodItem(AdapterView<?> parent, View view, int position, long id) {
        setPeriodMultiplier(getPeriodValue(position));
        notifyPropertyChanged(BR.description);
    }

    private long getPeriodValue(int item) {
        return periodValues[item];
    }

    @Bindable
    public long getPeriodMultiplier() {
        return periodMultiplier;
    }

    public void setPeriodMultiplier(long value) {
        periodMultiplier = value;
        pref.setLong(PREF_SLIDING_WINDOW_MULTIPLIER, value);
        notifyPropertyChanged(BR.periodMultiplier);
    }

    private void updateService() {
        // send to the service process
        final long this_ms = this.period * this.periodMultiplier;
        if ((this_ms != last_ms) || (this.units != last_total)) {
            try {
                synchronized (this) {
                    if (connector != null) {
                        connector.setAuthorized(CHANGE_PREFS_SPECIAL_CASE + CHANGE_PREFS_SPECIAL_CASE_DELIMITER
                                + PREF_SLIDING_WINDOW_MS + CHANGE_PREFS_SPECIAL_CASE_DELIMITER + this_ms, false);
                        connector.setAuthorized(CHANGE_PREFS_SPECIAL_CASE + CHANGE_PREFS_SPECIAL_CASE_DELIMITER
                                + PREF_SLIDING_WINDOW_LIMIT + CHANGE_PREFS_SPECIAL_CASE_DELIMITER + this.units, false);
                        last_ms = this.period * this.periodMultiplier;
                        last_total = this.units;
                    }
                }
            } catch (NullPointerException e) {
                android.util.Log.e("SlidingWindowView", "Caught unfortunate race condition with connector " + e);
            }
        } else {
            if (d) android.util.Log.d("SlidingWindowView", "Values same as last sent");
        }
    }

}
