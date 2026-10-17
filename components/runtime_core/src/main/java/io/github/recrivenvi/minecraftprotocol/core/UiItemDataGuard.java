package io.github.recrivenvi.minecraftprotocol.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Bounded, in-memory item encoding. Public revisions are scoped, keyed opaque tokens, not NBT. */
public final class UiItemDataGuard {
    public static final int MAX_ITEM_BYTES = 2 * 1024 * 1024;
    public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    public static final int MAX_SLOTS = 2048;
    private final Mac mac;
    private final Content empty;

    public UiItemDataGuard() {
        this(randomKey());
    }

    UiItemDataGuard(byte[] key) {
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.clone(), "HmacSHA256"));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Item revision authentication unavailable", failure);
        }
        empty = new Content(sign(ByteBuffer.allocate(0)), true);
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public record Content(String fingerprint, boolean available) {
        public static Content unavailable() {
            return new Content("", false);
        }
    }

    public static final class Budget {
        private int remaining;

        public Budget() {
            this(MAX_FRAME_BYTES);
        }

        public Budget(int bytes) {
            if (bytes < 0 || bytes > MAX_FRAME_BYTES)
                throw new IllegalArgumentException("Item budget");
            remaining = bytes;
        }

        public int remaining() {
            return remaining;
        }
    }

    public Content empty() {
        return empty;
    }

    public Content capture(Budget budget, Consumer<ByteBuf> encode) {
        int allowance = Math.min(MAX_ITEM_BYTES, budget.remaining);
        if (allowance == 0) return Content.unavailable();
        ByteBuf buffer = Unpooled.buffer(Math.min(256, allowance), allowance);
        try {
            encode.accept(buffer);
            budget.remaining -= buffer.readableBytes();
            return new Content(sign(buffer.nioBuffer()), true);
        } catch (RuntimeException encodingFailure) {
            // Failed encodings consume the whole allowance: repeated oversized values cannot
            // each allocate another full item budget in the same menu snapshot.
            budget.remaining -= allowance;
            return Content.unavailable();
        } finally {
            buffer.release();
        }
    }

    public String revision(String context, Content content) {
        if (!content.available()) return "";
        if (context.length() > 256) throw new IllegalArgumentException("Item revision context");
        return signText(context + ":" + content.fingerprint());
    }

    public String signText(String value) {
        if (value.length() > MAX_SLOTS * 192)
            throw new IllegalArgumentException("Menu index budget");
        return sign(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)));
    }

    private synchronized String sign(ByteBuffer bytes) {
        mac.update(bytes);
        return HexFormat.of().formatHex(mac.doFinal());
    }
}
