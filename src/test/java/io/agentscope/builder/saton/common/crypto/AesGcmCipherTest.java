package io.agentscope.builder.saton.common.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AesGcmCipherTest {

    @Autowired AesGcmCipher cipher;

    @Test
    void encryptThenDecryptRoundTrip() {
        String plain = "sk-abc-1234567890";
        String enc = cipher.encrypt(plain);
        assertNotNull(enc);
        assertTrue(enc.startsWith(AesGcmCipher.PREFIX));
        assertNotEquals(plain, enc);
        assertEquals(plain, cipher.decrypt(enc));
    }

    @Test
    void encryptIsNotDeterministic() {
        String plain = "sk-same";
        assertNotEquals(cipher.encrypt(plain), cipher.encrypt(plain),
                "GCM with random IV must produce different ciphertext each call");
    }

    @Test
    void plaintextPassthroughOnDecrypt() {
        // 未加密的字符串解密时原样返回（兼容老值或测试夹具）
        assertEquals("not-encrypted", cipher.decrypt("not-encrypted"));
    }

    @Test
    void nullsAreNulls() {
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }
}
