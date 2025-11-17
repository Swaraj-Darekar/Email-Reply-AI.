package com.email.writer.app;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

@Service
public class EmailGeneratorService {
	
	private final WebClient webclient;
	
	@Value("${gemini.api.url}")
	private String geminiApiUrl;
	
	@Value("${gemini.api.key}")
	private String geminiApiKey;
	
	public EmailGeneratorService(WebClient.Builder webclientBuilder) {
		this.webclient = webclientBuilder.build();
	}
	
	@PostConstruct
	public void init() {
		if (geminiApiUrl == null || geminiApiUrl.isEmpty()) {
			throw new IllegalStateException("gemini.api.url is not configured!");
		}
		if (geminiApiKey == null || geminiApiKey.isEmpty()) {
			throw new IllegalStateException("gemini.api.key is not configured!");
		}
		System.out.println("Gemini API configured successfully");
	}
	
	public String generateEmailReply(EmailRequest emailRequest) {
		try {
			// Build the Prompt 
			String prompt = buildPrompt(emailRequest);
			
			// Craft a request
			Map<String, Object> requestBody = Map.of(
				"contents", new Object[] {
					Map.of("parts", new Object[] {
						Map.of("text", prompt)
					})
				}
			);
			
			// Do request get response
			String response = webclient.post()
				.uri(geminiApiUrl + "?key=" + geminiApiKey)
				.header("Content-Type", "application/json")
				.bodyValue(requestBody)
				.retrieve()
				.onStatus(status -> status.isError(), 
					clientResponse -> clientResponse.bodyToMono(String.class)
						.map(body -> new RuntimeException("API Error: " + body)))
				.bodyToMono(String.class)
				.block();
			
			// Return response (Extract response and Return)
			return extractResponseContent(response);
			
		} catch (Exception e) {
			e.printStackTrace();
			return "Error generating email: " + e.getMessage();
		}
	}
	
	private String extractResponseContent(String response) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(response);
			
			return rootNode.path("candidates")
				.get(0)
				.path("content")
				.path("parts")
				.get(0)
				.path("text")
				.asText();
			
		} catch (Exception e) {
			return "Error processing response: " + e.getMessage();
		}
	}
	
	private String buildPrompt(EmailRequest emailRequest) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("Generate a professional email reply for the following email content. Please don't generate the subject line. ");
		
		if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
			prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
		}
		
		prompt.append("\nOriginal email:\n").append(emailRequest.getEmailContent());
		
		return prompt.toString();
	}
}