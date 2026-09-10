package com.example.taskboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.taskboard.domain.ProposalSet;
import com.example.taskboard.repository.ProposalSetRepository;
import com.example.taskboard.service.ProposalService;

@Controller
public class ProposalController {

    private final ProposalSetRepository proposalSetRepository;
	
	private final ProposalService proposalService;
	
	public ProposalController(ProposalService proposalService, ProposalSetRepository proposalSetRepository) {
		this.proposalService = proposalService;
		this.proposalSetRepository = proposalSetRepository;
		
	}
	
	// スタートボタンを押したとき。3案を作って、その表示画面へ移る
	@PostMapping("/proposals")
	public String start() {
		ProposalSet set = proposalService.start();
		return "redirect:/proposals/" + set.getId();
		
	}
	
	// 3案の表示
	@GetMapping("/proposals/{setId}")
	public String show(@PathVariable Long setId, Model model) {
		
		model.addAttribute("set", proposalService.findSet(setId));
		model.addAttribute("cards",proposalService.findCards(setId));
		
		return "proposal";
		
	}
	
	// カードを選んだとき。タスクになるので一覧へ移る
	@PostMapping("/proposals/{setId}/choose/{cardId}")
	public String choose(@PathVariable Long setId, @PathVariable Long cardId) {
		proposalService.choose(setId, cardId);
		return "redirect:/tasks";      // ← セミコロンではなくコロン
	}
	
	// 別の話題にするとき。新しいセットを作ってそちらへ移る
		@PostMapping("/proposals/{setId}/retopic")
		public String retopic(@PathVariable Long setId) {
			ProposalSet next = proposalService.retopic(setId);
			return "redirect:/proposals/" + next.getId();
		}
	}
