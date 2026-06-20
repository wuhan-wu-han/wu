package com.wu.entity;

import com.wu.enums.UnitType;
import jakarta.persistence.*;
import lombok.*;

/**
 * 比赛对阵记录 —— 涵盖小组赛和淘汰赛所有场次。
 * <p>
 * {@code round} 字段区分比赛阶段：
 * <ul>
 *   <li>小组赛：{@code "A组"} / {@code "B组"} ...</li>
 *   <li>淘汰赛：{@code "1/4决赛"} / {@code "半决赛"} / {@code "决赛"}</li>
 * </ul>
 */
@Entity
@Table(name = "match_result")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"team1", "team2", "winner"})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属赛事单元（男单/女单/男双/女双） */
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20)
    private UnitType unitType;

    /** 阶段：小组名（"A组"）或淘汰赛轮次名（"1/4决赛"） */
    @Column(nullable = false, length = 32)
    private String round;

    /** 同轮次内的序号（0-based） */
    @Column(name = "match_order")
    private Integer matchOrder;

    /** 球桌号（自动分配 1~N） */
    @Column(name = "table_number")
    private Integer tableNumber;

    // ---- 对阵双方 ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id", nullable = false)
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id", nullable = false)
    private Team team2;

    @Column(name = "score1")
    private Integer score1;

    @Column(name = "score2")
    private Integer score2;

    // ---- 结果 ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;
}
