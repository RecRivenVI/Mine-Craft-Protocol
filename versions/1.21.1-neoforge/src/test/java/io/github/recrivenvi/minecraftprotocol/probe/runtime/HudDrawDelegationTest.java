package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

class HudDrawDelegationTest {
    @Test
    void everyHudDelegateDrawsExactlyOnceWithUnchangedArgumentsBeforeObservation()
            throws Exception {
        int delegates = 0;
        for (String name : MIXINS) {
            try (InputStream input =
                    getClass()
                            .getResourceAsStream(
                                    "/io/github/recrivenvi/minecraftprotocol/probe/mixin/"
                                            + name
                                            + ".class")) {
                assertNotNull(input, name);
                ClassNode compiled = new ClassNode();
                new ClassReader(input)
                        .accept(compiled, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (MethodNode method : compiled.methods) {
                    if (java.util.stream.Stream.of(
                                    method.visibleAnnotations, method.invisibleAnnotations)
                            .filter(java.util.Objects::nonNull)
                            .flatMap(List::stream)
                            .noneMatch(
                                    a ->
                                            a.desc.equals(
                                                    "Lorg/spongepowered/asm/mixin/injection/Redirect;")))
                        continue;
                    delegates++;
                    var instructions =
                            java.util.Arrays.stream(method.instructions.toArray())
                                    .filter(i -> i.getOpcode() >= 0)
                                    .toList();
                    Type[] args = Type.getArgumentTypes(method.desc);
                    int local = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                    for (int i = 0; i < args.length; i++) {
                        VarInsnNode load =
                                assertInstanceOf(
                                        VarInsnNode.class, instructions.get(i), method.name);
                        assertEquals(
                                args[i].getOpcode(Opcodes.ILOAD), load.getOpcode(), method.name);
                        assertEquals(local, load.var, method.name);
                        local += args[i].getSize();
                    }
                    MethodInsnNode draw =
                            assertInstanceOf(
                                    MethodInsnNode.class,
                                    instructions.get(args.length),
                                    method.name);
                    assertEquals(Opcodes.INVOKEVIRTUAL, draw.getOpcode(), method.name);
                    assertTrue(
                            draw.owner.startsWith("net/minecraft/client/gui/GuiGraphics"),
                            method.name);
                    assertTrue(
                            draw.name.equals("blit") || draw.name.equals("blitSprite"),
                            method.name);
                    assertEquals(
                            1,
                            instructions.stream()
                                    .filter(
                                            i ->
                                                    i instanceof MethodInsnNode call
                                                            && call.owner.equals(draw.owner)
                                                            && call.name.equals(draw.name))
                                    .count(),
                            method.name);
                    assertEquals(Type.VOID_TYPE, Type.getReturnType(draw.desc));
                    assertEquals(
                            List.of(args).subList(1, args.length),
                            List.of(Type.getArgumentTypes(draw.desc)));
                }
            }
        }
        assertEquals(5, delegates);
    }

    private static final List<String> MIXINS = List.of("HudVitalIconsMixin");
}
