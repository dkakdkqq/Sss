package fun.lumis.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fun.lumis.api.QClient;
import fun.lumis.api.events.EventInvoker;
import fun.lumis.api.events.implement.EventRender;
import fun.lumis.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.lumis.api.utils.SidebarEntry;
import fun.lumis.api.utils.render.blur.BlurProgram;
import fun.lumis.client.modules.impl.misc.NameProtect;

import java.util.Comparator;
import java.util.List;

@Mixin(InGameHud.class)
public class InGameGuiMixin implements QClient {

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void renderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        if (ModuleClass.noVignette.isEnable()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        BlurProgram.getInstance().beginFrame();
        if (EventInvoker.hasListeners(EventRender.Default.class)) {
            new EventRender.Default(context, tickCounter.getTickDelta(true)).call();
        }
    }


    private static final int DOMAIN_COLOR = 0xED6521;

    @Shadow
    @Final
    private MinecraftClient client;


    @Shadow
    private PlayerEntity getCameraPlayer() {
        return null;
    }


    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lumis$renderPatchedScoreboard(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        if (!lumis$shouldPatchScoreboard()) {
            return;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

        List<SidebarEntry> lines = scoreboard.getScoreboardEntries(objective).stream()
                .filter(entry -> !entry.hidden())
                .sorted(Comparator.comparing(ScoreboardEntry::value).reversed()
                        .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15L)
                .map(entry -> {
                    Team team = scoreboard.getScoreHolderTeam(entry.owner());
                    Text name = lumis$patchText(Team.decorateName(team, entry.name()));
                    Text score = entry.formatted(numberFormat);
                    int scoreWidth = this.client.textRenderer.getWidth(score);
                    return new SidebarEntry(name, score, scoreWidth);
                })
                .toList();

        Text title = lumis$patchText(objective.getDisplayName());
        int titleWidth = this.client.textRenderer.getWidth(title);
        int maxWidth = titleWidth;
        int separatorWidth = this.client.textRenderer.getWidth(": ");

        for (SidebarEntry line : lines) {
            maxWidth = Math.max(maxWidth, this.client.textRenderer.getWidth(line.name) + (line.scoreWidth > 0 ? separatorWidth + line.scoreWidth : 0));
        }

        int lineCount = lines.size();
        int totalHeight = lineCount * 9;
        int bottom = drawContext.getScaledWindowHeight() / 2 + totalHeight / 3;
        int left = drawContext.getScaledWindowWidth() - maxWidth - 3;
        int right = drawContext.getScaledWindowWidth() - 1;
        int bodyColor = this.client.options.getTextBackgroundColor(0.3F);
        int headerColor = this.client.options.getTextBackgroundColor(0.4F);
        int top = bottom - lineCount * 9;

        drawContext.fill(left - 2, top - 10, right, top - 1, headerColor);
        drawContext.fill(left - 2, top - 1, right, bottom, bodyColor);
        drawContext.drawText(this.client.textRenderer, title, left + maxWidth / 2 - titleWidth / 2, top - 9, -1, false);

        for (int index = 0; index < lineCount; ++index) {
            SidebarEntry line = lines.get(index);
            int y = bottom - (lineCount - index) * 9;
            drawContext.drawText(this.client.textRenderer, line.name, left, y, -1, false);
            drawContext.drawText(this.client.textRenderer, line.score, right - line.scoreWidth, y, -1, false);
        }

        ci.cancel();
    }

    private boolean lumis$shouldPatchScoreboard() {
        return ModuleClass.INSTANCE != null
                && ModuleClass.INSTANCE.nameProtect != null
                && ModuleClass.INSTANCE.nameProtect.isEnable();
    }

    private Text lumis$patchText(Text text) {
        NameProtect nameProtect = ModuleClass.INSTANCE.nameProtect;
        Text patched = nameProtect.patchText(text);
        String patchedString = patched.getString();

        if (nameProtect.shouldHideGrief()) {
            if (patchedString.contains("Анархия-")) {
                patchedString = patchedString.replaceAll("Анархия-\\d+", "lumisclient.fun");
            }
            if (patchedString.contains("ГРИФ #")) {
                patchedString = patchedString.replaceAll("ГРИФ #\\d+", "lumisclient.fun");
            }
        }

        if (patchedString.equals(patched.getString())) {
            return patched;
        }
        return Text.literal(patchedString).setStyle(patched.getStyle().withColor(TextColor.fromRgb(DOMAIN_COLOR)));
    }
}
