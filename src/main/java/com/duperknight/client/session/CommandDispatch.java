package com.duperknight.client.session;

/** Truthful result of attempting to dispatch a command or chat message. */
public enum CommandDispatch {
    /** The payload was sent to the currently connected server. */
    SENT,
    /** Dry-run was captured, so no payload was sent; the caller reports a preview or summary. */
    SIMULATED,
    /** Dispatch was refused because validation or connection safety failed. */
    BLOCKED;

    public boolean accepted() {
        return this != BLOCKED;
    }
}
