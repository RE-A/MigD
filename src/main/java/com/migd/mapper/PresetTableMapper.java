package com.migd.mapper;

import com.migd.domain.PresetTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PresetTableMapper {
    List<PresetTable> findByPresetId(Long presetId);

    void insert(PresetTable presetTable);

    void update(PresetTable presetTable);

    void deleteById(Long id);

    void deleteByPresetId(Long presetId);

    void batchInsert(@Param("list") List<PresetTable> tables);

    int countAll();
}
