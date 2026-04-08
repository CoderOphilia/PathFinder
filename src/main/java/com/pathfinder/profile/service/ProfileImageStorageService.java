package com.pathfinder.profile.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProfileImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
    private final Path uploadDirectory = Paths.get("uploaded", "profile-images").toAbsolutePath().normalize();

    public String storeProfileImage(MultipartFile profileImageFile, String identifierHint) {
        if (profileImageFile == null || profileImageFile.isEmpty()) {
            return "";
        }

        String originalFilename = profileImageFile.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Upload a JPG, PNG, GIF, or WEBP image.");
        }

        String contentType = profileImageFile.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("The selected file must be an image.");
        }

        String fileName = sanitizeIdentifier(identifierHint) + "-" + UUID.randomUUID() + extension;

        try {
            Files.createDirectories(uploadDirectory);
            Path targetFile = uploadDirectory.resolve(fileName).normalize();
            try (InputStream inputStream = profileImageFile.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not save the selected profile image.");
        }

        return "/uploads/profile-images/" + fileName;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return originalFilename.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    private String sanitizeIdentifier(String identifierHint) {
        String normalized = identifierHint == null ? "" : identifierHint.trim().toLowerCase(Locale.ROOT);
        String safe = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "profile" : safe;
    }
}
