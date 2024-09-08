package com.troblecodings.signals.enums;

import com.troblecodings.core.ReadBuffer;

public enum SignalBoxNetwork {

    SEND_POS_ENTRY, SEND_INT_ENTRY, REMOVE_ENTRY, REQUEST_PW, REMOVE_POS, RESET_PW, SEND_GRID,
    SEND_PW_UPDATE, RESET_ALL_PW, SEND_CHANGED_MODES, REQUEST_LINKED_POS, PW_REQUEST_RESPONSE,
    REQUEST_SUBSIDIARY, SEND_ZS2_ENTRY, UPDATE_RS_OUTPUT, OUTPUT_UPDATE, RESET_SUBSIDIARY,
    SET_AUTO_POINT, SEND_NAME, SEND_BOOL_ENTRY, ADDED_TO_SAVER, REMOVE_SAVEDPW, SEND_POINT_ENTRY,
    SET_SIGNALS, SEND_COUNTER, SEND_TRAIN_NUMBER, RESET_ALL_SIGNALS, SEND_POSIDENT_LIST,
    SEND_CONNECTED_TRAINNUMBERS;

    public static SignalBoxNetwork of(final ReadBuffer buffer) {
        return values()[buffer.getByteToUnsignedInt()];
    }
}