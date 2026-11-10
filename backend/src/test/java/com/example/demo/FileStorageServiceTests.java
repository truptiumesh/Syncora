package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.exception.FileStorageException;
import com.example.demo.exception.UnsupportedFileTypeException;
import com.example.demo.service.FileStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTests {
	@TempDir Path temporaryDirectory;

	@Test
	void rejectsUnsupportedContentBasedOnBytesRatherThanSuppliedMimeType() {
		FileStorageService storage = new FileStorageService(temporaryDirectory.resolve("uploads").toString());
		MockMultipartFile file = new MockMultipartFile(
				"file", "fake.pdf", "application/pdf", "not a pdf".getBytes());

		assertThrows(UnsupportedFileTypeException.class, () -> storage.store(file));
	}

	@Test
	void reportsStorageFailureWithoutLeavingTemporaryFiles() throws IOException {
		Path invalidStorageDirectory = temporaryDirectory.resolve("not-a-directory");
		Files.writeString(invalidStorageDirectory, "blocks directory creation");
		FileStorageService storage = new FileStorageService(invalidStorageDirectory.toString());
		MockMultipartFile file = new MockMultipartFile(
				"file", "document.pdf", "application/pdf", "%PDF-1.7".getBytes());

		assertThrows(FileStorageException.class, () -> storage.store(file));
		assertTrue(Files.isRegularFile(invalidStorageDirectory));
	}
}
