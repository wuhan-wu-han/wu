package com.wu.service;

import com.wu.entity.Match;
import com.wu.entity.Team;
import com.wu.enums.Stage;
import com.wu.enums.UnitType;
import com.wu.event.MatchGenerateEvent;
import com.wu.repository.MatchRepository;
import com.wu.repository.TeamRepository;
import com.wu.service.RoundRobinService.MatchPlan;
import com.wu.service.ScheduleEngine.RoundPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final RoundRobinService roundRobinService;
    private final ScheduleEngine scheduleEngine;

    // ============ 事件监听 ============

    @EventListener
    @Transactional
    public void onMatchGenerate(MatchGenerateEvent event) {
        UnitType unitType = event.getUnitType();
        List<Team> rawTeams = event.getTeams();
        int n = rawTeams.size();

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  赛事生成 — {} ({} 队)                           ║", unitType, n);
        log.info("╚══════════════════════════════════════════════════════════╝");

        List<Team> teams = persistTeams(rawTeams);

        // ★ 自动赛制选择
        String formatLabel;
        List<Match> allMatches = new ArrayList<>();

        if (n <= 6) {
            // 单循环
            formatLabel = "单循环赛制";
            List<MatchPlan> plans = roundRobinService.planMatches(teams);
            int t = 1;
            for (MatchPlan p : plans) {
                allMatches.add(buildMatch(unitType, Stage.GROUP, null,
                        "循环赛", t++, p.getTeam1(), p.getTeam2()));
            }

        } else if (n <= 16) {
            // 小组循环 + 淘汰赛占位
            formatLabel = "小组循环 + 淘汰赛";
            int groupSize = 4;
            List<List<Team>> groups = drawGroups(teams, groupSize);
            printGroups(groups);
            int t = 1;
            for (int g = 0; g < groups.size(); g++) {
                char gn = (char) ('A' + g);
                List<Team> grp = groups.get(g);
                List<MatchPlan> plans = roundRobinService.planMatches(grp);
                for (MatchPlan p : plans) {
                    allMatches.add(buildMatch(unitType, Stage.GROUP,
                            String.valueOf(gn), "小组赛", t++,
                            p.getTeam1(), p.getTeam2()));
                }
            }

        } else {
            // 单败淘汰赛
            formatLabel = "单败淘汰赛";
            Collections.shuffle(teams, new Random());
            int t = 1;
            // 补足到 2 的幂次
            int slots = Integer.highestOneBit(n);
            if (slots < n) slots <<= 1;
            List<Team> bracket = new ArrayList<>(teams.subList(0, Math.min(n, slots)));
            for (int i = 0; i < bracket.size(); i += 2) {
                allMatches.add(buildMatch(unitType, Stage.KNOCKOUT, null,
                        "1/" + (slots / 2) + "决赛", t++,
                        bracket.get(i), bracket.get(i + 1)));
            }
        }

        // ★ 调度编排（使用用户指定的球桌数）
        int tableCount = event.getTableCount();
        List<RoundPlan> rounds = scheduleEngine.schedule(allMatches, tableCount);

        // 展平保存
        List<Match> flat = new ArrayList<>();
        for (RoundPlan rp : rounds) flat.addAll(rp.matches());
        matchRepository.saveAll(flat);

        // 缓存最后结果供视图查询
        lastResult = new GenerateResult(formatLabel, n, rounds, tableCount);

        log.info("✅ {} · {} 队 · {} 轮 · {} 场",
                formatLabel, n, rounds.size(), flat.size());
    }

    private Match buildMatch(UnitType ut, Stage stage, String groupName,
                             String roundName, int tableNum, Team t1, Team t2) {
        return Match.builder()
                .unitType(ut).stage(stage).groupName(groupName)
                .roundName(roundName).tableNumber(tableNum)
                .team1(t1).team2(t2)
                .score1(null).score2(null).winner(null)
                .build();
    }

    // ============ 分组 ============

    List<List<Team>> drawGroups(List<Team> teams, int groupSize) {
        List<Team> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled, new Random());
        int total = shuffled.size();
        int groupCount = (int) Math.ceil((double) total / groupSize);
        List<List<Team>> groups = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) groups.add(new ArrayList<>());
        for (int i = 0; i < total; i++) {
            int g = i % groupCount;
            if ((i / groupCount) % 2 == 1) g = groupCount - 1 - g;
            groups.get(g).add(shuffled.get(i));
        }
        return groups;
    }

    private void printGroups(List<List<Team>> groups) {
        log.info("========== 分组结果 ==========");
        for (int i = 0; i < groups.size(); i++) {
            char name = (char) ('A' + i);
            log.info("  {}组: {}", name,
                    groups.get(i).stream().map(Team::getName)
                            .collect(Collectors.joining(", ")));
        }
    }

    // ============ 视图查询 ============

    private volatile GenerateResult lastResult;

    @Transactional(readOnly = true)
    public ScheduleView buildView(UnitType unitType) {
        if (lastResult == null) return ScheduleView.empty();
        List<RoundPlan> rounds = lastResult.rounds();

        // 按组别整理
        Map<String, List<RoundRow>> groupMap = new LinkedHashMap<>();
        for (RoundPlan rp : rounds) {
            for (Match m : rp.matches()) {
                if (m.getStage() != Stage.GROUP) continue;
                String gn = (m.getGroupName() != null ? m.getGroupName() : "?") + "组";
                groupMap.computeIfAbsent(gn, k -> new ArrayList<>())
                        .add(new RoundRow(m.getId(), rp.roundNumber(),
                                m.getTableNumber() != null ? m.getTableNumber() : 0,
                                m.getTeam1().getName(), m.getTeam2().getName()));
            }
        }
        List<GroupBlock> blocks = new ArrayList<>();
        for (var e : groupMap.entrySet())
            blocks.add(new GroupBlock(e.getKey(), e.getValue()));

        return new ScheduleView(
                lastResult.formatLabel(),
                lastResult.teamCount(),
                rounds.size(),
                lastResult.tableCount(),
                blocks,
                rounds.stream().mapToInt(r -> r.matches().size()).sum());
    }

    // ============ DTO ============

    private record GenerateResult(String formatLabel, int teamCount,
                                  List<RoundPlan> rounds, int tableCount) {}

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduleView {
        private String formatLabel;
        private int teamCount;
        private int totalRounds;
        private int tableCount;
        private List<GroupBlock> groups;
        private int totalMatches;
        static ScheduleView empty() {
            return new ScheduleView("", 0, 0, 0, List.of(), 0);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GroupBlock {
        private String groupTitle;
        private List<RoundRow> rows;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RoundRow {
        private long matchId;
        private int roundNumber;
        private int tableNumber;
        private String teamA;
        private String teamB;
    }

    // ============ 持久化辅助 ============

    private List<Team> persistTeams(List<Team> teams) {
        List<Team> result = new ArrayList<>();
        for (Team t : teams) {
            if (t.getId() != null && teamRepository.existsById(t.getId()))
                result.add(t);
            else result.add(teamRepository.save(t));
        }
        return result;
    }
}
