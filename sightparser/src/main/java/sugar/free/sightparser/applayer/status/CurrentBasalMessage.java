package sugar.free.sightparser.applayer.status;

import lombok.Getter;
import sugar.free.sightparser.applayer.AppLayerMessage;
import sugar.free.sightparser.applayer.Service;
import sugar.free.sightparser.error.NotAvailableError;
import sugar.free.sightparser.error.SightError;
import sugar.free.sightparser.error.UnknownAppErrorCodeError;
import sugar.free.sightparser.pipeline.ByteBuf;

public class CurrentBasalMessage extends AppLayerMessage {

    @Getter
    private String currentBasalName;
    @Getter
    private float currentBasalAmount = 0;

    @Override
    public Service getService() {
        return Service.STATUS;
    }

    @Override
    public short getCommand() {
        return (short) 0xA905;
    }

    @Override
    protected boolean inCRC() {
        return true;
    }

    @Override
    protected void parse(ByteBuf byteBuf) throws Exception {
        byteBuf.shift(2);
        currentBasalName = byteBuf.readUTF8(62);
        currentBasalAmount = ((float) byteBuf.readShortLE()) /  100F;
    }
}
