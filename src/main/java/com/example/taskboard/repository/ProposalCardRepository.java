package com.example.taskboard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskboard.domain.ProposalCard;

public interface ProposalCardRepository extends JpaRepository<ProposalCard, Long>{
	
	// 1セット分のカードを、1→2→3 の順で取り出す
	List<ProposalCard>findByProposalSetIdOrderByPositionAsc(Long proposalSetId);

}
