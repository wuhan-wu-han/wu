package com.wu.service;

import com.wu.entity.Match;
import com.wu.entity.Team;
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

/**
 * 赛事编排服务。
 *
 * <h3>阶段一：生成小组赛赛程</h3>
 * <ol>
 *   <li>用户提交名单 → 触发 {@link MatchGenerateEvent}</li>
 *   <li>抽签分组</li>
 *   <li>仅生成小组循环赛对阵（比分字段留空，等待手动录入）</li>
 *   <li>淘汰赛暂不生成 —— 等比分录入后才能确定出线者</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final RoundRobinService roundRobinService;

    // ============ 事件监听入口 ============

    @EventListener
    @Transactional
    public void onMatchGenerate(MatchGenerateEvent event) {
        UnitType unitType = event.getUnitType();
        List<Team> rawTeams = event.getTeams();
        int groupSize = event.getGroupSize();

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  生成小组赛赛程 — {} ({} 队)                        ║", unitType, rawTeams.size());
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("");

        // 0. 持久化队伍
        List<Team> teams = persistTeams(rawTeams);

        // 1. 分组
        List<List<Team>> groups = drawGroups(teams, groupSize);
        printGroups(groups);

        // 2. 生成小组赛对阵（不模拟比分）
        List<Match> allMatches = new ArrayList<>();
        int tableCounter = 1;

        for (int g = 0; g < groups.size(); g++) {
            char groupName = (char) ('A' + g);
            List<Team> group = groups.get(g);
            String roundLabel = groupName + "组";

            List<MatchPlan> plans = roundRobinService.planMatches(group);

            log.info("───── {}组：{} 场比赛 ─────", groupName, plans.size());
            for (int i = 0; i < plans.size(); i++) {
                MatchPlan plan = plans.get(i);
                Match m = Match.builder()
                        .unitType(unitType)
                        .round(roundLabel)
                        .matchOrder(i)
                        .tableNumber(tableCounter++)
                        .team1(plan.getTeam1())
                        .team2(plan.getTeam2())
                        .score1(null)       // 留空，等待录入
                        .score2(null)
                        .winner(null)
                        .build();
                allMatches.add(m);
                log.info("  球桌{}  {} vs {}",
                        m.getTableNumber(),
                        plan.getTeam1().getName(),
                        plan.getTeam2().getName());
            }
        }

        // 3. 批量持久化
        matchRepository.saveAll(allMatches);
        log.info("");
        log.info("✅ 小组赛 {} 场对阵已生成（比分留空，等待录入）", allMatches.size());
        log.info("⚠ 淘汰赛暂未生成 —— 请先录入比分后再确定出线队伍");
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
            List<String> teamNames = groups.get(i).stream()
                    .map(Team::getName).collect(Collectors.toList());
            log.info("  {}组: {}", name, String.join(", ", teamNames));
        }
    }

    // ============ 视图模型 & 查询 ============

    /**
     * 从 DB 查询指定赛事单元的赛程表，供页面展示。
     */
    @Transactional(readOnly = true)
    public ScheduleView buildView(UnitType unitType) {
        List<Match> matches = matchRepository
                .findByUnitTypeOrderByRoundAscMatchOrderAsc(unitType);

        if (matches.isEmpty()) {
            return ScheduleView.empty();
        }

        // 只取小组赛（round 以 "组" 结尾）
        List<Match> groupMatches = matches.stream()
                .filter(m -> m.getRound().endsWith("组"))
                .collect(Collectors.toList());

        // 按组别分组
        Map<String, List<ScheduleRow>> byGroup = new LinkedHashMap<>();
        for (Match m : groupMatches) {
            String groupName = m.getRound();
            byGroup.computeIfAbsent(groupName, k -> new ArrayList<>())
                   .add(new ScheduleRow(
                       groupName,
                       m.getTableNumber(),
                       m.getTeam1().getName(),
                       m.getTeam2().getName()
                   ));
        }

        // 保持组别顺序
        List<String> ordered = new ArrayList<>(byGroup.keySet());
        ordered.sort(Comparator.naturalOrder());

        List<ScheduleRow> allRows = new ArrayList<>();
        for (String g : ordered) {
            allRows.addAll(byGroup.get(g));
        }

        return new ScheduleView(allRows, allRows.size());
    }

    // ============ 视图 DTO ============

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduleView {
        private List<ScheduleRow> rows;
        private int totalMatches;

        static ScheduleView empty() {
            return new ScheduleView(List.of(), 0);
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ScheduleRow {
        private String groupName;     // "A组"
        private Integer tableNumber;  // 球桌号
        private String teamA;         // 队伍A 名称
        private String teamB;         // 队伍B 名称
    }

    // ============ 持久化辅助 ============

    private List<Team> persistTeams(List<Team> teams) {
        List<Team> result = new ArrayList<>();
        for (Team t : teams) {
            if (t.getId() != null && teamRepository.existsById(t.getId())) {
                result.add(t);
            } else {
                result.add(teamRepository.save(t));
            }
        }
        return result;
    }
}
