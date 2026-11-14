package com.baracel.portal.leave.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: ON_LEAVE for people with approved leave today, back to ACTIVE afterwards. */
@Component
public class LeaveStatusJob {
    private static final Logger log = LoggerFactory.getLogger(LeaveStatusJob.class);
    private final LeaveService leave;

    public LeaveStatusJob(LeaveService leave) { this.leave = leave; }

    @Scheduled(cron = "${portal.leave.status-job-cron:0 5 0 * * *}", zone = "UTC")
    public void run() {
        log.info("Leave status job changed {} employee(s)", leave.syncEmployeeStatuses());
    }
}
