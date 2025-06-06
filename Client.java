import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import com.siddharth.chatcli.Message;

/**
 * @author Siddharth Soni
 */
public class Client implements Runnable {

	private int PORT;
	private String HOST_NAME;

	private String clientName;
	private Thread readerThread;
	private Socket socket;
	private BufferedReader clientInfo;

	public Client() {
		try {
			readerThread = new Thread(this);
			clientInfo = new BufferedReader(new InputStreamReader(System.in));

			// Getting client information
			getClientInfo();

			socket = new Socket(HOST_NAME, PORT);
			readerThread.start();
			ObjectOutputStream writer = new ObjectOutputStream(socket.getOutputStream());

			// Sending Information of the client.
			writer.writeObject(new Message(clientName, Message.USER_JOIN, InetAddress.getLocalHost().toString()));
			System.out.println(String.format("\nYou are connected with %s\n", HOST_NAME));

			String message;
			while (true) {
				message = clientInfo.readLine();
				if (message.isEmpty()) {
					continue;
				} else if (message.equals("exit")) {
					writer.writeObject(new Message(clientName, Message.USER_EXIT, ""));
					break;
				} else {
					writer.writeObject(new Message(clientName, Message.MESSAGE_SEND, message));
				}
			}
			if (readerThread.isAlive()) {
				readerThread.interrupt();
			}
			clientInfo.close();
		} catch (UnknownHostException e) {
			System.out.println("\nServer is not available on " + HOST_NAME);
		} catch (ConnectException e) {
			System.out.println("\nNo Server running on Port " + PORT);
		} catch (SocketException e) {
			System.out.println("\nServer is closed.");
		} catch (IOException e) {
			System.out.println("\nInput/Output interruption.");
		} catch (Exception e) {
			System.out.println("\nSomething is wrong.");
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
			}
		}
		System.exit(0);
	}

	private void getClientInfo() throws IOException {
		System.out.print("Name : ");
		clientName = clientInfo.readLine();
		System.out.print("Server IPV4 Address : ");
		HOST_NAME = clientInfo.readLine();
		System.out.print("Port (Default 54321): ");
		try {
			PORT = Integer.parseInt(clientInfo.readLine());
		} catch (NumberFormatException e) {
			PORT = 54321;
		}

		if (clientName.isEmpty() || HOST_NAME.isEmpty()) {
			System.out.println("\n** Please enter valid Info.**");
			System.exit(0);
		}
	}

	public void run() {
		try (ObjectInputStream reader = new ObjectInputStream(socket.getInputStream())) {

			Message message;
			while (true) {
				message = (Message) reader.readObject();
				switch (message.getMessageType()) {
				case Message.MESSAGE_RECEIVE:
					System.out.println(message.getAuthor() + " : " + message.getMessage());
					break;
				case Message.FILE_INFO:
					System.out.println(
							String.format("%s sent the %s file.", message.getAuthor(), message.getFile().getName()));
					break;
				default:
					break;
				}
			}
		} catch (SocketException e) {
			System.out.println("Server is closed.");
			System.exit(0);
		} catch (Exception e) {
		}
		System.exit(0);
	}

	public static void main(String arg[]) {
		try {
			new Client();
		} catch (Exception e) {
		}
	}
}