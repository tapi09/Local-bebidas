package com.softwaredebebidas.presenter;

import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.util.LogoUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Presenter for the "Datos del negocio" screen (admin only).
 * Validates and persists the business name and manages the business logo stored in
 * the data directory.
 */
public class BusinessSettingsPresenter {

    /** Longest business name accepted; keeps the sidebar and receipts readable. */
    static final int MAX_NAME_LENGTH = 60;

    private final ConfigRepository configRepository;
    private final Path dataDir;

    public BusinessSettingsPresenter(ConfigRepository configRepository, Path dataDir) {
        this.configRepository = configRepository;
        this.dataDir = dataDir;
    }

    public String getBusinessName() {
        try {
            return configRepository.getBusinessName();
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo cargar el nombre del negocio.", e);
        }
    }

    /** Returns the stored logo file, or {@code null} when none has been chosen. */
    public Path getLogoPath() {
        return LogoUtils.resolveLogoPath(dataDir);
    }

    /**
     * Saves the business settings.
     *
     * @param name       new business name (required, trimmed)
     * @param newLogo    image chosen by the user, or {@code null} to keep the current logo
     * @param removeLogo when {@code true} and no new logo is given, deletes the current logo
     */
    public void save(String name, Path newLogo, boolean removeLogo) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El nombre del negocio no puede superar los " + MAX_NAME_LENGTH + " caracteres.");
        }

        // Logo first: an invalid image must not leave the name half-saved.
        try {
            if (newLogo != null) {
                LogoUtils.saveLogo(dataDir, newLogo);
            } else if (removeLogo) {
                LogoUtils.removeLogo(dataDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "No se pudo guardar el logo. Verifique que sea una imagen PNG o JPG válida.", e);
        }

        try {
            configRepository.setBusinessName(trimmed);
        } catch (SQLException e) {
            throw new RuntimeException("No se pudo guardar el nombre del negocio.", e);
        }
    }
}
