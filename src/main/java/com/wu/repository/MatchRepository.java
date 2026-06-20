package com.wu.repository;

import com.wu.entity.Match;
import com.wu.enums.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    /** 按赛事单元查询所有对阵 */
    List<Match> findByUnitTypeOrderByRoundAscMatchOrderAsc(UnitType unitType);

    /** 按赛事单元和轮次查询 */
    List<Match> findByUnitTypeAndRound(UnitType unitType, String round);
}
