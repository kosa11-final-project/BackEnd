package com.stockit.backend.feature.media.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.stockit.backend.feature.media.service.ProductThumbnailService;

@WebMvcTest(ProductThumbnailController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductThumbnailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductThumbnailService productThumbnailService;

    @Test
    void returnsAJpegWithLongLivedPrivateBrowserCaching() throws Exception {
        String sourceUrl = "https://image.greating.co.kr/IL/item/202312/29/product.jpg";
        byte[] bytes = {1, 2, 3, 4};
        given(productThumbnailService.get(sourceUrl)).willReturn(new ProductThumbnailService.Thumbnail(bytes));

        mockMvc.perform(get("/api/v1/media/product-thumbnail").param("url", sourceUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=2592000, private, immutable"));
    }
}
