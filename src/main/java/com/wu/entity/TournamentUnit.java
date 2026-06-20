package com.wu.entity;

import com.wu.enums.UnitType;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 赛事单元 —— 一次赛事中的某个独立比赛类别。
 * <p>
 * 例如 "男子单打" 是一个单元，"女子双打" 是另一个单元。
 * 每个单元下挂载一组参赛队伍。
 */
@Entity
@Table(name = "tournament_unit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "teamList")
public class TournamentUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 单元类型：男单 / 女单 / 男双 / 女双 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType unitType;

    /**
     * 该单元下的参赛队伍列表。
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "tournament_unit_team",
        joinColumns        = @JoinColumn(name = "unit_id"),
        inverseJoinColumns = @JoinColumn(name = "team_id")
    )
    @Builder.Default
    private List<Team> teamList = new ArrayList<>();
}
