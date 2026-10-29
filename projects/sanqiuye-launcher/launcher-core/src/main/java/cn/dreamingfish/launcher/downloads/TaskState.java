package cn.dreamingfish.launcher.downloads;
public enum TaskState {QUEUED,RESOLVING,DOWNLOADING,VERIFYING,COMPLETED,FAILED,CANCELLED,INTERRUPTED;public boolean terminal(){return ordinal()>=COMPLETED.ordinal();}}
