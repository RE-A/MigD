package com.migd.mapper;

import com.migd.domain.CatalogTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogTableMapper {
    List<CatalogTable> findByCatalogId(Long catalogId);
    CatalogTable findById(Long id);
    void insert(CatalogTable table);
    void batchInsert(@Param("list") List<CatalogTable> tables);
    void deleteByCatalogId(Long catalogId);
}
