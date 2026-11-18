package com.smartpay.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka/outbox settings. The publisher and consumers are off unless KAFKA_ENABLED=true. */
@ConfigurationProperties(prefix = "app.kafka")
public class EventProperties {

    private boolean enabled;
    private Topics topics = new Topics();
    private Outbox outbox = new Outbox();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Topics getTopics() { return topics; }
    public void setTopics(Topics topics) { this.topics = topics; }
    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }

    public static class Topics {
        private String txCreated = "smartpay.tx.created";
        private String txStatus = "smartpay.tx.status.changed";
        private String notification = "smartpay.notification.event";
        private String dlq = "smartpay.dlq";

        public String getTxCreated() { return txCreated; }
        public void setTxCreated(String txCreated) { this.txCreated = txCreated; }
        public String getTxStatus() { return txStatus; }
        public void setTxStatus(String txStatus) { this.txStatus = txStatus; }
        public String getNotification() { return notification; }
        public void setNotification(String notification) { this.notification = notification; }
        public String getDlq() { return dlq; }
        public void setDlq(String dlq) { this.dlq = dlq; }
    }

    public static class Outbox {
        private int maxAttempts = 5;
        private long retryDelayMs = 2000;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
}
