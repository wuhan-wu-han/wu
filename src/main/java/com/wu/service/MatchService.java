package com.wu.service;

import com.wu.entity.Match;
import com.wu.entity.Team;
import com.wu.enums.Stage;
import com.wu.enums.UnitType;
import com.wu.event.MatchGenerateEvent;
import com.wu.repository.MatchRepository;
import com.wu.repository.TeamRepository;
import com.wu.service.RoundRobinService.MatchPlan;
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

    // ============ 事件监听 ============

    @EventListener
    @Transactional
    public void onMatchGenerate(MatchGenerateEvent event) {
        UnitType unitType = event.getUnitType();
        List<Team> rawTeams = event.getTeams();
        int groupSize = event.getGroupSize();

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  生成小组赛赛程 — {} ({} 队)                        ║", unitType, rawTeams.size());
        log.info("╚══════════════════════════════════════════════════════════╝");

        List<Team> teams = persistTeams(rawTeams);
        List<List<Team>> groups = drawGroups(teams, groupSize);
        printGroups(groups);

        List<Match> allMatches = new ArrayList<>();
        int tableCounter = 1;

        for (int g = 0; g < groups.size(); g++) {
            char gName = (char) ('A' + g);
            List<Team> group = groups.get(g);

            List<MatchPlan> plans = roundRobinService.planMatches(group);
            int roundNum = 1;
            for (int i = 0; i < plans.size(); i++) {
                // 每 (groupSize/2) 场为一轮
                if (i > 0 && i % (group.size() / 2) == 0) roundNum++;

                MatchPlan plan = plans.get(i);
                Match m = Match.builder()
                        .unitType(unitType)
                        .stage(Stage.GROUP)
                        .groupName(String.valueOf(gName))
                        .roundName("第" + roundNum + "轮")
                        .matchOrder(i)
                        .tableNumber(tableCounter++)
                        .team1(plan.getTeam1())
                        .team2(plan.getTeam2())
                        .score1(null)
                        .score2(null)
                        .winner(null)
                        .build();
                allMatches.add(m);
                log.info("  {}组 {}  球桌{}  {} vs {}",
                        gName, m.getRoundName(), m.getTableNumber(),
                        plan.getTeam1().getName(), plan.getTeam2().getName());
            }
        }

        matchRepository.saveAll(allMatches);
        log.info("✅ 小组赛 {} 场对阵已生成（比分留空）", allMatches.size());
    }

    // ============ 分组（不变） ============

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
                    groups.get(i).stream().map(Team::getName).collect(Collectors.joining(", ")));
        }
    }

    // ============ 视图查询 ============

    @Transactional(readOnly = true)
    public ScheduleView buildView(UnitType unitType) {
        List<Match> all = matchRepository
                .findByUnitTypeAndStageOrderByGroupNameAscRoundNameAscMatchOrderAsc(unitType, Stage.GROUP);

        if (all.isEmpty()) return ScheduleView.empty();

        // 按组别-轮次分组
        Map<String, Map<String, List<ScheduleRow>>> grouped = new LinkedHashMap<>();
        for (Match m : all) {
            String g = m.getGroupName() + "组";
            String r = m.getRoundName();
            grouped.computeIfAbsent(g, k -> new LinkedHashMap<>())
                   .computeIfAbsent(r, k -> new ArrayList<>())
                   .add(new ScheduleRow(m.getTableNumber(), m.getTeam1().getName(), m.getTeam2().getName()));
        }

        // 展平为有序列表
        List<GroupBlock> blocks = new ArrayList<>();
        for (var gEntry : grouped.entrySet()) {
            List<RoundBlock> rounds = new ArrayList<>();
            for (var rEntry : gEntry.getValue().entrySet()) {
                rounds.add(new RoundBlock(rEntry.getKey(), rEntry.getValue()));
            }
            blocks.add(new GroupBlock(gEntry.getKey(), rounds));
        }

        return new ScheduleView(blocks, all.size());
    }

    // ============ 视图 DTO ============

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduleView {
        private List<GroupBlock> groups;
        private int totalMatches;
        static ScheduleView empty() { return new ScheduleView(List.of(), 0); }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GroupBlock {
        private String groupTitle;          // "A组"
        private List<RoundBlock> rounds;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RoundBlock {
        private String roundLabel;          // "第1轮"
        private List<ScheduleRow> rows;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduleRow {
        private Integer tableNumber;
        private String teamA;
        private String teamB;
    }

    // ============ 持久化辅助 ============

    private List<Team> persistTeams(List<Team> teams) {
        List<Team> result = new ArrayList<>();
        for (Team t : teams) {
            if (t.getId() != null && teamRepository.existsById(t.getId()))
                result.add(t);
            else
                result.add(teamRepository.save(t));
        }
        return result;
    }
}
