package com.pei.zfile.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pei.zfile.file.entity.FileObject;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    @Insert("""
            INSERT INTO file_objects
                (checksum, size_bytes, content_type, storage_key, reference_count)
            VALUES
                (#{checksum}, #{sizeBytes}, #{contentType}, #{storageKey}, #{referenceCount})
            ON DUPLICATE KEY UPDATE id = id
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(FileObject fileObject);

    @Update("""
            UPDATE file_objects
            SET reference_count = reference_count + 1
            WHERE id = #{id}
            """)
    int retain(@Param("id") Long id);

    @Update("""
            UPDATE file_objects
            SET reference_count = reference_count - #{count}
            WHERE id = #{id}
              AND reference_count >= #{count}
            """)
    int release(@Param("id") Long id, @Param("count") Long count);
}
