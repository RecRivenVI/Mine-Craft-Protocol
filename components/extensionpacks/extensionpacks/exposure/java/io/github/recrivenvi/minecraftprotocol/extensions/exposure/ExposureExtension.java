package io.github.recrivenvi.minecraftprotocol.extensions.exposure;

import io.github.recrivenvi.minecraftprotocol.api.extension.Extension;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;

public final class ExposureExtension implements Extension {
    @Override
    public void initialize(ExtensionContext context) throws Exception {
        if (context.side() == ExtensionContext.Side.CLIENT)
            new io.github.recrivenvi.minecraftprotocol.extensions.exposure.client
                            .ExposureClientExtension()
                    .initialize(context);
    }
}
