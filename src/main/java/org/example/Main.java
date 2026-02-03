package org.example;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("editor.fxml"));

        Scene scene = new Scene(loader.load());
        stage.setTitle("CodePad");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("nodecode.png")));
        stage.setScene(scene);
        var controller = loader.getController();
        if (controller instanceof org.example.controllers.EditorController editorController) {
            stage.setOnCloseRequest(event -> {
                event.consume();
                editorController.requestExit();
            });
        }
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

