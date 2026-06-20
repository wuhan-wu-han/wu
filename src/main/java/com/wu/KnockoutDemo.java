package com.wu;

import com.wu.entity.Team;
import com.wu.service.KnockoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 淘汰赛 Demo —— 模拟 4 个小组（A/B/C/D）各出线 2 队，共 8 队蛇形交叉打淘汰赛。
 */
@Component
@Order(3)
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("demo")
public class KnockoutDemo implements CommandLineRunner {

    private final KnockoutService knockoutService;

    @Override
    public void run(String... args) {
        // 模拟晋级名单：[A1, A2, B1, B2, C1, C2, D1, D2]
        List<Team> qualified = new ArrayList<>();
        qualified.add(mock("A1-赵云"));
        qualified.add(mock("A2-关羽"));
        qualified.add(mock("B1-张飞"));
        qualified.add(mock("B2-马超"));
        qualified.add(mock("C1-黄忠"));
        qualified.add(mock("C2-许褚"));
        qualified.add(mock("D1-典韦"));
        qualified.add(mock("D2-甘宁"));

        knockoutService.run(qualified); // 返回 KnockoutResult，Demo 只验证控制台输出
    }

    private Team mock(String name) {
        Team t = new Team();
        t.setId((long) name.hashCode());
        t.setName(name);
        return t;
    }
}
