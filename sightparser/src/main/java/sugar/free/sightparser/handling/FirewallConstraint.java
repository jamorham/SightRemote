package sugar.free.sightparser.handling;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import sugar.free.sightparser.Pref;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.remote_control.CancelTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.ChangeTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.ExtendedBolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.MultiwaveBolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.SetTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.StandardBolusMessage;


/**
 * Created by jamorham on 01/02/2018.
 *
 * Allow or deny Applayer messages based on a mapped preference boolean
 *
 * Undefined items are allowed by default
 *
 */


public class FirewallConstraint {

    public static final String FIREWALL_STORAGE = "SERVICE_FIREWALL";
    public static final String USER_AUTHORIZATION_SPECIAL_CASE = "USER_AUTHORIZATION";
    public static final String USER_AUTHORIZATION_SPECIAL_CASE_DELIMITER = "^";
    private static final String TAG = "INSIGHTFIREWALL";
    private static final String REQUEST_AUTH_EXTRA = "REQUEST_AUTH_EXTRA";
    private static final String REQUEST_AUTH_UUID = "REQUEST_AUTH_UUID";
    private static final Map<Class, String> block_lookup;
    private static final Map<Class, String> auth_lookup;
    private static final boolean d = false;
    private static final long AUTH_TIMEOUT_MS = 20000;
    private static final String PREF_SLIDING_WINDOW_MS = "sliding-window-ms";
    private static final String PREF_SLIDING_WINDOW_LIMIT = "sliding-window-limit";
    private static volatile String pending_uuid = null;
    private static volatile long waiting_auth = 0;

    static {
        block_lookup = new HashMap<>();
        // these message types will be restricted by the named preference item
        block_lookup.put(StandardBolusMessage.class, "firewall_allow_standard_bolus");
        block_lookup.put(ExtendedBolusMessage.class, "firewall_allow_extended_bolus");
        block_lookup.put(MultiwaveBolusMessage.class, "firewall_allow_extended_bolus");
        block_lookup.put(ChangeTBRMessage.class, "firewall_allow_temporary_basal");
        block_lookup.put(SetTBRMessage.class, "firewall_allow_temporary_basal");
        block_lookup.put(CancelTBRMessage.class, "firewall_allow_temporary_basal");
    }

    static {
        auth_lookup = new HashMap<>();
        // these message types will be restricted by the named preference item
        auth_lookup.put(StandardBolusMessage.class, "firewall_password_boluses");
        auth_lookup.put(ExtendedBolusMessage.class, "firewall_password_boluses");
        auth_lookup.put(MultiwaveBolusMessage.class, "firewall_password_boluses");
    }

    private final Pref pref;
    private SlidingWindowConstraint window;
    private final Map<String, Boolean> authorizations = new ConcurrentHashMap<>();
    private Context context;

    public FirewallConstraint(Context context) {
        this.context = context;
        pref = Pref.get(context, FIREWALL_STORAGE);
        initializeSlidingWindow();
        initializeDefaults();
    }

    private synchronized void initializeSlidingWindow() {
        window = new SlidingWindowConstraint(getLimit(), getWindowMS(), "generic-bolus-restriction", true, context);
    }

    private static double roundDouble(double value, int places) {
        if (places < 0) throw new IllegalArgumentException("Invalid decimal places");
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private double getLimit() {
        try {
            return Double.parseDouble(pref.getString(PREF_SLIDING_WINDOW_LIMIT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long getWindowMS() {
        try {
            return Long.parseLong(pref.getString(PREF_SLIDING_WINDOW_MS, "86400000"));
        } catch (NumberFormatException e) {
            return 86400000;
        }
    }

    private void initializeDefaults() {
        // set unset class types to allow by default
        for (Map.Entry<Class, String> item : block_lookup.entrySet()) {
            if (!pref.isSet(item.getValue())) {
                pref.setBoolean(item.getValue(), true);
            }
        }
    }

    void parsePreference(String packageName) {
        android.util.Log.d(TAG, "Updating preferences");
        pref.parsePrefs(TAG, packageName);
        initializeSlidingWindow(); // this is a bit expensive but shouldn't happen too often
    }

    String descriptionFromAppLayer(AppLayerMessage msg) {
        if (msg instanceof StandardBolusMessage) {
            return "Standard Bolus" + " " + ((StandardBolusMessage) msg).getAmount() + "U";
        }
        // TODO add msgs for extended and multiwave
        // TODO remember to work with activities background task runner

        return "";
    }

    // get the total insulin if this is any kind of bolus
    private float bolusValue(AppLayerMessage msg) {
        if (msg instanceof StandardBolusMessage) {
            return ((StandardBolusMessage) msg).getAmount();
        }
        if (msg instanceof ExtendedBolusMessage) {
            return ((ExtendedBolusMessage) msg).getAmount();
        }
        if (msg instanceof MultiwaveBolusMessage) {
            return ((MultiwaveBolusMessage) msg).getAmount() + ((MultiwaveBolusMessage) msg).getDelayedAmount();
        }
        return 0;
    }

    boolean isAllowed(AppLayerMessage msg) {
        if (msg == null) return true; // not sure if this should be false as its invalid
        final String prefString = block_lookup.get(msg.getClass());
        if (prefString == null) {
            if (d) android.util.Log.e(TAG, "FIREWALL default Allow: " + msg);
            return true; // default allow
        }
        final boolean allow = pref.getBooleanDefaultFalse(prefString);
        if (!allow) {
            android.util.Log.e(TAG, "FIREWALL Blocked: " + msg);
        } else {
            // check if restricted by authentication
            final String authPrefString = auth_lookup.get(msg.getClass());
            if ((authPrefString != null) && pref.getBooleanDefaultFalse(authPrefString)) {
                // if restricted by fingerprint
                final double amount = roundDouble(bolusValue(msg), 3);
                if ((amount > 0) && (window.checkAndAddIfAcceptable(amount))) {
                    android.util.Log.e(TAG, "Sliding window Allow: " + msg);
                    return true;
                }
                return busyWaitForAuthorization(descriptionFromAppLayer(msg));
            }
            android.util.Log.e(TAG, "FIREWALL Allow: " + msg);
        }
        return allow;
    }

    void requestAuth(Context context, String uuid, String reason) {
        android.util.Log.e(TAG, "Requesting user authentication");
        context.startActivity(new Intent()
                .setComponent(new ComponentName("sugar.free.sightremote", "sugar.free.sightremote.activities.security.FingerprintActivity"))
                .putExtra(REQUEST_AUTH_EXTRA, reason)
                .putExtra(REQUEST_AUTH_UUID, uuid)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private String getAuthorization(String reason) {
        final String uuid = UUID.randomUUID().toString();
        requestAuth(context, uuid, reason);
        return uuid;
    }

    private boolean busyWaitForAuthorization(String reason) {
        synchronized (this) {
            if ((pending_uuid != null) && (System.currentTimeMillis() - waiting_auth < AUTH_TIMEOUT_MS * 2)) {
                android.util.Log.d("INSIGHTFIREWALL", "Already a pending uuid waiting: " + pending_uuid);
                return false;
            }
            pending_uuid = getAuthorization(reason);
            waiting_auth = System.currentTimeMillis();
        }

        boolean result = false;
        while (System.currentTimeMillis() - waiting_auth < AUTH_TIMEOUT_MS) {
            android.util.Log.d("INSIGHTFIREWALL", "Busy wait for auth: " + pending_uuid);
            if (authorizations.get(pending_uuid) != null) {
                result = authorizations.get(pending_uuid);
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                //
            }
        }
        authorizations.remove(pending_uuid);
        android.util.Log.d("INSIGHTFIREWALL", "Returning authorization result: " + result);
        pending_uuid = null;
        return result;
    }

    void parseAuthorization(String msg) {
        String tag = "INSIGHTFIREWALL";
        if (msg.startsWith(USER_AUTHORIZATION_SPECIAL_CASE)) {
            final StringTokenizer st = new StringTokenizer(msg, USER_AUTHORIZATION_SPECIAL_CASE_DELIMITER);
            if (st.countTokens() == 3) {
                st.nextToken(); // skip prefix
                final String uuid = st.nextToken();
                final String value = st.nextToken();
                android.util.Log.d(tag, "Setting: " + uuid + " -> " + value);
                if ((uuid != null) && (uuid.length() > 0)) {
                    switch (value) {
                        case "true":
                            authorizations.put(uuid, true);
                            break;
                        case "false":
                            authorizations.put(uuid, false);
                            break;
                    }
                }

            } else {
                android.util.Log.d(tag, "Invalid number of auth tokens: " + st.countTokens());
            }
        }
    }

}
