package com.wu.service;

import com.wu.entity.Team;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 淘汰赛服务 —— 蛇形交叉落位 + 队列模拟晋级。
 *
 * <p><b>蛇形落位</b>：以 4 组每组前 2 名为例——
 * <pre>
 *   输入：[A1, A2, B1, B2,  C1, C2, D1, D2]
 *   落位： A1 vs B2  |  B1 vs A2  |  C1 vs D2  |  D1 vs C2
 * </pre>
 * 相邻两组的第1名对阵对方第2名，同组1/2名分在不同半区。
 */
@Slf4j
@Service
public class KnockoutService {

    @Data
    @AllArgsConstructor
    public static class KnockoutMatch {
        private Team team1;
        private Team team2;
        private int score1;
        private int score2;
        private Team winner;
        private String roundName;
    }

    // ============ 入口 ============

    /**
     * @param qualified 每组前 2 名依次排列：[A1, A2, B1, B2, C1, C2, ...]
     * @return 冠军
     */
    public Team run(List<Team> qualified) {
        int n = qualified.size();
        if (n < 2 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("晋级队伍数必须是 2 的幂次，当前 " + n);
        }
        int totalRounds = log2(n);

        log.info("");
        log.info("╔══════════════════════════════════════╗");
        log.info("║   淘汰赛 — {} 队 {} 轮（蛇形交叉）       ║", n, totalRounds);
        log.info("╚══════════════════════════════════════╝");
        log.info("");

        // 1. 蛇形落位
        Queue<Team> queue = snakeSeed(qualified);

        // 2. 逐轮模拟（Queue 驱动晋级）
        List<List<KnockoutMatch>> allRounds = new ArrayList<>();

        for (int round = 1; round <= totalRounds; round++) {
            String name = roundName(round, totalRounds);
            List<KnockoutMatch> matches = new ArrayList<>();
            Queue<Team> next = new LinkedList<>();

            log.info("┌─ {} ────────────────────────┐", name);
            while (!queue.isEmpty()) {
                Team t1 = queue.poll();
                Team t2 = queue.poll();
                KnockoutMatch m = simulate(t1, t2, name);
                matches.add(m);
                next.add(m.winner);
                log.info("│  {}  {}-{}  {}    →  {}",
                        padRight(t1.getName(), 10), m.score1, m.score2,
                        padRight(t2.getName(), 10), m.winner.getName());
            }
            log.info("└────────────────────────────────────┘");
            log.info("");

            allRounds.add(matches);
            queue = next; // 胜者进入下一轮
        }

        Team champion = queue.poll();

        // 3. 晋级路线图
        printBracket(allRounds, champion);

        return champion;
    }

    // ============ 蛇形落位 ============

    /**
     * [A1, A2,  B1, B2,  C1, C2,  D1, D2, ...]
     * → Queue: A1, B2,  B1, A2,  C1, D2,  D1, C2, ...
     */
    private Queue<Team> snakeSeed(List<Team> qualified) {
        int groups = qualified.size() / 2;
        LinkedList<Team> q = new LinkedList<>();

        List<String> pairs = new ArrayList<>();
        for (int g = 0; g < groups; g += 2) {
            int a1 = g * 2;           // 组 g   第1
            int a2 = a1 + 1;          // 组 g   第2
            int b1 = (g + 1) * 2;     // 组 g+1 第1
            int b2 = b1 + 1;          // 组 g+1 第2

            q.add(qualified.get(a1));   // A1
            q.add(qualified.get(b2));   // B2
            q.add(qualified.get(b1));   // B1
            q.add(qualified.get(a2));   // A2

            char cg = (char) ('A' + g);
            char cg2 = (char) ('A' + g + 1);
            pairs.add(cg + "1-" + cg2 + "2  " + cg2 + "1-" + cg + "2");
        }
        log.info("蛇形交叉配对：{}", String.join("  |  ", pairs));
        return q;
    }

    // ============ 轮次命名 ============

    private String roundName(int round, int total) {
        if (round == total)     return "决赛";
        if (round == total - 1) return "半决赛";
        return "1/" + (1 << (total - round)) + "决赛";
    }

    // ============ 模拟 ============

    private KnockoutMatch simulate(Team t1, Team t2, String round) {
        boolean t1Wins = ThreadLocalRandom.current().nextBoolean();
        int loser = ThreadLocalRandom.current().nextInt(0, 3);
        return new KnockoutMatch(t1, t2,
                t1Wins ? 3 : loser,
                t1Wins ? loser : 3,
                t1Wins ? t1 : t2,
                round);
    }

    // ============ 晋级路线图 ============

    private void printBracket(List<List<KnockoutMatch>> rounds, Team champion) {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║            晋  级  路  线  图                 ║");
        log.info("╚══════════════════════════════════════════════╝");
        log.info("");

        // 将赛果编码为逐轮逐队的信息，供树形递归使用
        // 从决赛（最后一轮）向下展开
        int total = rounds.size();
        List<String> lines = new ArrayList<>();
        buildTree(lines, rounds, total - 1, 0, "", true);
        for (String line : lines) {
            log.info(line);
        }

        log.info("");
        log.info("══════════════════════════════════");
        log.info("  🏆 冠军：{}", champion.getName());
        log.info("══════════════════════════════════");
    }

    /**
     * 递归构建树形文本：从决赛向下倒推第一轮。
     * @param round 当前轮次 (total-1 为决赛)
     * @param idx   该轮比赛下标
     */
    private void buildTree(List<String> lines, List<List<KnockoutMatch>> rounds,
                           int round, int idx, String prefix, boolean isLast) {
        KnockoutMatch m = rounds.get(round).get(idx);

        String connector;
        if (round == rounds.size() - 1) {
            connector = "🏆 " + m.winner.getName();
        } else if (isLast) {
            connector = "└─ " + m.winner.getName();
        } else {
            connector = "├─ " + m.winner.getName();
        }

        String line = prefix + connector
                + "  (" + m.team1.getName() + " " + m.score1 + "-" + m.score2 + " " + m.team2.getName() + ")";
        lines.add(line);

        if (round > 0) {
            // 该场比赛的两个来源：round-1 的第 idx*2 和 idx*2+1 场
            String childPrefix = prefix + (round == rounds.size() - 1 ? "" : (isLast ? "   " : "│  "));
            buildTree(lines, rounds, round - 1, idx * 2, childPrefix, false);
            buildTree(lines, rounds, round - 1, idx * 2 + 1, childPrefix, true);
        }
    }

    // ============ 工具 ============

    private static int log2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    private static String padRight(String s, int w) {
        if (s.length() >= w) return s;
        return s + " ".repeat(w - s.length());
    }
}
