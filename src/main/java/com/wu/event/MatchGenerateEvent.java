package com.wu.event;

import com.wu.entity.Team;
import com.wu.enums.UnitType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 赛事生成事件 —— 当用户提交参赛名单后触发。
 * <p>
 * 由 {@code MatchService.onMatchGenerate()} 监听，
 * 执行 分组 → 循环赛 → 淘汰赛 全流程。
 */
@Getter
public class MatchGenerateEvent extends ApplicationEvent {

    /** 赛事单元类型 */
    private final UnitType unitType;

    /** 参赛队伍列表（单打=1人队，双打=2人队） */
    private final List<Team> teams;

    /** 每组队伍数（默认 4） */
    private final int groupSize;

    /** 每组晋级人数（默认 2） */
    private final int advancePerGroup;

    /** 可用球桌数 */
    private final int tableCount;

    public MatchGenerateEvent(Object source, UnitType unitType, List<Team> teams) {
        this(source, unitType, teams, 4, 2, 8);
    }

    public MatchGenerateEvent(Object source, UnitType unitType, List<Team> teams,
                              int groupSize, int advancePerGroup, int tableCount) {
        super(source);
        this.unitType = unitType;
        this.teams = teams;
        this.groupSize = groupSize;
        this.advancePerGroup = advancePerGroup;
        this.tableCount = tableCount;
    }
}
