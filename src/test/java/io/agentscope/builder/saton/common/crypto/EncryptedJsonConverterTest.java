package io.agentscope.builder.saton.common.crypto;

import io.agentscope.builder.saton.common.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EncryptedJsonConverterTest {

    @Autowired EncryptedJsonConverter converter;
    @Autowired AesGcmCipher cipher;

    @Test
    void sensitiveFieldsAreEncryptedInDbForm() throws Exception {
        String plain = """
                { "modelName":"qwen-max", "apiKey":"sk-12345", "baseUrl":"https://x" }""";
        String db = converter.convertToDatabaseColumn(plain);

        JsonNode root = JsonUtil.mapper().readTree(db);
        assertEquals("qwen-max", root.get("modelName").asString());
        assertEquals("https://x", root.get("baseUrl").asString());
        String storedApiKey = root.get("apiKey").asString();
        assertTrue(storedApiKey.startsWith(AesGcmCipher.PREFIX), "apiKey must be encrypted");
        assertEquals("sk-12345", cipher.decrypt(storedApiKey));
    }

    @Test
    void readingDecryptsBackToPlain() throws Exception {
        String plain = """
                { "modelName":"qwen-max", "apiKey":"sk-12345" }""";
        String db = converter.convertToDatabaseColumn(plain);
        String back = converter.convertToEntityAttribute(db);
        JsonNode root = JsonUtil.mapper().readTree(back);
        assertEquals("sk-12345", root.get("apiKey").asString());
    }

    @Test
    void nullPassThrough() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void plaintextLegacyJsonReadStillWorks() throws Exception {
        // 早期未加密的 JSON 行；converter 应原样解出
        String legacy = "{\"apiKey\":\"sk-old-plaintext\"}";
        String back = converter.convertToEntityAttribute(legacy);
        JsonNode root = JsonUtil.mapper().readTree(back);
        assertEquals("sk-old-plaintext", root.get("apiKey").asString());
    }
}
