package com.gyb.xqfx.service;

import com.gyb.xqfx.model.ml.MlFeatureRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部开源数据集加载器测试。
 */
@DisplayName("外部开源数据集加载测试")
class ExternalDatasetLoaderTest {

    private ExternalDatasetLoader loader;
    private Path tmp;

    @BeforeEach
    void setUp() throws IOException {
        loader = new ExternalDatasetLoader();
        tmp = Files.createTempFile("external_features", ".csv");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tmp);
    }

    @Test
    @DisplayName("未配置路径时返回不存在且加载为空")
    void testNotConfigured() {
        ReflectionTestUtils.setField(loader, "externalDatasetPath", "");
        assertFalse(loader.exists());
        assertTrue(loader.load().isEmpty());
    }

    @Test
    @DisplayName("解析外部特征 CSV 并映射为 MlFeatureRow")
    void testParse() throws IOException {
        Files.writeString(tmp, "student_id,term_id,avg_score,score_trend,score_std,min_score,"
                + "fail_count,fail_rate,grade_point_avg,attendance_rate,late_count,absent_count,"
                + "avg_participation,avg_homework,question_count,study_minutes_avg,video_progress_avg,"
                + "resource_view_count,forum_post_count,assignment_submit_rate,credit_total,course_count,label\n"
                + "9000000,-1,73.33,5.0,6.24,65.0,0,0.0,0.0,1.0,0,3,42.0,0.0,15,180.0,0.0,22,8,1.0,0.0,1,0\n"
                + "9000001,-1,28.33,5.0,2.36,25.0,3,1.0,0.0,1.0,0,0,0.0,0.0,0,120.0,0.0,0,0,0.0,0.0,1,1\n",
                StandardCharsets.UTF_8);
        ReflectionTestUtils.setField(loader, "externalDatasetPath", tmp.toString());

        List<MlFeatureRow> rows = loader.load();
        assertEquals(2, rows.size());
        MlFeatureRow r0 = rows.get(0);
        assertEquals(9000000L, r0.getStudentId());
        assertEquals(-1L, r0.getTermId());
        assertEquals(73.33, r0.getAvgScore(), 0.001);
        assertEquals(3, r0.getAbsentCount());
        assertEquals(15, r0.getQuestionCount());
        assertEquals(22, r0.getResourceViewCount());
        assertEquals(8, r0.getForumPostCount());
        assertEquals(0, r0.getLabel());
        MlFeatureRow r1 = rows.get(1);
        assertEquals(1, r1.getLabel());
        assertEquals(3, r1.getFailCount());
    }

    @Test
    @DisplayName("文件缺失时返回空且不抛异常")
    void testMissingFile() throws IOException {
        Files.deleteIfExists(tmp);
        ReflectionTestUtils.setField(loader, "externalDatasetPath", tmp.toString());
        assertFalse(loader.exists());
        assertTrue(loader.load().isEmpty());
    }
}
