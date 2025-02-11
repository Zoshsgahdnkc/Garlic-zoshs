package org.teacon.nocaet.bungee;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.md_5.bungee.api.config.ServerInfo;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerData {

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(Claim.class, new Claim.Serializer()).disableHtmlEscaping().setPrettyPrinting().create();

    private final Map<String, Map<UUID, Set<Claim>>> map = new ConcurrentHashMap<>();

    public void add(ServerInfo info, UUID uuid, String name) {
        var group = ServerGroup.instance().getGroup(info);
        if (group != null) {
            var map = this.map.computeIfAbsent(group, k -> new ConcurrentHashMap<>());
            map.computeIfAbsent(uuid, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(new Claim(name, Instant.now()));
        }
    }

    public List<String> getAll(ServerInfo info, UUID uuid) {
        var group = ServerGroup.instance().getGroup(info);
        if (group != null) {
            var map = this.map.computeIfAbsent(group, k -> new ConcurrentHashMap<>());
            var set = map.get(uuid);
            return set == null ? null : set.stream().map(Claim::name).toList();
        } else return null;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void load(Path path) throws IOException {
        this.map.clear();
        for (var group : ServerGroup.instance().getGroups()) {
            try (var reader = Files.newBufferedReader(path.resolveSibling("data_" + group + ".yml"))) {
                var map = this.map.computeIfAbsent(group, k -> new ConcurrentHashMap<>());
                var obj = JsonParser.parseReader(reader).getAsJsonObject();
                for (var s : obj.keySet()) {
                    var set = Collections.<Claim>newSetFromMap(new ConcurrentHashMap<>());
                    map.put(UUID.fromString(s), set);
                    set.addAll(GSON.fromJson(obj.get(s), new TypeToken<List<Claim>>() {}.getType()));
                }
            }
        }
    }

    public void save(Path path) throws IOException {
        for (var group : this.map.keySet()) {
            var map = this.map.get(group);
            var s = GSON.toJson(map.entrySet().stream().collect(Collectors.toMap(it -> it.getKey().toString(), Map.Entry::getValue)));
            var oldData = path.resolveSibling("data_" + group + "_old.yml");
            var newData = path.resolveSibling("data_" + group + ".yml");
            Files.move(newData, oldData, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(newData, s, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    private record Claim(String name, Instant instant) {

        static class Serializer implements JsonSerializer<Claim>, JsonDeserializer<Claim> {

            @Override
            public JsonElement serialize(Claim src, Type typeOfSrc, JsonSerializationContext context) {
                var object = new JsonObject();
                object.addProperty("name", src.name);
                object.addProperty("time", src.instant.toString());
                return object;
            }

            @Override
            public Claim deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                var object = json.getAsJsonObject();
                var name = object.get("name").getAsString();
                var instant = Instant.parse(object.get("time").getAsString());
                return new Claim(name, instant);
            }
        }
    }
}
