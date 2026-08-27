package com.stockit.backend.feature.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

@Service
public class ProductThumbnailService {

    private static final String GREATING_IMAGE_HOST = "image.greating.co.kr";
    private static final int THUMBNAIL_MAX_EDGE = 112;
    private static final int MAX_SOURCE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient;
    private final Map<String, Thumbnail> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Thumbnail> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    public ProductThumbnailService() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    ProductThumbnailService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Thumbnail get(String sourceUrl) {
        URI sourceUri = validateSourceUri(sourceUrl);
        Thumbnail cached = cache.get(sourceUri.toString());
        if (cached != null) return cached;

        Thumbnail thumbnail = new Thumbnail(downloadAndResize(sourceUri));
        cache.put(sourceUri.toString(), thumbnail);
        return thumbnail;
    }

    private byte[] downloadAndResize(URI sourceUri) {
        HttpRequest request = HttpRequest.newBuilder(sourceUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "image/jpeg,image/png,image/gif,image/*;q=0.8")
                .header("User-Agent", "StockFit-Thumbnail/1.0")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                closeQuietly(response.body());
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 썸네일을 불러오지 못했습니다.");
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase().startsWith("image/")) {
                closeQuietly(response.body());
                throw new AppException(ErrorCode.INVALID_PARAMETER, "이미지 URL만 썸네일로 변환할 수 있습니다.");
            }

            byte[] sourceBytes;
            try (InputStream body = response.body()) {
                sourceBytes = body.readNBytes(MAX_SOURCE_BYTES + 1);
            }
            if (sourceBytes.length == 0 || sourceBytes.length > MAX_SOURCE_BYTES) {
                throw new AppException(ErrorCode.PAYLOAD_TOO_LARGE, "원본 이미지 크기가 허용 범위를 초과했습니다.");
            }
            return resizeToJpeg(sourceBytes);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 썸네일 처리가 중단되었습니다.");
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "상품 썸네일을 처리하지 못했습니다.");
        }
    }

    static URI validateSourceUri(String sourceUrl) {
        URI uri;
        try {
            uri = URI.create(sourceUrl == null ? "" : sourceUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, "상품 이미지 URL 형식이 올바르지 않습니다.");
        }
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && GREATING_IMAGE_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1
                && uri.getUserInfo() == null
                && uri.getFragment() == null
                && uri.getPath() != null
                && !uri.getPath().isBlank();
        if (!allowed) {
            throw new AppException(ErrorCode.INVALID_PARAMETER, "허용된 상품 이미지 URL이 아닙니다.");
        }
        return uri;
    }

    static byte[] resizeToJpeg(byte[] sourceBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IOException("Unsupported image data");
        }
        if ((long) source.getWidth() * source.getHeight() > MAX_SOURCE_PIXELS) {
            throw new IOException("Image dimensions exceed the limit");
        }

        double scale = Math.min(
                1.0,
                Math.min((double) THUMBNAIL_MAX_EDGE / source.getWidth(), (double) THUMBNAIL_MAX_EDGE / source.getHeight())
        );
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.82f);
            writer.write(null, new IIOImage(thumbnail, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 응답 실패 경로에서는 연결 해제만 보장합니다.
        }
    }

    public record Thumbnail(byte[] bytes) {
    }
}
