package com.softwaredebebidas.view;

import com.softwaredebebidas.SoftwareDeBebidasApp;
import com.softwaredebebidas.presenter.BusinessSettingsPresenter;
import com.softwaredebebidas.presenter.MainPresenter;
import com.softwaredebebidas.repository.ConfigRepository;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.AppDataDir;
import com.softwaredebebidas.util.LogoUtils;
import com.softwaredebebidas.util.Refreshable;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the "Datos del negocio" view (admin only).
 * Edits the business name and logo; saving refreshes the sidebar, top bar and window
 * without a restart.
 */
public class BusinessSettingsController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(BusinessSettingsController.class.getName());

    @FXML
    private TextField nameField;

    @FXML
    private ImageView logoPreview;

    @FXML
    private Label noLogoLabel;

    private BusinessSettingsPresenter presenter;

    /** Image picked but not saved yet; {@code null} when the stored logo is unchanged. */
    private Path pendingLogo;

    /** True when the user asked to remove the stored logo on save. */
    private boolean removeLogo;

    @FXML
    public void initialize() {
        presenter = new BusinessSettingsPresenter(
                new ConfigRepository(SoftwareDeBebidasApp.getDatabaseManager()),
                AppDataDir.getBaseDir());
        refresh();
    }

    /** Reloads the stored values, discarding unsaved edits. */
    @Override
    public void refresh() {
        pendingLogo = null;
        removeLogo = false;
        try {
            nameField.setText(presenter.getBusinessName());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No se pudo cargar el nombre del negocio", e);
        }
        showPreview(presenter.getLogoPath());
    }

    @FXML
    private void onChooseLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir logo del negocio");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes (PNG, JPG)", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(nameField.getScene() != null ? nameField.getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        pendingLogo = file.toPath();
        removeLogo = false;
        showPreview(pendingLogo);
    }

    @FXML
    private void onRemoveLogo() {
        pendingLogo = null;
        removeLogo = true;
        showPreview(null);
    }

    @FXML
    private void onSave() {
        try {
            presenter.save(nameField.getText(), pendingLogo, removeLogo);
        } catch (IllegalArgumentException e) {
            AlertService.showWarningDialog("Datos del negocio", e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "No se pudieron guardar los datos del negocio", e);
            AlertService.showErrorDialog("Error", e.getMessage());
            return;
        }
        MainPresenter main = MainPresenter.getInstance();
        if (main != null) {
            main.refreshBranding();
            main.showToast("Datos del negocio guardados");
        }
        refresh();
    }

    private void showPreview(Path path) {
        Image image = null;
        if (path != null && Files.isReadable(path)) {
            image = path.equals(presenter.getLogoPath()) ? LogoUtils.loadLogoImage() : loadFromFile(path);
        }
        logoPreview.setImage(image);
        boolean hasLogo = image != null;
        logoPreview.setVisible(hasLogo);
        noLogoLabel.setVisible(!hasLogo);
    }

    private static Image loadFromFile(Path path) {
        try {
            Image image = new Image(new java.io.ByteArrayInputStream(Files.readAllBytes(path)));
            return image.isError() ? null : image;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo mostrar la vista previa del logo", e);
            return null;
        }
    }
}
