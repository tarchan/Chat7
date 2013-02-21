/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mac.tarchan.chat7;

import com.mac.tarchan.irc.client.util.KanaInputFilter;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/**
 *
 * @author Takashi Ogura <tarchan at mac.com>
 */
public class Chat7Controller implements Initializable {

    private static final Logger log = Logger.getLogger(Chat7Controller.class.getName());
    private FileChooser fileChooser = new FileChooser();
    private File file;
    @FXML
    private TextArea console;
    @FXML
    private TextField input;
    @FXML
    private Button send;

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.CONFIG);
        log.setLevel(Level.CONFIG);
        log.setUseParentHandlers(false);
        log.addHandler(handler);
    }

    @FXML
    private void handleSend(ActionEvent e) {
        console.appendText(String.format("%s%n", input.textProperty().get()));
        input.clear();
    }

    @FXML
    private void handleOpen(ActionEvent e) {
        log.info("ファイルを選択します。");
        File f = fileChooser.showOpenDialog(null);
        if (f != null) {
            openFile(f.toPath());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("すべてのファイル (*.*)", "*.*");
        fileChooser.getExtensionFilters().add(allFilter);
    }

    private void openFile(Path path) {
        log.log(Level.INFO, "ファイルをオープンします。: {0}", path);
        int lineCount = 0;
        Charset cs = Charset.forName("JIS");
        try (BufferedReader reader = newBufferedReader(path, cs)) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                lineCount++;
                console.appendText(String.format("%s%n", line));
            }
            log.log(Level.INFO, "{0} 行出力しました。", lineCount);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "ファイルを読み込めません。", ex);
        }
    }

    private static BufferedReader newBufferedReader(Path path, Charset cs) throws IOException {
        InputStream input = Files.newInputStream(path);
        log.log(Level.CONFIG, "文字コード: {0}", cs.displayName());
        if (cs.displayName().equals("ISO-2022-JP")) {
            log.log(Level.CONFIG, "KanaInputFilter を使用します。");
            input = new KanaInputFilter(input);
        }
        CharsetDecoder decoder = cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        Reader reader = new InputStreamReader(input, decoder);
        return new BufferedReader(reader);
    }
}
