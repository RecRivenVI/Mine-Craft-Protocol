package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import java.util.*;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;

final class VanillaUiTree {
    private final long screenRevision;
    private final ToLongFunction<Object> identity;
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final JsonArray recipeOverlays = new JsonArray();
    private int count;
    private boolean truncated;
    private Screen screen;
    private VanillaInteractionRegions interactionRegions;

    VanillaUiTree(long screenRevision, ToLongFunction<Object> identity) {
        this.screenRevision = screenRevision;
        this.identity = identity;
    }

    JsonArray capture(Screen screen, JsonObject root) {
        this.screen = screen;
        interactionRegions = new VanillaInteractionRegions(screenRevision, identity);
        JsonArray regions = interactionRegions.capture(screen);
        ScreenRectangle clip = new ScreenRectangle(0, 0, screen.width, screen.height);
        JsonArray children = captureChildren(screen.children(), clip);
        if (screen instanceof CreativeModeInventoryScreen creative) {
            CreativeUiAccessor access = (CreativeUiAccessor) creative;
            for (CreativeModeTab tab : creative.getCurrentPage().getVisibleTabs()) {
                if (count >= 2048) {
                    truncated = true;
                    break;
                }
                JsonObject node = new JsonObject();
                node.addProperty(
                        "nodeId", "ui:" + screenRevision + ":" + identity.applyAsLong(tab));
                node.addProperty("elementIdentity", identity.applyAsLong(tab));
                node.addProperty("class", tab.getClass().getName());
                node.addProperty("role", "tab");
                node.addProperty("label", tab.getDisplayName().getString());
                node.addProperty(
                        "resourceId", BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString());
                node.addProperty(
                        "selected", tab == CreativeUiAccessor.minecraftProtocolProbe$selectedTab());
                node.addProperty("coverage", "semantic_native");
                node.addProperty("acquisition", "typed_vanilla_adapter");
                // Native checkTabClicked uses local getTabX/Y and a 26 by 32 hit box.
                bounds(
                        node,
                        new ScreenRectangle(
                                creative.getLeftPos() + access.minecraftProtocolProbe$tabX(tab),
                                creative.getTopPos() + access.minecraftProtocolProbe$tabY(tab),
                                26,
                                32),
                        clip,
                        true,
                        tab.shouldDisplay());
                actions(node, "hover", "click", "mouse_down", "mouse_up");
                children.add(node);
                count++;
            }
        }
        for (JsonElement value :
                new RenderedUiAdapter(screenRevision, identity).capture(screen, root)) {
            int added = nodeCount(value.getAsJsonObject());
            if (count + added > 2048) {
                truncated = true;
                break;
            }
            children.add(value);
            count += added;
        }
        for (JsonElement value : regions) {
            if (count >= 2048) {
                truncated = true;
                break;
            }
            children.add(value);
            count++;
        }
        truncated |= interactionRegions.truncated();
        markOccluded(children);
        root.addProperty("treeNodeCount", count);
        root.addProperty("treeTruncated", truncated);
        root.addProperty("treeMaxNodes", 2048);
        root.addProperty("treeMaxDepth", 16);
        JsonArray limits = new JsonArray();
        limits.add("render_only_controls_require_typed_screen_adapter");
        root.add("treeLimitations", limits);
        return children;
    }

    JsonArray captureChildren(List<? extends GuiEventListener> listeners, ScreenRectangle clip) {
        JsonArray children = new JsonArray();
        for (GuiEventListener child : listeners) {
            if (count >= 2048) {
                truncated = true;
                break;
            }
            JsonObject node = capture(child, clip, 0, true);
            if (node != null) children.add(node);
        }
        children.addAll(recipeOverlays);
        return children;
    }

    private JsonObject capture(
            GuiEventListener child, ScreenRectangle clip, int depth, boolean enabled) {
        if (depth > 16 || count >= 2048) {
            truncated = true;
            return null;
        }
        if (!seen.add(child)) return null;
        count++;
        JsonObject node = new JsonObject();
        node.addProperty("nodeId", "ui:" + screenRevision + ":" + identity.applyAsLong(child));
        node.addProperty("elementIdentity", identity.applyAsLong(child));
        node.addProperty("class", child.getClass().getName());
        node.addProperty("role", role(child));
        node.addProperty("nodeRevision", screenRevision);
        node.addProperty("coverage", "semantic_native");
        node.addProperty("focused", child.isFocused());
        node.addProperty("acquisition", "native_gui_api");
        node.addProperty(
                "capabilitySource",
                child instanceof AbstractWidget
                        ? "native_widget_contract"
                        : "native_listener_contract");
        String label = "";
        net.minecraft.network.chat.Component semanticMessage = null;
        ScreenRectangle rectangle = child.getRectangle();
        boolean active = enabled, visible = true;
        if (child instanceof AbstractWidget widget) {
            semanticMessage = widget.getMessage();
            if (semanticMessage.getString().isBlank()) {
                Tooltip tooltip =
                        ((WidgetTooltipUiAccessor) widget)
                                .minecraftProtocolProbe$tooltipHolder()
                                .get();
                if (tooltip != null) {
                    semanticMessage =
                            ((TooltipUiAccessor) tooltip).minecraftProtocolProbe$message();
                    node.addProperty("labelSource", "native_tooltip");
                }
            }
            label = semanticMessage.getString();
            active = enabled && widget.active;
            visible = widget.visible;
            rectangle =
                    new ScreenRectangle(
                            widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            node.addProperty("hovered", widget.isHovered());
        }
        if (child instanceof RecipeBookComponent<?> recipe) {
            visible = recipe.isVisible();
            if (visible) {
                // Native drawing has a real background, but its inherited navigation rectangle is
                // empty.
                RecipeUiAccessor access = (RecipeUiAccessor) recipe;
                rectangle =
                        new ScreenRectangle(
                                access.minecraftProtocolProbe$xOrigin(),
                                access.minecraftProtocolProbe$yOrigin(),
                                RecipeBookComponent.IMAGE_WIDTH,
                                RecipeBookComponent.IMAGE_HEIGHT);
            }
        }
        if (child instanceof ObjectSelectionList.Entry<?> entry)
            label = entry.getNarration().getString();
        if (child instanceof ServerSelectionList.OnlineServerEntry server)
            label = server.getServerData().name;
        if (child instanceof WorldSelectionList.WorldListEntry world) {
            label = world.getLevelName();
            node.addProperty("worldName", world.getLevelName());
            node.addProperty("worldId", world.getLevelSummary().getLevelId());
            node.addProperty("worldLocked", world.getLevelSummary().isLocked());
        }
        node.addProperty("label", bounded(label));
        if (semanticMessage != null
                && semanticMessage.getContents()
                        instanceof
                        net.minecraft.network.chat.contents.TranslatableContents translated)
            node.addProperty("translationKey", translated.getKey());
        if (child instanceof EditBox edit) {
            node.addProperty("value", bounded(edit.getValue()));
            node.addProperty("editable", active);
            node.addProperty("cursorPosition", edit.getCursorPosition());
        }
        if (child instanceof MultiLineEditBox) node.addProperty("multiline", true);
        if (child instanceof MultiLineEditBox edit)
            node.addProperty("value", bounded(edit.getValue()));
        if (child instanceof AbstractSliderButton slider) {
            node.addProperty(
                    "value", ((SliderValueAccessor) slider).minecraftProtocolProbe$getValue());
            node.addProperty("valueMinimum", 0);
            node.addProperty("valueMaximum", 1);
            node.addProperty("valueSemantics", "normalized_slider_fraction");
        }
        if (child instanceof Checkbox checkbox) node.addProperty("selected", checkbox.selected());
        if (child instanceof TabButton tab) node.addProperty("selected", tab.isSelected());
        if (child instanceof RecipeBookTabButton tab) {
            var category = tab.getCategory();
            String categoryLabel = "";
            if (category
                    instanceof net.minecraft.world.item.crafting.RecipeBookCategory registered) {
                var categoryId = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(registered);
                if (categoryId != null) {
                    categoryLabel = categoryId.toString();
                    node.addProperty("resourceId", categoryLabel);
                }
            } else if (category
                    instanceof
                    net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory
                            searchCategory) {
                categoryLabel = searchCategory.name();
            }
            node.addProperty("label", categoryLabel);
            node.addProperty("role", "tab");
        }
        if (child instanceof RecipeButton button && button.visible) {
            var stack = button.getDisplayStack();
            node.addProperty("label", stack.getHoverName().getString());
            node.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            node.addProperty("count", stack.getCount());
            node.addProperty("role", "recipe");
        }

        JsonArray nested = new JsonArray();
        ScreenRectangle effectiveClip = visible ? clip : ScreenRectangle.empty();
        if (child instanceof AbstractSelectionList<?> list) {
            node.addProperty("entryCount", list.children().size());
            node.addProperty("scrollAmount", list.scrollAmount());
            node.addProperty("maxScrollAmount", list.maxScrollAmount());
            ScreenRectangle listClip = intersect(effectiveClip, rectangle);
            int index = 0;
            for (GuiEventListener entry : list.children()) {
                if (count >= 2048) {
                    truncated = true;
                    break;
                }
                JsonObject row = capture(entry, listClip, depth + 1, active);
                if (row != null) {
                    ScreenRectangle bounds =
                            new ScreenRectangle(
                                    list.getRowLeft(),
                                    list.getRowTop(index),
                                    list.getRowWidth(),
                                    Math.max(1, list.getRowBottom(index) - list.getRowTop(index)));
                    bounds(row, bounds, listClip, active, visible);
                    row.addProperty("role", "list_item");
                    row.addProperty("entryIndex", index);
                    if (entry instanceof KeyEntryUiAccessor binding) {
                        String key =
                                "key_binding." + binding.minecraftProtocolProbe$key().getName();
                        row.addProperty("controlKey", key);
                        row.addProperty(
                                "label",
                                bounded(binding.minecraftProtocolProbe$name().getString()));
                        JsonArray controls = row.getAsJsonArray("children");
                        if (controls != null)
                            for (JsonElement value : controls) {
                                JsonObject control = value.getAsJsonObject();
                                long id = control.get("elementIdentity").getAsLong();
                                if (id
                                        == identity.applyAsLong(
                                                binding.minecraftProtocolProbe$changeButton()))
                                    control.addProperty("controlKey", key + ".change");
                                else if (id
                                        == identity.applyAsLong(
                                                binding.minecraftProtocolProbe$resetButton()))
                                    control.addProperty("controlKey", key + ".reset");
                            }
                    }
                    row.addProperty("selected", list.getSelected() == entry);
                    row.addProperty("scrollParentId", node.get("nodeId").getAsString());
                    row.addProperty("contentY", bounds.top());
                    row.addProperty("contentHeight", bounds.height());
                    tagScrollDescendants(
                            row, node.get("nodeId").getAsString(), bounds.top(), bounds.height());
                    if (row.get("visible").getAsBoolean()) {
                        if (entry instanceof ContainerEventHandler) actions(row, "hover");
                        else
                            actions(
                                    row,
                                    "hover",
                                    "click",
                                    "double_click",
                                    "mouse_down",
                                    "mouse_up");
                    } else actions(row, "scroll_into_view");
                    if (entry
                            instanceof
                            net.minecraft.client.gui.screens.packs.TransferableSelectionList
                                            .PackEntry
                                    pack) {
                        JsonArray packControls =
                                new RenderedUiAdapter(screenRevision, identity)
                                        .packControls(pack, row, bounds, listClip);
                        if (count + packControls.size() <= 2048) {
                            count += packControls.size();
                            row.add("children", packControls);
                        } else truncated = true;
                        tagScrollDescendants(
                                row,
                                node.get("nodeId").getAsString(),
                                bounds.top(),
                                bounds.height());
                    }
                    if (list instanceof ServerSelectionList
                            && entry instanceof ServerSelectionList.OnlineServerEntry server) {
                        int savedCount =
                                (int)
                                        list.children().stream()
                                                .filter(
                                                        ServerSelectionList.OnlineServerEntry.class
                                                                ::isInstance)
                                                .count();
                        JsonArray controls =
                                new RenderedUiAdapter(screenRevision, identity)
                                        .serverControls(
                                                server,
                                                server.getServerData().name,
                                                row,
                                                new ScreenRectangle(
                                                        server.getContentX(),
                                                        server.getContentY(),
                                                        32,
                                                        32),
                                                listClip,
                                                index,
                                                savedCount,
                                                (Minecraft.getInstance().options.touchscreen().get()
                                                        || server.isMouseOver(
                                                                Minecraft.getInstance()
                                                                        .mouseHandler
                                                                        .getScaledXPos(
                                                                                Minecraft
                                                                                        .getInstance()
                                                                                        .getWindow()),
                                                                Minecraft.getInstance()
                                                                        .mouseHandler
                                                                        .getScaledYPos(
                                                                                Minecraft
                                                                                        .getInstance()
                                                                                        .getWindow()))));
                        if (count + controls.size() <= 2048) {
                            count += controls.size();
                            row.add("children", controls);
                        } else truncated = true;
                    } else if (entry instanceof ServerSelectionList.LANHeader) {
                        row.addProperty("role", "text");
                        actions(row);
                    }
                    nested.add(row);
                }
                index++;
            }
        } else if (child instanceof ContainerEventHandler container) {
            ScreenRectangle childClip =
                    child instanceof AbstractScrollArea
                            ? intersect(effectiveClip, rectangle)
                            : effectiveClip;
            for (GuiEventListener next : container.children()) {
                JsonObject descendant = capture(next, childClip, depth + 1, active);
                if (descendant != null) nested.add(descendant);
            }
        }
        if (child instanceof RecipeBookComponent<?> recipe && recipe.isVisible()) {
            RecipeUiAccessor access = (RecipeUiAccessor) recipe;
            List<GuiEventListener> controls = new ArrayList<>(access.minecraftProtocolProbe$tabs());
            if (access.minecraftProtocolProbe$search() != null)
                controls.add(access.minecraftProtocolProbe$search());
            if (access.minecraftProtocolProbe$filter() != null)
                controls.add(access.minecraftProtocolProbe$filter());
            ((RecipePageAccessor) access.minecraftProtocolProbe$page())
                    .minecraftProtocolProbe$buttons(controls::add);
            var page = (RecipePageAccessor) access.minecraftProtocolProbe$page();
            if (page.minecraftProtocolProbe$forward() != null)
                controls.add(page.minecraftProtocolProbe$forward());
            if (page.minecraftProtocolProbe$back() != null)
                controls.add(page.minecraftProtocolProbe$back());
            var overlay = page.minecraftProtocolProbe$overlay();
            if (overlay != null && overlay.isVisible())
                rootRecipeOverlay(overlay, recipeOverlays, effectiveClip, depth, active);
            for (GuiEventListener control : controls) {
                JsonObject descendant = capture(control, effectiveClip, depth + 1, active);
                if (descendant != null) {
                    if (control instanceof RecipeBookTabButton tab)
                        descendant.addProperty(
                                "selected", access.minecraftProtocolProbe$selectedTab() == tab);
                    nested.add(descendant);
                }
            }
        }
        if (!nested.isEmpty()) node.add("children", nested);
        bounds(node, rectangle, clip, active, visible);
        if (node.get("visible").getAsBoolean() && active) {
            String role = node.get("role").getAsString();
            if (role.equals("list") || role.equals("container")) actions(node, "hover", "scroll");
            else if (role.equals("text")) actions(node, "hover");
            else {
                actions(node, "hover", "click", "double_click", "mouse_down", "mouse_up");
                if (role.equals("text_field")) node.getAsJsonArray("actions").add("type_text");
                if (role.equals("slider")) node.getAsJsonArray("actions").add("set_value");
                if (child instanceof AbstractScrollArea)
                    node.getAsJsonArray("actions").add("scroll");
            }
        } else actions(node);
        if (screen != null
                && child instanceof AbstractStringWidget text
                && node.get("visible").getAsBoolean()
                && active) interactionRegions.textWidget(text, clip);
        if (screen != null)
            new RenderedUiAdapter(screenRevision, identity).annotate(screen, child, node);
        return node;
    }

    private void rootRecipeOverlay(
            net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent overlay,
            JsonArray nested,
            ScreenRectangle clip,
            int depth,
            boolean active) {
        if (count >= 2048) {
            truncated = true;
            return;
        }
        count++;
        JsonObject modal = new JsonObject();
        modal.addProperty(
                "nodeId",
                "ui:" + screenRevision + ":recipe.overlay:" + identity.applyAsLong(overlay));
        modal.addProperty("controlKey", "recipe.overlay");
        modal.addProperty("role", "popup");
        modal.addProperty("interactionLayer", 50);
        modal.addProperty("modal", true);
        modal.addProperty("visible", true);
        modal.addProperty("active", true);
        modal.addProperty("label", "Recipe choices");
        actions(modal);
        bounds(modal, new ScreenRectangle(0, 0, screen.width, screen.height), clip, true, true);
        JsonArray buttons = new JsonArray();
        int index = 0;
        for (var button : ((RecipeOverlayUiAccessor) overlay).minecraftProtocolProbe$buttons()) {
            JsonObject choice = capture(button, clip, depth + 1, active);
            if (choice != null) {
                choice.addProperty("role", "recipe_option");
                choice.addProperty("controlKey", "recipe.overlay.option." + index);
                choice.addProperty("label", "Recipe option " + (index + 1));
                choice.addProperty("interactionLayer", 51);
                buttons.add(choice);
            }
            index++;
        }
        modal.add("children", buttons);
        nested.add(modal);
    }

    static void markOccluded(JsonArray children) {
        List<JsonObject> flat = new ArrayList<>();
        flatten(children, flat);
        for (JsonObject blocker : flat) {
            if (!blocker.has("controlKey")
                    || (!blocker.get("controlKey").getAsString().equals("chat.suggestions")
                            && !blocker.get("controlKey").getAsString().equals("recipe.overlay")))
                continue;
            for (JsonObject node : flat) {
                if (node == blocker || !node.has("interactionX") || !node.has("interactionY"))
                    continue;
                if (node.has("interactionLayer") && node.get("interactionLayer").getAsInt() >= 50)
                    continue;
                double x = node.get("interactionX").getAsDouble(),
                        y = node.get("interactionY").getAsDouble();
                if (x >= blocker.get("x").getAsDouble()
                        && x < blocker.get("x").getAsDouble() + blocker.get("width").getAsDouble()
                        && y >= blocker.get("y").getAsDouble()
                        && y
                                < blocker.get("y").getAsDouble()
                                        + blocker.get("height").getAsDouble()) {
                    node.addProperty("occluded", true);
                    node.add("occludedBy", blocker.get("nodeId").deepCopy());
                }
            }
        }
    }

    private static void flatten(JsonArray children, List<JsonObject> flat) {
        for (JsonElement e : children) {
            JsonObject node = e.getAsJsonObject();
            flat.add(node);
            if (node.has("children")) flatten(node.getAsJsonArray("children"), flat);
        }
    }

    private static void tagScrollDescendants(
            JsonObject node, String owner, int contentY, int contentHeight) {
        node.addProperty("scrollParentId", owner);
        node.addProperty("contentY", contentY);
        node.addProperty("contentHeight", contentHeight);
        if (!node.get("visible").getAsBoolean() && node.get("active").getAsBoolean())
            actions(node, "scroll_into_view");
        if (node.has("children"))
            for (JsonElement value : node.getAsJsonArray("children"))
                tagScrollDescendants(value.getAsJsonObject(), owner, contentY, contentHeight);
    }

    static ScreenRectangle intersect(ScreenRectangle a, ScreenRectangle b) {
        if (a == null || b == null) return ScreenRectangle.empty();
        ScreenRectangle intersection = a.intersection(b);
        return intersection == null ? ScreenRectangle.empty() : intersection;
    }

    static void bounds(
            JsonObject node,
            ScreenRectangle rectangle,
            ScreenRectangle clip,
            boolean active,
            boolean shown) {
        ScreenRectangle hit = intersect(rectangle, clip);
        boolean visible = shown && hit.width() > 0 && hit.height() > 0;
        node.addProperty("active", active);
        node.addProperty("visible", visible);
        if (rectangle.width() <= 0 || rectangle.height() <= 0) {
            node.addProperty("boundsAvailable", false);
            return;
        }
        node.addProperty("boundsAvailable", true);
        node.addProperty("x", visible ? hit.left() : rectangle.left());
        node.addProperty("y", visible ? hit.top() : rectangle.top());
        node.addProperty("width", visible ? hit.width() : rectangle.width());
        node.addProperty("height", visible ? hit.height() : rectangle.height());
        node.addProperty(
                "interactionX",
                (visible ? hit.left() : rectangle.left())
                        + (visible ? hit.width() : rectangle.width()) / 2.0);
        node.addProperty(
                "interactionY",
                (visible ? hit.top() : rectangle.top())
                        + (visible ? hit.height() : rectangle.height()) / 2.0);
        node.addProperty("clipped", visible && !hit.equals(rectangle));
    }

    private static String role(GuiEventListener child) {
        if (child instanceof AbstractSelectionList<?>) return "list";
        if (child instanceof RecipeBookComponent<?>) return "container";
        if (child instanceof EditBox || child instanceof MultiLineEditBox) return "text_field";
        if (child instanceof AbstractSliderButton) return "slider";
        if (child instanceof Checkbox) return "checkbox";
        if (child instanceof TabButton) return "tab";
        if (child instanceof ContainerEventHandler) return "container";
        if (child instanceof StringWidget || child instanceof MultiLineTextWidget) return "text";
        if (child instanceof AbstractButton) return "button";
        return child instanceof AbstractWidget ? "widget" : "listener";
    }

    static String bounded(String text) {
        return text.length() > 2048 ? text.substring(0, 2048) : text;
    }

    private static void actions(JsonObject node, String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        node.add("actions", array);
    }

    private int nodeCount(JsonObject node) {
        if (node.has("collectionTruncated") && node.get("collectionTruncated").getAsBoolean())
            truncated = true;
        int count = 1;
        if (node.has("children"))
            for (JsonElement child : node.getAsJsonArray("children"))
                count += nodeCount(child.getAsJsonObject());
        return count;
    }
}
