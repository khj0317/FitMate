package com.fitmate.global.image;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 사진을 검증·가공해서 저장소에 올린다.
 * DB 저장이 실패(롤백)하면 이미 올린 파일을 지우고, 예전 파일 삭제는 커밋이 성공한 뒤에만 해서
 * DB와 저장소가 어긋나지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUploader {

    private final ImageProcessor imageProcessor;
    private final FileStorage fileStorage;

    public StoredImage upload(MultipartFile file, ImagePurpose purpose) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
        ProcessedImage image;
        try {
            image = imageProcessor.process(file.getBytes(), purpose.maxSide(), purpose.squareCrop());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }

        String key = "%s/%s/%s.%s".formatted(purpose.folder(), YearMonth.now().toString().replace('-', '/'),
                UUID.randomUUID(), image.extension());
        String url = fileStorage.store(key, image.data(), image.contentType());
        afterRollback(() -> fileStorage.delete(key));
        return new StoredImage(url, image.width(), image.height());
    }

    /** 이 서비스가 올린 파일이면 트랜잭션 커밋 후에 지운다 (외부 URL은 무시) */
    public void deleteAfterCommit(String url) {
        fileStorage.keyOf(url).ifPresent(key -> {
            Runnable delete = () -> {
                try {
                    fileStorage.delete(key);
                } catch (RuntimeException e) {
                    log.warn("이전 사진 삭제 실패: {}", key, e); // 파일이 남을 뿐 기능에는 영향 없음
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        delete.run();
                    }
                });
            } else {
                delete.run();
            }
        });
    }

    private static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
