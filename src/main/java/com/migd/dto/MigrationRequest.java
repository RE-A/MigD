package com.migd.dto;

import com.migd.domain.PresetTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class MigrationRequest {
    private String tgtHost;
    private int tgtPort = 5432;
    private String tgtDb;
    private String tgtUser;
    private String tgtPassword;
    private List<PresetTable> tables = new ArrayList<>();

    public MigrationRequest(List<PresetTable> tables) {
        this.tables = tables != null ? new ArrayList<>(tables) : new ArrayList<>();
    }

    public DbConnInfo toTgtConnInfo() {
        return new DbConnInfo(tgtHost, tgtPort, tgtDb, tgtUser, tgtPassword);
    }
}
