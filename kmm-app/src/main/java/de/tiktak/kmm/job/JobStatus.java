package de.tiktak.kmm.job;

public enum JobStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
