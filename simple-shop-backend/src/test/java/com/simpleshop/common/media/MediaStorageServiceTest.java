package com.simpleshop.common.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.simpleshop.common.api.BusinessException;
import com.simpleshop.common.api.ErrorCode;
import com.simpleshop.common.config.AppProperties;
import com.simpleshop.common.time.DatabaseTimeProvider;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("商品图片写入 goods 目录，返回 /media/goods/... 并可回验")
    void storesGoodsImage() {
        MediaStorageService service = service();
        MockMultipartFile file = jpeg();

        StoredMedia stored = service.store(file, MediaPurpose.GOODS_IMAGE);

        assertThat(stored.kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(stored.url()).matches("^/media/goods/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.jpg$");
        assertThat(Files.isRegularFile(pathOf(stored.url()))).isTrue();

        service.verifyStoredUrl(stored.url(), MediaPurpose.GOODS_IMAGE);
    }

    @Test
    @DisplayName("描述视频写入 description 目录，返回 .mp4 地址并可回验")
    void storesDescriptionVideo() {
        MediaStorageService service = service();
        MockMultipartFile file = mp4();

        StoredMedia stored = service.store(file, MediaPurpose.DESCRIPTION_MEDIA);

        assertThat(stored.kind()).isEqualTo(MediaKind.VIDEO);
        assertThat(stored.url()).matches("^/media/description/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.mp4$");
        assertThat(Files.isRegularFile(pathOf(stored.url()))).isTrue();

        service.verifyStoredUrl(stored.url(), MediaPurpose.DESCRIPTION_MEDIA);
    }

    @Test
    @DisplayName("商品图片用途拒绝视频")
    void rejectsVideoForGoodsImage() {
        MediaStorageService service = service();

        assertThatThrownBy(() -> service.store(mp4(), MediaPurpose.GOODS_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MEDIA_TYPE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("描述图片与描述视频分别使用各自的限制")
    void appliesIndependentLimits() {
        AppProperties properties = properties();
        properties.getMedia().setDescriptionImageMaxSize(DataSize.ofBytes(10));
        properties.getMedia().setDescriptionVideoMaxSize(DataSize.ofBytes(10));
        MediaStorageService service = new MediaStorageService(properties, new DatabaseTimeProvider());

        assertThatThrownBy(() -> service.store(jpeg(), MediaPurpose.DESCRIPTION_MEDIA))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_INVALID);

        assertThatThrownBy(() -> service.store(mp4(), MediaPurpose.DESCRIPTION_MEDIA))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.VIDEO_INVALID);
    }

    @Test
    @DisplayName("描述媒体拒绝未知内容，商品图片拒绝非图片")
    void rejectsUnsupportedContent() {
        MediaStorageService service = service();
        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.store(text, MediaPurpose.DESCRIPTION_MEDIA))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.MEDIA_INVALID);

        assertThatThrownBy(() -> service.store(text, MediaPurpose.GOODS_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_INVALID);
    }

    @Test
    @DisplayName("URL 用途不匹配或路径穿越时拒绝")
    void rejectsUrlWithWrongPurposeOrTraversal() {
        MediaStorageService service = service();
        StoredMedia stored = service.store(jpeg(), MediaPurpose.GOODS_IMAGE);

        assertThatThrownBy(() -> service.verifyStoredUrl(stored.url(), MediaPurpose.DESCRIPTION_MEDIA))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.verifyStoredUrl("/media/goods/../../etc/passwd", MediaPurpose.GOODS_IMAGE))
                .isInstanceOf(BusinessException.class);
    }

    private MediaStorageService service() {
        return new MediaStorageService(properties(), new DatabaseTimeProvider());
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.getMedia().setDirectory(tempDir.toString());
        properties.getMedia().setUrlPrefix("/media");
        return properties;
    }

    private Path pathOf(String url) {
        return tempDir.resolve(url.substring("/media/".length()));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0});
    }

    private static MockMultipartFile mp4() {
        return new MockMultipartFile("file", "a.mp4", "video/mp4", VideoTypeTest.mp4Header());
    }
}
