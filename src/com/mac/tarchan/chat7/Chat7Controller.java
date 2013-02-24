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
import java.util.Arrays;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.application.Application.Parameters;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
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
    @FXML
    private Label target;
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
    @FXML
    private TableView<Room> channels;
    @FXML
    private TableView<User> users;
    @FXML
    private TableColumn<Room, String> channelName;
    @FXML
    private TableColumn<Room, String> channelMode;
    @FXML
    private TableColumn<User, String> userName;
    @FXML
    private TableColumn<User, String> userMode;
    @FXML
    private TitledPane userTile;
    @FXML
    private TitledPane channelTile;
    
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
        echo(System.currentTimeMillis(), irc.getUserNick(), text);
        irc.privmsg(target.getText(), text);
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

//        String[] values = {"399", "400", "401", "598", "599", "600"};
//        for (String v : values) {
//            log.log(Level.INFO, "{0} is error {1}", new Object[] {v, NumericReply.isError(v)});
//        }

        channelName.setCellValueFactory(new PropertyValueFactory<Room, String>("name"));
        channelMode.setCellValueFactory(new PropertyValueFactory<Room, String>("mode"));
        userName.setCellValueFactory(new PropertyValueFactory<User, String>("name"));
        userMode.setCellValueFactory(new PropertyValueFactory<User, String>("mode"));
//        ArrayList<Room> list = new ArrayList<>();
//        Room room1 = new Room("#javabreak");
//        room1.name = "#javabreak";
//        list.add(room1);
//        ObservableList<Room> items = FXCollections.observableArrayList();
        ObservableList<Room> items = channels.getItems();
        items.add(new Room("#javabreak"));
        items.add(new Room("#dameTunes"));
        items.add(new Room("#irodorie:*.jp"));
        channels.setItems(items);
        
        channels.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Room>() {
            @Override
            public void changed(ObservableValue<? extends Room> self, Room oldValue, Room newValue) {
                target.textProperty().bind(newValue.name);
            }
        });
    }
    
    private void connect() {
        try {
            log.info("IRCに接続します。");
            Parameters params = (Parameters) root.getUserData();
            Map<String, String> named = params.getNamed();
            log.log(Level.INFO, "コマンドライン引数: {0}", Arrays.asList(named));
//            System.setProperty("java.net.useSystemProxies", "true");
            String host = named.get("host");
            int port = Integer.valueOf(named.get("port"));
            String enc = named.get("encoding");
            String nick = named.get("nick");
            String user = named.get("user");
            String real = named.get("real");
            int mode = Integer.valueOf(named.get("mode"));
            String pass = named.get("pass");
            irc = IRCClient.createClient(this).connect(host, port, enc).login(nick, user, real, mode, pass);
//            irc = IRCClient.createClient(host, port, nick);
//            irc.addHandler(this);
//            irc.start();
        } catch (Exception ex) {
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
                String line = irc.readMessage();
                if (line == null) {
                    break;
                }
                updateMessage(line);
//                Thread.sleep(10);
            }
            log.log(Level.INFO, "IRCを切断します。: {0}", irc);
            return null;
        }
    }
    
    @Reply(value = RPL_HELLO, property = "message.trail")
    public void hello(String trail) {
        console.appendText(String.format("しばらくお待ちください。: %s%n", trail));
    }
    
    @Reply(value = RPL_WELCOME, property = "message.trail")
    public void welcome(String trail) {
        log.log(Level.INFO, "IRCに接続しました。: {0}", trail);
        console.appendText(String.format("IRCに接続しました。: %s%n", trail));
        channels.getItems().clear();
        users.getItems().clear();
//        irc.join(target.getText());
    }
    
    @Reply(value = "PING", property = "message.trail")
    public void ping(String trail) {
        log.log(Level.INFO, "継続します。: {0}", trail);
        irc.pong(trail);
    }
    
    @Reply("NICK")
    public void nick(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String oldNick = msg.getPrefix().getNick();
        String newNick = msg.getTrail();
        echo(String.format("ニックネーム変更: %s to %s.", oldNick, newNick));
        if (irc.getUserNick().equals(oldNick)) {
            irc.setUserNick(newNick);
        }
    }
    
    private void addChannel(String name) {
        Room room = new Room(name);
        ObservableList<Room> items = channels.getItems();
        items.add(room);
    }
    
    private void addUser(String channel, String[] names) {
        ObservableList<User> items = users.getItems();
        for (String name : names) {
            User user = new User(name);
            user.mode.set(channel);
            items.add(user);
        }
    }
    
    @Reply("JOIN")
    public void join(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String nick = msg.getPrefix().getNick();
        String channel = msg.getTrail();
        log.log(Level.INFO, "入室: {0} {1} ({2})", new Object[]{channel, nick, nick.equals(irc.getUserNick())});
        if (nick.equals(irc.getUserNick())) {
            addChannel(channel);
        }
    }
    
    private Room findRoom(String name) {
        for (Room room : channels.getItems()) {
            if (room.name.get().equals(name)) {
                return room;
            }
        }
        return null;
    }
    
    @Reply(RPL_TOPIC)
    public void topic(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String topic = msg.getTrail();
        String channel = msg.getParam1();
        int pos = channels.getItems().indexOf(channel);
        Room room = findRoom(channel);
        log.log(Level.INFO, "トピック: {0}({2}): {1}", new Object[]{channel, topic, room});
        if (room != null) {
            room.topic.set(topic);
        }
    }
    
    @Reply(RPL_NAMREPLY)
    public void names(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String channel = msg.getParam2();
        String[] names = msg.getTrail().split(" ");
        log.log(Level.INFO, "ユーザーリスト: {0} {1}", new Object[]{channel, Arrays.asList(names)});
        addUser(channel, names);
    }
    
    @Reply("PRIVMSG")
    public void talk(IRCEvent e) {
        IRCMessage msg = e.getMessage();
        String nick = msg.getPrefix().getNick();
        String text = msg.getTrail();
        long when = msg.getWhen();
        echo(when, nick, text);
    }
    
    @Reply("NOTICE")
    public void notice(IRCEvent e) {
        IRCMessage msg = e.getMessage();
//        String nick = msg.getPrefix().getNick();
        String nick = msg.getParam0();
        String text = msg.getTrail();
        long when = msg.getWhen();
        echo(when, nick, text);
    }
    
    private static String getTimeString(long when) {
        return String.format("%tH:%<tM", when);
    }
    
    private void echo(String text) {
        long when = System.currentTimeMillis();
        console.appendText(String.format("%s %s%n", getTimeString(when), text));
    }
    
    private void echo(long when, String nick, String text) {
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
    
    public class Room {
        
        public StringProperty name = new SimpleStringProperty(this, "name");
        public StringProperty topic = new SimpleStringProperty(this, "topic");
        public String[] users;
        
        Room(String name) {
            this.name.set(name);
        }

//        @Override
//        public boolean equals(Object other) {
//            log.info("比較します。: " + other);
//            if (other instanceof String) {
//                return name.get().equals(other);
//            }
//            return this == other;
//        }
        public String getName() {
            return name.get();
        }
        
        public String getMode() {
            return topic.get();
        }
    }
    
    public class User {
        
        public StringProperty name = new SimpleStringProperty(this, "name");
        public StringProperty mode = new SimpleStringProperty(this, "mode");
        
        User(String name) {
            this.name.set(name);
        }
        
        public String getName() {
            return name.get();
        }
        
        public String getMode() {
            return mode.get();
        }
    }
}
