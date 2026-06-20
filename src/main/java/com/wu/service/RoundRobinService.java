package com.wu.service;

import com.wu.entity.Team;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 循环赛服务 —— 纯内存计算，不依赖数据库。
 * <p>
 * 职责：
 * <ol>
 *   <li>输入一组 Team，生成所有对阵组合 (A vs B, A vs C...)</li>
 *   <li>随机模拟每场比赛的局分（3-0, 3-1, 3-2）</li>
 *   <li>按 积分 → 组内胜负关系/小积分 → 总净胜局 排序</li>
 * </ol>
 * <p>
 * 平积分裁决规则：
 * <ul>
 *   <li>2 人平积分 → 直接看双方交手结果</li>
 *   <li>3+ 人平积分 → 只看平积分队伍之间的"小循环"：
 *       小循环积分↓ → 小循环净胜局↓ → 总净胜局↓</li>
 * </ul>
 */
@Slf4j
@Service
public class RoundRobinService {

    // ============ 数据传输对象 ============

    @Data
    @AllArgsConstructor
    public static class MatchResult {
        private Team team1;
        private Team team2;
        private int team1Score;   // team1 赢的局数 (3 或 0-2)
        private int team2Score;   // team2 赢的局数 (3 或 0-2)
        private Team winner;
        private Team loser;

        /** 格式化比分，如 "3-1" */
        public String scoreText() {
            return winner.equals(team1)
                    ? team1Score + "-" + team2Score
                    : team2Score + "-" + team1Score;
        }
    }

    @Data
    public static class TeamStanding {
        private final Team team;
        private int points;       // 积分（胜+1 负+0）
        private int gamesWon;     // 胜局总数
        private int gamesLost;    // 负局总数

        public TeamStanding(Team team) {
            this.team = team;
        }

        public int getNetGames() {
            return gamesWon - gamesLost;
        }
    }

    @Data
    @AllArgsConstructor
    public static class RoundRobinResult {
        private List<MatchResult> matches;
        private List<TeamStanding> sortedStandings;
    }

    // ============ 核心逻辑 ============

    /**
     * 执行循环赛。
     *
     * @param teams 参赛队伍列表
     * @return 包含对阵结果与排名表的完整结果
     */
    public RoundRobinResult run(List<Team> teams) {
        if (teams == null || teams.size() < 2) {
            throw new IllegalArgumentException("至少需要 2 支队伍进行循环赛");
        }

        int n = teams.size();
        int totalMatches = n * (n - 1) / 2;

        log.info("========== 循环赛开始：{} 支队伍，共 {} 场比赛 ==========", n, totalMatches);

        // ---- 1. 生成对阵并模拟 ----
        List<MatchResult> matches = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                MatchResult match = simulateMatch(teams.get(i), teams.get(j));
                matches.add(match);
                log.info("[对阵] {} vs {}  →  {} ({})",
                        match.getTeam1().getName(),
                        match.getTeam2().getName(),
                        match.getWinner().getName(),
                        match.scoreText());
            }
        }

        // ---- 2. 累积积分 ----
        Map<Team, TeamStanding> standingMap = new LinkedHashMap<>();
        for (Team t : teams) {
            standingMap.put(t, new TeamStanding(t));
        }

        for (MatchResult m : matches) {
            TeamStanding ws = standingMap.get(m.getWinner());
            TeamStanding ls = standingMap.get(m.getLoser());

            ws.points += 1;

            int winScore = m.getWinner().equals(m.getTeam1()) ? m.getTeam1Score() : m.getTeam2Score();
            int loseScore = m.getWinner().equals(m.getTeam1()) ? m.getTeam2Score() : m.getTeam1Score();

            ws.gamesWon  += winScore;
            ws.gamesLost += loseScore;
            ls.gamesWon  += loseScore;
            ls.gamesLost += winScore;
        }

        // ---- 3. 按积分分组后逐组裁决平积分 ----
        List<TeamStanding> sorted = resolveAllTies(new ArrayList<>(standingMap.values()), matches);

        log.info("========== 最终排名 ==========");
        int rank = 1;
        for (TeamStanding s : sorted) {
            log.info("第{}名  {}  积分:{}  胜局:{}  负局:{}  净胜局:{}",
                    rank++, s.getTeam().getName(), s.points,
                    s.gamesWon, s.gamesLost, s.getNetGames());
        }

        return new RoundRobinResult(matches, sorted);
    }

    // ============ 平积分裁决 ============

    /**
     * 对全体 standings 按积分分组，每组内部解决平积分排序后展平。
     */
    private List<TeamStanding> resolveAllTies(List<TeamStanding> all, List<MatchResult> matches) {
        // 按积分降序分组（LinkedHashMap 保持插入顺序）
        Map<Integer, List<TeamStanding>> groups = new LinkedHashMap<>();
        for (TeamStanding s : all) {
            groups.computeIfAbsent(s.points, k -> new ArrayList<>()).add(s);
        }

        // 各组内部排序，积分高的组在前
        List<Integer> pointLevels = new ArrayList<>(groups.keySet());
        pointLevels.sort(Comparator.reverseOrder());

        List<TeamStanding> result = new ArrayList<>();
        for (int pts : pointLevels) {
            List<TeamStanding> group = groups.get(pts);
            if (group.size() == 1) {
                result.add(group.get(0));
            } else {
                result.addAll(resolveGroupTie(group, matches));
            }
        }
        return result;
    }

    /**
     * 解决同一积分组内的排序。
     */
    private List<TeamStanding> resolveGroupTie(List<TeamStanding> group, List<MatchResult> allMatches) {
        int size = group.size();

        if (size == 2) {
            // 两人平积分 → 直接看交手结果
            return resolveTwoWayTie(group.get(0), group.get(1), allMatches);
        }

        // 3+ 人平积分 → 小循环（只看平积分队伍之间的比赛）
        log.info("--- 积分 {} 的 {} 支队伍平积分，进入小循环裁决 ---",
                group.get(0).points, size);

        Set<Team> tiedTeams = new HashSet<>();
        for (TeamStanding s : group) tiedTeams.add(s.getTeam());

        // 筛选出仅涉及这些平积分队伍的场次
        List<MatchResult> miniMatches = new ArrayList<>();
        for (MatchResult m : allMatches) {
            if (tiedTeams.contains(m.getTeam1()) && tiedTeams.contains(m.getTeam2())) {
                miniMatches.add(m);
            }
        }

        // 在小循环内重新计算 standings
        Map<Team, TeamStanding> miniMap = new LinkedHashMap<>();
        for (Team t : tiedTeams) miniMap.put(t, new TeamStanding(t));

        for (MatchResult m : miniMatches) {
            TeamStanding ws = miniMap.get(m.getWinner());
            TeamStanding ls = miniMap.get(m.getLoser());
            ws.points += 1;

            int winScore  = m.getWinner().equals(m.getTeam1()) ? m.getTeam1Score() : m.getTeam2Score();
            int loseScore = m.getWinner().equals(m.getTeam1()) ? m.getTeam2Score() : m.getTeam1Score();
            ws.gamesWon  += winScore;
            ws.gamesLost += loseScore;
            ls.gamesWon  += loseScore;
            ls.gamesLost += winScore;
        }

        log.info("  小循环内部排名：");
        List<TeamStanding> subSorted = new ArrayList<>(miniMap.values());
        subSorted.sort(Comparator
                .comparingInt(TeamStanding::getPoints).reversed()
                .thenComparingInt(TeamStanding::getNetGames).reversed());

        for (TeamStanding ss : subSorted) {
            // 用全程总数据替换小循环数据（仅用于展示）
            TeamStanding full = group.stream()
                    .filter(s -> s.getTeam().equals(ss.getTeam()))
                    .findFirst().orElse(ss);
            log.info("    {}  小积分:{}  小净胜局:{} | 总净胜局:{}",
                    ss.getTeam().getName(), ss.points,
                    ss.getNetGames(), full.getNetGames());
        }

        // 如果小循环内部仍有平积分 → 用总净胜局裁决
        return resolveSubTies(subSorted, allMatches, group);
    }

    /**
     * 小循环排序后仍可能有平积分（如 A>B>C>A 且净胜局也相同），
     * 此时退回总净胜局裁决。
     */
    private List<TeamStanding> resolveSubTies(List<TeamStanding> subSorted,
                                               List<MatchResult> allMatches,
                                               List<TeamStanding> fullGroup) {
        // 将 fullGroup 装进 Map 方便取值
        Map<Team, TeamStanding> fullMap = new LinkedHashMap<>();
        for (TeamStanding s : fullGroup) fullMap.put(s.getTeam(), s);

        List<TeamStanding> result = new ArrayList<>();
        List<TeamStanding> buffer = new ArrayList<>();

        for (TeamStanding s : subSorted) {
            if (buffer.isEmpty()) {
                buffer.add(s);
            } else {
                TeamStanding prev = buffer.get(0);
                if (prev.points == s.points && prev.getNetGames() == s.getNetGames()) {
                    // 小循环内也平了 → 入缓冲，等下用总净胜局
                    buffer.add(s);
                } else {
                    // 小循环分出高下 → 缓冲里的直接输出
                    flushBuffer(buffer, result, fullMap, allMatches);
                    buffer.clear();
                    buffer.add(s);
                }
            }
        }
        flushBuffer(buffer, result, fullMap, allMatches);
        return result;
    }

    /** 将一组在小循环中仍平的队伍，按总净胜局↓排序输出 */
    private void flushBuffer(List<TeamStanding> buffer, List<TeamStanding> result,
                             Map<Team, TeamStanding> fullMap, List<MatchResult> allMatches) {
        if (buffer.size() == 1) {
            result.add(fullMap.get(buffer.get(0).getTeam()));
        } else if (buffer.size() == 2) {
            List<TeamStanding> resolved = resolveTwoWayTie(
                    fullMap.get(buffer.get(0).getTeam()),
                    fullMap.get(buffer.get(1).getTeam()),
                    allMatches);
            result.addAll(resolved);
        } else {
            // 3+ 队伍连小循环都完全平 → 直接用总净胜局
            buffer.sort(Comparator.comparingInt(a -> {
                TeamStanding fs = fullMap.get(a.getTeam());
                return fs == null ? 0 : -fs.getNetGames();
            }));
            for (TeamStanding b : buffer) result.add(fullMap.get(b.getTeam()));
        }
    }

    /**
     * 两人平积分：直接查交手记录。
     */
    private List<TeamStanding> resolveTwoWayTie(TeamStanding a, TeamStanding b,
                                                 List<MatchResult> allMatches) {
        MatchResult h2h = findMatch(allMatches, a.getTeam(), b.getTeam());
        if (h2h != null && h2h.getWinner().equals(a.getTeam())) {
            return Arrays.asList(a, b);
        } else if (h2h != null && h2h.getWinner().equals(b.getTeam())) {
            return Arrays.asList(b, a);
        }
        // 没有交手记录（理论不应发生）→ 按总净胜局
        return a.getNetGames() >= b.getNetGames()
                ? Arrays.asList(a, b) : Arrays.asList(b, a);
    }

    // ============ 内部方法 ============

    /** 随机模拟一场比赛 */
    private MatchResult simulateMatch(Team t1, Team t2) {
        boolean t1Wins = ThreadLocalRandom.current().nextBoolean();
        int loserScore = ThreadLocalRandom.current().nextInt(0, 3); // 0 / 1 / 2

        int s1, s2;
        Team winner, loser;
        if (t1Wins) {
            s1 = 3;
            s2 = loserScore;
            winner = t1;
            loser  = t2;
        } else {
            s1 = loserScore;
            s2 = 3;
            winner = t2;
            loser  = t1;
        }
        return new MatchResult(t1, t2, s1, s2, winner, loser);
    }

    /** 从已完成的比赛中查找两支队伍之间的那场 */
    private MatchResult findMatch(List<MatchResult> matches, Team t1, Team t2) {
        for (MatchResult m : matches) {
            if ((m.getTeam1().equals(t1) && m.getTeam2().equals(t2))
                    || (m.getTeam1().equals(t2) && m.getTeam2().equals(t1))) {
                return m;
            }
        }
        return null;
    }
}
