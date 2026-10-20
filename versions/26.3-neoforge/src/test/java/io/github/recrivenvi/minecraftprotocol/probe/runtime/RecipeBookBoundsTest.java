package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.AutomationEngine;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.RecipeOverlayUiAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.RecipePageAccessor;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.RecipeUiAccessor;
import io.github.recrivenvi.minecraftprotocol.safety.UiDataView;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

class RecipeBookBoundsTest {
    @Test
    void actualNativeRecipeOverlayIsARootModalAndNeverPrunedWithItsOccludedBookOwner()
            throws Exception {
        FixtureBook book = new FixtureBook(true);
        book.page.overlay = new FixtureOverlay();
        book.page.overlay.setVisible(true);
        JsonObject captured = captureScreen(book);
        JsonArray raw = captured.getAsJsonArray("children");
        assertEquals(
                1,
                java.util.stream.StreamSupport.stream(raw.spliterator(), false)
                        .filter(
                                value ->
                                        value.getAsJsonObject().has("controlKey")
                                                && value.getAsJsonObject()
                                                        .get("controlKey")
                                                        .getAsString()
                                                        .equals("recipe.overlay"))
                        .count());
        assertTrue(raw.get(0).getAsJsonObject().get("occluded").getAsBoolean());
        assertTrue(raw.get(1).getAsJsonObject().get("occluded").getAsBoolean());
        JsonObject user = UiDataView.tree(captured, UiDataView.Category.USER);
        assertEquals(1, user.getAsJsonArray("children").size());
        JsonObject modal = user.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("recipe.overlay", modal.get("controlKey").getAsString());
        assertEquals(50, modal.get("interactionLayer").getAsInt());
        assertEquals(2, modal.getAsJsonArray("children").size());
        JsonObject optionSelector = new JsonObject();
        optionSelector.addProperty("controlKey", "recipe.overlay.option.0");
        JsonObject option =
                AutomationEngine.resolveTree(user, optionSelector).getAsJsonObject("node");
        assertEquals("recipe_option", option.get("role").getAsString());
        assertEquals(51, option.get("interactionLayer").getAsInt());
        assertThrows(
                RuntimeException.class,
                () -> AutomationEngine.resolveTree(user, selector("Visible recipe control")));
        assertThrows(
                RuntimeException.class,
                () -> AutomationEngine.resolveTree(user, selector("Underlying control")));
    }

    @Test
    void aClosedNativeOverlayDoesNotPublishOrOccludeTheBookAndGameOverlayStillBlocksAllTargets()
            throws Exception {
        FixtureBook book = new FixtureBook(true);
        book.page.overlay = new FixtureOverlay();
        book.page.overlay.setVisible(false);
        JsonObject captured = captureScreen(book),
                user = UiDataView.tree(captured, UiDataView.Category.USER);
        assertEquals(2, user.getAsJsonArray("children").size());
        AutomationEngine.resolveTree(user, selector("Visible recipe control"));
        AutomationEngine.resolveTree(user, selector("Underlying control"));
        JsonObject overlaySelector = new JsonObject();
        overlaySelector.addProperty("controlKey", "recipe.overlay");
        assertThrows(
                RuntimeException.class, () -> AutomationEngine.resolveTree(user, overlaySelector));
        book.page.overlay.setVisible(true);
        captured = captureScreen(book);
        captured.addProperty("overlayIdentity", 12);
        assertTrue(
                UiDataView.tree(captured, UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .isEmpty());
    }

    @Test
    void tabSelectionIsTheActualBookOwnerIdentityAndUnknownCategoriesNeverLeakObjectHashes()
            throws Exception {
        FixtureBook book = new FixtureBook(true);
        var first =
                new RecipeBookTabButton(
                        54,
                        65,
                        new RecipeBookComponent.TabInfo(
                                null, Optional.empty(), new FixtureCategory()),
                        ignored -> {});
        var second =
                new RecipeBookTabButton(
                        54,
                        94,
                        new RecipeBookComponent.TabInfo(
                                null, Optional.empty(), new FixtureCategory()),
                        ignored -> {});
        first.setMessage(Component.literal("Fixture native tab"));
        second.setMessage(Component.literal("Fixture native tab"));
        book.tabs = List.of(first, second);
        book.setNative("selectedTab", first);
        JsonObject user =
                UiDataView.tree(
                        capture(book, new ScreenRectangle(0, 0, 480, 270)),
                        UiDataView.Category.USER);
        JsonArray tabs =
                user.getAsJsonArray("children").get(0).getAsJsonObject().getAsJsonArray("children");
        assertTrue(tabs.get(0).getAsJsonObject().get("selected").getAsBoolean());
        assertFalse(tabs.get(1).getAsJsonObject().get("selected").getAsBoolean());
        assertEquals("", tabs.get(0).getAsJsonObject().get("label").getAsString());
        assertFalse(tabs.get(0).getAsJsonObject().has("resourceId"));
        book.setNative("selectedTab", second);
        JsonArray changed =
                UiDataView.tree(
                                capture(book, new ScreenRectangle(0, 0, 480, 270)),
                                UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("children");
        assertFalse(changed.get(0).getAsJsonObject().get("selected").getAsBoolean());
        assertTrue(changed.get(1).getAsJsonObject().get("selected").getAsBoolean());
        book.setNative("visible", false);
        assertTrue(
                UiDataView.tree(
                                capture(book, new ScreenRectangle(0, 0, 480, 270)),
                                UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .isEmpty());
    }

    @Test
    void stableCategoryIdentityAndSelectionAreWiredToNativeRegistryEnumAndOwner() throws Exception {
        Method getter = RecipeUiAccessor.class.getMethod("minecraftProtocolProbe$selectedTab");
        assertEquals(RecipeBookTabButton.class, getter.getReturnType());
        assertEquals("selectedTab", getter.getAnnotation(Accessor.class).value());
        assertEquals(
                RecipeBookTabButton.class,
                RecipeBookComponent.class.getDeclaredField("selectedTab").getType());
        var capture =
                bytecode(VanillaUiTree.class).methods.stream()
                        .filter(
                                method ->
                                        method.name.equals("capture")
                                                && method.desc.startsWith(
                                                        "(Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
                        .findFirst()
                        .orElseThrow();
        var instructions = java.util.Arrays.stream(capture.instructions.toArray()).toList();
        assertTrue(
                instructions.stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof FieldInsnNode field
                                                && field.owner.equals(
                                                        "net/minecraft/core/registries/BuiltInRegistries")
                                                && field.name.equals("RECIPE_BOOK_CATEGORY")));
        assertTrue(
                instructions.stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals("net/minecraft/core/Registry")
                                                && call.name.equals("getKey")));
        assertTrue(
                instructions.stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/client/gui/screens/recipebook/SearchRecipeBookCategory")
                                                && call.name.equals("name")));
        assertTrue(
                instructions.stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        RecipeUiAccessor.class
                                                                .getName()
                                                                .replace('.', '/'))
                                                && call.name.equals(getter.getName())));
        assertFalse(
                instructions.stream()
                        .anyMatch(
                                instruction ->
                                        instruction instanceof MethodInsnNode call
                                                && call.owner.equals(
                                                        "net/minecraft/world/item/crafting/ExtendedRecipeBookCategory")
                                                && call.name.equals("toString")));
    }

    @Test
    void visibleNativeBookSurvivesProductionCaptureUserProjectionAndSemanticResolution()
            throws Exception {
        FixtureBook book = new FixtureBook(true);
        assertEquals(
                GuiEventListener.class,
                book.getClass().getMethod("getRectangle").getDeclaringClass());
        assertEquals(0, book.getRectangle().width());
        assertEquals(0, book.getRectangle().height());
        JsonObject captured = capture(book, new ScreenRectangle(0, 0, 480, 270));
        JsonObject parent = captured.getAsJsonArray("children").get(0).getAsJsonObject();
        assertTrue(parent.get("visible").getAsBoolean());
        assertEquals(80, parent.get("x").getAsInt());
        assertEquals(52, parent.get("y").getAsInt());
        assertEquals(RecipeBookComponent.IMAGE_WIDTH, parent.get("width").getAsInt());
        assertEquals(RecipeBookComponent.IMAGE_HEIGHT, parent.get("height").getAsInt());
        JsonObject user = UiDataView.tree(captured, UiDataView.Category.USER);
        assertEquals(1, user.getAsJsonArray("children").size());
        JsonObject resolved =
                AutomationEngine.resolveTree(user, selector("Visible recipe control"));
        assertEquals(132, resolved.getAsJsonObject("interactionPoint").get("x").getAsDouble());
        assertEquals(82, resolved.getAsJsonObject("interactionPoint").get("y").getAsDouble());
        assertEquals(2, book.originCalls);
    }

    @Test
    void nativeControlsOutsideTheBackgroundKeepTheirRealViewportBounds() throws Exception {
        FixtureBook book = new FixtureBook(true);
        JsonObject user =
                UiDataView.tree(
                        capture(book, new ScreenRectangle(0, 0, 480, 270)),
                        UiDataView.Category.USER);
        JsonObject node =
                AutomationEngine.resolveTree(user, selector("Left recipe control"))
                        .getAsJsonObject("node");
        assertEquals(54, node.get("x").getAsInt());
        assertEquals(25, node.get("width").getAsInt());
        assertTrue(node.get("visible").getAsBoolean());
        assertTrue(
                node.get("x").getAsInt()
                        < user.getAsJsonArray("children")
                                .get(0)
                                .getAsJsonObject()
                                .get("x")
                                .getAsInt());
    }

    @Test
    void closedNativeBookDoesNotEnumerateOrExposeItsCachedControls() throws Exception {
        FixtureBook book = new FixtureBook(false);
        JsonObject captured = capture(book, new ScreenRectangle(0, 0, 480, 270));
        assertFalse(
                captured.getAsJsonArray("children")
                        .get(0)
                        .getAsJsonObject()
                        .get("visible")
                        .getAsBoolean());
        assertEquals(0, book.originCalls);
        assertEquals(0, book.pageReads);
        assertEquals(
                0,
                UiDataView.tree(captured, UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .size());
    }

    @Test
    void nativeBackgroundAndChildrenStillRespectTheViewportAndBlockingOverlay() throws Exception {
        JsonObject clipped = capture(new FixtureBook(true), new ScreenRectangle(0, 0, 100, 80));
        JsonObject parent = clipped.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals(20, parent.get("width").getAsInt());
        assertEquals(28, parent.get("height").getAsInt());
        assertTrue(parent.get("clipped").getAsBoolean());
        JsonObject user = UiDataView.tree(clipped, UiDataView.Category.USER);
        JsonObject node =
                AutomationEngine.resolveTree(user, selector("Visible recipe control"))
                        .getAsJsonObject("node");
        assertEquals(8, node.get("width").getAsInt());
        assertEquals(8, node.get("height").getAsInt());
        JsonObject outside = capture(new FixtureBook(true), new ScreenRectangle(0, 0, 40, 40));
        assertEquals(
                0,
                UiDataView.tree(outside, UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .size());
        clipped.addProperty("overlayIdentity", 9);
        assertEquals(
                0,
                UiDataView.tree(clipped, UiDataView.Category.USER)
                        .getAsJsonArray("children")
                        .size());
    }

    @Test
    void originInvokersBindTheActualPrivateMethodsUsedByNativeDrawingAndMouseDispatch()
            throws Exception {
        ClassNode nativeType = bytecode(RecipeBookComponent.class);
        for (String axis : List.of("X", "Y")) {
            Method bridge =
                    RecipeUiAccessor.class.getMethod(
                            "minecraftProtocolProbe$" + axis.toLowerCase() + "Origin");
            Invoker invoker = bridge.getAnnotation(Invoker.class);
            assertNotNull(invoker);
            assertEquals("get" + axis + "Origin", invoker.value());
            Method original = RecipeBookComponent.class.getDeclaredMethod(invoker.value());
            assertEquals(int.class, original.getReturnType());
            assertTrue(Modifier.isPrivate(original.getModifiers()));
            for (String entry : List.of("extractRenderState", "mouseClicked")) {
                assertTrue(
                        nativeType.methods.stream()
                                .filter(method -> method.name.equals(entry))
                                .flatMap(
                                        method ->
                                                java.util.Arrays.stream(
                                                        method.instructions.toArray()))
                                .anyMatch(
                                        instruction ->
                                                instruction instanceof MethodInsnNode call
                                                        && call.owner.equals(nativeType.name)
                                                        && call.name.equals(invoker.value())
                                                        && call.desc.equals("()I")));
            }
        }
    }

    private static JsonObject selector(String label) {
        JsonObject selector = new JsonObject();
        selector.addProperty("label", label);
        return selector;
    }

    private static JsonObject capture(FixtureBook book, ScreenRectangle clip) {
        JsonObject root =
                JsonParser.parseString(
                                "{\"screenClass\":\"native.InventoryScreen\",\"screenIdentity\":1,\"overlayIdentity\":0}")
                        .getAsJsonObject();
        JsonArray children =
                new VanillaUiTree(1, System::identityHashCode).captureChildren(List.of(book), clip);
        root.add("children", children);
        return root;
    }

    private static JsonObject captureScreen(FixtureBook book) {
        JsonObject root =
                JsonParser.parseString(
                                "{\"screenClass\":\"native.InventoryScreen\",\"screenIdentity\":1,\"overlayIdentity\":0}")
                        .getAsJsonObject();
        Screen screen =
                new Screen(null, null, Component.literal("Fixture")) {
                    {
                        addWidget(book);
                        addRenderableWidget(
                                Button.builder(
                                                Component.literal("Underlying control"),
                                                ignored -> {})
                                        .bounds(300, 100, 80, 20)
                                        .build());
                    }
                };
        screen.width = 480;
        screen.height = 270;
        root.add("children", new VanillaUiTree(1, System::identityHashCode).capture(screen, root));
        return root;
    }

    private static ClassNode bytecode(Class<?> type) throws IOException {
        try (var input =
                type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static final class FixtureBook extends RecipeBookComponent<RecipeBookMenu>
            implements RecipeUiAccessor {
        private final FixturePage page;
        private List<RecipeBookTabButton> tabs = List.of();
        private int originCalls;
        private int pageReads;

        FixtureBook(boolean visible) throws ReflectiveOperationException {
            super(null, List.of());
            page = new FixturePage(this);
            setNative("width", 480);
            setNative("height", 270);
            setNative("xOffset", 86);
            setNative("visible", visible);
        }

        private void setNative(String name, Object value) throws ReflectiveOperationException {
            Field field = RecipeBookComponent.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        }

        private int origin(String name) {
            try {
                originCalls++;
                Method method = RecipeBookComponent.class.getDeclaredMethod(name);
                method.setAccessible(true);
                return (int) method.invoke(this);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public int minecraftProtocolProbe$xOrigin() {
            return origin("getXOrigin");
        }

        @Override
        public int minecraftProtocolProbe$yOrigin() {
            return origin("getYOrigin");
        }

        @Override
        public List<RecipeBookTabButton> minecraftProtocolProbe$tabs() {
            return tabs;
        }

        @Override
        public RecipeBookTabButton minecraftProtocolProbe$selectedTab() {
            try {
                Field field = RecipeBookComponent.class.getDeclaredField("selectedTab");
                field.setAccessible(true);
                return (RecipeBookTabButton) field.get(this);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public EditBox minecraftProtocolProbe$search() {
            return null;
        }

        @Override
        public CycleButton<Boolean> minecraftProtocolProbe$filter() {
            return null;
        }

        @Override
        public RecipeBookPage minecraftProtocolProbe$page() {
            pageReads++;
            return page;
        }

        @Override
        protected WidgetSprites getFilterButtonTextures() {
            return RECIPE_BUTTON_SPRITES;
        }

        @Override
        protected boolean isCraftingSlot(Slot slot) {
            return false;
        }

        @Override
        protected void selectMatchingRecipes(
                RecipeCollection collection, StackedItemContents contents) {}

        @Override
        protected Component getRecipeFilterName() {
            return Component.literal("Fixture filter");
        }

        @Override
        protected void fillGhostRecipe(
                GhostSlots slots, RecipeDisplay display, ContextMap context) {}
    }

    private static final class FixtureCategory implements ExtendedRecipeBookCategory {}

    private static final class FixturePage extends RecipeBookPage implements RecipePageAccessor {
        private FixtureOverlay overlay;
        private final List<Button> controls =
                List.of(
                        Button.builder(Component.literal("Visible recipe control"), ignored -> {})
                                .bounds(92, 72, 80, 20)
                                .build(),
                        Button.builder(Component.literal("Left recipe control"), ignored -> {})
                                .bounds(54, 65, 25, 20)
                                .build());

        FixturePage(RecipeBookComponent<?> owner) {
            super(owner, () -> 0, false);
        }

        @Override
        public OverlayRecipeComponent minecraftProtocolProbe$overlay() {
            return overlay;
        }

        @Override
        public ImageButton minecraftProtocolProbe$forward() {
            return null;
        }

        @Override
        public ImageButton minecraftProtocolProbe$back() {
            return null;
        }

        @Override
        public void minecraftProtocolProbe$buttons(Consumer<AbstractWidget> consumer) {
            controls.forEach(consumer);
        }
    }

    private static final class FixtureOverlay extends OverlayRecipeComponent
            implements RecipeOverlayUiAccessor {
        private final List<Button> controls =
                List.of(
                        Button.builder(
                                        Component.literal("Native first recipe option"),
                                        ignored -> {})
                                .bounds(120, 100, 25, 25)
                                .build(),
                        Button.builder(
                                        Component.literal("Native second recipe option"),
                                        ignored -> {})
                                .bounds(147, 100, 25, 25)
                                .build());

        FixtureOverlay() {
            super(() -> 0, false);
        }

        @Override
        public List<? extends AbstractWidget> minecraftProtocolProbe$buttons() {
            return controls;
        }
    }
}
