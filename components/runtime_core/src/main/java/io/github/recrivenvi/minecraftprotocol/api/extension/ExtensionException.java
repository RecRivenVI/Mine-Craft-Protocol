package io.github.recrivenvi.minecraftprotocol.api.extension;

/** Stable local SDK failure; installed packages need not link Runtime implementation classes. */
public class ExtensionException extends RuntimeException {
    private final String code;

    public ExtensionException(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public ExtensionException(String code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }

    public final String code() {
        return code;
    }
}
