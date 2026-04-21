package com.kms.katalon.core.checkpoint;

/**
 * Katalon compatibility class for Checkpoint
 */
public class Checkpoint {
    private String checkpointId;
    private String name;
    
    public Checkpoint() {}
    
    public Checkpoint(String checkpointId) {
        this.checkpointId = checkpointId;
    }
    
    public String getCheckpointId() {
        return checkpointId;
    }
    
    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean verify() {
        return true;
    }
}
