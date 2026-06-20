package com.wu;

import com.wu.entity.Team;
import com.wu.enums.UnitType;
import com.wu.event.MatchGenerateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 完整流程 Demo —— 模拟用户提交 16 人男单名单，
 * 触发 分组 → 循环赛 → 淘汰赛 → 持久化 全流程。
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class MatchEventDemo implements CommandLineRunner {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void run(String... args) {
        // 16 名选手 → 16 支单人队伍
        List<Team> teams = new ArrayList<>();
        String[] names = {
            "赵云", "关羽", "张飞", "马超", "黄忠", "许褚", "典韦", "甘宁",
            "吕布", "张辽", "太史慈", "夏侯惇", "周瑜", "陆逊", "孙策", "孙权"
        };
        for (int i = 0; i < names.length; i++) {
            Team t = new Team();
            t.setName(names[i]);
            t.setPlayers(new ArrayList<>()); // 单打空列表
            teams.add(t);
        }

        // 发布事件 → MatchService.onMatchGenerate() 自动响应
        eventPublisher.publishEvent(new MatchGenerateEvent(this, UnitType.MEN_SINGLE, teams));
    }
}
