package com.document.documentetl.controller;

import com.document.documentetl.model.v2.SourceDocument;
import com.document.documentetl.repository.v2.SourceDocumentRepository;
import com.document.documentetl.repository.v2.TextChunkRepository;
import com.document.documentetl.service.v2.DocumentV2StagingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentV2StagingService stagingService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final TextChunkRepository textChunkRepository;
    private final String documentsDirectory;

    public DocumentController(DocumentV2StagingService stagingService,
                              SourceDocumentRepository sourceDocumentRepository,
                              TextChunkRepository textChunkRepository,
                              @Value("${app.etl.v2.documents-directory}") String documentsDirectory) {
        this.stagingService = stagingService;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.textChunkRepository = textChunkRepository;
        this.documentsDirectory = documentsDirectory;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<UploadedDocumentResponse>> uploadDocument(
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile) {
        List<MultipartFile> uploadFiles = files == null ? List.of() : files;
        if (uploadFiles.isEmpty() && singleFile != null) {
            uploadFiles = List.of(singleFile);
        }

        if (uploadFiles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one upload file is required");
        }

        Path documentsPath = Paths.get(documentsDirectory).toAbsolutePath().normalize();
        Set<String> requestFileNames = new LinkedHashSet<>();
        List<ValidatedUpload> validatedUploads = uploadFiles.stream()
                .map(file -> validateUpload(file, documentsPath, requestFileNames))
                .toList();

        try {
            Files.createDirectories(documentsPath);
            for (ValidatedUpload upload : validatedUploads) {
                Files.copy(upload.file().getInputStream(), upload.targetPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file(s)", e);
        }

        stagingService.stageLocalFiles();

        List<UploadedDocumentResponse> responses = validatedUploads.stream()
                .map(upload -> sourceDocumentRepository.findBySourceUri(upload.targetPath().toAbsolutePath().toString())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Uploaded file was stored but not staged: " + upload.fileName())))
                .map(this::toResponse)
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping
    public List<UploadedDocumentResponse> listDocuments() {
        return sourceDocumentRepository.findAll().stream()
                .sorted(Comparator.comparing(SourceDocument::getStagedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    private UploadedDocumentResponse toResponse(SourceDocument document) {
        return new UploadedDocumentResponse(
                document.getDocumentId(),
                document.getFileName(),
                fileType(document.getFileName()),
                document.getStagedAt(),
                uiStatus(document.getStatus()),
                textChunkRepository.countByDocumentId(document.getDocumentId())
        );
    }

    private ValidatedUpload validateUpload(MultipartFile file, Path documentsPath, Set<String> requestFileNames) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file must not be empty");
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (!StringUtils.hasText(fileName)
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file name is invalid");
        }
        if (!requestFileNames.add(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate upload file name in request: " + fileName);
        }

        Path targetPath = documentsPath.resolve(fileName).normalize();
        if (!targetPath.startsWith(documentsPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file path is invalid");
        }
        return new ValidatedUpload(file, fileName, targetPath);
    }

    private static String fileType(String fileName) {
        int extensionStart = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return "FILE";
        }
        return fileName.substring(extensionStart + 1).toUpperCase(Locale.ROOT);
    }

    private static String uiStatus(String status) {
        if ("COMPLETED".equalsIgnoreCase(status)) {
            return "Ready";
        }
        if ("CHUNKED".equalsIgnoreCase(status)) {
            return "Chunked";
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return "Failed";
        }
        return "Processing";
    }

    public record UploadedDocumentResponse(Long id,
                                           String fileName,
                                           String fileType,
                                           OffsetDateTime uploadedAt,
                                           String status,
                                           long chunkCount) {
    }

    private record ValidatedUpload(MultipartFile file, String fileName, Path targetPath) {
    }
}
