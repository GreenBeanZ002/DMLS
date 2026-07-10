package com.duperknight.client.message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerMessageRouterTest {
 @BeforeEach void reset(){ServerMessageRouter.resetDuplicateStateForTests();}
 @Test void suppressesOnlyCrossEventDuplicateWithinWindow(){long now=1_000_000_000L; assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.PLAYER_CHAT,now)); assertTrue(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.SERVER_SYSTEM,now+1)); assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.SERVER_SYSTEM,now+2)); assertFalse(ServerMessageRouter.isCrossEventDuplicate("watched word",MessageOrigin.PLAYER_CHAT,now+300_000_000L));}
}
