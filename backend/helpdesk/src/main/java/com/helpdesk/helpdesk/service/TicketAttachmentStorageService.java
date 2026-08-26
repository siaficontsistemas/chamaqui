package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class TicketAttachmentStorageService {

	private static final Logger logger = LoggerFactory.getLogger(TicketAttachmentStorageService.class);
	private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.ofEntries(
		Map.entry("pdf", "application/pdf"),
		Map.entry("png", "image/png"),
		Map.entry("jpg", "image/jpeg"),
		Map.entry("jpeg", "image/jpeg"),
		Map.entry("webp", "image/webp"),
		Map.entry("gif", "image/gif"),
		Map.entry("txt", "text/plain"),
		Map.entry("csv", "text/csv"),
		Map.entry("doc", "application/msword"),
		Map.entry(
			"docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
		),
		Map.entry("xls", "application/vnd.ms-excel"),
		Map.entry(
			"xlsx",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
		),
		Map.entry("mp4", "video/mp4"),
		Map.entry("mp3", "audio/mpeg"),
		Map.entry("ogg", "audio/ogg"),
		Map.entry("webm", "audio/webm")
	);
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.copyOf(CONTENT_TYPES_BY_EXTENSION.values());

	private final Path rootDirectory;
	private final List<Path> readableDirectories;
	private final long maxFileSizeBytes;
	private final String bucketName;
	private final String keyPrefix;
	private final S3Client s3Client;

	public TicketAttachmentStorageService(
		@Value("${app.storage.attachments-dir:${user.home}/.helpdesk/uploads/ticket-attachments}") String rootDirectory,
		@Value("${app.storage.attachments-legacy-dirs:${user.dir}/uploads/ticket-attachments}") String legacyDirectories,
		@Value("${app.storage.attachments-max-file-size-bytes:26214400}") long maxFileSizeBytes,
		@Value("${app.storage.attachments-s3.bucket:}") String bucketName,
		@Value("${app.storage.attachments-s3.region:us-east-1}") String region,
		@Value("${app.storage.attachments-s3.prefix:ticket-attachments}") String keyPrefix,
		@Value("${app.storage.attachments-s3.endpoint:}") String endpoint,
		@Value("${app.storage.attachments-s3.path-style-access:false}") boolean pathStyleAccess
	) {
		this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
		this.readableDirectories = buildReadableDirectories(this.rootDirectory, legacyDirectories);
		this.maxFileSizeBytes = maxFileSizeBytes;
		this.bucketName = bucketName == null ? "" : bucketName.trim();
		this.keyPrefix = normalizePrefix(keyPrefix);
		this.s3Client = buildS3Client(region, endpoint, pathStyleAccess);
	}

	public StoredAttachment store(MultipartFile file) {
		String originalFileName = sanitizeFileName(file.getOriginalFilename());
		String normalizedContentType = normalizeContentType(originalFileName, file.getContentType());
		validate(originalFileName, normalizedContentType, file.getSize());
		Path targetPath = prepareTargetPath(originalFileName);
		String storageKey = targetPath.getFileName().toString();

		if (isS3Configured()) {
			try {
				putOnS3(storageKey, normalizedContentType, file.getInputStream(), file.getSize());
			} catch (IOException exception) {
				throw new IllegalStateException("Não foi possível salvar o arquivo enviado.", exception);
			}
			return new StoredAttachment(originalFileName, storageKey, normalizedContentType, file.getSize());
		}

		try {
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível salvar o arquivo enviado.");
		}

		return new StoredAttachment(originalFileName, storageKey, normalizedContentType, file.getSize());
	}

	public StoredAttachment store(String originalFileName, String contentType, byte[] content) {
		if (content == null || content.length == 0) {
			throw new IllegalArgumentException("Os anexos enviados devem conter conteúdo.");
		}

		String sanitizedFileName = sanitizeFileName(originalFileName);
		String normalizedContentType = normalizeContentType(sanitizedFileName, contentType);
		validate(sanitizedFileName, normalizedContentType, content.length);
		Path targetPath = prepareTargetPath(sanitizedFileName);
		String storageKey = targetPath.getFileName().toString();

		if (isS3Configured()) {
			putOnS3(storageKey, normalizedContentType, content);
			return new StoredAttachment(sanitizedFileName, storageKey, normalizedContentType, content.length);
		}

		try {
			Files.write(targetPath, content);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível salvar o arquivo enviado.");
		}

		return new StoredAttachment(sanitizedFileName, storageKey, normalizedContentType, content.length);
	}

	public Resource loadAsResource(String storageKey) {
		Resource s3Resource = loadFromS3(storageKey);
		if (s3Resource != null) {
			return s3Resource;
		}

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

	public void deleteIfManaged(String storageKey) {
		if (storageKey == null || storageKey.isBlank()) {
			return;
		}

		deleteFromS3(storageKey.trim());
		for (Path directory : readableDirectories) {
			Path candidate = directory.resolve(storageKey.trim()).normalize();
			if (!candidate.startsWith(directory)) {
				continue;
			}

			try {
				Files.deleteIfExists(candidate);
			} catch (IOException exception) {
				logger.warn("Não foi possível remover o anexo gerenciado: storageKey={}", storageKey.trim());
			}
		}
	}

	private boolean isS3Configured() {
		return !bucketName.isBlank();
	}

	private void putOnS3(String storageKey, String contentType, byte[] content) {
		s3Client.putObject(
			PutObjectRequest.builder().bucket(bucketName).key(buildObjectKey(storageKey)).contentType(contentType).build(),
			RequestBody.fromBytes(content)
		);
	}

	private void putOnS3(String storageKey, String contentType, java.io.InputStream content, long size) {
		s3Client.putObject(
			PutObjectRequest.builder().bucket(bucketName).key(buildObjectKey(storageKey)).contentType(contentType).build(),
			RequestBody.fromInputStream(content, size)
		);
	}

	private Resource loadFromS3(String storageKey) {
		if (!isS3Configured()) {
			return null;
		}

		try {
			ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
				GetObjectRequest.builder().bucket(bucketName).key(buildObjectKey(storageKey)).build()
			);
			return new ByteArrayResource(response.asByteArray());
		} catch (NoSuchKeyException exception) {
			return null;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Arquivo não encontrado para download.", exception);
		}
	}

	private void deleteFromS3(String storageKey) {
		if (!isS3Configured()) {
			return;
		}
		s3Client.deleteObject(builder -> builder.bucket(bucketName).key(buildObjectKey(storageKey)));
	}

	private S3Client buildS3Client(String region, String endpoint, boolean pathStyleAccess) {
		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(region == null || region.isBlank() ? "us-east-1" : region.trim()))
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build());
		if (endpoint != null && !endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint.trim()));
		}
		return builder.build();
	}

	private String buildObjectKey(String storageKey) {
		return keyPrefix.isBlank() ? storageKey : keyPrefix + "/" + storageKey;
	}

	private String normalizePrefix(String value) {
		if (value == null || value.isBlank()) return "";
		return value.trim().replaceAll("^/+", "").replaceAll("/+$", "");
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

	private void validate(String originalFileName, String contentType, long sizeBytes) {
		if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException(
				"Tipo de anexo não permitido. Envie PDF, imagem, texto, planilha, documento Office, áudio MP3/OGG ou vídeo MP4."
			);
		}
		if (sizeBytes <= 0) {
			throw new IllegalArgumentException("Os anexos enviados devem conter conteúdo.");
		}
		if (sizeBytes > maxFileSizeBytes) {
			throw new IllegalArgumentException("O anexo excede o limite permitido de 25 MB.");
		}
		if (extractExtension(originalFileName).isEmpty()) {
			throw new IllegalArgumentException("O anexo precisa ter uma extensão válida.");
		}
	}

	private String normalizeContentType(String originalFileName, String contentType) {
		String normalizedContentType = Objects.requireNonNullElse(contentType, "").trim().toLowerCase(Locale.ROOT);
		if (!normalizedContentType.isBlank() && ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
			return normalizedContentType;
		}

		return CONTENT_TYPES_BY_EXTENSION.getOrDefault(extractExtension(originalFileName), normalizedContentType);
	}

	private String extractExtension(String originalFileName) {
		String fileName = originalFileName == null ? "" : originalFileName.trim().toLowerCase(Locale.ROOT);
		int extensionIndex = fileName.lastIndexOf('.');
		if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(extensionIndex + 1);
	}

	public record StoredAttachment(
		String originalFileName,
		String storageKey,
		String contentType,
		long sizeBytes
	) {
	}
}
