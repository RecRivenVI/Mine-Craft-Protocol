package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

final class RecipeButtonSemanticTest {
    private static final String TREE =
            "io/github/recrivenvi/minecraftprotocol/probe/runtime/VanillaUiTree";
    private static final String BUTTON = "net/minecraft/client/gui/screens/recipebook/RecipeButton";
    private static final String COLLECTION =
            "net/minecraft/client/gui/screens/recipebook/RecipeCollection";
    private static final String RECIPE = "net/minecraft/world/item/crafting/Recipe";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String RESULT = "(Lnet/minecraft/core/RegistryAccess;)L" + STACK + ";";

    @Test
    void productionUsesTheSameSelectedRecipeAndResultAsTheActualNativeDrawAndTooltip()
            throws Exception {
        ClassNode button = compiled(BUTTON);
        MethodNode selected = method(button, "getRecipe");
        assertEquals("()Lnet/minecraft/world/item/crafting/Recipe;", selected.desc);
        assertTrue(calls(selected, BUTTON, "getOrderedRecipes"));
        assertTrue(field(selected, BUTTON, "currentIndex", Opcodes.GETFIELD));
        MethodNode render = method(button, "renderWidget");
        assertTrue(field(render, BUTTON, "currentIndex", Opcodes.PUTFIELD));
        assertTrue(field(render, BUTTON, "currentIndex", Opcodes.GETFIELD));
        assertTrue(calls(render, BUTTON, "getOrderedRecipes"));
        assertTrue(calls(render, RECIPE, "getResultItem", RESULT));
        assertTrue(calls(method(button, "getTooltipText"), RECIPE, "getResultItem", RESULT));
        MethodNode capture = capture();
        assertTrue(calls(capture, BUTTON, "getRecipe", selected.desc));
        assertTrue(calls(capture, BUTTON, "getCollection", "()L" + COLLECTION + ";"));
        assertTrue(
                calls(
                        capture,
                        COLLECTION,
                        "registryAccess",
                        "()Lnet/minecraft/core/RegistryAccess;"));

        assertTrue(calls(capture, RECIPE, "getResultItem", RESULT));
        for (String getter : List.of("getHoverName", "getItem", "getCount"))
            assertTrue(calls(capture, STACK, getter), getter);
        for (String key : List.of("label", "item", "count", "recipe"))
            assertTrue(constants(capture).contains(key), key);
        assertFalse(calls(capture, COLLECTION, "getRecipes"));
        assertFalse(calls(capture, COLLECTION, "getDisplayRecipes"));
        assertFalse(
                calls(capture, BUTTON, "getOrderedRecipes"),
                "Only the native selected result, not its hidden recipe collection, is published");
    }

    @Test
    void nativeRecipeSelectionIsReadOnlyAndGatedByTheCurrentVisibleClippedWidget()
            throws Exception {
        MethodNode capture = capture();
        var instructions = capture.instructions.toArray();
        int type = -1, clip = -1, width = -1, height = -1;
        int selected = index(capture, BUTTON, "getRecipe");
        for (int i = 0; i < selected; i++) {
            if (instructions[i] instanceof TypeInsnNode value
                    && value.getOpcode() == Opcodes.INSTANCEOF
                    && value.desc.equals(BUTTON)) type = i;
            if (type >= 0 && instructions[i] instanceof MethodInsnNode call) {
                if (call.owner.equals(TREE) && call.name.equals("intersect")) clip = i;
                if (call.owner.equals("net/minecraft/client/gui/navigation/ScreenRectangle")
                        && call.name.equals("width")) width = i;
                if (call.owner.equals("net/minecraft/client/gui/navigation/ScreenRectangle")
                        && call.name.equals("height")) height = i;
            }
        }
        assertTrue(type >= 0 && clip > type && width > clip && height > width && selected > height);
        assertTrue(
                Arrays.stream(instructions)
                                .skip(type + 1L)
                                .limit(clip - type - 1L)
                                .filter(JumpInsnNode.class::isInstance)
                                .count()
                        >= 2,
                "Both the actual widget's visible and active states gate native result reading");
        assertTrue(
                Arrays.stream(instructions)
                                .skip(clip + 1L)
                                .limit(selected - clip - 1L)
                                .filter(JumpInsnNode.class::isInstance)
                                .count()
                        >= 3,
                "Positive clip dimensions and an initialized native collection are required");
        assertFalse(
                calls(
                        capture,
                        "net/minecraft/client/gui/screens/recipebook/RecipeBookPage",
                        "recipeClicked"));
        assertFalse(
                calls(
                        capture,
                        "net/minecraft/client/multiplayer/MultiPlayerGameMode",
                        "handlePlaceRecipe"));
        assertFalse(calls(capture, BUTTON, "mouseClicked"));
        assertTrue(
                calls(
                        capture,
                        "net/minecraft/client/gui/screens/recipebook/RecipeBookComponent",
                        "isVisible"));
    }

    @Test
    void nativeSearchCategoryRemainsItsActualLegacyIdentityRatherThanAModernAlias()
            throws Exception {
        ClassNode categories = compiled("net/minecraft/client/RecipeBookCategories");
        assertTrue(
                categories.fields.stream()
                        .anyMatch(
                                field ->
                                        field.name.equals("CRAFTING_SEARCH")
                                                && field.desc.equals(
                                                        "Lnet/minecraft/client/RecipeBookCategories;")));
        ClassNode tab = compiled("net/minecraft/client/gui/screens/recipebook/RecipeBookTabButton");
        assertEquals(
                "()Lnet/minecraft/client/RecipeBookCategories;", method(tab, "getCategory").desc);
        assertTrue(
                calls(
                        capture(),
                        tab.name,
                        "getCategory",
                        "()Lnet/minecraft/client/RecipeBookCategories;"));
        assertTrue(constants(capture()).contains("tab"));
    }

    private static MethodNode capture() throws Exception {
        return compiled(TREE).methods.stream()
                .filter(
                        method ->
                                method.name.equals("capture")
                                        && method.desc.startsWith(
                                                "(Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
                .findFirst()
                .orElseThrow();
    }

    private static boolean field(MethodNode method, String owner, String name, int opcode) {
        return Arrays.stream(method.instructions.toArray())
                .anyMatch(
                        instruction ->
                                instruction instanceof FieldInsnNode field
                                        && field.getOpcode() == opcode
                                        && field.owner.equals(owner)
                                        && field.name.equals(name));
    }

    private static boolean calls(MethodNode method, String owner, String name, String descriptor) {
        return Arrays.stream(method.instructions.toArray())
                .anyMatch(
                        instruction ->
                                instruction instanceof MethodInsnNode call
                                        && call.owner.equals(owner)
                                        && call.name.equals(name)
                                        && call.desc.equals(descriptor));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        return index(method, owner, name) >= 0;
    }

    private static int index(MethodNode method, String owner, String name) {
        var instructions = method.instructions.toArray();
        for (int i = 0; i < instructions.length; i++)
            if (instructions[i] instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)) return i;
        return -1;
    }

    private static List<Object> constants(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .map(instruction -> instruction.cst)
                .toList();
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled(String owner) throws Exception {
        try (InputStream input =
                RecipeButtonSemanticTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input, owner);
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
