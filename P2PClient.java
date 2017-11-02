import java.util.*;
import java.io.*;
import java.net.*;

public class P2PClient {

/* Usage
	java P2PClient list 
		- return list of files recorded on tracker
	java P2PClient upload myfile
		- return a list of peers to upload file
		- upload
	java P2PClient download hisfile
		- return a list of peers to download from
		- download
*/

	private static TrackerMessage.MODE mode;
	private static String fileName;
	private static ArrayList<PeerInfo> peerList;
	private static long selfID;
	private static long fileSize = -1;
	private static int welcomePort = 4949;
	private static int chunkSize = 256 * 1024;

	private static BitSet chunkList;

	public static void main(String[] args) {
		String m = args[0];
		try {
			mode = TrackerMessage.MODE.valueOf(m.toUpperCase());
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		if (mode.equals(TrackerMessage.MODE.LIST)) {
			try {
				printFileList(Tracker.getFileList());
				return;
			} catch (Exception e) {
				System.out.println("Tracker error");
				e.printStackTrace();
				return;
			}
		}

		fileName = args[1];
		System.out.println("Initiating " + mode + " for file " + fileName);
	
		selfID = generateID();
		TrackerMessage.MODE cmd = TrackerMessage.MODE.LIST;

		try {
			if (mode.equals(TrackerMessage.MODE.UPLOAD)) {
				cmd = TrackerMessage.MODE.UPLOAD;
				File file = new File(fileName);
				if (!file.exists()) {
					System.out.println("File not found, exiting...");
					return;
				}
				fileSize = file.length();
				chunkList = new BitSet((int)Math.ceil(fileSize / (double)chunkSize));
				chunkList.flip(0, chunkList.length());
				Tracker.initializeUpload(mode, fileName, fileSize);
			} else if (mode.equals(TrackerMessage.MODE.DOWNLOAD)) {
				TrackerMessage msg = Tracker.getDownloadInfo(mode, fileName);
				fileSize = msg.getFileSize();
				peerList = msg.getPeerList();
				chunkList = new BitSet((int)Math.ceil(fileSize / (double)chunkSize)); // <-- need to load file status from disk
			}
		} catch (Exception e) {
			System.out.println("Tracker error");
			e.printStackTrace();
			return;
		}

		//start(peerList);
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

	static class Peer implements Runnable {
		private Socket socket;
		private ObjectOutputStream out;
		private ObjectInputStream in;

		public Peer(Socket socket) {
			socket = socket;
		}
		
		public void run() {
		}

		private void sendChunkList() {
		}

		private void receiveChunkList() {
		}

		private void sendChunkRequest() {
		}

		private void sendChunks() {
		}

		private void receiveChunks() {
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

class Tracker {

	private static final String TRACKER_ADDRESS = "128.199.108.79";
	private static final int TRACKER_PORT = 1234;
	private Socket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;

	private Tracker() throws Exception {
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

	public static void initializeUpload(TrackerMessage.MODE cmd, String fileName, long fileSize) throws Exception {
		Tracker tracker = new Tracker();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(cmd);
		msg.setFileName(fileName);
		msg.setFileSize(fileSize);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		tracker.receive();
	}

	public static TrackerMessage getDownloadInfo(TrackerMessage.MODE cmd, String fileName) throws Exception {
		Tracker tracker = new Tracker();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(cmd);
		msg.setFileName(fileName);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		return tracker.receive();
	}

	public static Set<String> getFileList() throws Exception {
		Tracker tracker = new Tracker();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.LIST);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		Set<String> list = tracker.receive().getFileList();
		return list;
	}

}
