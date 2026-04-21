package com.kms.katalon.core.checkpoint;

/**
 * Katalon compatibility class for CheckpointFactory
 */
public class CheckpointFactory {
    
    /**
     * Find a checkpoint by ID
     */
    public static Checkpoint findCheckpoint(String checkpointId) {
        return new Checkpoint(checkpointId);
    }
    
    public static Checkpoint findCheckpoint(String checkpointId, int retryCount) {
        return new Checkpoint(checkpointId);
    }
}
