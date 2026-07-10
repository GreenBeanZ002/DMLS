package com.duperknight.client.session;

/** Pure fail-closed gate for one destructive rollback step. */
public final class RollbackSafetyGate {
    private final int timeoutTicks;
    private int elapsedTicks;
    private OperationOutcome outcome = OperationOutcome.PENDING;

    public RollbackSafetyGate(int timeoutTicks) {
        if (timeoutTicks < 1) throw new IllegalArgumentException("timeoutTicks");
        this.timeoutTicks = timeoutTicks;
    }

    public OperationOutcome tick() {
        if (outcome == OperationOutcome.PENDING && ++elapsedTicks > timeoutTicks) outcome = OperationOutcome.TIMED_OUT;
        return outcome;
    }

    public void confirm() { if (outcome == OperationOutcome.PENDING) outcome = OperationOutcome.CONFIRMED; }
    public void reject() { if (outcome == OperationOutcome.PENDING) outcome = OperationOutcome.REJECTED; }
    public void cancel() { if (outcome == OperationOutcome.PENDING) outcome = OperationOutcome.CANCELLED; }
    public OperationOutcome outcome() { return outcome; }
    public boolean mayAdvance() { return outcome == OperationOutcome.CONFIRMED; }
}
