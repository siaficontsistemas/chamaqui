package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.LegalDocumentType;
import com.helpdesk.helpdesk.dto.legal.PublicLegalDocumentResponse;
import com.helpdesk.helpdesk.dto.legal.PublicLegalDocumentSectionResponse;

@Service
public class LegalDocumentService {

	private final Map<LegalDocumentType, PublicLegalDocumentResponse> documentsByType;

	public LegalDocumentService() {
		Map<LegalDocumentType, PublicLegalDocumentResponse> documents = new EnumMap<>(LegalDocumentType.class);
		documents.put(LegalDocumentType.TERMS_OF_USE, buildTermsOfUse());
		documents.put(LegalDocumentType.PRIVACY_POLICY, buildPrivacyPolicy());
		this.documentsByType = Map.copyOf(documents);
	}

	public List<PublicLegalDocumentResponse> listPublicDocuments() {
		return List.copyOf(documentsByType.values());
	}

	public PublicLegalDocumentResponse getPublicDocument(LegalDocumentType documentType) {
		PublicLegalDocumentResponse document = documentsByType.get(documentType);
		if (document == null) {
			throw new NotFoundException("Documento legal não encontrado.");
		}
		return document;
	}

	public String getCurrentVersion(LegalDocumentType documentType) {
		return getPublicDocument(documentType).version();
	}

	private PublicLegalDocumentResponse buildTermsOfUse() {
		return new PublicLegalDocumentResponse(
			LegalDocumentType.TERMS_OF_USE.name(),
			"Termo de Uso do ChamAqui Helpdesk",
			"2026-06",
			OffsetDateTime.parse("2026-06-17T00:00:00Z"),
			"Regras de acesso, uso adequado, seguranca da conta e responsabilidades de quem utiliza a plataforma.",
			List.of(
				new PublicLegalDocumentSectionResponse(
					"1. Objeto",
					List.of(
						"O ChamAqui Helpdesk e uma plataforma para abertura, triagem, acompanhamento e resposta de chamados entre empresas clientes, administradores, funcionarios e canais integrados como WhatsApp."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"2. Cadastro e credenciais",
					List.of(
						"A pessoa usuaria deve fornecer dados verdadeiros, atualizados e de sua titularidade.",
						"O acesso e pessoal, e a guarda da senha e das demais credenciais e de responsabilidade da propria pessoa usuaria."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"3. Uso permitido",
					List.of(
						"O uso da plataforma deve se limitar a atividades de atendimento, suporte, organizacao operacional e relacionamento entre as empresas e os usuarios vinculados ao ambiente.",
						"E proibido utilizar o sistema para fraude, acesso indevido, envio de conteudo ilicito, ofensivo, discriminatorio ou que comprometa a seguranca do servico."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"4. Perfis e limites de acesso",
					List.of(
						"Cada perfil possui permissoes especificas de acordo com sua funcao operacional.",
						"A plataforma pode restringir, suspender ou revisar acessos quando identificar uso indevido, risco de seguranca ou descumprimento destas regras."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"5. Disponibilidade e manutencao",
					List.of(
						"O servico pode passar por indisponibilidades temporarias, atualizacoes ou manutencoes necessarias para seguranca, continuidade operacional ou evolucao da plataforma."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"6. Tratamento de dados e privacidade",
					List.of(
						"O tratamento de dados pessoais necessario ao funcionamento do sistema segue a Politica de Privacidade publicada separadamente.",
						"Ao utilizar a plataforma, a pessoa usuaria declara ciencia de que seus dados poderao ser tratados para autenticacao, seguranca, atendimento, historico operacional e cumprimento de obrigacoes legais."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"7. Aceite",
					List.of(
						"O aceite deste Termo de Uso e registrado eletronicamente e pode ser exigido novamente quando houver nova versao aplicavel."
					)
				)
			)
		);
	}

	private PublicLegalDocumentResponse buildPrivacyPolicy() {
		return new PublicLegalDocumentResponse(
			LegalDocumentType.PRIVACY_POLICY.name(),
			"Politica de Privacidade do ChamAqui Helpdesk",
			"2026-06",
			OffsetDateTime.parse("2026-06-17T00:00:00Z"),
			"Descricao das categorias de dados pessoais tratadas, finalidades, bases legais, compartilhamentos, prazos de retencao e direitos do titular.",
			List.of(
				new PublicLegalDocumentSectionResponse(
					"1. Dados tratados",
					List.of(
						"A plataforma pode tratar nome, email, CPF, telefone, empresa vinculada, mensagens trocadas no atendimento, dados operacionais do chamado, identificadores de sessao, anexos e metadados de integracao com WhatsApp."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"2. Finalidades e bases legais",
					List.of(
						"Os dados sao tratados para autenticacao e seguranca da conta, execucao do atendimento contratado, organizacao do fluxo operacional entre empresas, registro de historico, resposta a solicitacoes e cumprimento de obrigacoes legais e regulatorias.",
						"As bases legais podem incluir execucao de contrato, exercicio regular de direitos, cumprimento de obrigacao legal, protecao do credito, interesses legitimos relacionados a seguranca e operacao do servico e consentimento quando aplicavel."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"3. Compartilhamentos",
					List.of(
						"Os dados podem ser compartilhados entre empresas vinculadas ao chamado, administradores e funcionarios autorizados no mesmo tenant, provedores de infraestrutura, servicos de email e integracoes necessarias ao atendimento, sempre nos limites da finalidade declarada."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"4. Retencao",
					List.of(
						"Os dados cadastrais, historicos de chamados, mensagens, anexos, registros de aceite e trilhas de auditoria sao mantidos pelo prazo necessario para atendimento, seguranca, demonstracao de historico operacional e cumprimento de obrigacoes legais ou regulatórias.",
						"Quando cabivel, dados podem ser excluidos ou anonimizados ao fim da necessidade operacional e legal."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"5. Direitos do titular",
					List.of(
						"A pessoa titular pode solicitar acesso, correcao, exclusao, portabilidade, oposicao ao tratamento e revisao de decisoes que a afetem, nos termos da legislacao aplicavel.",
						"O sistema registra internamente essas solicitacoes para controle de prazo, acompanhamento e resposta."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"6. Registro de aceite",
					List.of(
						"O aceite desta Politica de Privacidade e registrado com versao do documento, data e evidencias tecnicas de origem da acao para fins de auditoria e demonstracao de conformidade."
					)
				),
				new PublicLegalDocumentSectionResponse(
					"7. Atualizacoes",
					List.of(
						"Esta politica pode ser atualizada para refletir alteracoes legais, operacionais ou tecnicas. Em caso de mudanca relevante, a versao vigente sera publicada no fluxo publico da plataforma."
					)
				)
			)
		);
	}
}
