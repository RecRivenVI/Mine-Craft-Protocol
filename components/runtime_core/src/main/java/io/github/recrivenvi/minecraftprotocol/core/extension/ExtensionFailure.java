package io.github.recrivenvi.minecraftprotocol.core.extension;

/** Local package diagnostics use stable codes, not Throwable text as a capability contract. */
public final class ExtensionFailure extends RuntimeException {
    private final String code;

    public ExtensionFailure(String code, String detail) {
        super(detail);
        this.code = code;
    }

    public ExtensionFailure(String code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
