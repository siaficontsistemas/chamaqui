package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
public class CompanyLogoStorageService {

	private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
		"image/png",
		"image/jpeg",
		"image/jpg",
		"image/webp",
		"image/gif"
	);

	private final Path rootDirectory;
	private final String bucketName;
	private final String keyPrefix;
	private final S3Client s3Client;

	public CompanyLogoStorageService(
		@Value("${app.storage.company-logos-dir:${user.home}/.helpdesk/uploads/company-logos}") String rootDirectory,
		@Value("${app.storage.company-logos-s3.bucket:}") String bucketName,
		@Value("${app.storage.company-logos-s3.region:us-east-1}") String region,
		@Value("${app.storage.company-logos-s3.prefix:company-logos}") String keyPrefix,
		@Value("${app.storage.company-logos-s3.endpoint:}") String endpoint,
		@Value("${app.storage.company-logos-s3.path-style-access:false}") boolean pathStyleAccess
	) {
		this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
		this.bucketName = bucketName == null ? "" : bucketName.trim();
		this.keyPrefix = normalizePrefix(keyPrefix);
		this.s3Client = buildS3Client(region, endpoint, pathStyleAccess);
	}

	public StoredCompanyLogo store(MultipartFile file) {
		validate(file);

		String originalFileName = sanitizeFileName(file.getOriginalFilename());
		String storageKey = UUID.randomUUID() + "-" + originalFileName;

		if (bucketName.isBlank()) {
			storeOnLegacyDisk(file, storageKey);
			return new StoredCompanyLogo(
				originalFileName,
				storageKey,
				Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"),
				file.getSize()
			);
		}

		try {
			s3Client.putObject(
				PutObjectRequest.builder()
					.bucket(requireBucketName())
					.key(buildObjectKey(storageKey))
					.cacheControl("public, max-age=31536000, immutable")
					.contentType(Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"))
					.build(),
				RequestBody.fromInputStream(file.getInputStream(), file.getSize())
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Nao foi possivel salvar a logo enviada.");
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Nao foi possivel salvar a logo enviada.", exception);
		}

		return new StoredCompanyLogo(
			originalFileName,
			storageKey,
			Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"),
			file.getSize()
		);
	}

	private void storeOnLegacyDisk(MultipartFile file, String storageKey) {
		try {
			Files.createDirectories(rootDirectory);
			Path targetPath = rootDirectory.resolve(storageKey).normalize();
			if (!targetPath.startsWith(rootDirectory)) {
				throw new IllegalStateException("Nao foi possivel salvar a logo enviada.");
			}

			try (java.io.InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Nao foi possivel salvar a logo enviada.");
		}
	}

	public StoredCompanyLogoContent load(String storageKey) {
		StoredCompanyLogoContent s3Logo = loadFromS3(storageKey);
		if (s3Logo != null) {
			return s3Logo;
		}

		return loadFromLegacyDisk(storageKey);
	}

	public void deleteIfManaged(String logoUrl) {
		String storageKey = extractStorageKey(logoUrl);
		if (storageKey == null) {
			return;
		}

		deleteFromS3(storageKey);
		deleteFromLegacyDisk(storageKey);
	}

	private StoredCompanyLogoContent loadFromS3(String storageKey) {
		if (bucketName.isBlank()) {
			return null;
		}

		try {
			ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(
				GetObjectRequest.builder()
					.bucket(bucketName)
					.key(buildObjectKey(storageKey))
					.build()
			);
			Resource resource = new ByteArrayResource(responseBytes.asByteArray());
			String contentType = responseBytes.response().contentType();
			return new StoredCompanyLogoContent(
				resource,
				contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType
			);
		} catch (NoSuchKeyException exception) {
			return null;
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("Logo nao encontrada.", exception);
		}
	}

	private StoredCompanyLogoContent loadFromLegacyDisk(String storageKey) {
		Path filePath = rootDirectory.resolve(storageKey).normalize();
		if (!filePath.startsWith(rootDirectory) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
			throw new IllegalArgumentException("Logo nao encontrada.");
		}

		try {
			Resource resource = new UrlResource(filePath.toUri());
			String contentType = Files.probeContentType(filePath);
			return new StoredCompanyLogoContent(
				resource,
				contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType
			);
		} catch (MalformedURLException exception) {
			throw new IllegalArgumentException("Logo nao encontrada.");
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private void deleteFromS3(String storageKey) {
		if (bucketName.isBlank()) {
			return;
		}
		try {
			s3Client.deleteObject(builder -> builder.bucket(bucketName).key(buildObjectKey(storageKey)));
		} catch (RuntimeException exception) {
			// Ignora a remocao para nao interromper a atualizacao da logo.
		}
	}

	private void deleteFromLegacyDisk(String storageKey) {
		Path filePath = rootDirectory.resolve(storageKey).normalize();
		if (!filePath.startsWith(rootDirectory)) {
			return;
		}

		try {
			Files.deleteIfExists(filePath);
		} catch (IOException exception) {
			// Ignora a remocao para nao interromper a atualizacao da logo.
		}
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Envie uma imagem para a logo da empresa.");
		}

		String contentType = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase();
		if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("A logo deve ser uma imagem PNG, JPG, WEBP ou GIF.");
		}

		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException("A logo deve ter no maximo 5 MB.");
		}
	}

	private String sanitizeFileName(String value) {
		String sanitizedValue = value == null ? "" : value.replace("\\", "/");
		sanitizedValue = sanitizedValue.substring(sanitizedValue.lastIndexOf('/') + 1).trim();

		if (sanitizedValue.isEmpty()) {
			return "logo-empresa";
		}

		return sanitizedValue;
	}

	private String extractStorageKey(String logoUrl) {
		if (logoUrl == null || logoUrl.isBlank()) {
			return null;
		}

		String normalizedValue = logoUrl.trim();
		int queryIndex = normalizedValue.indexOf('?');
		if (queryIndex >= 0) {
			normalizedValue = normalizedValue.substring(0, queryIndex);
		}

		String marker = "/api/v1/public/company-assets/";
		int markerIndex = normalizedValue.indexOf(marker);
		if (markerIndex < 0) {
			return null;
		}

		String storageKey = normalizedValue.substring(markerIndex + marker.length()).trim();
		return storageKey.isEmpty() ? null : storageKey;
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

	private String requireBucketName() {
		if (bucketName.isBlank()) {
			throw new IllegalStateException("O bucket S3 das logos da empresa nao foi configurado.");
		}
		return bucketName;
	}

	private String buildObjectKey(String storageKey) {
		return keyPrefix.isBlank() ? storageKey : keyPrefix + "/" + storageKey;
	}

	private String normalizePrefix(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().replaceAll("^/+", "").replaceAll("/+$", "");
	}

	public record StoredCompanyLogo(
		String originalFileName,
		String storageKey,
		String contentType,
		long sizeBytes
	) {
	}

	public record StoredCompanyLogoContent(
		Resource resource,
		String contentType
	) {
	}
}
