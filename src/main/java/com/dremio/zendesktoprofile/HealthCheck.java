package com.dremio.zendesktoprofile;

public class HealthCheck {

    private static HealthCheck instance;

    //static block initialization provides exception handling
    static {
        try {
            instance = new HealthCheck();
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred creating singleton instance");
        }
    }

    private long sendSafelyPackagesProcessed = 0;
    private long filesChecked = 0;
    private long profilesUploaded = 0;
    private long lastProfilesUploadEpoch = 0;
    private long lastWebhookEpoch = 0;
    private static final long startEpoch = System.currentTimeMillis();

    private HealthCheck() {
        //nothing
    }

    public static HealthCheck getInstance() {
        return instance;
    }

    void incrementFilesChecked() {
        instance.filesChecked++;
    }

    public long getFilesChecked() {
        return instance.filesChecked;
    }

    void incrementProfilesUploaded() {
        instance.profilesUploaded++;
    }

    public long getProfilesUploaded() {
        return instance.profilesUploaded;
    }

    void incrementSendSafelyPackagesProcessed() {
        instance.sendSafelyPackagesProcessed++;
    }

    public long getSendSafelyPackagesProcessed() {
        return instance.sendSafelyPackagesProcessed;
    }

    public void setLastWebhookEpoch() {
        instance.lastWebhookEpoch = System.currentTimeMillis();
    }

    public long getLastWebhookEpoch() {
        return instance.lastWebhookEpoch;
    }

    public long getLastProfilesUploadEpoch() {
        return instance.lastProfilesUploadEpoch;
    }

    public void setLastProfilesUploadEpoch() {
        instance.lastProfilesUploadEpoch = System.currentTimeMillis();
    }

    public long getStartEpoch() {
        return instance.startEpoch;
    }
}
