/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mac.tarchan.chat7;

import com.mac.tarchan.irc.client.IRCClient;
import com.mac.tarchan.irc.client.IRCEvent;
import com.mac.tarchan.irc.client.IRCMessage;
import static com.mac.tarchan.irc.client.NumericReply.*;
import com.mac.tarchan.irc.client.Reply;
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
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 *
 * @author Takashi Ogura <tarchan at mac.com>
 */
public class Chat7Controller implements Initializable {

    private static final Logger log = Logger.getLogger(Chat7Controller.class.getName());

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.CONFIG);
        log.setLevel(Level.CONFIG);
        log.setUseParentHandlers(false);
        log.addHandler(handler);
    }
    private FileChooser fileChooser = new FileChooser();
    private SimpleObjectProperty<File> file = new SimpleObjectProperty<>(this, "file");
    private ReadLogService readLogService = new ReadLogService();
    private IRCClient irc;
    private IrcService ircService = new IrcService();
    private String channel = "#dameTunes";
    @FXML
    private VBox root;
    @FXML
    private TextArea console;
    @FXML
    private TextField input;
    @FXML
    private Button send;
    @FXML
    private Region glass;
    @FXML
    private ProgressIndicator loading;

    public Property<File> fileProperty() {
        return file;
    }

    @FXML
    private void handleSend(ActionEvent e) {
        String text = input.textProperty().get();
        input.clear();
        if (text.isEmpty()) {
            return;
        }
        if (irc == null) {
            return;
        }

//        console.appendText(String.format("%s%n", text));
        talk(irc.getUserNick(), text, System.currentTimeMillis());
        irc.privmsg(channel, text);
    }

    @FXML
    private void handleOpen(ActionEvent e) {
        log.info("ファイルを選択します。");
        File f = fileChooser.showOpenDialog(null);
        if (f != null) {
            fileProperty().setValue(f);
            readLogService.restart();
//            openFile(f.toPath());
        }
    }

    @FXML
    private void handleConnect(ActionEvent e) {
        ircService.start();
    }

    @FXML
    private void handleExit(ActionEvent e) {
        log.log(Level.INFO, "アプリケーションを終了します。");
        Platform.exit();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
//        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("すべてのファイル (*.*)", "*.*");
//        fileChooser.getExtensionFilters().add(allFilter);

//        console.textProperty().bind(readLogService.valueProperty());
        glass.visibleProperty().bind(readLogService.runningProperty());
        loading.visibleProperty().bind(readLogService.runningProperty());
        loading.progressProperty().bind(readLogService.progressProperty());
    }

    private void connect() {
        log.info("IRCに接続します。");
        try {
//            System.setProperty("java.net.useSystemProxies", "true");
            String host = "irc.ircnet.ne.jp";
            int port = 6667;
            String enc = "jis";
            String nick = "lchat7";
            String user = "tarchan";
            irc = IRCClient.createClient(this).connect(host, port, enc).login(nick, user, "powered by IRCKit", 0xf, null);
//            irc = IRCClient.createClient(host, port, nick);
//            irc.addHandler(this);
//            irc.start();
        } catch (IOException ex) {
            log.log(Level.SEVERE, "IRCに接続できません。", ex);
            irc = null;
        }
    }

    class IrcService extends Service<String> {

        @Override
        protected Task<String> createTask() {
            return new IrcTask();
        }
    }

    class IrcTask extends Task<String> {

        @Override
        protected String call() throws Exception {
            connect();
            while (true) {
                String line = irc.next();
                if (line == null) {
                    break;
                }
                updateMessage(line);
                Thread.sleep(10);
            }
            log.log(Level.INFO, "IRCを切断します。: {0}", irc);
            return null;
        }
    }

    @Reply(value = "020", property = "message.trail")
    public void wait(String trail) {
        console.appendText(String.format("しばらくお待ちください。: %s%n", trail));
    }

    @Reply(value = RPL_WELCOME, property = "message.trail")
    public void welcome(String trail) {
        log.log(Level.INFO, "IRCに接続しました。: {0}", trail);
        console.appendText(String.format("IRCに接続しました。: %s%n", trail));
        irc.join(channel);
    }

    @Reply(value = "PING", property = "message.trail")
    public void ping(String trail) {
        log.log(Level.INFO, "継続します。: {0}", trail);
        irc.pong(trail);
    }

    @Reply("NICK")
    public void nick(IRCEvent e) {
        
    }

    @Reply("PRIVMSG")
    public void talk(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String nick = msg.getPrefix().getNick();
        String text = msg.getTrail();
        long when = msg.getWhen();
        talk(nick, text, when);
    }

    private static String getTimeString(long when) {
        return String.format("%tH:%<tM", when);
    }

    private void talk(String nick, String text, long when) {
        console.appendText(String.format("%s %s: %s%n", getTimeString(when), nick, text));
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

    class ReadLogService extends Service<String> {

        @Override
        protected Task<String> createTask() {
//            file = TwoFaceController.this.file.get();
            return new ReadLogTask(fileProperty().getValue().toPath());
        }
    }

    class ReadLogTask extends Task<String> {

        private Path path;

        ReadLogTask(Path path) {
            this.path = path;
        }

        @Override
        protected String call() throws Exception {
            log.log(Level.INFO, "ファイルをオープンします。: {0}", path);
            int lineCount = 0;
            StringBuilder buf = new StringBuilder();
            Charset cs = Charset.forName("JIS");
            try (BufferedReader reader = newBufferedReader(path, cs)) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }

                    lineCount++;
//                    console.appendText(String.format("%s%n", line));
                    buf.append(String.format("%s%n", line));
                    updateMessage(line);
                }
                log.log(Level.INFO, "{0} 行出力しました。", lineCount);
                return buf.toString();
            } catch (IOException ex) {
                log.log(Level.SEVERE, "ファイルを読み込めません。", ex);
                throw ex;
            }
        }
    }
}
