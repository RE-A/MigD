package com.migd.mapper;

import com.migd.domain.SchemaCatalog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SchemaCatalogMapper {
    List<SchemaCatalog> findAll();
    SchemaCatalog findById(Long id);
    SchemaCatalog findByKey(@Param("schemaName") String schemaName,
                            @Param("dbHost") String dbHost,
                            @Param("dbName") String dbName);
    void insert(SchemaCatalog catalog);
    void updateStats(@Param("id") Long id,
                     @Param("tableCount") int tableCount,
                     @Param("routineCount") int routineCount);
    void deleteById(Long id);
}
