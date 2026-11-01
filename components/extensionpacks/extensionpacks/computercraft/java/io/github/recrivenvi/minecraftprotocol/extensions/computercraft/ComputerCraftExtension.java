package io.github.recrivenvi.minecraftprotocol.extensions.computercraft;

import io.github.recrivenvi.minecraftprotocol.api.extension.Extension;
import io.github.recrivenvi.minecraftprotocol.api.extension.ExtensionContext;

public final class ComputerCraftExtension implements Extension {
    @Override
    public void initialize(ExtensionContext context) throws Exception {
        if (context.side() == ExtensionContext.Side.CLIENT)
            new io.github.recrivenvi.minecraftprotocol.extensions.computercraft.client
                            .ComputerCraftClientExtension()
                    .initialize(context);
    }
}
