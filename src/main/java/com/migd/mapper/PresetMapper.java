package com.migd.mapper;

import com.migd.domain.Preset;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PresetMapper {
    List<Preset> findAll();

    Preset findById(Long id);

    void insert(Preset preset);

    void update(Preset preset);

    void deleteById(Long id);
}
