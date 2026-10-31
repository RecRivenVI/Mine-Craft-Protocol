package io.github.recrivenvi.minecraftprotocol.core.extension;

/** Local package diagnostics use stable codes, not Throwable text as a capability contract. */
public final class ExtensionFailure
        extends io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionException {

    public ExtensionFailure(String code, String detail) {
        super(code, detail);
    }

    public ExtensionFailure(String code, String detail, Throwable cause) {
        super(code, detail, cause);
    }
}
