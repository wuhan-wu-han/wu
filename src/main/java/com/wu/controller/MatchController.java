package com.wu.controller;

import com.wu.entity.Team;
import com.wu.enums.UnitType;
import com.wu.event.MatchGenerateEvent;
import com.wu.entity.Match;
import com.wu.repository.MatchRepository;
import com.wu.service.MatchService;
import com.wu.service.MatchService.ScheduleView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 赛事页面控制器。
 * <p>
 * GET  /              → 表单页（选择性别+项目+输入选手名）
 * POST /generate      → 生成赛事，重定向到结果页
 * GET  /results       → 结果页（小组积分榜 Tab + 淘汰赛对阵图 Tab）
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MatchController {

    private final ApplicationEventPublisher eventPublisher;
    private final MatchService matchService;
    private final MatchRepository matchRepository;

    // ============ 表单页 ============

    @GetMapping("/")
    public String form(Model model,
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String selectedGender,
                       @RequestParam(required = false) String selectedEvent,
                       @RequestParam(required = false) String lastNames) {
        model.addAttribute("genders", Arrays.asList("男", "女"));
        model.addAttribute("events", Arrays.asList("单打", "双打"));
        model.addAttribute("selectedGender", selectedGender != null ? selectedGender : "男");
        model.addAttribute("selectedEvent",  selectedEvent  != null ? selectedEvent  : "单打");
        model.addAttribute("lastNames",      lastNames      != null ? lastNames      : "");
        model.addAttribute("error", error);
        return "index";
    }

    // ============ 生成赛事 ============

    @PostMapping("/generate")
    public String generate(@RequestParam String gender,
                           @RequestParam String event,
                           @RequestParam String names,
                           @RequestParam(defaultValue = "8") int tableCount,
                           RedirectAttributes redirectAttrs) {
        // 确定 UnitType
        UnitType unitType;
        if ("男".equals(gender) && "单打".equals(event)) {
            unitType = UnitType.MEN_SINGLE;
        } else if ("女".equals(gender) && "单打".equals(event)) {
            unitType = UnitType.WOMEN_SINGLE;
        } else if ("男".equals(gender) && "双打".equals(event)) {
            unitType = UnitType.MEN_DOUBLE;
        } else if ("女".equals(gender) && "双打".equals(event)) {
            unitType = UnitType.WOMEN_DOUBLE;
        } else {
            unitType = UnitType.MEN_SINGLE;
        }

        // ★ 控制台日志：方便验证是否取到了新数据
        System.out.println("当前输入：" + names);

        // 解析选手名 → 创建 Team（单打每队1人，双打每队2人）
        List<String> nameList = Arrays.stream(names.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        // 双打：每行一组，空格分隔两人；单打：每行一人
        List<Team> teams = new ArrayList<>();
        if ("双打".equals(event)) {
            for (String line : nameList) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    Team t = new Team();
                    t.setName(parts[0] + "/" + parts[1]);
                    t.setPlayers(new ArrayList<>());
                    teams.add(t);
                }
            }
        } else {
            for (String name : nameList) {
                Team t = new Team();
                t.setName(name);
                t.setPlayers(new ArrayList<>());
                teams.add(t);
            }
        }

        if (teams.size() < 2) {
            return redirectWithError(redirectAttrs, gender, event, names,
                    "请至少输入2支队伍（双打每行两人空格分隔）");
        }

        if (teams.size() < 4) {
            return redirectWithError(redirectAttrs, gender, event, names,
                    "请至少输入4名选手（双打至少4队8人）才能分组");
        }

        log.info("收到表单提交：gender={}, event={}, unitType={}, teams={}",
                gender, event, unitType, teams.size());

        // 发布事件 → MatchService.onMatchGenerate() 同步执行全流程
        eventPublisher.publishEvent(
                new MatchGenerateEvent(this, unitType, teams, 4, 2, tableCount));

        redirectAttrs.addAttribute("unitType", unitType.name());
        return "redirect:/results";
    }

    // ============ 结果页 ============

    @GetMapping("/results")
    public String results(@RequestParam String unitType, Model model) {
        UnitType ut;
        try {
            ut = UnitType.valueOf(unitType);
        } catch (IllegalArgumentException e) {
            ut = UnitType.MEN_SINGLE;
        }

        ScheduleView view = matchService.buildView(ut);
        model.addAttribute("view", view);

        // 翻译 unitType 为中文
        String label = switch (ut) {
            case MEN_SINGLE   -> "男子单打";
            case WOMEN_SINGLE -> "女子单打";
            case MEN_DOUBLE   -> "男子双打";
            case WOMEN_DOUBLE -> "女子双打";
        };
        model.addAttribute("unitLabel", label);

        return "results";
    }

    // ============ 保存桌号 API ============

    @PostMapping("/api/update-table-numbers")
    @ResponseBody
    public ResponseEntity<?> updateTableNumbers(@RequestBody List<TableUpdate> updates) {
        List<Match> toSave = new ArrayList<>();
        for (TableUpdate u : updates) {
            Match m = matchRepository.findById(u.matchId).orElse(null);
            if (m != null && u.tableNumber >= 1) {
                m.setTableNumber(u.tableNumber);
                toSave.add(m);
            }
        }
        matchRepository.saveAll(toSave);
        return ResponseEntity.ok(Map.of("status", "ok", "updated", toSave.size()));
    }

    public record TableUpdate(long matchId, int tableNumber) {}

    /** 验证失败时通过 URL 参数回传 */
    private String redirectWithError(RedirectAttributes attrs, String gender,
                                     String event, String names, String message) {
        attrs.addAttribute("error", message);
        attrs.addAttribute("selectedGender", gender);
        attrs.addAttribute("selectedEvent", event);
        attrs.addAttribute("lastNames", names);
        return "redirect:/";
    }
}
