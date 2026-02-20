package com.pathfinder.session.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionExpiryJob {

    private final DemoSessionStore sessionStore;

    public SessionExpiryJob(DemoSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Scheduled(fixedDelayString = "${app.session.expiry.interval-ms:60000}")
    public void expireHeldRequests() {
        sessionStore.expirePendingRequests();
    }
}
