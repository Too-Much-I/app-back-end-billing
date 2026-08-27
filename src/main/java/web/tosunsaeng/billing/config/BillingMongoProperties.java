package web.tosunsaeng.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing.mongodb")
public class BillingMongoProperties {

    private boolean initializeIndexes;
    private boolean requireTransactions;
    private int schemaVersion = 1;

    public boolean isInitializeIndexes() {
        return initializeIndexes;
    }

    public void setInitializeIndexes(boolean initializeIndexes) {
        this.initializeIndexes = initializeIndexes;
    }

    public boolean isRequireTransactions() {
        return requireTransactions;
    }

    public void setRequireTransactions(boolean requireTransactions) {
        this.requireTransactions = requireTransactions;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
