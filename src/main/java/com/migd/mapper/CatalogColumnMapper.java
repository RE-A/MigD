package com.migd.mapper;

import com.migd.domain.CatalogColumn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogColumnMapper {
    List<CatalogColumn> findByTableId(Long catalogTableId);
    List<CatalogColumn> searchByColumnName(@Param("catalogId") Long catalogId,
                                           @Param("keyword") String keyword);
    void batchInsert(@Param("list") List<CatalogColumn> columns);
    void deleteByCatalogId(Long catalogId);
}
