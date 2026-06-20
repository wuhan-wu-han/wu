package com.wu.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 队伍 —— 承载 1 名或 2 名选手的参赛单元。
 * <p>
 * 规则：
 * <ul>
 *   <li>单打：队伍包含 1 名选手，队名即为选手姓名。</li>
 *   <li>双打：队伍包含 2 名选手，队名形如 "张飞/关羽"。</li>
 * </ul>
 */
@Entity
@Table(name = "team")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"players", "tournamentUnit"})
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 队名：单打=选手名；双打=组合名 */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 队伍包含的选手列表。
     * 单打时 size=1，双打时 size=2。
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "team_player",
        joinColumns        = @JoinColumn(name = "team_id"),
        inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    /** 所属赛事单元 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private TournamentUnit tournamentUnit;
}
