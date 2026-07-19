package com.duperknight.client.utils;

/** Controls whether a simulated dispatch prints its exact payload immediately. */
public enum DryRunFeedback {
    /** One-shot user action: print the exact command that would have been sent. */
    COMMAND_PREVIEW,
    /** Managed operation: the operation prints one concise terminal summary instead. */
    OPERATION_SUMMARY
}
