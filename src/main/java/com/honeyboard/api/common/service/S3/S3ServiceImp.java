package com.honeyboard.api.common.service.S3;

import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3ServiceImp implements S3Service {

    @Value("${storage.local.base-dir:/app/uploads}")
    private String baseDir;

    @Value("${storage.local.public-prefix:/uploads}")
    private String publicPrefix;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            String fileName = createFileName(safeOriginalName(file.getOriginalFilename()));

            Path dir = Paths.get(baseDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            Path target = dir.resolve(fileName).normalize();
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            return publicPrefix.endsWith("/")
                    ? publicPrefix + encoded
                    : publicPrefix + "/" + encoded;

        } catch (IOException e) {
            log.error("파일 업로드 실패: {}", e.getMessage());
            throw new RuntimeException("파일 업로드에 실패했습니다.", e);
        }

    }

    @Override
    public String createFileName(String originalFileName) {
        return UUID.randomUUID() + "_" + originalFileName;
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            Path target = Paths.get(baseDir).toAbsolutePath().normalize().resolve(fileName).normalize();
            if (!target.startsWith(Paths.get(baseDir).toAbsolutePath().normalize())) {
                throw new SecurityException("잘못된 파일 경로입니다.");
            }
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.error("파일 삭제 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 삭제에 실패했습니다.", e);
        }
    }

    @Override
    public String extractFileNameFromUrl(String fileUrl) {
        try {
            int idx = fileUrl.lastIndexOf('/');
            if (idx < 0) throw new StringIndexOutOfBoundsException("슬래시 없음");
            return java.net.URLDecoder.decode(fileUrl.substring(idx + 1), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("URL에서 파일명 추출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("잘못된 파일 URL입니다.", e);
        }
    }

    private String safeOriginalName(String name) {
        if (name == null) return "file";
        // 윈도우/리눅스 금지문자 제거 + 경로 분리자 제거
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
