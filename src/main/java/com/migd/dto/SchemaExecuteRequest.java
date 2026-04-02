package com.migd.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SchemaExecuteRequest {
    private String tgtHost;
    private int tgtPort = 5432;
    private String tgtDb;
    private String tgtUser;
    private String tgtPassword;
    private String schemaName = "public";
}
