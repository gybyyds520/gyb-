package com.gyb.xqfx.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 云端大模型客户端测试（JDK 内置 HttpServer 模拟 OpenAI 兼容网关）。
 */
@DisplayName("RemoteLlm 云端客户端测试")
class RemoteLlmClientTest {

    private HttpServer server;
    private RemoteLlmClient client;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", this::handleChat);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        lastRequestBody.set(new String(body, StandardCharsets.UTF_8));
        String payload = new String(body, StandardCharsets.UTF_8);
        boolean stream = payload.contains("\"stream\":true");
        String resp;
        if (stream) {
            resp = "data: {\"choices\":[{\"delta\":{\"content\":\"云\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"端\"}}]}\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"回复\"}}]}\n"
                    + "data: [DONE]\n";
        } else {
            resp = "{\"choices\":[{\"message\":{\"content\":\"云端非流式回复\"}}],"
                    + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}";
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp.getBytes(StandardCharsets.UTF_8));
        }
    }

    private RemoteLlmClient newClient(String apiKey) {
        return new RemoteLlmClient(baseUrl, apiKey, "deepseek-chat", "10s", true, new ObjectMapper());
    }

    @Test
    @DisplayName("未配置 api-key 时云端通道不启用")
    void testNotConfigured() {
        RemoteLlmClient c = new RemoteLlmClient(baseUrl, "", "deepseek-chat", "10s", true, new ObjectMapper());
        assertFalse(c.isConfigured());
        assertNull(c.chat("s", "p"));
        assertFalse(c.chatStream("s", "p", x -> { }));
    }

    @Test
    @DisplayName("非流式对话成功并返回模型/令牌信息")
    void testChatSuccess() {
        RemoteLlmClient c = newClient("sk-test");
        OllamaClient.ChatResult r = c.chat("系统提示", "用户问题");
        assertNotNull(r);
        assertEquals("云端非流式回复", r.content);
        assertTrue(r.model.startsWith("cloud:"));
        assertEquals(12, r.promptTokens);
        assertEquals(7, r.completionTokens);
        assertEquals(19, r.totalTokens());
    }

    @Test
    @DisplayName("出校提示词脱敏：手机号与身份证打码")
    void testPromptSanitized() {
        RemoteLlmClient c = newClient("sk-test");
        c.chat("学生张三，电话13812345678，身份证350102199001011234",
                "请分析学号2023010111");
        String body = lastRequestBody.get();
        assertNotNull(body);
        assertTrue(body.contains("138****5678"));
        assertFalse(body.contains("13812345678"));
        assertTrue(body.contains("350********234"));
        assertFalse(body.contains("350102199001011234"));
    }

    @Test
    @DisplayName("脱敏边界：短学号/业务编号不被误伤")
    void testSanitizeBoundary() {
        RemoteLlmClient c = newClient("sk-test");
        c.chat("系统提示", "学号2023010111，订单号123456789，成绩89.5，电话13812345678转分机88");
        String body = lastRequestBody.get();
        assertNotNull(body);
        // 手机号被打码，前后缀保留
        assertTrue(body.contains("138****5678转分机88"));
        // 10 位学号 / 9 位订单号不被误伤
        assertTrue(body.contains("2023010111"));
        assertTrue(body.contains("123456789"));
        // 原文手机号不再出现
        assertFalse(body.contains("13812345678"));
    }

    @Test
    @DisplayName("连通性探测：/models 返回 200 即可用，带缓存")
    void testProbe() throws IOException {
        HttpServer probe = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<Integer> hitCount = new AtomicReference<>(0);
        probe.createContext("/models", exchange -> {
            hitCount.set(hitCount.get() + 1);
            byte[] resp = "{\"data\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        probe.start();
        try {
            RemoteLlmClient c = new RemoteLlmClient(
                    "http://127.0.0.1:" + probe.getAddress().getPort(), "sk-test",
                    "deepseek-chat", "10s", true, new ObjectMapper());
            assertTrue(c.isConfigured());
            assertTrue(c.isAvailable());
            assertTrue(c.lastProbeResult());
            // 60s TTL 内重复探测走缓存，不重复请求
            assertTrue(c.isAvailable());
            assertEquals(1, hitCount.get());
        } finally {
            probe.stop(0);
        }
    }

    @Test
    @DisplayName("连通性探测：服务端异常时不可用并记录错误")
    void testProbeFailure() throws IOException {
        HttpServer probe = HttpServer.create(new InetSocketAddress(0), 0);
        probe.createContext("/models", exchange -> {
            byte[] resp = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        probe.start();
        try {
            RemoteLlmClient c = new RemoteLlmClient(
                    "http://127.0.0.1:" + probe.getAddress().getPort(), "sk-test",
                    "deepseek-chat", "10s", true, new ObjectMapper());
            assertFalse(c.isAvailable());
            assertFalse(c.lastProbeResult());
            assertTrue(c.probeError().contains("401"));
        } finally {
            probe.stop(0);
        }
    }

    @Test
    @DisplayName("未配置 api-key 时探测直接不可用")
    void testProbeNotConfigured() {
        RemoteLlmClient c = new RemoteLlmClient(baseUrl, "", "deepseek-chat", "10s", true, new ObjectMapper());
        assertFalse(c.isAvailable());
    }

    @Test
    @DisplayName("流式对话按 SSE 增量拼接")
    void testChatStream() {
        RemoteLlmClient c = newClient("sk-test");
        List<String> chunks = new ArrayList<>();
        boolean ok = c.chatStream("系统提示", "用户问题", chunks::add);
        assertTrue(ok);
        assertEquals("云端回复", String.join("", chunks));
    }

    @Test
    @DisplayName("服务端异常时 chat 返回 null、流式返回 false")
    void testServerError() throws IOException {
        HttpServer err = HttpServer.create(new InetSocketAddress(0), 0);
        err.createContext("/chat/completions", exchange -> {
            byte[] resp = "{\"error\":\"bad\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        err.start();
        try {
            RemoteLlmClient c = new RemoteLlmClient(
                    "http://127.0.0.1:" + err.getAddress().getPort(), "sk-test",
                    "deepseek-chat", "10s", true, new ObjectMapper());
            assertNull(c.chat("s", "p"));
            assertFalse(c.chatStream("s", "p", x -> { }));
        } finally {
            err.stop(0);
        }
    }
}
