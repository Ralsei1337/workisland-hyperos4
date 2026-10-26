package com.dafeiyu.workisland;

/**
 * 移植自 agent-hud 的 BotState：14 种 look、四种阶段、7 个头顶标识。
 * 状态字符串由 driver 通过 am start-foreground-service --es state 送进来。
 */
final class BotState {
    /** BotLook.swift：合并顺序不能改，Sign 的优先级也按这个顺序。 */
    enum Look {
        PERMISSION, QUESTION, PLAN, COMPACTING, THINKING,
        TOOL_EDIT, TOOL_COMMAND, TOOL_READ, TOOL_LONG_COMMAND,
        PLAIN, NO_NEWS, IDLE, ASLEEP, DONE
    }

    /** Wakes / Works / Asks / Done —— 岛上只看这四态。 */
    enum Phase { WAKES, WORKS, ASKS, DONE }

    enum Sign { EXCLAMATION, QUESTION, LINES, CROSS, HOURGLASS, ELLIPSIS, Z }

    static final Sign[] SIGN_ORDER = {
            Sign.EXCLAMATION, Sign.QUESTION, Sign.LINES, Sign.CROSS,
            Sign.HOURGLASS, Sign.ELLIPSIS, Sign.Z
    };

    /** 5×7 点阵，与 BotSigns.swift 逐格一致。 */
    static final String[][] SIGN_ROWS = {
            {"..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."}, // !
            {".###.", "#...#", "....#", "..##.", "..#..", ".....", "..#.."}, // ?
            {".....", "#####", ".....", "#####", ".....", "#####", "....."}, // ≡
            {".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "....."}, // ×
            {".....", "#####", ".#.#.", "..#..", ".###.", "#####", "....."}, // ⧗
            {".....", ".....", ".....", ".....", ".....", ".....", "#.#.#"}, // …
            {".....", ".....", "#####", "...#.", "..#..", ".#...", "#####"}, // z
    };

    private BotState() {}

    static boolean isAttention(Look look) {
        return look == Look.PERMISSION || look == Look.QUESTION || look == Look.PLAN;
    }

    static boolean isWorking(Look look) {
        return look == Look.COMPACTING || look == Look.THINKING
                || look == Look.TOOL_EDIT || look == Look.TOOL_COMMAND
                || look == Look.TOOL_READ || look == Look.TOOL_LONG_COMMAND
                || look == Look.PLAIN;
    }

    static boolean isCalm(Look look) {
        return look == Look.NO_NEWS || look == Look.IDLE || look == Look.ASLEEP;
    }

    static Phase phase(Look look) {
        if (isAttention(look)) return Phase.ASKS;
        if (isWorking(look)) return Phase.WORKS;
        if (look == Look.DONE) return Phase.DONE;
        return Phase.WAKES;
    }

    /** 头顶标识：请求/提问/清单优先，其次是失败（×）和限额（⧗）。 */
    static Sign sign(Look look, boolean failed) {
        Sign sign = look == Look.PERMISSION ? Sign.EXCLAMATION
                : look == Look.QUESTION ? Sign.QUESTION
                : look == Look.PLAN ? Sign.LINES
                : look == Look.THINKING ? Sign.ELLIPSIS
                : look == Look.ASLEEP ? Sign.Z
                : null;
        return shown(sign, failed ? Sign.CROSS : null);
    }

    static Sign shown(Sign one, Sign other) {
        if (one == null) return other;
        if (other == null) return one;
        return index(one) <= index(other) ? one : other;
    }

    private static int index(Sign sign) {
        for (int i = 0; i < SIGN_ORDER.length; i++) if (SIGN_ORDER[i] == sign) return i;
        return 0;
    }

    /** driver 的旧状态名和新 look 名都认，别把历史脚本弄坏。 */
    static Look look(String wire) {
        String s = wire == null ? "" : wire.trim().toLowerCase();
        switch (s) {
            case "permission": case "waiting": case "ask": return Look.PERMISSION;
            case "question": return Look.QUESTION;
            case "plan": case "planning": case "todo": return Look.PLAN;
            case "compacting": case "compact": return Look.COMPACTING;
            case "thinking": case "subtask": case "subagent": case "retry":
                return Look.THINKING;
            case "tool-edit": case "writing": case "write": case "editing":
                return Look.TOOL_EDIT;
            case "tool-command": case "running": case "command": case "bash":
                return Look.TOOL_COMMAND;
            case "tool-read": case "reading": case "read": case "searching":
            case "search": case "web": return Look.TOOL_READ;
            case "tool-long-command": case "long-command": return Look.TOOL_LONG_COMMAND;
            case "plain": case "working": return Look.PLAIN;
            case "no-news": case "none": return Look.NO_NEWS;
            case "idle": case "wakes": return Look.IDLE;
            case "asleep": case "sleep": case "sleeping": return Look.ASLEEP;
            case "done": case "complete": return Look.DONE;
            default: return Look.PLAIN;
        }
    }

    static boolean failed(String wire) {
        return "error".equalsIgnoreCase(wire) || "failed".equalsIgnoreCase(wire);
    }

    static String lookName(Look look) {
        switch (look) {
            case PERMISSION: return "等你确认";
            case QUESTION: return "有问题";
            case PLAN: return "整理清单";
            case COMPACTING: return "压缩上下文";
            case THINKING: return "思考中";
            case TOOL_EDIT: return "写文件";
            case TOOL_COMMAND: return "执行命令";
            case TOOL_READ: return "读文件";
            case TOOL_LONG_COMMAND: return "长命令";
            case PLAIN: return "干活";
            case NO_NEWS: return "没有新消息";
            case IDLE: return "待命";
            case ASLEEP: return "睡着了";
            case DONE: return "完成";
            default: return "干活";
        }
    }

    static String phaseName(Phase phase) {
        switch (phase) {
            case WAKES: return "醒来";
            case WORKS: return "干活";
            case ASKS: return "请求";
            case DONE: return "完成";
            default: return "干活";
        }
    }
}
