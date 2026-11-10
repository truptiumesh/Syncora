package com.example.demo.service;

import com.example.demo.exception.FileAttachmentNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.exception.UnsupportedFileTypeException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
	private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
	private static final int HEADER_LENGTH = 8;
	private final Path storageDirectory;

	public FileStorageService(@Value("${app.files.storage-dir}") String storageDirectory) {
		this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
	}

	public StoredFile store(MultipartFile multipartFile) {
		String contentType;
		Path temporaryFile = null;
		try {
			Files.createDirectories(storageDirectory);
			temporaryFile = Files.createTempFile(storageDirectory, ".syncora-upload-", ".tmp");
			try (InputStream source = multipartFile.getInputStream();
					BufferedInputStream input = new BufferedInputStream(source)) {
				input.mark(HEADER_LENGTH);
				byte[] signature = input.readNBytes(HEADER_LENGTH);
				input.reset();
				contentType = detectContentType(signature);
				Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
			}

			String storageKey = UUID.randomUUID().toString();
			Path destination = resolve(storageKey);
			try {
				Files.move(temporaryFile, destination, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporaryFile, destination);
			}
			return new StoredFile(storageKey, contentType);
		} catch (UnsupportedFileTypeException exception) {
			deleteTemporaryFile(temporaryFile);
			throw exception;
		} catch (IOException | RuntimeException exception) {
			deleteTemporaryFile(temporaryFile);
			throw exception instanceof FileStorageException storageException
					? storageException
					: new FileStorageException(exception);
		}
	}

	public InputStreamResource open(String storageKey) {
		Path path = resolve(storageKey);
		try {
			if (!Files.isRegularFile(path)) {
				throw new FileAttachmentNotFoundException();
			}
			return new InputStreamResource(Files.newInputStream(path));
		} catch (FileAttachmentNotFoundException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new FileStorageException(exception);
		}
	}

	public void delete(String storageKey) {
		Path path = resolve(storageKey);
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new FileStorageException(exception);
		}
	}

	private Path resolve(String storageKey) {
		UUID key;
		try {
			key = UUID.fromString(storageKey);
		} catch (IllegalArgumentException exception) {
			throw new FileStorageException(exception);
		}
		Path resolved = storageDirectory.resolve(key + ".blob").normalize();
		if (!resolved.startsWith(storageDirectory)) {
			throw new FileStorageException();
		}
		return resolved;
	}

	private String detectContentType(byte[] signature) {
		if (startsWith(signature, new byte[] {'%', 'P', 'D', 'F', '-'})) return "application/pdf";
		if (startsWith(signature, new byte[] {
				(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
		if (startsWith(signature, new byte[] {
				(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
		if (startsWith(signature, new byte[] {'G', 'I', 'F', '8'})
				&& signature.length >= 6
				&& (signature[4] == '7' || signature[4] == '9')
				&& signature[5] == 'a') return "image/gif";
		throw new UnsupportedFileTypeException();
	}

	private boolean startsWith(byte[] value, byte[] prefix) {
		if (value.length < prefix.length) return false;
		for (int index = 0; index < prefix.length; index++) {
			if (value[index] != prefix[index]) return false;
		}
		return true;
	}

	private void deleteTemporaryFile(Path temporaryFile) {
		if (temporaryFile == null) return;
		try {
			Files.deleteIfExists(temporaryFile);
		} catch (IOException exception) {
			logger.error("Could not remove temporary file upload", exception);
		}
	}

	public record StoredFile(String storageKey, String contentType) {}
}
