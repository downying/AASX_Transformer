package com.aasx.transformer.upload.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aasx.transformer.upload.dto.Files;
import com.aasx.transformer.upload.dto.FilesMeta;

@Mapper
public interface UploadMapper {
    // ✅ files
    // 특정 해시의 파일 정보를 조회
    Files selectFileByHash(@Param("hash") String hash);

    // 패키지 파일 업로드 시 파일 해시 등록 (없으면 삽입, 이미 존재하면 ref_count 증가)
    // 파일 크기(size)도 함께 전달하여, 동일 해시지만 크기가 다른 경우의 충돌을 방지할 수 있도록 함.
    int insertFile(@Param("hash") String hash, @Param("size") int size);

    // ref_count를 감소 시키기 (파일 메타 삭제 시 사용)
    int decrementFileRefCount(@Param("hash") String hash);

    // ref_count가 0 이하인 파일 정보를 삭제
    int deleteFileByHash(@Param("hash") String hash);

    // ✅ filesMeta
    // 복합 키(aas_id, submodel_id, idShort)로 파일 메타 조회
    FilesMeta selectFileMetaByKey(@Param("aasId") String aasId, 
                                  @Param("submodelId") String submodelId, 
                                  @Param("idShort") String idShort);

    // 특정 해시와 복합 키 조건에 해당하는 파일 메타 정보를 조회 (다운로드 시 사용)
    FilesMeta selectFileMetaByHashAndKey(@Param("hash") String hash,
                                         @Param("aasId") String aasId, 
                                         @Param("submodelId") String submodelId, 
                                         @Param("idShort") String idShort);

    // 파일 메타 신규 등록 (path는 AASId/SubmodelId/Idshort 형식)
    int insertFileMeta(FilesMeta filesMeta);

    // 파일 메타 삭제
    int deleteFileMeta(@Param("aasId") String aasId, 
                       @Param("submodelId") String submodelId, 
                       @Param("idShort") String idShort);

    // 첨부파일 다운로드를 위한 해싱 값을 기준으로 파일 메타 정보 조회                    
    FilesMeta selectOneFileMetaByHash(@Param("hash") String hash);
}
