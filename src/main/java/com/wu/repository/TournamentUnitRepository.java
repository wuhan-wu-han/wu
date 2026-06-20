package com.wu.repository;

import com.wu.entity.TournamentUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TournamentUnitRepository extends JpaRepository<TournamentUnit, Long> {
}
