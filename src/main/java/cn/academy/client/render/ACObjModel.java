package cn.academy.client.render;

import cn.academy.AcademyCraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Small Wavefront renderer for AcademyCraft's original grouped OBJ assets. */
public final class ACObjModel {
    /** LambdaLib2 identified a rendered vertex only by position and UV; OBJ vn entries were ignored. */
    private record Index(int vertex, int uv) {}
    private record Triangle(Index a, Index b, Index c, String group) {}
    private static final Map<ResourceLocation, ACObjModel> CACHE = new ConcurrentHashMap<>();

    private final List<Vector3f> vertices = new ArrayList<>();
    private final List<Vector2f> uvs = new ArrayList<>();
    private final List<Triangle> triangles = new ArrayList<>();
    private final Map<Index, Vector3f> smoothedNormals = new HashMap<>();

    public static ACObjModel get(String name) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MOD_ID, "models/" + name + ".obj");
        return CACHE.computeIfAbsent(location, ACObjModel::load);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static ACObjModel load(ResourceLocation location) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location).orElseThrow();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                ACObjModel model = parse(reader);
                AcademyCraft.LOGGER.debug("Loaded legacy OBJ {} ({} triangles)", location, model.triangles.size());
                return model;
            }
        } catch (Exception exception) {
            AcademyCraft.LOGGER.error("Unable to load legacy OBJ {}", location, exception);
            return new ACObjModel();
        }
    }

    /** Parser-compatible port of LambdaLib2 0.2.0 ObjParser. */
    static ACObjModel parse(Reader source) throws java.io.IOException {
        ACObjModel model = new ACObjModel();
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String group = "Default";
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] split = line.split("\\s+");
            switch (split[0]) {
                case "v" -> model.vertices.add(new Vector3f(Float.parseFloat(split[1]),
                        Float.parseFloat(split[2]), Float.parseFloat(split[3])));
                case "vt" -> model.uvs.add(new Vector2f(Float.parseFloat(split[1]), Float.parseFloat(split[2])));
                case "g" -> { if (split.length > 1) group = split[1]; }
                case "f" -> {
                    List<Index> face = new ArrayList<>();
                    for (int i = 1; i < split.length; i++) face.add(parseIndex(split[i], model));
                    for (int i = 1; i + 1 < face.size(); i++) {
                        model.triangles.add(new Triangle(face.getFirst(), face.get(i), face.get(i + 1), group));
                    }
                }
                // The legacy parser deliberately ignored file normals, objects, materials and smoothing flags.
                case "vn", "o", "usemtl", "mtllib", "s" -> { }
                default -> { }
            }
        }
        model.rebuildLegacyNormals();
        return model;
    }

    private static Index parseIndex(String token, ACObjModel model) {
        String[] values = token.split("/", -1);
        return new Index(resolve(values[0], model.vertices.size()),
                values.length > 1 && !values[1].isBlank() ? resolve(values[1], model.uvs.size()) : -1);
    }

    private static int resolve(String value, int size) {
        int index = Integer.parseInt(value);
        return index < 0 ? size + index : index - 1;
    }

    private void rebuildLegacyNormals() {
        Map<Index, Vector3f> sums = new HashMap<>();
        for (Triangle triangle : triangles) {
            Vector3f origin = vertices.get(triangle.a().vertex());
            Vector3f edge1 = new Vector3f(vertices.get(triangle.b().vertex())).sub(origin);
            Vector3f edge2 = new Vector3f(vertices.get(triangle.c().vertex())).sub(origin);
            Vector3f faceNormal = edge1.cross(edge2);
            if (faceNormal.lengthSquared() > 0) faceNormal.normalize();
            addNormal(sums, triangle.a(), faceNormal);
            addNormal(sums, triangle.b(), faceNormal);
            addNormal(sums, triangle.c(), faceNormal);
        }
        sums.forEach((index, normal) -> {
            if (normal.lengthSquared() > 0) normal.normalize();
            smoothedNormals.put(index, normal);
        });
    }

    private static void addNormal(Map<Index, Vector3f> sums, Index index, Vector3f normal) {
        sums.computeIfAbsent(index, ignored -> new Vector3f()).add(normal);
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
                       float red, float green, float blue, float alpha, String... groups) {
        Set<String> selected = groups.length == 0 ? Set.of() : new HashSet<>(List.of(groups));
        for (Triangle triangle : triangles) {
            if (!selected.isEmpty() && !selected.contains(triangle.group())) continue;
            emit(poseStack, consumer, triangle.a(), packedLight, packedOverlay, red, green, blue, alpha);
            emit(poseStack, consumer, triangle.b(), packedLight, packedOverlay, red, green, blue, alpha);
            emit(poseStack, consumer, triangle.c(), packedLight, packedOverlay, red, green, blue, alpha);
            // Entity RenderTypes use quad buffers. Repeating the last point preserves each OBJ triangle.
            emit(poseStack, consumer, triangle.c(), packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    private void emit(PoseStack poseStack, VertexConsumer consumer, Index index, int packedLight, int packedOverlay,
                      float red, float green, float blue, float alpha) {
        if (index.vertex() < 0 || index.vertex() >= vertices.size()) return;
        Vector3f vertex = vertices.get(index.vertex());
        Vector2f uv = index.uv() >= 0 && index.uv() < uvs.size() ? uvs.get(index.uv()) : new Vector2f();
        Vector3f normal = smoothedNormals.getOrDefault(index, new Vector3f());
        consumer.addVertex(poseStack.last(), vertex.x, vertex.y, vertex.z)
                .setColor(red, green, blue, alpha)
                .setUv(uv.x, 1 - uv.y)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(poseStack.last(), normal.x, normal.y, normal.z);
    }

    int triangleCount() {
        return triangles.size();
    }

    Vector3f legacyNormal(int vertex, int uv) {
        return new Vector3f(smoothedNormals.getOrDefault(new Index(vertex, uv), new Vector3f()));
    }

    private ACObjModel() {}
}
