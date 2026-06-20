package com.wu;

import com.wu.entity.Team;
import com.wu.service.RoundRobinService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环赛验证 Demo —— 纯内存模拟，不写数据库。
 * <p>
 * 创建 4 支队伍跑循环赛，观察控制台输出的对阵表和排名。
 */
@Component
@Order(2)
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("demo")
public class RoundRobinDemo implements CommandLineRunner {

    private final RoundRobinService roundRobinService;

    @Override
    public void run(String... args) {
        // 4 支模拟队伍（不存库，纯内存对象）
        List<Team> teams = new ArrayList<>();
        teams.add(mockTeam("赵云"));
        teams.add(mockTeam("张飞"));
        teams.add(mockTeam("马超"));
        teams.add(mockTeam("黄忠"));

        roundRobinService.run(teams);
    }

    private Team mockTeam(String name) {
        Team t = new Team();
        t.setId((long) name.hashCode());  // 随便给个 id 以便 equals 比较
        t.setName(name);
        return t;
    }
}
