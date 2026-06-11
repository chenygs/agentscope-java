package io.agentscope.builder.saton.common.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 包装。密文格式（base64）= 12 字节 IV || ciphertext || 16 字节 tag。
 * 解密时 IV 从前 12 字节恢复。
 */
@Component
public class AesGcmCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final SecureRandom RNG = new SecureRandom();
    /** 加密文已带这个前缀，方便识别已加密 vs 未加密（升级旧库时容错）。 */
    public static final String PREFIX = "enc:v1:";

    private final SecretKeyHolder keyHolder;

    public AesGcmCipher(SecretKeyHolder keyHolder) {
        this.keyHolder = keyHolder;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, keyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(IV_BYTES + cipherBytes.length);
            bb.put(iv).put(cipherBytes);
            return PREFIX + Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES encrypt failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            // 兼容：未加密的旧值原样返回（也方便测试给明文初始值）
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, keyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decrypt failed", e);
        }
    }
}
