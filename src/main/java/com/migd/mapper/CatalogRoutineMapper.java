package com.migd.mapper;

import com.migd.domain.CatalogRoutine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogRoutineMapper {
    List<CatalogRoutine> findByCatalogId(Long catalogId);
    CatalogRoutine findById(Long id);
    List<CatalogRoutine> searchByBody(@Param("catalogId") Long catalogId,
                                      @Param("keyword") String keyword);
    List<CatalogRoutine> searchByName(@Param("catalogId") Long catalogId,
                                      @Param("keyword") String keyword);
    void batchInsert(@Param("list") List<CatalogRoutine> routines);
    void deleteByCatalogId(Long catalogId);
}
