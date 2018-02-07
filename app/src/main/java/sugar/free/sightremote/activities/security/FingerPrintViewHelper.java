package sugar.free.sightremote.activities.security;

import android.content.Context;
import android.databinding.BaseObservable;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.widget.Toast;

import sugar.free.sightparser.Pref;
import sugar.free.sightremote.R;
import sugar.free.sightremote.adapters.PrefsView;

/**
 * Created by jamorham on 04/02/2018.
 */

public class FingerPrintViewHelper extends BaseObservable {

    private static final String PREF_FINGERPRINT_PASSWORD = "pref-fingerprint-password";
    private static Pref pref;
    public String password = "";
    private FingerprintManager mFingerprintManager;
    private Context context;

    public FingerPrintViewHelper(Context context, PrefsView pview) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mFingerprintManager = context.getSystemService(FingerprintManager.class);
        }
        // TODO prefix
        if (pref == null) pref = Pref.get(context, "ACTIVITY_FIREWALL");

        if (pview != null) {
            if (getStoredPassword().length() == 0) {
                pview.setbool("firewall_password_boluses", false);
            }
        }
    }

    public boolean supportsFingerprint() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isFingerprintAuthAvailable();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isFingerprintAuthAvailable() {
        // The line below prevents the false positive inspection from Android Studio
        // noinspection ResourceType
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isFingerprintAuthPossible() {
        // The line below prevents the false positive inspection from Android Studio
        // noinspection ResourceType
        return mFingerprintManager.isHardwareDetected();
    }

    String getStoredPassword() {
        return pref.getString(PREF_FINGERPRINT_PASSWORD, "");
    }

    public void setStoredPassword() {
        if (password == null) return;
        if (password.length() > 0) {
            pref.setString(PREF_FINGERPRINT_PASSWORD, password);
            Toast.makeText(context, R.string.password_set, Toast.LENGTH_LONG).show();
        }
    }
}
