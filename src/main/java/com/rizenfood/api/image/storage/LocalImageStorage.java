package com.rizenfood.api.image.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 저장소. S3 계정 없이도 개발이 되도록 두는 폴백이다.
 *
 * app.storage.type=local 일 때만 뜬다. 운영에서는 s3 를 쓴다.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalImageStorage(
            @Value("${app.storage.local.path:./uploads}") String rootPath,
            @Value("${app.storage.local.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    @Override
    public String put(String key, byte[] bytes, String contentType) {
        Path target = resolveSafely(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 저장하지 못했다: " + key, e);
        }
        return urlOf(key);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException e) {
            throw new UncheckedIOException("이미지를 지우지 못했다: " + key, e);
        }
    }

    @Override
    public String urlOf(String key) {
        return publicBaseUrl + "/" + key;
    }

    /**
     * 키가 저장 루트를 벗어나지 못하게 한다.
     * 키는 서버가 만들지만, 경로 조작은 한 번만 뚫려도 치명적이라 여기서도 막는다.
     */
    private Path resolveSafely(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("허용되지 않는 저장 경로: " + key);
        }
        return resolved;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
