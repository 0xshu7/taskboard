package com.example.taskboard.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.domain.ProposalStatus;

public interface ProposalSetRepository extends JpaRepository<ProposalSet, Long> {

	// まだ使われていない（OPEN のままの）セットのうち、一番新しいものを1件
	// findFirst で「最初の1件だけ」という意味になる
	Optional<ProposalSet> findFirstByUserIdAndStatusOrderByIdDesc(
			Long userId, ProposalStatus status);
}