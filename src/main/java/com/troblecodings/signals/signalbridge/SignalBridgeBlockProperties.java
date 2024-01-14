package com.troblecodings.signals.signalbridge;

import com.troblecodings.signals.contentpacks.ContentPackException;
import com.troblecodings.signals.enums.SignalBridgeType;

public class SignalBridgeBlockProperties {

    public transient SignalBridgeType bridgeType;
    private String type;
    public int extentionX = 0;
    public int extentionY = 0;
    public int extentionZ = 0;

    public SignalBridgeType getType() {
        if (bridgeType == null) {
            if (type == null) {
                throw new ContentPackException("You need to define a Type for the SignalBridge!");
            }
            bridgeType = Enum.valueOf(SignalBridgeType.class, type);
        }
        return bridgeType;
    }
}