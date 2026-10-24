package com.simpleshop.common.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.time.DatabaseTimeProvider;

/**
 * 媒体落盘与地址校验。
 *
 * <p>商品图片与描述媒体都存放在同一媒体根目录下，按用途分目录、
 * 再按 UTC 年/月分目录。文件名由服务端生成，数据库只存相对 URL。
 *
 * <p>商品图片只允许图片；描述媒体允许图片和视频。两类图片当前都限制 5MB，
 * 但走不同的配置项，后续可以分别调整；视频暂限 20MB。
 * 写盘失败属于服务端故障，向上抛出，不报成媒体不合规。
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private static final int HEADER_PROBE_BYTES = 12;
    private static final DateTimeFormatter DATE_DIRECTORY = DateTimeFormatter.ofPattern("uuuu/MM");

    private final Path rootDirectory;
    private final String urlPrefix;
    private final DatabaseTimeProvider timeProvider;
    private final DataSize goodsImageMaxSize;
    private final DataSize descriptionImageMaxSize;
    private final DataSize descriptionVideoMaxSize;
    private final Pattern storedUrlPattern;

    public MediaStorageService(AppProperties properties, DatabaseTimeProvider timeProvider) {
        AppProperties.Media media = properties.getMedia();
        this.rootDirectory = media.directoryPath();
        this.urlPrefix = media.getUrlPrefix();
        this.timeProvider = timeProvider;
        this.goodsImageMaxSize = media.getGoodsImageMaxSize();
        this.descriptionImageMaxSize = media.getDescriptionImageMaxSize();
        this.descriptionVideoMaxSize = media.getDescriptionVideoMaxSize();
        this.storedUrlPattern = Pattern.compile(
                "^" + Pattern.quote(urlPrefix)
                        + "/(goods|description)/(\\d{4})/(\\d{2})/"
                        + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                        + "\\.(jpg|png|mp4)$");
    }

    /**
     * 校验并保存媒体，返回可直接写入数据库的相对 URL。
     */
    public StoredMedia store(MultipartFile file, MediaPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        if (file == null || file.isEmpty()) {
            throw invalidFor(purpose);
        }

        byte[] header = readHeader(file);
        MediaKind kind = detectKind(header).orElseThrow(() -> {
            log.warn("rejected upload with unsupported content: purpose={} declaredSize={}",
                    purpose, file.getSize());
            return invalidFor(purpose);
        });
        if (!purpose.allows(kind)) {
            log.warn("rejected media type for purpose: purpose={} kind={}", purpose, kind);
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_ALLOWED);
        }

        DataSize maxSize = maxSize(purpose, kind);
        if (file.getSize() > maxSize.toBytes()) {
            log.warn("rejected oversized upload: purpose={} kind={} declaredSize={} limit={}",
                    purpose, kind, file.getSize(), maxSize);
            throw tooLarge(kind);
        }

        String extension = extensionOf(header, kind);
        String datePath = DATE_DIRECTORY.format(timeProvider.nowUtc());
        String fileName = UUID.randomUUID() + "." + extension;
        Path targetDirectory = rootDirectory.resolve(purpose.folder()).resolve(datePath);
        Path target = targetDirectory.resolve(fileName).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw invalidFor(purpose);
        }

        try {
            Files.createDirectories(targetDirectory);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to store uploaded media at " + target, exception);
        }

        String url = urlPrefix + "/" + purpose.folder() + "/" + datePath + "/" + fileName;
        log.info("stored media: purpose={} kind={} bytes={} url={}",
                purpose, kind, file.getSize(), url);
        return new StoredMedia(url, kind, file.getSize());
    }

    /**
     * 校验 URL 确实指向本服务为该用途落盘过的媒体。
     *
     * <p>同时检查用途目录、扩展名、文件存在性，防止外部地址或猜出的路径写入数据库。
     * {@code null} 表示未上传，直接通过。
     */
    public void verifyStoredUrl(String url, MediaPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose");
        if (url == null) {
            return;
        }
        Matcher matcher = storedUrlPattern.matcher(url);
        if (!matcher.matches() || !purpose.folder().equals(matcher.group(1))) {
            log.warn("rejected media url with unexpected shape or purpose: purpose={}", purpose);
            throw invalidFor(purpose);
        }
        MediaKind kind = kindOfExtension(matcher.group(5));
        if (!purpose.allows(kind)) {
            log.warn("rejected media url with disallowed kind: purpose={} kind={}", purpose, kind);
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_ALLOWED);
        }
        Path candidate = rootDirectory
                .resolve(matcher.group(1))
                .resolve(matcher.group(2))
                .resolve(matcher.group(3))
                .resolve(matcher.group(4) + "." + matcher.group(5))
                .normalize();
        if (!candidate.startsWith(rootDirectory) || !Files.isRegularFile(candidate)) {
            log.warn("rejected media url pointing at a non-existent file");
            throw invalidFor(purpose);
        }
    }

    private Optional<MediaKind> detectKind(byte[] header) {
        if (ImageType.detect(header).isPresent()) {
            return Optional.of(MediaKind.IMAGE);
        }
        if (VideoType.detect(header).isPresent()) {
            return Optional.of(MediaKind.VIDEO);
        }
        return Optional.empty();
    }

    private String extensionOf(byte[] header, MediaKind kind) {
        if (kind == MediaKind.IMAGE) {
            return ImageType.detect(header).orElseThrow().extension();
        }
        return VideoType.detect(header).orElseThrow().extension();
    }

    private DataSize maxSize(MediaPurpose purpose, MediaKind kind) {
        return switch (purpose) {
            case GOODS_IMAGE -> goodsImageMaxSize;
            case DESCRIPTION_MEDIA -> kind == MediaKind.IMAGE
                    ? descriptionImageMaxSize
                    : descriptionVideoMaxSize;
        };
    }

    private BusinessException invalidFor(MediaPurpose purpose) {
        return new BusinessException(purpose == MediaPurpose.GOODS_IMAGE
                ? ErrorCode.IMAGE_INVALID
                : ErrorCode.MEDIA_INVALID);
    }

    private BusinessException tooLarge(MediaKind kind) {
        return new BusinessException(kind == MediaKind.IMAGE
                ? ErrorCode.IMAGE_INVALID
                : ErrorCode.VIDEO_INVALID);
    }

    private static MediaKind kindOfExtension(String extension) {
        return "mp4".equalsIgnoreCase(extension) ? MediaKind.VIDEO : MediaKind.IMAGE;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (var in = file.getInputStream()) {
            return in.readNBytes(HEADER_PROBE_BYTES);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read uploaded media header", exception);
        }
    }
}
