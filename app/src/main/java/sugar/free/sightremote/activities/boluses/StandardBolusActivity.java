package sugar.free.sightremote.activities.boluses;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;

import java.text.DecimalFormat;

import sugar.free.sightparser.applayer.messages.remote_control.StandardBolusMessage;
import sugar.free.sightparser.handling.SingleMessageTaskRunner;
import sugar.free.sightparser.handling.TaskRunner;
import sugar.free.sightparser.handling.taskrunners.BolusPreparationTaskRunner;
import sugar.free.sightparser.pipeline.Status;
import sugar.free.sightremote.R;
import sugar.free.sightremote.activities.SightActivity;
import sugar.free.sightremote.utils.BolusAmountPicker;
import sugar.free.sightremote.utils.HTMLUtil;
import sugar.free.sightremote.utils.UnitFormatter;

public class StandardBolusActivity extends SightActivity implements TaskRunner.ResultCallback, View.OnClickListener, BolusAmountPicker.OnAmountChangeListener {

    private BolusAmountPicker bolusAmountPicker;

    private BolusPreparationTaskRunner.PreperationResult preperationResult;

    private NumberPicker digit1;
    private NumberPicker digit2;
    private NumberPicker digit3;
    private NumberPicker digit4;
    private Button deliver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContent(R.layout.activity_standard_bolus);

        showManualOverlay();

        digit1 = findViewById(R.id.digit1);
        digit2 = findViewById(R.id.digit2);
        digit3 = findViewById(R.id.digit3);
        digit4 = findViewById(R.id.digit4);

        bolusAmountPicker = new BolusAmountPicker(digit1, digit2, digit3, digit4);
        bolusAmountPicker.setOnAmountChangeListener(this);

        deliver = findViewById(R.id.deliver);
        deliver.setOnClickListener(this);
        deliver.setEnabled(false);
    }

    @Override
    public void onResult(Object result) {
        runOnUiThread(() -> {
            if (result instanceof BolusPreparationTaskRunner.PreperationResult) {
                preperationResult = (BolusPreparationTaskRunner.PreperationResult) result;
                bolusAmountPicker.adjustNumberPickers(preperationResult.getMaxBolusAmount());
                if (preperationResult.isPumpStarted()) {
                    if (preperationResult.getAvailableBoluses().isStandardAvailable()) {
                        hideManualOverlay();
                        dismissSnackbar();
                    } else {
                        showManualOverlay();
                        showSnackbar(Snackbar.make(getRootView(), R.string.bolus_type_not_available, Snackbar.LENGTH_INDEFINITE));
                    }
                } else {
                    showManualOverlay();
                    showSnackbar(Snackbar.make(getRootView(), R.string.pump_not_started, Snackbar.LENGTH_INDEFINITE));
                }
            } else finish();
        });
    }

    @Override
    protected void connectedToService() {
        getServiceConnector().connect();
        statusChanged(getServiceConnector().getStatus());
    }

    @Override
    public void onError(Exception e) {
        runOnUiThread(() -> Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void statusChanged(Status status) {
        if (status == Status.CONNECTED) {
            BolusPreparationTaskRunner taskRunner = new BolusPreparationTaskRunner(getServiceConnector());
            taskRunner.fetch(this);
        } else {
            showManualOverlay();
        }
    }

    @Override
    public void onClick(View view) {
        StandardBolusMessage message = new StandardBolusMessage();
        message.setAmount(bolusAmountPicker.getPickerValue());
        final SingleMessageTaskRunner taskRunner = new SingleMessageTaskRunner(getServiceConnector(), message);
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirmation)
                .setMessage(HTMLUtil.getHTML(R.string.standard_bolus_confirmation, UnitFormatter.formatUnits(bolusAmountPicker.getPickerValue())))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    showManualOverlay();
                    taskRunner.fetchOnBackgroundThread(StandardBolusActivity.this);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected int getSelectedNavItemID() {
        return R.id.nav_standard_bolus;
    }

    @Override
    public void onAmountChange(BolusAmountPicker bolusAmountPicker, float newValue) {
        deliver.setEnabled(newValue >= preperationResult.getMinBolusAmount());
    }
}