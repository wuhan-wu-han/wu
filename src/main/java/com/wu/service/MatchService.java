package com.wu.service;

import com.wu.entity.Match;
import com.wu.entity.Team;
import com.wu.enums.UnitType;
import com.wu.event.MatchGenerateEvent;
import com.wu.repository.MatchRepository;
import com.wu.repository.TeamRepository;
import com.wu.service.KnockoutService.KnockoutMatch;
import com.wu.service.KnockoutService.KnockoutResult;
import com.wu.service.RoundRobinService.MatchResult;
import com.wu.service.RoundRobinService.RoundRobinResult;
import com.wu.service.RoundRobinService.TeamStanding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 赛事编排服务 —— 监听 {@link MatchGenerateEvent}，串联完整流程。
 *
 * <h3>执行顺序</h3>
 * <ol>
 *   <li>分组 —— 随机抽签，将队伍分入若干小组</li>
 *   <li>循环赛 —— 组内 Round-Robin，生成积分表</li>
 *   <li>取前 N —— 每组前 N 名晋级淘汰赛</li>
 *   <li>淘汰赛 —— 蛇形交叉落位，Queue 模拟至冠军</li>
 *   <li>持久化 —— 所有对阵批量写入数据库</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final RoundRobinService roundRobinService;
    private final KnockoutService knockoutService;

    // ============ 事件监听入口 ============

    /**
     * 当用户提交参赛名单后触发，执行完整赛事流程。
     */
    @EventListener
    @Transactional
    public void onMatchGenerate(MatchGenerateEvent event) {
        UnitType unitType = event.getUnitType();
        List<Team> rawTeams = event.getTeams();
        int groupSize = event.getGroupSize();
        int advancePerGroup = event.getAdvancePerGroup();

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  赛事生成开始 — {} ({} 队)                         ║", unitType, rawTeams.size());
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("");

        // 0. 确保队伍已持久化（有 ID 才能被 Match 引用）
        List<Team> teams = persistTeams(rawTeams);

        // 1. 分组
        List<List<Team>> groups = drawGroups(teams, groupSize);
        printGroups(groups);

        // 2. 小组循环赛
        List<Team> qualified = new ArrayList<>();
        List<Match> allMatches = new ArrayList<>();

        for (int g = 0; g < groups.size(); g++) {
            char groupName = (char) ('A' + g);
            List<Team> group = groups.get(g);

            log.info("───── {}组 循环赛 ─────", groupName);
            RoundRobinResult rr = roundRobinService.run(group);

            // 转换为 Match 实体
            for (int i = 0; i < rr.getMatches().size(); i++) {
                MatchResult mr = rr.getMatches().get(i);
                Match m = toMatchEntity(mr, unitType, groupName + "组", i);
                allMatches.add(m);
            }

            // 取前 N 晋级
            List<TeamStanding> standings = rr.getSortedStandings();
            for (int i = 0; i < Math.min(advancePerGroup, standings.size()); i++) {
                TeamStanding ts = standings.get(i);
                qualified.add(ts.getTeam());
                log.info("  晋级：{} (积分:{}, 净胜局:{})",
                        ts.getTeam().getName(), ts.getPoints(), ts.getNetGames());
            }
        }

        // 3. 淘汰赛
        log.info("");
        log.info("───── 淘汰赛 ({} 队晋级) ─────", qualified.size());
        KnockoutResult ko = knockoutService.run(qualified);

        // 转换为 Match 实体
        for (int i = 0; i < ko.getAllMatches().size(); i++) {
            KnockoutMatch km = ko.getAllMatches().get(i);
            Match m = toMatchEntity(km, unitType, km.getRoundName(), i);
            allMatches.add(m);
        }

        // 4. 批量持久化
        matchRepository.saveAll(allMatches);
        log.info("");
        log.info("✅ 全部 {} 场对阵已存入数据库 (小组赛 + 淘汰赛)", allMatches.size());
        log.info("🏆 冠军：{}", ko.getChampion().getName());
    }

    // ============ 分组 ============

    /**
     * 随机抽签，将队伍尽量均匀分入小组。
     * 每组约 {@code groupSize} 队，共 ceil(n/groupSize) 组。
     */
    List<List<Team>> drawGroups(List<Team> teams, int groupSize) {
        List<Team> shuffled = new ArrayList<>(teams);
        Collections.shuffle(shuffled, new Random());

        int total = shuffled.size();
        int groupCount = (int) Math.ceil((double) total / groupSize);

        List<List<Team>> groups = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) {
            groups.add(new ArrayList<>());
        }

        // 蛇形分配，减少组间实力偏差
        for (int i = 0; i < total; i++) {
            int g = i % groupCount;
            if ((i / groupCount) % 2 == 1) {
                g = groupCount - 1 - g; // 偶数轮反向
            }
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

    // ============ 实体转换 ============

    private Match toMatchEntity(MatchResult mr, UnitType unitType, String round, int order) {
        return Match.builder()
                .unitType(unitType)
                .round(round)
                .matchOrder(order)
                .team1(mr.getTeam1())
                .team2(mr.getTeam2())
                .score1(mr.getTeam1Score())
                .score2(mr.getTeam2Score())
                .winner(mr.getWinner())
                .build();
    }

    private Match toMatchEntity(KnockoutMatch km, UnitType unitType, String round, int order) {
        return Match.builder()
                .unitType(unitType)
                .round(round)
                .matchOrder(order)
                .team1(km.getTeam1())
                .team2(km.getTeam2())
                .score1(km.getScore1())
                .score2(km.getScore2())
                .winner(km.getWinner())
                .build();
    }

    // ============ 持久化辅助 ============

    /**
     * 批量保存队伍，确保返回的实体有 ID。
     * 如果已经持久化（有 ID 且 DB 中存在），则跳过。
     */
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
