package com.stockit.backend.feature.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.stockit.backend.common.exception.AppException;
import com.stockit.backend.common.exception.ErrorCode;

class ProductThumbnailServiceTest {

    @Test
    void acceptsOnlyTheKnownGreatingHttpsImageHost() {
        assertThat(ProductThumbnailService.validateSourceUri(
                "https://image.greating.co.kr/IL/item/202312/29/product.jpg"
        ).getHost()).isEqualTo("image.greating.co.kr");

        assertThatThrownBy(() -> ProductThumbnailService.validateSourceUri("http://image.greating.co.kr/product.jpg"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
        assertThatThrownBy(() -> ProductThumbnailService.validateSourceUri("https://example.com/product.jpg"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PARAMETER));
    }

    @Test
    void convertsAnEightHundredPixelOriginalIntoACompactThumbnail() throws Exception {
        BufferedImage source = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 800, 800);
        graphics.setColor(new Color(30, 130, 81));
        graphics.fillOval(80, 80, 640, 640);
        graphics.dispose();

        ByteArrayOutputStream sourceOutput = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceOutput);
        byte[] thumbnailBytes = ProductThumbnailService.resizeToJpeg(sourceOutput.toByteArray());
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(thumbnailBytes));

        assertThat(thumbnail.getWidth()).isEqualTo(112);
        assertThat(thumbnail.getHeight()).isEqualTo(112);
        assertThat(thumbnailBytes.length).isLessThan(sourceOutput.size());
    }
}
