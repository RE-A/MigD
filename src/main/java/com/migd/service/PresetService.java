package com.migd.service;

import com.migd.domain.Preset;
import com.migd.domain.PresetTable;
import com.migd.mapper.PresetMapper;
import com.migd.mapper.PresetTableMapper;
import com.migd.util.JdbcConnectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class PresetService {

    private final PresetMapper presetMapper;
    private final PresetTableMapper presetTableMapper;

    @Transactional(readOnly = true)
    public List<Preset> findAll() {
        return presetMapper.findAll();
    }

    @Transactional(readOnly = true)
    public Preset findById(Long id) {
        Preset preset = presetMapper.findById(id);
        if (preset == null) {
            throw new IllegalArgumentException("프리셋을 찾을 수 없습니다. id=" + id);
        }
        preset.setTables(presetTableMapper.findByPresetId(id));
        return preset;
    }

    @Transactional(readOnly = true)
    public List<PresetTable> findTablesByPresetId(Long presetId) {
        return presetTableMapper.findByPresetId(presetId);
    }

    /**
     * 프리셋 저장/수정. 자식 테이블은 전체 교체(delete + batchInsert) 방식.
     */
    public Preset save(Preset preset) {
        if (preset.getId() == null) {
            presetMapper.insert(preset);
        } else {
            presetMapper.update(preset);
        }

        presetTableMapper.deleteByPresetId(preset.getId());
        if (preset.getTables() != null && !preset.getTables().isEmpty()) {
            preset.getTables().forEach(t -> t.setPresetId(preset.getId()));
            presetTableMapper.batchInsert(preset.getTables());
        }

        return preset;
    }

    public void delete(Long id) {
        presetMapper.deleteById(id);
    }

    /**
     * Target DB 연결 테스트. 성공 시 PostgreSQL 버전 문자열 반환.
     */
    @Transactional(readOnly = true)
    public String testConnection(String host, int port, String db,
                                 String user, String password) throws SQLException {
        return JdbcConnectionUtil.testConnection(host, port, db, user, password);
    }
}
