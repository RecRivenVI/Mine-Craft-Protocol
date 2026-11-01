package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface SignUiAccessor {
    @Accessor("line")
    int mineCraftProtocol$line();

    @Accessor("line")
    void mineCraftProtocol$line(int line);

    @Accessor("signField")
    TextFieldHelper mineCraftProtocol$field();

    @Accessor("messages")
    String[] mineCraftProtocol$messages();

    @Accessor("sign")
    SignBlockEntity mineCraftProtocol$sign();

    @Invoker("isValid")
    boolean mineCraftProtocol$valid();
}
