package io.github.recrivenvi.minecraftprotocol.extensions.emi;

import io.github.recrivenvi.minecraftprotocol.api.extension.Extension;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;

public final class EmiExtension implements Extension {
    @Override
    public void initialize(ExtensionContext context) throws Exception {
        if (context.side() == ExtensionContext.Side.CLIENT)
            new io.github.recrivenvi.minecraftprotocol.extensions.emi.client.EmiClientExtension()
                    .initialize(context);
    }
}
