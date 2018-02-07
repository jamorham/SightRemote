package sugar.free.sightremote.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import java.util.UUID;

import sugar.free.sightparser.handling.FirewallConstraint;
import sugar.free.sightremote.R;
import sugar.free.sightremote.activities.security.FingerPrintViewHelper;
import sugar.free.sightremote.activities.security.FingerprintActivity;
import sugar.free.sightremote.adapters.PrefsViewImpl;
import sugar.free.sightremote.databinding.ActivityFirewallBinding;

/**
 * Created by jamorham on 30/01/2018.
 *
 * Activity to manage firewall preferences
 *
 * Layout and wiring is in activity_firewall.xml
 */

public class FirewallActivity extends SightActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firewall);

        // to initialize defaults
        FirewallConstraint fw = new FirewallConstraint(getApplicationContext());
        fw = null;

        final ActivityFirewallBinding binding = ActivityFirewallBinding.inflate(getLayoutInflater());
        final PrefsViewImpl pview = new PrefsViewImpl(getApplicationContext(), "ACTIVITY_FIREWALL", getServiceConnector());
        binding.setFinger(new FingerPrintViewHelper(getApplicationContext(), pview));
        binding.setPrefs(pview);
        setContentView(binding.getRoot());
    }

    public void testPassword(View v) {
        FingerprintActivity.requestAuth(getApplicationContext(), UUID.randomUUID().toString(), "Testing feature");
    }

}

