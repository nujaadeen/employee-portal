package com.baracel.portal.task.domain;

public enum TaskStatus {
    BACKLOG("Backlog"), IN_PROGRESS("In progress"), IN_REVIEW("In review"), DONE("Done");

    public final String label;
    TaskStatus(String label) { this.label = label; }
}
