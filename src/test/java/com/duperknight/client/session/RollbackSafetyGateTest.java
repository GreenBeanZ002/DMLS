package com.duperknight.client.session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RollbackSafetyGateTest {
 @Test void timeoutNeverPermitsAdvance(){var gate=new RollbackSafetyGate(2); gate.tick(); gate.tick(); assertEquals(OperationOutcome.TIMED_OUT,gate.tick()); assertFalse(gate.mayAdvance()); gate.confirm(); assertFalse(gate.mayAdvance());}
 @Test void onlyConfirmationPermitsAdvance(){var gate=new RollbackSafetyGate(10); gate.confirm(); assertTrue(gate.mayAdvance());}
 @Test void rejectionAndCancellationAreTerminal(){var rejected=new RollbackSafetyGate(10); rejected.reject(); rejected.confirm(); assertEquals(OperationOutcome.REJECTED,rejected.outcome()); var cancelled=new RollbackSafetyGate(10); cancelled.cancel(); assertEquals(OperationOutcome.CANCELLED,cancelled.outcome());}
}
