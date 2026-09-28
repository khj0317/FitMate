package com.fitmate.global.storage;

import java.util.Optional;

/**
 * 업로드한 파일을 저장하고 공개 URL을 돌려준다.
 * 개발은 로컬 폴더(LocalFileStorage), 배포는 S3 호환 저장소(S3FileStorage)를 설정으로 고른다.
 */
public interface FileStorage {

    /** @return 브라우저에서 바로 쓸 수 있는 공개 URL */
    String store(String key, byte[] data, String contentType);

    void delete(String key);

    /** 이 저장소가 만든 URL이면 저장 키를 돌려준다 (외부 URL이면 empty) */
    Optional<String> keyOf(String url);
}
