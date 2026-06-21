package com.wu.entity;

import com.wu.enums.Stage;
import com.wu.enums.UnitType;
import jakarta.persistence.*;
import lombok.*;

/**
 * 比赛对阵记录。
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

    /** 所属赛事单元 */
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20)
    private UnitType unitType;

    /** 阶段：GROUP 或 KNOCKOUT */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Stage stage;

    /** 组别标识（仅小组赛）："A" / "B" / ... */
    @Column(length = 4)
    private String groupName;

    /** 轮次显示名："第1轮" / "1/4决赛" / "半决赛" / "决赛" */
    @Column(name = "round_name", nullable = false, length = 32)
    private String roundName;

    /** 同轮次内的序号 */
    @Column(name = "match_order")
    private Integer matchOrder;

    /** 球桌号 */
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
