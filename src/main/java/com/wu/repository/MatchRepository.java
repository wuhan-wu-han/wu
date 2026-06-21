package com.wu.repository;

import com.wu.entity.Match;
import com.wu.enums.Stage;
import com.wu.enums.UnitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    /** 按赛事单元+阶段查询，按组别→轮次→序号排序 */
    List<Match> findByUnitTypeAndStageOrderByGroupNameAscRoundNameAscMatchOrderAsc(
            UnitType unitType, Stage stage);

    /** 按单元+阶段+轮次名查询 */
    List<Match> findByUnitTypeAndStageAndRoundName(UnitType unitType, Stage stage, String roundName);
}
