import java.awt.CardLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

import com.siddharth.chatcli.Message;
import com.siddharth.chatcli.Utils;
import com.siddharth.layouts.ChatArea;
import com.siddharth.layouts.InfoPanel;
import com.siddharth.layouts.MessagePanel;

/**
 * @author Siddharth Soni
 */
public class ClientGUI extends JFrame implements Runnable {
    public static final String CLIENT_DATA_PARENT_DIRECTORY = "Chatly_Client_Data";

    private ChatArea chatArea;
    private InfoPanel infoPanel;
    private CardLayout card;

    private String clientName;
    private String HOST_ADDRESS;
    private int PORT;

    private Socket socket;
    private ObjectOutputStream sender;

    private class DropFileListener extends DropTarget {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            Transferable tf = dtde.getTransferable();
            DataFlavor[] dataFlavors = tf.getTransferDataFlavors();

            for (DataFlavor dataFlavor : dataFlavors) {
                try {
                    if (dataFlavor.isFlavorJavaFileListType()) {
                        List<?> files = (List<?>) tf.getTransferData(dataFlavor);
                        boolean showWarning = true;
                        for (Object f : files) {
                            File file = (File) f;
                            if (file.isFile())
                                sendFileHelper((File) file);
                            else if (file.isDirectory() && showWarning) {
                                showToast("Can't send directory direct.");
                                showWarning = false;
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    public ClientGUI() {
        card = new CardLayout();
        this.setLayout(card);

        // Setting Look and Feel of the frame.
        setLookAndFeel();

        infoPanel = new InfoPanel();
        add(infoPanel, "infoPanel");

        chatArea = new ChatArea();
        add(chatArea, "chatArea");

        addActionListeners();

        setSize(500, 700);
        setIconImage(new ImageIcon("./assets/Chatly_logo.png").getImage());
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Chatly");
    }

    private void addActionListeners() {

        infoPanel.getConnectButton().addActionListener(e -> {
            connectToServer();
        });

        infoPanel.getConnectButton().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == '\n')
                    connectToServer();
            }
        });

        chatArea.getBtnSend().addActionListener(e -> {
            if (chatArea.getMessageText().isEmpty()) {
                return;
            }
            sendMessage(chatArea.getMessageText());
        });

        chatArea.getInputMessage().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    if (chatArea.getMessageText().isEmpty()) {
                        return;
                    }
                    sendMessage(chatArea.getMessageText());
                }
            }

        });

        chatArea.getAttachmentLabel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                sendFile();
            }
        });

        // File Drop Listener.
        new DropTarget(chatArea.getMessagesPanel(), new DropFileListener());
    }

    private void connectToServer() {
        if (isInvalidInfo()) {
            return;
        }
        // connect with socket.
        if (isConnected()) {
            card.show(getContentPane(), "chatArea");
            chatArea.getInputMessage().requestFocusInWindow();
        }
    }

    private void sendMessage(String messageText) {
        Message message = new Message(clientName, Message.MESSAGE_SEND, messageText);
        try {
            synchronized (sender) {
                sender.writeObject(message);
                sender.flush();
            }
            chatArea.addMessage(message, MessagePanel.USER_SEND);
            chatArea.clearInputMessageField();
        } catch (SocketException e) {
            showToast("Server is closed.");
        } catch (Exception e) {
            showToast("Some problem in connection. \nRestart the application and reconnect to ther server.");
        }
    }

    private void sendFile() {
        try {
            // String path = "E:/Urvesh/Learning Tutorials/ICE GATE Lectures/DMGT/";
            String path = "C:/Users/urves/Desktop/";
            JFileChooser fileChooser = new JFileChooser(path);
            int returnVal = fileChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                sendFileHelper(file);
            }
        } catch (Exception e) {
        }
    }

    private void sendFileHelper(File file) {
        Message message = new Message(clientName, Message.FILE_INFO_SEND, "File Sending...");
        message.setFile(file);
        chatArea.addMessage(message, sender, MessagePanel.USER_SEND);
    }

    private boolean isConnected() {
        try {
            Thread readerThread = new Thread(this);
            socket = new Socket(HOST_ADDRESS, PORT);
            readerThread.start();
            sender = new ObjectOutputStream(socket.getOutputStream());

            // Sending Information of the client.
            sender.writeObject(new Message(clientName, Message.USER_JOIN, InetAddress.getLocalHost().toString()));
            String msg = String.format("\nYou are connected with %s\n", HOST_ADDRESS);
            chatArea.addMessage(new Message(clientName, Message.USER_JOIN, msg), MessagePanel.USER_INFO);
        } catch (UnknownHostException e) {
            showToast("\nServer is not available on " + HOST_ADDRESS);
            return false;
        } catch (ConnectException e) {
            showToast("\nNo Server running on Port " + PORT);
            return false;
        } catch (SocketException e) {
            showToast("\nServer is closed.");
            return false;
        } catch (IOException e) {
            showToast("\nInput/Output interruption.");
            return false;
        } catch (Exception e) {
            showToast("\nSomething is wrong.");
            return false;
        }
        return true;
    }

    private boolean isInvalidInfo() {

        if (infoPanel.getClientName().isEmpty()) {
            showToast("Name should not be empty.");
            return true;
        }
        if (infoPanel.getServerAddress().isEmpty()) {
            showToast("Host Name should not be empty.");
            return true;
        }
        if (infoPanel.getServerPortNo().isEmpty()) {
            showToast("Port should not be empty.");
            return true;
        }
        try {
            int portNo = Integer.parseInt(infoPanel.getServerPortNo());
            if (portNo < 1 || portNo > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showToast("Port should be in between 1 to 65535");
            return true;
        }

        clientName = infoPanel.getClientName();
        HOST_ADDRESS = infoPanel.getServerAddress();
        PORT = Integer.parseInt(infoPanel.getServerPortNo());

        return false;
    }

    public static void main(String[] args) {
        try {
            // Initializing Client Data Directory.
            File directory = new File(CLIENT_DATA_PARENT_DIRECTORY);
            if (!directory.exists()) {
                directory.mkdir();
            }

            // Showing GUI
            new ClientGUI().setVisible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error Message", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void run() {
        try (ObjectInputStream reader = new ObjectInputStream(socket.getInputStream())) {

            Message message;
            while (true) {
                message = (Message) reader.readObject();
                switch (message.getMessageType()) {
                case Message.MESSAGE_RECEIVE:
                    chatArea.addMessage(message, MessagePanel.USER_RECEIVE);
                    break;
                case Message.FILE_INFO:
                    chatArea.addMessage(message, sender, MessagePanel.USER_RECEIVE);
                    break;
                case Message.FILE_INFO_RECEIVE:
                    fileInfoReceiveAction(message);
                    break;
                case Message.FILE_RECEIVING:
                    fileReceivingAction(message);
                    break;
                case Message.FILE_RECEIVED:
                    fileReceivedAction(message);
                    break;
                default:
                    chatArea.addMessage(message, MessagePanel.USER_INFO);
                }
            }
        } catch (SocketException e) {
            showToast("Server is closed.");
            chatArea.removeAllMessages();
            card.show(getContentPane(), "infoPanel");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Some problem in connection. \nRestart the application and reconnect to ther server.");
        }
    }

    private DataOutputStream fileOut = null;
    private JProgressBar progressBar;
    private long totalLen, receivedBytes;

    private void fileInfoReceiveAction(Message message) {
        try {
            String fileName = message.getFile().getName();
            File file = new File(CLIENT_DATA_PARENT_DIRECTORY, fileName);

            fileOut = new DataOutputStream(new FileOutputStream(file));
            progressBar = chatArea.getFirstProgressBar();

            totalLen = Long.parseLong(message.getMessage());
            receivedBytes = 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fileReceivingAction(Message message) {
        try {
            receivedBytes += message.getByteRead();
            fileOut.write(Utils.getPrimitiveArray(message.getData(), message.getByteRead()), 0, message.getByteRead());
            fileOut.flush();

            // Setting Progressbar value.
            int sent = (int) ((receivedBytes * 100) / totalLen);

            progressBar.setValue(sent);
            progressBar.setString(sent + "%");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fileReceivedAction(Message message) {
        // Closing the fileOut
        if (fileOut != null) {
            try {
                fileOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setLookAndFeel() {
        try {
            String lookAndFeel = "com.sun.java.swing.plaf.windows.WindowsLookAndFeel";
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception e) {
            e.printStackTrace();

        }
    }
}