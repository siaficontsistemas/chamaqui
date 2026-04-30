package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TicketAttachmentStorageService {

	private static final Logger logger = LoggerFactory.getLogger(TicketAttachmentStorageService.class);

	private final Path rootDirectory;
	private final List<Path> readableDirectories;

	public TicketAttachmentStorageService(
		@Value("${app.storage.attachments-dir:${user.home}/.helpdesk/uploads/ticket-attachments}") String rootDirectory,
		@Value("${app.storage.attachments-legacy-dirs:${user.dir}/uploads/ticket-attachments}") String legacyDirectories
	) {
		this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
		this.readableDirectories = buildReadableDirectories(this.rootDirectory, legacyDirectories);
	}

	public StoredAttachment store(MultipartFile file) {
		String originalFileName = sanitizeFileName(file.getOriginalFilename());
		Path targetPath = prepareTargetPath(originalFileName);

		try {
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível salvar o arquivo enviado.");
		}

		return new StoredAttachment(
			originalFileName,
			targetPath.getFileName().toString(),
			Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"),
			file.getSize()
		);
	}

	public StoredAttachment store(String originalFileName, String contentType, byte[] content) {
		if (content == null || content.length == 0) {
			throw new IllegalArgumentException("Os anexos enviados devem conter conteúdo.");
		}

		String sanitizedFileName = sanitizeFileName(originalFileName);
		Path targetPath = prepareTargetPath(sanitizedFileName);

		try {
			Files.write(targetPath, content);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível salvar o arquivo enviado.");
		}

		return new StoredAttachment(
			sanitizedFileName,
			targetPath.getFileName().toString(),
			Objects.requireNonNullElse(contentType, "application/octet-stream"),
			content.length
		);
	}

	public Resource loadAsResource(String storageKey) {
		Path filePath = resolveReadablePath(storageKey);

		try {
			Resource resource = new UrlResource(filePath.toUri());
			if (!resource.exists() || !resource.isReadable()) {
				throw new IllegalArgumentException("Arquivo não encontrado para download.");
			}
			return resource;
		} catch (MalformedURLException exception) {
			throw new IllegalArgumentException("Arquivo não encontrado para download.");
		}
	}

	private void ensureRootDirectory() {
		try {
			Files.createDirectories(rootDirectory);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível preparar o diretório de anexos.");
		}
	}

	private Path prepareTargetPath(String originalFileName) {
		String storageKey = UUID.randomUUID() + "-" + originalFileName;
		Path targetPath = rootDirectory.resolve(storageKey).normalize();

		ensureRootDirectory();

		if (!targetPath.startsWith(rootDirectory)) {
			throw new IllegalArgumentException("Nome de arquivo inválido.");
		}

		return targetPath;
	}

	private List<Path> buildReadableDirectories(Path primaryDirectory, String legacyDirectories) {
		Set<Path> directories = new LinkedHashSet<>();
		directories.add(primaryDirectory);

		if (legacyDirectories == null || legacyDirectories.isBlank()) {
			return List.copyOf(directories);
		}

		for (String directory : legacyDirectories.split(",")) {
			String trimmedDirectory = directory == null ? "" : directory.trim();
			if (trimmedDirectory.isEmpty()) {
				continue;
			}
			directories.add(Paths.get(trimmedDirectory).toAbsolutePath().normalize());
		}

		return List.copyOf(directories);
	}

	private Path resolveReadablePath(String storageKey) {
		for (Path directory : readableDirectories) {
			Path candidate = directory.resolve(storageKey).normalize();

			if (!candidate.startsWith(directory)) {
				continue;
			}

			if (Files.exists(candidate) && Files.isReadable(candidate)) {
				return candidate;
			}
		}

		logger.warn("Anexo não encontrado nos diretórios configurados: storageKey={}, directories={}", storageKey, readableDirectories);
		throw new IllegalArgumentException("Arquivo não encontrado para download.");
	}

	private String sanitizeFileName(String value) {
		String sanitizedValue = value == null ? "" : value.replace("\\", "/");
		sanitizedValue = sanitizedValue.substring(sanitizedValue.lastIndexOf('/') + 1).trim();

		if (sanitizedValue.isEmpty()) {
			return "arquivo";
		}

		return sanitizedValue;
	}

	public record StoredAttachment(
		String originalFileName,
		String storageKey,
		String contentType,
		long sizeBytes
	) {
	}
}
