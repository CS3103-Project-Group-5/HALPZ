import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.io.*;
import java.net.*;

public class Client {

    public BitSet chunkList;

	public static void main(String[] args) throws Exception {

		Scanner scanner = new Scanner(System.in);

		// Wait for a request of user
		int option = -1, chunkSize = 256 * 1024;
		String input;
		long fileSize;
		ArrayList<PeerInfo> peerList;
		TrackerMessage.MODE mode;

		while (option != 5) {
			System.out.println("\nSelect an option:");
			System.out.println("1. Query the centralised server for list of files available.");
			System.out.println("2. Query the centralised server for a specific file.");
			System.out.println("3. Download a file by specifying the filename.");
			System.out.println("4. Inform availability of a new file.");
			System.out.println("5. Exit.\n");

			try {
				option = scanner.nextInt();
				scanner.nextLine();
			} catch (Exception e) {
				System.out.println("Please enter a valid command.");
				scanner.nextLine();
				continue;
			}
			switch (option) {
				case 1:
					try {
						printFileList(TrackerManager.getFileList());
						break;
					} catch (Exception e) {
						System.out.println("Tracker error");
						e.printStackTrace();
						break;
					}
				case 2:
					System.out.println("Please input file name.");
					input = scanner.nextLine();
					fileSize = TrackerManager.getFileSize(input);
					if (fileSize == -1) {
						System.out.println("File does not exist.");
					} else {
						System.out.println("Filename: " + input + ", Filesize: " + fileSize);
					}
					break;

				case 3:
					System.out.println("Please input file name.");
					input = scanner.nextLine();
					TrackerMessage msg = TrackerManager.getDownloadInfo(input);
					fileSize = msg.getFileSize();
					peerList = msg.getPeerList();
					chunkList = new BitSet((int) Math.ceil(fileSize / (double) chunkSize)); // <-- need to load file
					/* call peer class to do the p2p */
					break;

				case 4:
					System.out.println("Please input location of file to upload.");
					input = scanner.nextLine();
					File file = new File(input);
					if (!file.exists()) {
						System.out.println("File not found, exiting...");
						break;
					}
					fileSize = file.length();
					chunkList = new BitSet((int) Math.ceil(fileSize / (double) chunkSize));
					chunkList.flip(0, chunkList.length());
					TrackerManager.initializeUpload(input, fileSize);
					break;

			}
		}

		scanner.close();
	}

	private static long generateID() {
		Random rnd = new Random(506);
		return rnd.nextLong();
	}

	private static void printFileList(Set<String> list) {
		System.out.println("File list: ");
		for (String s : list) {
			System.out.println(s);
		}
	}

	private static void start(ArrayList<PeerInfo> list) {
		for (PeerInfo info : list) {
			try {
				new Thread(new Peer(new Socket(info.getPeerIP(), info.getPeerPort()))).start();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}

		ServerSocket welcomeSocket;
		try {
			welcomeSocket = new ServerSocket(welcomePort);
		} catch (Exception e) {
			System.out.println("Welcome port in use.");
			e.printStackTrace();
			return;
		} 

		while (true) {
			try {
				Socket connectionSocket = welcomeSocket.accept();
				System.out.println("New peer connected " + connectionSocket.getInetAddress().getHostAddress());
				new Thread(new Peer(connectionSocket)).start();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}
	}

//To access the bitset from the main class, use Client.this.chunkList to access.
	static class Peer implements Runnable {
		private Socket socket;
		private ObjectOutputStream out;
		private ObjectInputStream in;
		private BitSet
		//private ArrayList<Integer> desiredChunkList;

		public Peer(Socket socket) {
			socket = socket;
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
		}

		public void run() {
			sendChunkList();
			ArrayList<Integer> otherChunkList = receiveChunkList();
			if (otherChunkList.size() > 0) {
				ArrayList<Integer> desiredChunkList = getDesiredChunkList();
				if (desiredChunkList.size() > 0) {
					sendChunkRequest(desiredChunkList);
				}
			}

			ClientMessage msg;
			while (true) {
				msg = receiveMsg();
				if (msg.type == ClientMessage.MODE.DATA) {
					writeToFile();

				} else if (msg.type == ClientMessage.MODE.REQUEST) {
				} else {
					System.out.println("Unknown message.");
				}
			}
		}

		private void sendChunkList() {
		}

		private ClientMessage receiveMsg() {
		}

		private void sendChunkRequest() {
		}

		private void sendChunks() {
		}

		private ArrayList<Integer> getDesiredChunkList() {
			return null;
		}

		private void writeToFile() {
		}

		private void readFromFile() {
		}

		private int getChunkOffset(int number) {
			return 0;
		}

		private void removeFileStatus() {
		}
	}
}

class TrackerManager {

	private static final String TRACKER_ADDRESS = "128.199.108.79";
	private static final int TRACKER_PORT = 1234;
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private TrackerManager() throws Exception {
		socket = new Socket(TRACKER_ADDRESS, TRACKER_PORT);
		out = new ObjectOutputStream(socket.getOutputStream());
		in = new ObjectInputStream(socket.getInputStream());
	}

	private void close() throws Exception {
		socket.close();
	}

	private void send(TrackerMessage msg) throws Exception {
		out.writeObject(msg);
	}

	private TrackerMessage receive() throws Exception {
		TrackerMessage msg = (TrackerMessage)in.readObject();
		close();
		return msg;
	}

	public static void initializeUpload(String fileName, long fileSize) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.UPLOAD);
		msg.setFileName(fileName);
		msg.setFileSize(fileSize);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		tracker.receive();
	}

	public static TrackerMessage getDownloadInfo(String fileName) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.DOWNLOAD);
		msg.setFileName(fileName);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		return tracker.receive();
	}

	public static Set<String> getFileList() throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.FILELIST);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		Set<String> list = tracker.receive().getFileList();
		return list;
	}

	public static long getFileSize(String fileName) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.FILEINFO);
		msg.setFileName(fileName);
		tracker.send(msg);
		long fileSize = tracker.receive().getFileSize();
		return fileSize;
	}

}

class ClientMessage implements Serializable {

    public enum MODE {
        DATA, REQUEST, UPDATE
    }

    private MODE type;
    private int chunkID;
    private byte[256*1024] data;
    private BitSet chunkList;

    public ClientMessage(byte[] data, BitSet chunkList) {
        this.MODE = DATA;
        this.data = data;
        this.chunkList = chunkList;
    }

    public ClientMessage(int chunkID, BitSet chunkList) {
        this.MODE = REQUEST;
        this.chunkID = chunkID;
        this.chunkList;
    }

    public ClientMessage(BitSet chunkList) {
        this.MODE = UPDATE;
        this.chunkList = chunkList;
    }

    public MODE getType() {
        return this.type;
    }

    public int getChunkID() {
        return this.chunkID;
    }

    public byte[] getData() {
        return this.data;
    }

    public BitSet getChunkList() {
        return this.chunkList;
    }

}