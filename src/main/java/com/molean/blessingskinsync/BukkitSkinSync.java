package com.molean.blessingskinsync;

import com.google.common.collect.Iterables;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.skinsrestorer.bukkit.SkinsRestorer;
import net.skinsrestorer.shared.storage.SkinStorage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.mineskin.MineskinClient;
import org.mineskin.Model;
import org.mineskin.SkinOptions;
import org.mineskin.Visibility;
import org.mineskin.data.Skin;
import org.mineskin.data.SkinCallback;
import org.mineskin.data.Texture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

public final class BukkitSkinSync extends JavaPlugin implements Listener {

    private MineskinClient skinClient;
    private SkinsRestorer skinsRestorer;
    private Configuration config;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        skinClient = new MineskinClient();
        skinsRestorer = (SkinsRestorer) Bukkit.getPluginManager().getPlugin("SkinsRestorer");

        if (!getDataFolder().exists()) {
            boolean mkdir = getDataFolder().mkdir();
        }
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResource("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.reloadConfig();
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            this.reloadConfig();
            getLogger().info("正在为" + event.getPlayer().getName() + "加载皮肤...");
            String texturesUrl = config.getString("textureUrl");
            SimpleSkin skin = getSkin(event.getPlayer().getName());
            if (skin == null || texturesUrl == null) {
                getLogger().warning("为" + event.getPlayer().getName() + "读取皮肤失败!");
                getLogger().warning("请检查网络是否畅通且角色是否存在!");
                return;
            }
            String urlString = texturesUrl.replace("%texture%", skin.getTexture());
            setSkin(event.getPlayer(), urlString, skin.getModel().equals("slim") ? Model.SLIM : Model.DEFAULT);
        });
    }

    public void setSkin(Player player, String urlString, Model model) {
        try {
            URL url = new URL(urlString);
            skinClient.generateUrl(url.toString(), SkinOptions.create(player.getName(), model, Visibility.PRIVATE), new SkinCallback() {
                @Override
                public void error(String errorMessage) {
                    getLogger().warning(errorMessage);
                }

                @Override
                public void exception(Exception exception) {
                    exception.printStackTrace();
                }

                @Override
                public void done(Skin skin) {
                    SkinStorage skinStorage = skinsRestorer.getSkinStorage();
                    Texture texture = skin.data.texture;
                    Object property = skinStorage.createProperty(player.getName().toLowerCase(), texture.value, texture.signature);
                    skinStorage.setSkinData(player.getName().toLowerCase(), property);
                    skinsRestorer.getFactory().applySkin(player, skinStorage.getSkinData(player.getName().toLowerCase()));
                    getLogger().info("为"+player.getName()+"加载皮肤成功!");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SimpleSkin getSkin(String username) {
        try {
            String profileUrl = config.getString("profileUrl").replace("%playername%", username);
            URL url = new URL(profileUrl);
            InputStream inputStream = url.openStream();
            byte[] bytes = readInputStream(inputStream);
            int read = inputStream.read(bytes);
            JsonParser jsonParser = new JsonParser();
            JsonElement parse = jsonParser.parse(new String(bytes));
            JsonObject jsonObject = parse.getAsJsonObject();
            JsonObject skins = jsonObject.getAsJsonObject("skins");
            Set<Map.Entry<String, JsonElement>> entries = skins.entrySet();
            Map.Entry<String, JsonElement> first = Iterables.getFirst(entries, null);
            if (first == null)
                return null;
            JsonElement value = first.getValue();
            String key = first.getKey();
            return new SimpleSkin(value.getAsString(), key);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }
}
