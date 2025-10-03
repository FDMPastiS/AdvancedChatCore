/*
 * Copyright (C) 2021-2022 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchatcore.chat;

import io.github.darkkronicle.advancedchatcore.AdvancedChatCore;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.config.gui.GuiConfigHandler;
import io.github.darkkronicle.advancedchatcore.gui.IconButton;
import io.github.darkkronicle.advancedchatcore.interfaces.AdvancedChatScreenSection;
import io.github.darkkronicle.advancedchatcore.util.Color;
import io.github.darkkronicle.advancedchatcore.util.RowList;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Migrated from GuiBase to Screen for modern Fabric/Minecraft versions.
 * This keeps the original logic but uses Screen APIs.
 */
public class AdvancedChatScreen extends Screen {

    public static boolean PERMANENT_FOCUS = false;

    private String finalHistory = "";
    private int messageHistorySize = -1;
    private int startHistory = -1;
    private boolean passEvents = false;

    /** Chat field at the bottom of the screen */
    @Getter protected AdvancedTextField chatField;

    /** What the chat box started out with */
    @Getter private String originalChatText = "";

    private static String last = "";
    private final List<AdvancedChatScreenSection> sections = new ArrayList<>();

    @Getter
    private final RowList<ButtonWidget> rightSideButtons = new RowList<>();

    @Getter
    private final RowList<ButtonWidget> leftSideButtons = new RowList<>();

    public AdvancedChatScreen() {
        // Title required by Screen constructor
        super(Text.literal("Advanced Chat"));
        setupSections();
    }

    public AdvancedChatScreen(boolean passEvents) {
        this();
        this.passEvents = passEvents;
    }

    public AdvancedChatScreen(int indexOfLast) {
        this();
        startHistory = indexOfLast;
    }

    public AdvancedChatScreen(String originalChatText) {
        this();
        this.originalChatText = originalChatText;
    }

    private void setupSections() {
        for (Function<AdvancedChatScreen, AdvancedChatScreenSection> supplier : ChatScreenSectionHolder.getInstance().getSectionSuppliers()) {
            AdvancedChatScreenSection section = supplier.apply(this);
            if (section != null) {
                sections.add(section);
            }
        }
    }

    private Color getColor() {
        return ConfigStorage.ChatScreen.COLOR.config.get();
    }

    public void resetCurrentMessage() {
        this.messageHistorySize = this.client.inGameHud.getChatHud().getMessageHistory().size();
    }

    @Override
    public boolean charTyped(char charIn, int modifiers) {
        if (passEvents) {
            return true;
        }
        return super.charTyped(charIn, modifiers);
    }

    /**
     * init is called by Minecraft when the screen is (re)created.
     */
    @Override
    public void init(MinecraftClient client, int width, int height) {
        super.init(client, width, height);

        this.rightSideButtons.clear();
        this.leftSideButtons.clear();
        resetCurrentMessage();

        // Create chat field. AdvancedTextField should be compatible with TextFieldWidget.
        this.chatField =
                new AdvancedTextField(
                        this.textRenderer,
                        4,
                        this.height - 12,
                        this.width - 10,
                        12,
                        Text.translatable("chat.editBox")) {
                    protected MutableText getNarrationMessage() {
                        return null;
                    }
                };

        if (ConfigStorage.ChatScreen.MORE_TEXT.config.getBooleanValue()) {
            this.chatField.setMaxLength(64000);
        } else {
            this.chatField.setMaxLength(256);
        }
        this.chatField.setDrawsBackground(false);

        if (!this.originalChatText.equals("")) {
            this.chatField.setText(this.originalChatText);
        } else if (ConfigStorage.ChatScreen.PERSISTENT_TEXT.config.getBooleanValue()
                && !last.equals("")) {
            this.chatField.setText(last);
        }
        this.chatField.setChangedListener(this::onChatFieldUpdate);

        // Add settings button (IconButton should extend ButtonWidget)
        // When pressed, open the config screen via MinecraftClient#setScreen(...)
        IconButton settingsBtn = new IconButton(0, 0, 14, 64, new Identifier(AdvancedChatCore.MOD_ID, "textures/gui/settings.png"), (button) -> {
            MinecraftClient.getInstance().setScreen(GuiConfigHandler.getInstance().getDefaultScreen());
        });
        rightSideButtons.add("settings", settingsBtn);

        // Add chat field as selectable child so it receives focus and keyboard events
        this.addSelectableChild(this.chatField);
        this.setFocused(this.chatField);
        this.chatField.setFocused(true);

        for (AdvancedChatScreenSection section : sections) {
            section.initGui();
        }

        // Layout right side buttons
        int originalX = client.getWindow().getScaledWidth() - 1;
        int y = client.getWindow().getScaledHeight() - 30;
        for (int i = 0; i < rightSideButtons.rowSize(); i++) {
            List<ButtonWidget> buttonList = rightSideButtons.get(i);
            int maxHeight = 0;
            int x = originalX;
            for (ButtonWidget button : buttonList) {
                maxHeight = Math.max(maxHeight, button.getHeight());
                x -= button.getWidth() + 1;
                button.setPosition(x, y);
                this.addDrawableChild(button);
            }
            y -= maxHeight + 1;
        }

        // Layout left side buttons
        originalX = 1;
        y = client.getWindow().getScaledHeight() - 30;
        for (int i = 0; i < leftSideButtons.rowSize(); i++) {
            List<ButtonWidget> buttonList = leftSideButtons.get(i);
            int maxHeight = 0;
            int x = originalX;
            for (ButtonWidget button : buttonList) {
                maxHeight = Math.max(maxHeight, button.getHeight());
                button.setPosition(x, y);
                this.addDrawableChild(button);
                x += button.getWidth() + 1;
            }
            y -= maxHeight + 1;
        }

        if (startHistory >= 0) {
            setChatFromHistory(-startHistory - 1);
        }
    }

    @Override
    public void removed() {
        for (AdvancedChatScreenSection section : sections) {
            section.removed();
        }
    }

    @Override
    public void tick() {
        if (this.chatField != null) {
            this.chatField.tick();
        }
    }

    private void onChatFieldUpdate(String chatText) {
        String string = this.chatField.getText();
        for (AdvancedChatScreenSection section : sections) {
            section.onChatFieldUpdate(chatText, string);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (passEvents) {
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
            KeyBinding.setKeyPressed(key, false);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!passEvents) {
            for (AdvancedChatScreenSection section : sections) {
                if (section.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            if (super.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Map legacy KeyCodes to GLFW codes
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Exit out
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String string = this.chatField.getText().trim();
            // Strip message and send
            MessageSender.getInstance().sendMessage(string);
            this.chatField.setText("");
            last = "";
            // Exit
            MinecraftClient.getInstance().setScreen(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            // Go through previous history
            this.setChatFromHistory(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            // Go through previous history
            this.setChatFromHistory(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            // Scroll
            client.inGameHud
                    .getChatHud()
                    .scroll(this.client.inGameHud.getChatHud().getVisibleLineCount() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            // Scroll
            client.inGameHud
                    .getChatHud()
                    .scroll(-this.client.inGameHud.getChatHud().getVisibleLineCount() + 1);
            return true;
        }
        if (passEvents) {
            this.chatField.setText("");
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
            KeyBinding.setKeyPressed(key, true);
            KeyBinding.onKeyPressed(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 1.0D) {
            amount = 1.0D;
        }
        if (amount < -1.0D) {
            amount = -1.0D;
        }

        for (AdvancedChatScreenSection section : sections) {
            if (section.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        if (!hasShiftDown()) {
            amount *= 7.0D;
        }

        // Send to hud to scroll
        client.inGameHud.getChatHud().scroll((int) amount);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (AdvancedChatScreenSection section : sections) {
            if (section.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        ChatHud hud = client.inGameHud.getChatHud();
        if (hud.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        Style style = hud.getTextStyleAt(mouseX, mouseY);
        if (style != null && style.getClickEvent() != null) {
            if (this.handleTextClick(style)) {
                return true;
            }
        }
        return (this.chatField.mouseClicked(mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        for (AdvancedChatScreenSection section : sections) {
            if (section.mouseReleased(mouseX, mouseY, mouseButton)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (AdvancedChatScreenSection section : sections) {
            if (section.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void insertText(String text, boolean override) {
        if (override) {
            this.chatField.setText(text);
        } else {
            this.chatField.write(text);
        }
    }

    public void setChatFromHistory(int i) {
        int targetIndex = this.messageHistorySize + i;
        int maxIndex = this.client.inGameHud.getChatHud().getMessageHistory().size();
        targetIndex = MathHelper.clamp(targetIndex, 0, maxIndex);
        if (targetIndex != this.messageHistorySize) {
            if (targetIndex == maxIndex) {
                this.messageHistorySize = maxIndex;
                this.chatField.setText(this.finalHistory);
            } else {
                if (this.messageHistorySize == maxIndex) {
                    this.finalHistory = this.chatField.getText();
                }

                String hist = this.client.inGameHud.getChatHud().getMessageHistory().get(targetIndex);
                this.chatField.setText(hist);
                for (AdvancedChatScreenSection section : sections) {
                    section.setChatFromHistory(hist);
                }
                this.messageHistorySize = targetIndex;
            }
        }
    }

    // Keep original MatrixStack render signature for compatibility with other code that may call it.
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        // Delegate to DrawContext-based render if available; but keep MatrixStack behavior for compatibility
        // Render chat field and sections similarly to original implementation
        ChatHud hud = client.inGameHud.getChatHud();
        this.setFocused(this.chatField);
        this.chatField.setFocused(true);

        if (this.chatField != null) {
            this.chatField.render(matrices, mouseX, mouseY, partialTicks);
        }

        super.render(matrices, mouseX, mouseY, partialTicks);

        for (AdvancedChatScreenSection section : sections) {
            section.render(matrices, mouseX, mouseY, partialTicks);
        }

        Style style = hud.getTextStyleAt(mouseX, mouseY);
        if (style != null && style.getHoverEvent() != null) {
            // Try to render hover (best-effort)
            this.renderTextHoverEffect(matrices, style, mouseX, mouseY);
        }
    }

    // fallback hover renderer using MatrixStack (best-effort)
    private void renderTextHoverEffect(MatrixStack matrices, Style style, int mouseX, int mouseY) {
        if (style == null || style.getHoverEvent() == null) return;
        // If hover event has text, show tooltip
        if (style.getHoverEvent().getValue() instanceof net.minecraft.text.Text) {
            this.renderTooltip(matrices, (Text) style.getHoverEvent().getValue(), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        if (ConfigStorage.ChatScreen.PERSISTENT_TEXT.config.getBooleanValue()) {
            last = (this.chatField != null) ? this.chatField.getText() : "";
        }
        super.onClose();
    }

    private void setText(String text) {
        if (this.chatField != null) {
            this.chatField.setText(text);
        }
    }
}
