import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.io.*;
import java.net.*;

public class Client {

    private static BitSet inprogress;
	private static BitSet completed;
	private static RandomAccessFile RAFile;
	private static int chunkSize;
	private static int peerNumber;
	private static String fileName;

	public static void main(String[] args) throws Exception {

		Scanner scanner = new Scanner(System.in);

		// Wait for a request of user
		int option = -1;
		chunkSize = 256 * 1024;
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
					fileName = scanner.nextLine();
					fileSize = TrackerManager.getFileSize(fileName);
					if (fileSize == -1) {
						System.out.println("File does not exist.");
					} else {
						System.out.println("Filename: " + fileName + ", Filesize: " + fileSize);
					}
					break;

				case 3:
					System.out.println("Please input file name.");
					fileName = scanner.nextLine();
					TrackerMessage msg = TrackerManager.getDownloadInfo(fileName);
					fileSize = msg.getFileSize();
					peerList = msg.getPeerList();
					peerNumber = peerList.size();
					inprogress = new BitSet((int) Math.ceil(fileSize / (double) chunkSize)); // <-- need to load file
					completed = (BitSet)inprogress.clone();
					start(peerList);
					break;

				case 4:
					System.out.println("Please input location of file to upload.");
					fileName = scanner.nextLine();
					File file = new File(fileName);
					if (!file.exists()) {
						System.out.println("File not found, exiting...");
						break;
					}
					fileSize = file.length();
					inprogress = new BitSet((int) Math.ceil(fileSize / (double) chunkSize));
					inprogress.flip(0, inprogress.length());
					completed = (BitSet)inprogress.clone();
					TrackerManager.initializeUpload(fileName, fileSize);
					start(new ArrayList<PeerInfo>());
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

	private static void writeToFile(int id, byte[] data) throws IOException {
		RAFile.seek(id*Client.chunkSize);
		RAFile.write(data);
		completed.set(id);
	}

	private static byte[] readFromFile(int id) throws IOException {
		RAFile.seek(id*chunkSize);
		byte[] bytes = new byte[chunkSize];
		RAFile.read(bytes);
		return bytes;
	}

	private static int getDesiredChunkID(BitSet others) {
		try {
			int start = inprogress.nextClearBit(0);
			int end = inprogress.previousClearBit(inprogress.length() - 1);
			int chunkID = (int)Math.random() * (end - start) + start;
			while (true) {
				chunkID = inprogress.nextClearBit(chunkID);
				if (others.get(chunkID)) break;
			}
			inprogress.set(chunkID);
			return chunkID;
		} catch (IndexOutOfBoundsException e) {
			return -1;
		}
	}

	private static void start(ArrayList<PeerInfo> list) {
		int welcomePort = 8099;
		File file = new File(fileName);

		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			RAFile = new RandomAccessFile(file, "rwd");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		for (PeerInfo info : list) {
			try {
				new Thread(new Peer(new Socket(info.getPeerIP(), welcomePort))).start();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}

		ServerSocket welcomeSocket;
		try {
			welcomeSocket = new ServerSocket(welcomePort);
			peerNumber++;
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

//To access the bitset from the main class, use Client.chunkList to access.
	static class Peer implements Runnable {
		private Socket socket;
		private ObjectOutputStream out;
		private ObjectInputStream in;
		private BitSet otherChunkList;
		private int currentChunkID = -1;
		//private ArrayList<Integer> desiredChunkList;

		public Peer(Socket socket) throws IOException {
			this.socket = socket;
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());
		}

		public void run() {
			try {
				sendChunkRequest(-1);

				ClientMessage msg;
				while (true) {
					msg = receiveMsg();
					System.out.println("Received " + msg.getType() + " message from " + socket.getInetAddress().getHostAddress());
					if (msg.getType() == ClientMessage.MODE.DATA) {
						Client.writeToFile(msg.getChunkID(), msg.getData());
						System.out.println("Received chunk " + msg.getChunkID() + " from " + socket.getInetAddress().getHostAddress());
						otherChunkList = msg.getChunkList();
						currentChunkID = getDesiredChunkID(otherChunkList);
						sendChunkRequest(currentChunkID);
						System.out.println("Sent chunk " + currentChunkID);
					} else if (msg.getType() == ClientMessage.MODE.REQUEST) {
						int chunkRequest = msg.getChunkID();
						sendChunks(chunkRequest);
					} else if (msg.getType() == ClientMessage.MODE.UPDATE) {
						otherChunkList = msg.getChunkList();
						currentChunkID = getDesiredChunkID(otherChunkList);
						sendChunkRequest(currentChunkID);
						System.out.println("Sent chunk request " + currentChunkID);
					} else {
						System.out.println("Unknown message.");
					}
					try {
						Client.completed.nextClearBit(0);
					} catch (IndexOutOfBoundsException e) {
						break;
					}
				}
			} catch (Exception e) {
				System.out.println("Peer thread error");
				if (currentChunkID != -1) {
					Client.inprogress.clear(currentChunkID);
				}
				e.printStackTrace();
			}
		}

		private ClientMessage receiveMsg() throws Exception {
			ClientMessage msg = (ClientMessage)in.readObject();
			return msg;
		}

		private void sendChunkRequest(int id) throws IOException {
			if (id == -1){
				out.writeObject(new ClientMessage(Client.completed));
			} else {
				out.writeObject(new ClientMessage(id, Client.completed));
			}
		}

		private void sendChunks(int id) throws IOException {
			byte[] data = Client.readFromFile(id);
			out.writeObject(new ClientMessage(data, Client.completed));
			System.out.println("Sent chunk " + id + " to " + socket.getInetAddress().getHostAddress());
		}

		private int getDesiredChunkID(BitSet others) {
			return Client.getDesiredChunkID(others);
		}

		private void readFromFile() {
		}

		private int getChunkOffset(int number) {
			return 0;
		}
	}
}

class TrackerManager {

	private static final String TRACKER_ADDRESS = "172.25.103.208";
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
    private byte[] data = new byte[256*1024];
    private BitSet chunkList;

    public ClientMessage(byte[] data, BitSet chunkList) {
        this.type = MODE.DATA;
        this.data = data;
        this.chunkList = chunkList;
    }

    public ClientMessage(int chunkID, BitSet chunkList) {
        this.type = MODE.REQUEST;
        this.chunkID = chunkID;
        this.chunkList = chunkList;
    }

    public ClientMessage(BitSet chunkList) {
        this.type = MODE.UPDATE;
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
