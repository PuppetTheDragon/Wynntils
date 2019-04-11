/*
 *  * Copyright © Wynntils - 2019.
 */

package com.wynntils.modules.core.overlays;

import com.wynntils.ModCore;
import com.wynntils.Reference;
import com.wynntils.core.framework.overlays.Overlay;
import com.wynntils.core.framework.rendering.SmartFontRenderer;
import com.wynntils.core.framework.rendering.colors.CommonColors;
import com.wynntils.core.framework.rendering.colors.CustomColor;
import com.wynntils.core.utils.Utils;
import com.wynntils.modules.core.CoreModule;
import com.wynntils.modules.core.config.CoreDBConfig;
import com.wynntils.modules.core.enums.UpdateStream;
import com.wynntils.webapi.WebManager;
import com.wynntils.webapi.downloader.DownloaderManager;
import com.wynntils.webapi.downloader.enums.DownloadAction;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;

public class UpdateOverlay extends Overlay {

    private static CustomColor background = CustomColor.fromString("333341",1);
    private static CustomColor box = CustomColor.fromString("434355",1);
    private static CustomColor yes = CustomColor.fromString("80fd80",1);
    private static CustomColor no = CustomColor.fromString("fd8080",1);

    public UpdateOverlay() {
        super("Update", I18n.format("wynntils.core.ui.update.display_name"), 20, 20, true, 1f, 0f, 0, 0, null);
    }

    static boolean disappear = false;
    static boolean acceptYesOrNo = false;
    static boolean download = false;

    public static int size = 63;
    public static long timeout = 0;

    @Override
    public void render(RenderGameOverlayEvent.Post e) {
        if(e.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        if(Reference.developmentEnvironment || WebManager.getUpdate() == null || !WebManager.getUpdate().hasUpdate()) {
            return;
        }

        if(disappear) {
            return;
        }

        if(timeout == 0) {
            timeout = System.currentTimeMillis();
        }

        drawRect(background, -172,0 - size, 0, 62 - size);
        drawRect(box, -170,0 - size, 0, 60 - size);

        drawString(I18n.format("wynntils.core.ui.update.upper_text", Reference.VERSION, ((timeout + 35000) - System.currentTimeMillis()) / 1000), -165, 5 - size, CommonColors.ORANGE);
        if (WebManager.getUpdate().getLatestUpdate().startsWith("B")) {
            drawString(I18n.format("wynntils.core.ui.update.build_is_avaliable", WebManager.getUpdate().getLatestUpdate().replace("B", "")), -165, 15 - size, CommonColors.WHITE);
        } else {
            drawString(I18n.format("wynntils.core.ui.update.version_is_avaliable", WebManager.getUpdate().getLatestUpdate()), -165, 15 - size, CommonColors.WHITE);
        }

        drawString(I18n.format("wynntils.core.ui.update.download_question"), -165, 25 - size, CommonColors.LIGHT_GRAY);

        drawRect(yes, -155,40 - size, -95, 55 - size);
        drawRect(no, -75 ,40 - size, -15, 55 - size);

        drawString(I18n.format("wynntils.core.ui.update.yes"), -125, 44 - size, CommonColors.WHITE, SmartFontRenderer.TextAlignment.MIDDLE, SmartFontRenderer.TextShadow.OUTLINE);
        drawString(I18n.format("wynntils.core.ui.update.no"), -43, 44 - size, CommonColors.WHITE, SmartFontRenderer.TextAlignment.MIDDLE, SmartFontRenderer.TextShadow.OUTLINE);

        if (size > 0 && System.currentTimeMillis() - timeout < 35000) {
            size--;
            if(size <= 0) {
                acceptYesOrNo = true;
            }
        }else if(size < 63 && System.currentTimeMillis() - timeout >= 35000) {
            size++;
            if(size >= 63) {
                disappear = true;
                acceptYesOrNo = false;
                download = false;
            }
        }
    }

    public static void reset() {
        disappear = false;
        acceptYesOrNo = false;
        download = false;
        size = 63;
        timeout = 0;
    }

    public static void forceDownload() {
        disappear = true;
        acceptYesOrNo = false;
        download = true;
    }

    @Override
    public void tick(TickEvent.ClientTickEvent event, long ticks){
        if(download && disappear) {
            download = false;

            try {
                File f = new File(Reference.MOD_STORAGE_ROOT + "/updates");

                String url;
                if (CoreDBConfig.INSTANCE.updateStream == UpdateStream.CUTTING_EDGE) {
                    url = WebManager.getCuttingEdgeJarFileUrl();
                } else {
                    url = WebManager.getStableJarFileUrl();
                }
                String[] sUrl = url.split("/");
                String jar_name = sUrl[sUrl.length - 1];

                DownloadOverlay.size = 0;
                DownloaderManager.restartGameOnNextQueue();
                DownloaderManager.queueDownload(I18n.format("wynntils.core.ui.update.updating_to", WebManager.getUpdate().getLatestUpdate()), url, f, DownloadAction.SAVE, (x) -> {
                    if(x) {
                        try {
                            TextComponentTranslation message;
                            if (CoreDBConfig.INSTANCE.updateStream == UpdateStream.STABLE) {
                                message = new TextComponentTranslation("wynntils.core.ui.update.update_downloaded_version", jar_name.split("_")[0].split("-")[1]);
                            } else {
                                message = new TextComponentTranslation("wynntils.core.ui.update.update_downloaded_build", jar_name.split("_")[1].replace(".jar", ""));
                            }
                            ModCore.mc().player.sendMessage(message);
                            copyUpdate(jar_name);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if(acceptYesOrNo) {
            if(Keyboard.isKeyDown(Keyboard.KEY_Y)) {
                disappear = true;
                acceptYesOrNo = false;
                download = true;

                CoreDBConfig.INSTANCE.showChangelogs = true;
                CoreDBConfig.INSTANCE.lastVersion = Reference.VERSION;
                CoreDBConfig.INSTANCE.saveSettings(CoreModule.getModule());
            }else if(Keyboard.isKeyDown(Keyboard.KEY_N)) {
                timeout = 35000;
                acceptYesOrNo = false;
                download = false;
            }
        }
    }

    public void copyUpdate(String jarName) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Reference.LOGGER.info("Attempting to apply Wynntils update.");
                File oldJar = ModCore.jarFile;

                if (oldJar == null || !oldJar.exists() || oldJar.isDirectory()) {
                    Reference.LOGGER.warn("Old jar file not found.");
                    return;
                }

                File newJar = new File(Reference.MOD_STORAGE_ROOT + "/updates", jarName);
                Utils.copyFile(newJar, oldJar);
                newJar.delete();
                Reference.LOGGER.info("Successfully applied Wynntils update.");
            } catch (IOException ex) {
                Reference.LOGGER.error("Unable to apply Wynntils update.", ex);
            }
        }));
    }

    public static boolean isDownloading() {
        return download;
    }

}
