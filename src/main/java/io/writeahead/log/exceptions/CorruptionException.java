package io.writeahead.log.exceptions;

import io.writeahead.log.enums.CorruptionType;
import io.writeahead.log.enums.ErrorContext;
import io.writeahead.log.enums.ErrorRecoveryAction;

public class CorruptionException extends WalException {

    private final String segmentName;
    private final long byteOffset;
    private final CorruptionType corruptionType;
    private final long computedValue;  // Computed CRC or magic byte
    private final long expectedValue;  // Expected CRC or magic byte

    public CorruptionException(String message,
                               String segmentName,
                               long byteOffset,
                               CorruptionType type,
                               long computed,
                               long expected) {
        super(message, ErrorContext.DATA_CORRUPTION,
                ErrorRecoveryAction.QUARANTINE_AND_ALERT,
                "reading " + segmentName + " at offset " + byteOffset);

        this.segmentName = segmentName;
        this.byteOffset = byteOffset;
        this.corruptionType = type;
        this.computedValue = computed;
        this.expectedValue = expected;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public boolean indicatesDataLoss() {
        return true;
    }

    @Override
    public String errorTypeCode() {
        return "CORRUPTION_" + corruptionType.name();
    }

    // Getters for corruption details
    public String segmentName() {
        return segmentName;
    }

    public long byteOffset() {
        return byteOffset;
    }

    public CorruptionType corruptionType() {
        return corruptionType;
    }

    public long computedValue() {
        return computedValue;
    }

    public long expectedValue() {
        return expectedValue;
    }
}
