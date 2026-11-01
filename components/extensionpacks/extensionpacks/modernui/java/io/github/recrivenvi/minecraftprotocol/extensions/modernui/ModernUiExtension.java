package io.github.recrivenvi.minecraftprotocol.extensions.modernui;

import io.github.recrivenvi.minecraftprotocol.api.extension.Extension;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;

public final class ModernUiExtension implements Extension {
    @Override
    public void initialize(ExtensionContext context) throws Exception {
        if (context.side() == ExtensionContext.Side.CLIENT)
            new io.github.recrivenvi.minecraftprotocol.extensions.modernui.client
                            .ModernUiClientExtension()
                    .initialize(context);
    }
}
