package com.migd.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("migd")
public class MigdProperties {

    private String pgDumpPath;
    private SourceDb sourceDb = new SourceDb();

    @Getter
    @Setter
    public static class SourceDb {
        private String host;
        private int port = 5432;
        private String db;
        private String user;
        private String password;
    }
}
