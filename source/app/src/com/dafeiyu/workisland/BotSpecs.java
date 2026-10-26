package com.dafeiyu.workisland;

/**
 * BotSpec.swift 的 Java 版：每个 agent 的形状、颜色、眼睛、鳍、装饰。
 * 坐标都是「身体半径」单位；颜色字符串按 agent-hud 暗色地面（light=false）取值。
 */
final class BotSpecs {
    static final int CIRCLE = 0, OUTLINE = 1, POLYGON = 2, RECT = 3, CAT = 4, BOB = 5;

    static final class Shape {
        int kind;
        float keep, smooth, rotation, round, width, height, skew;
        int sides;
    }

    static final class Paint {
        String color;
        String[] colors;
        float[] from, to, center;
        float inner, outer;
        boolean radial;
    }

    static final class Fin {
        float[] root;
        float angle, length, base, tip, sweep;
        int hand;
    }

    static final class Decor {
        int kind; // 0 blocks, 1 soft, 2 notch, 3 cut, 4 line, 5 fins, 6 feelers
        float[][] boxes;
        String color;
        float round;
        boolean follows;
        float[][] points;
        float[][][] lines;
        float width;
        Fin[] fins;
    }

    static final class Ring {
        float radiusX, radiusY, drop, tilt, width;
    }

    static final class Patch {
        float[] box;
        float round;
        Paint paint;
    }

    static final class Limbs {
        float[][] arms, legs;
        float pixel;
    }

    static final class Spec {
        float radius = 0.36f;
        float[] center = {0.5f, 0.5f};
        float reach = 1f;
        Shape shape = new Shape();
        Paint paint;
        float[] face = {0, 0};
        float eyeHeight = 0.62f, eyeWidth = 0.25f, eyeRound = 1f, eyeSpread = 0.34f;
        float eyeAcross = Float.NaN;
        String eyeColor;
        float spin, settle;
        java.util.List<Decor> decor;
        Ring ring;
        Patch patch;
        String stroke, pupil;
        float[] signAt;
        float[][] ownEyes;
        float[][] keeps;
        Limbs limbs;
        boolean crossesEyes;
    }

    private BotSpecs() {}

    private static Shape shape(int kind) {
        Shape s = new Shape();
        s.kind = kind;
        return s;
    }

    private static Shape circle() {
        return shape(CIRCLE);
    }

    private static Shape outline(float keep, float smooth) {
        Shape s = shape(OUTLINE);
        s.keep = keep;
        s.smooth = smooth;
        return s;
    }

    private static Shape rect(float width, float height, float round) {
        Shape s = shape(RECT);
        s.width = width;
        s.height = height;
        s.round = round;
        return s;
    }

    private static Spec spec() {
        return new Spec();
    }

    private static Paint color(String value) {
        Paint p = new Paint();
        p.color = value;
        return p;
    }

    private static Paint linear(float[] from, float[] to, String... colors) {
        Paint p = new Paint();
        p.from = from;
        p.to = to;
        p.colors = colors;
        return p;
    }

    private static String mono(boolean light) {
        return light ? "#1d1d1f" : "#ffffff";
    }

    private static Spec cat(String head, Paint face) {
        Spec s = spec();
        s.center = new float[]{0.5f, 0.55f};
        s.reach = 0.5f;
        s.shape = shape(CAT);
        s.paint = color(head);
        s.face = new float[]{0, 0.2f};
        s.eyeHeight = 0.84f;
        s.eyeWidth = 0.36f;
        s.eyeColor = "#ffffff";
        s.patch = new Patch();
        s.patch.box = new float[]{-0.9f, -0.52f, 0.9f, 1.3f};
        s.patch.round = 0.5f;
        s.patch.paint = face;
        s.crossesEyes = true;
        return s;
    }

    private static Decor blocks(String color, float[][] boxes) {
        Decor d = new Decor();
        d.kind = 0;
        d.color = color;
        d.boxes = boxes;
        return d;
    }

    private static Decor soft(String color, float round, float[]... boxes) {
        Decor d = new Decor();
        d.kind = 1;
        d.color = color;
        d.round = round;
        d.boxes = boxes;
        return d;
    }

    private static Decor notch(float[]... points) {
        Decor d = new Decor();
        d.kind = 2;
        d.points = points;
        return d;
    }

    private static Decor fins(String color, Fin... fins) {
        Decor d = new Decor();
        d.kind = 5;
        d.color = color;
        d.fins = fins;
        return d;
    }

    private static Fin fin(float[] root, float angle, float length, float base, float tip, float sweep, int hand) {
        Fin f = new Fin();
        f.root = root;
        f.angle = angle;
        f.length = length;
        f.base = base;
        f.tip = tip;
        f.sweep = sweep;
        f.hand = hand;
        return f;
    }

    private static Decor feelers(String color, float width, float[][]... lines) {
        Decor d = new Decor();
        d.kind = 6;
        d.color = color;
        d.width = width;
        d.lines = lines;
        return d;
    }

    private static Ring ring(float radiusX, float radiusY, float drop, float tilt, float width) {
        Ring r = new Ring();
        r.radiusX = radiusX;
        r.radiusY = radiusY;
        r.drop = drop;
        r.tilt = tilt;
        r.width = width;
        return r;
    }

    private static float[][] boxes(float[]... rows) {
        return rows;
    }

    /** 与 BotSpec.table 逐项对应；light=false 是岛上的暗色地面。 */
    static Spec of(String key, boolean light) {
        if (key == null) key = "hud";
        switch (key) {
            case "hud": {
                Spec s = spec();
                s.radius = 0.34f;
                s.reach = 0.6f;
                Shape sh = rect(0.9f, 0.46f, 0.5f);
                s.shape = sh;
                s.paint = color("#1f1e1d");
                s.stroke = "#faf9f5";
                s.eyeHeight = 0.5f;
                s.eyeWidth = 0.22f;
                s.eyeSpread = 0.3f;
                s.eyeColor = "#faf9f5";
                return s;
            }
            case "fish": {
                Spec s = spec();
                s.radius = 0.34f;
                s.shape = shape(BOB);
                s.paint = linear(new float[]{0.2f, 0.1f}, new float[]{0.85f, 0.95f},
                        "#ea6a4a", "#c94f3a");
                s.face = new float[]{0, -0.1f};
                s.eyeColor = "#faf9f5";
                s.crossesEyes = true;
                s.decor = new java.util.ArrayList<>();
                s.decor.add(soft("#ffd9c7", 1, new float[]{-0.8f, 0.4f, 0.8f, 1.7f}));
                return s;
            }
            case "claude": {
                Spec s = spec();
                s.radius = 0.22f;
                s.center = new float[]{0.5f, 0.445f};
                s.reach = 0.7f;
                s.shape = rect(0.66f, 0.44f, 0.008f);
                s.face = new float[]{0, -0.25f};
                s.eyeHeight = 0.64f;
                s.eyeWidth = 0.32f;
                s.eyeRound = 0;
                s.eyeAcross = 0.875f;
                s.limbs = new Limbs();
                s.limbs.arms = boxes(new float[]{-2, 0, -1.5f, 0.5f}, new float[]{1.5f, 0, 2, 0.5f});
                s.limbs.legs = boxes(
                        new float[]{-1.25f, 1, -1, 1.5f}, new float[]{-0.75f, 1, -0.5f, 1.5f},
                        new float[]{0.5f, 1, 0.75f, 1.5f}, new float[]{1, 1, 1.25f, 1.5f});
                s.limbs.pixel = 0.5f;
                return s;
            }
            case "openai": {
                Spec s = spec();
                s.radius = 0.37f;
                s.shape = outline(1.1f, 5);
                s.spin = 7;
                s.crossesEyes = true;
                return s;
            }
            case "antigravity": {
                Spec s = spec();
                s.reach = 0.75f;
                s.shape = outline(0.5f, 16);
                s.face = new float[]{0, -0.2f};
                s.settle = 0.093f;
                return s;
            }
            case "deepseek": {
                Spec s = spec();
                s.face = new float[]{0, -0.14f};
                s.crossesEyes = true;
                s.decor = new java.util.ArrayList<>();
                // 鳍只在扑腾时露出来；静止时收在身体里，免得圆身体边上多出三个小点。
                s.decor.add(soft("#c9d4ff", 1, new float[]{-0.86f, 0.34f, 0.86f, 1.9f}));
                return s;
            }
            case "grok": {
                Spec s = spec();
                s.radius = 0.33f;
                s.reach = 0.55f;
                s.face = new float[]{0, -0.3f};
                s.ring = ring(1.5f, 0.32f, 0.3f, -20, 0.14f);
                return s;
            }
            case "grok-bot": {
                Spec s = spec();
                s.radius = 0.37f;
                s.paint = color(light ? "#000000" : "#ffffff");
                s.crossesEyes = true;
                s.eyeHeight = 0.78f;
                s.eyeWidth = 0.3f;
                if (light) s.eyeColor = "#f5f5f7";
                return s;
            }
            case "cursor": {
                Spec s = spec();
                s.radius = 0.41f;
                s.reach = 0.7f;
                Shape sh = shape(POLYGON);
                sh.sides = 6;
                sh.rotation = -90;
                sh.round = 0.07f;
                s.shape = sh;
                s.face = new float[]{-0.12f, 0.16f};
                s.decor = new java.util.ArrayList<>();
                s.decor.add(notch(new float[]{0.32f, -0.954f}, new float[]{0.75f, -0.704f},
                        new float[]{0.266f, -0.361f}));
                if (light) s.eyeColor = "#f5f5f7";
                return s;
            }
            case "opencode": {
                Spec s = spec();
                s.reach = 0.5f;
                s.shape = rect(0.66f, 0.66f, 0.04f);
                s.paint = color(light ? "#211e1e" : "#f1ecec");
                s.face = new float[]{0, -0.02f};
                s.eyeHeight = 0.4f;
                s.eyeWidth = 0.24f;
                s.eyeRound = 0.12f;
                if (light) s.eyeColor = "#f5f5f7";
                s.decor = new java.util.ArrayList<>();
                s.decor.add(blocks(light ? "#cfcecd" : "#4b4646", boxes(
                        new float[]{-1, -1.06f, 1, -0.56f}, new float[]{-1, -0.56f, -0.62f, -0.36f},
                        new float[]{-0.24f, -0.56f, 0.16f, -0.42f}, new float[]{0.58f, -0.56f, 1, -0.3f})));
                Decor follows = blocks(light ? "#cfcecd" : "#4b4646",
                        boxes(new float[]{-0.6f, 0.36f, 0.6f, 0.6f}));
                follows.follows = true;
                s.decor.add(follows);
                return s;
            }
            case "kimi": {
                Spec s = spec();
                s.radius = 0.32f;
                s.center = new float[]{0.41f, 0.59f};
                s.shape = outline(0.3f, 22);
                s.keeps = boxes(new float[]{0.78f, 0.02f, 0.995f, 0.235f});
                s.signAt = new float[]{0.44f, 0.22f};
                if (light) s.eyeColor = "#f5f5f7";
                return s;
            }
            case "glm": {
                Spec s = spec();
                s.shape = rect(0.74f, 0.74f, 0.16f);
                s.paint = color("#28282b");
                s.eyeColor = "#ffffff";
                s.stroke = "#ffffff";
                return s;
            }
            case "pi": {
                Spec s = spec();
                s.center = new float[]{0.5f, 0.31f};
                s.reach = 0.3f;
                s.shape = rect(0.9f, 0.29f, 0.02f);
                s.eyeHeight = 0.3f;
                s.eyeWidth = 0.3f;
                s.eyeRound = 0;
                if (light) s.eyeColor = "#f5f5f7";
                s.decor = new java.util.ArrayList<>();
                s.decor.add(blocks(mono(light), boxes(
                        new float[]{-0.8f, 0.36f, -0.3f, 1.5f},
                        new float[]{0.3f, 0.36f, 0.8f, 1.5f})));
                return s;
            }
            case "hermes": {
                Spec s = spec();
                s.crossesEyes = true;
                s.radius = 0.35f;
                s.reach = 0.5f;
                s.paint = color(mono(light));
                s.face = new float[]{0, 0.24f};
                s.eyeHeight = 0.72f;
                s.eyeWidth = 0.3f;
                s.eyeSpread = 0.44f;
                if (light) s.eyeColor = "#f5f5f7";
                s.decor = new java.util.ArrayList<>();
                s.decor.add(soft("#b7b6c2", 1,
                        new float[]{-1.1f, -1.25f, -0.2f, -0.22f},
                        new float[]{-0.44f, -1.3f, 0.44f, -0.3f},
                        new float[]{0.2f, -1.25f, 1.1f, -0.22f}));
                return s;
            }
            case "zcode": {
                Spec s = spec();
                s.reach = 0.8f;
                Shape sh = rect(0.66f, 0.64f, 0.05f);
                sh.skew = 0.2f;
                s.shape = sh;
                if (light) s.eyeColor = "#f5f5f7";
                return s;
            }
            case "qwen": {
                Spec s = spec();
                s.radius = 0.37f;
                s.shape = outline(0.55f, 7);
                s.spin = 7;
                s.crossesEyes = true;
                return s;
            }
            case "devin": {
                Spec s = spec();
                s.reach = 0.55f;
                s.shape = outline(1f, 5);
                s.paint = color(mono(light));
                s.eyeAcross = 0.42f;
                if (light) s.eyeColor = "#f5f5f7";
                return s;
            }
            case "openclaw": {
                Spec s = spec();
                s.crossesEyes = true;
                s.center = new float[]{0.5f, 0.54f};
                s.reach = 0.7f;
                s.paint = linear(new float[]{0.2f, 0.1f}, new float[]{0.85f, 0.95f},
                        "#ee4747", "#c22e2e");
                s.face = new float[]{0, 0.06f};
                s.eyeHeight = 0.46f;
                s.eyeWidth = 0.46f;
                s.eyeSpread = 0.46f;
                s.eyeColor = "#050810";
                s.decor = new java.util.ArrayList<>();
                s.decor.add(feelers("#ea4a4a", 0.1f,
                        new float[][]{{-0.24f, -0.9f}, {-0.34f, -1.14f}, {-0.54f, -1.22f}},
                        new float[][]{{0.24f, -0.9f}, {0.34f, -1.14f}, {0.54f, -1.22f}}));
                return s;
            }
            case "codebuddy":
                return cat(mono(light), linear(new float[]{0.2f, 0.2f}, new float[]{0.85f, 1},
                        "#5570da", "#4587c1", "#369dab"));
            case "workbuddy":
                return cat(light ? "#1b2622" : "#eafaf6",
                        linear(new float[]{0.5f, 0.55f}, new float[]{0.5f, 1},
                                "#00cd8c", "#7dd76d"));
            case "qoder": {
                Spec s = spec();
                s.crossesEyes = true;
                s.reach = 0.55f;
                s.paint = color(light ? "#161616" : "#f8f8f8");
                s.face = new float[]{0, -0.24f};
                s.eyeHeight = 0.7f;
                s.eyeWidth = 0.3f;
                s.eyeSpread = 0.42f;
                if (light) s.eyeColor = "#f5f5f7";
                s.decor = new java.util.ArrayList<>();
                s.decor.add(soft("#28d858", 0, new float[]{-1.2f, -0.3f, 1.2f, 1.2f}));
                s.decor.add(soft("#5be584", 1, new float[]{-1.4f, -1.7f, 1.4f, 0.45f}));
                s.decor.add(soft(light ? "#161616" : "#f8f8f8", 1,
                        new float[]{-1.4f, -1.7f, 1.4f, 0.34f}));
                return s;
            }
            case "copilot": {
                Spec s = spec();
                s.ownEyes = boxes(
                        new float[]{0.355f, 0.57f, 0.434f, 0.723f},
                        new float[]{0.562f, 0.57f, 0.645f, 0.723f});
                return s;
            }
            default:
                return spec();
        }
    }
}
