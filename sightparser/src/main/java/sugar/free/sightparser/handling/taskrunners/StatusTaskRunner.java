package sugar.free.sightparser.handling.taskrunners;

import android.util.Log;

import java.io.Serializable;

import lombok.Getter;
import sugar.free.sightparser.applayer.AppLayerMessage;
import sugar.free.sightparser.applayer.status.ActiveBolusesMessage;
import sugar.free.sightparser.applayer.status.BatteryAmountMessage;
import sugar.free.sightparser.applayer.status.CartridgeAmountMessage;
import sugar.free.sightparser.applayer.status.CurrentBasalMessage;
import sugar.free.sightparser.applayer.status.CurrentTBRMessage;
import sugar.free.sightparser.applayer.status.PumpStatus;
import sugar.free.sightparser.applayer.status.PumpStatusMessage;
import sugar.free.sightparser.handling.SightServiceConnector;
import sugar.free.sightparser.handling.TaskRunner;

public class StatusTaskRunner extends TaskRunner {

    private StatusResult statusResult = new StatusResult();

    public StatusTaskRunner(SightServiceConnector serviceConnector) {
        super(serviceConnector);
    }

    @Override
    protected AppLayerMessage run(AppLayerMessage message) throws Exception {
        if (message == null) return new PumpStatusMessage();
        else if (message instanceof PumpStatusMessage) {
            PumpStatusMessage pumpStatusMessage = (PumpStatusMessage) message;
            statusResult.pumpStatusMessage = pumpStatusMessage;
            if (statusResult.pumpStatusMessage.getPumpStatus() != PumpStatus.STOPPED)return new ActiveBolusesMessage();
            else return new BatteryAmountMessage();
        } else if (message instanceof ActiveBolusesMessage) {
            statusResult.activeBolusesMessage = (ActiveBolusesMessage) message;
            return new CurrentTBRMessage();
        } else if (message instanceof CurrentTBRMessage) {
            statusResult.currentTBRMessage = (CurrentTBRMessage) message;
            return new CurrentBasalMessage();
        } else if (message instanceof CurrentBasalMessage) {
            statusResult.currentBasalMessage = (CurrentBasalMessage) message;
            return new BatteryAmountMessage();
        } else if (message instanceof BatteryAmountMessage) {
            statusResult.batteryAmountMessage = (BatteryAmountMessage) message;
            return new CartridgeAmountMessage();
        } else if (message instanceof CartridgeAmountMessage) {
            statusResult.cartridgeAmountMessage = (CartridgeAmountMessage) message;
            finish(statusResult);
        }
        return null;
    }

    @Getter
    public static final class StatusResult implements Serializable {
        private PumpStatusMessage pumpStatusMessage;
        private ActiveBolusesMessage activeBolusesMessage;
        private CurrentTBRMessage currentTBRMessage;
        private CurrentBasalMessage currentBasalMessage;
        private BatteryAmountMessage batteryAmountMessage;
        private CartridgeAmountMessage cartridgeAmountMessage;
    }
}
