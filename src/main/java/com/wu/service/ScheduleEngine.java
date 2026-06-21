package com.wu.service;

import com.wu.entity.Match;
import com.wu.entity.Team;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能排程引擎。
 *
 * <h3>约束</h3>
 * <ol>
 *   <li>每轮最多 {@code maxTables} 场</li>
 *   <li>球桌号轮内唯一</li>
 *   <li><b>体力规则</b>：同一队伍不在相邻轮次连续出场</li>
 *   <li>小组赛：不同组交替编排 + 组内选手轮换</li>
 * </ol>
 */
@Slf4j
@Component
public class ScheduleEngine {

    @Value("${schedule.max-tables:8}")
    private int defaultMaxTables;

    /** 使用用户指定的球桌数编排 */
    public List<RoundPlan> schedule(List<Match> rawMatches, int tableCount) {
        return doSchedule(rawMatches, tableCount);
    }

    /** 使用默认球桌数编排 */
    public List<RoundPlan> schedule(List<Match> rawMatches) {
        return doSchedule(rawMatches, defaultMaxTables);
    }

    private List<RoundPlan> doSchedule(List<Match> rawMatches, int maxTables) {
        // 按组别分组
        Map<String, LinkedList<Match>> byGroup = new LinkedHashMap<>();
        for (Match m : rawMatches) {
            String g = m.getGroupName() != null ? m.getGroupName() : "_";
            byGroup.computeIfAbsent(g, k -> new LinkedList<>()).add(m);
        }

        List<RoundPlan> rounds = new ArrayList<>();
        Set<Team> lastRoundTeams = new HashSet<>();
        int roundNum = 1;

        while (byGroup.values().stream().anyMatch(l -> !l.isEmpty())) {
            List<Match> thisRound = new ArrayList<>();
            Set<Team> thisRoundTeams = new HashSet<>();
            int tableIdx = 1;

            List<String> groupKeys = new ArrayList<>(byGroup.keySet());
            for (String gk : groupKeys) {
                if (thisRound.size() >= maxTables) break;
                LinkedList<Match> pending = byGroup.get(gk);
                if (pending.isEmpty()) continue;

                // ★ 组内也找双方都不在上一轮出场的
                Match picked = null;
                for (Match m : pending) {
                    if (!lastRoundTeams.contains(m.getTeam1())
                            && !lastRoundTeams.contains(m.getTeam2())) {
                        picked = m;
                        break;
                    }
                }
                if (picked == null) continue; // 该组本轮无可排

                pending.remove(picked);
                picked.setRoundNumber(roundNum);
                picked.setTableNumber(tableIdx++);
                thisRound.add(picked);
                thisRoundTeams.add(picked.getTeam1());
                thisRoundTeams.add(picked.getTeam2());
            }

            // 死锁兜底
            if (thisRound.isEmpty()) {
                for (String gk : groupKeys) {
                    if (thisRound.size() >= maxTables) break;
                    LinkedList<Match> pending = byGroup.get(gk);
                    if (pending.isEmpty()) continue;
                    Match m = pending.removeFirst();
                    m.setRoundNumber(roundNum);
                    m.setTableNumber(tableIdx++);
                    thisRound.add(m);
                    thisRoundTeams.add(m.getTeam1());
                    thisRoundTeams.add(m.getTeam2());
                }
            }

            rounds.add(new RoundPlan(roundNum, new ArrayList<>(thisRound)));
            lastRoundTeams = thisRoundTeams;
            roundNum++;
        }

        return rounds;
    }

    public record RoundPlan(int roundNumber, List<Match> matches) {}
}
