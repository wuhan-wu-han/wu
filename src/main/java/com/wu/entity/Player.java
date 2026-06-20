package com.wu.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 选手 —— 参与比赛的个体运动员。
 * <p>
 * 一个选手可以同时出现在多个队伍中
 * （例如：同一人既打单打又打双打）。
 */
@Entity
@Table(name = "player")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 选手姓名 */
    @Column(nullable = false, length = 64)
    private String name;
}
