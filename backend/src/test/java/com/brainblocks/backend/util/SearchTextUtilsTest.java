package com.brainblocks.backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextUtilsTest {

    @Test
    void stripsVietnameseMarksAndLowercases() {
        assertThat(SearchTextUtils.normalize("Bộ Lắp Ráp Đồ Chơi Ướt Ỷ")).isEqualTo("bo lap rap do choi uot y");
        assertThat(SearchTextUtils.normalize("đường ĐI")).isEqualTo("duong di");
    }

    @Test
    void collapsesWhitespaceAndKeepsNull() {
        assertThat(SearchTextUtils.normalize("  robot \n  dò   đường ")).isEqualTo("robot do duong");
        assertThat(SearchTextUtils.normalize(null)).isNull();
    }
}
