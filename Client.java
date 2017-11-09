import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class Client {

    private static BitSet inprogress;
	private static BitSet completed;
	private static int chunkSize;
	private static int peerNumber;
	private static String fileName;
	private static int totalChunkNumber;
	private static int port = 4100;

	public static void main(String[] args) throws Exception, IOException {

		port = Integer.parseInt(args[0]);

		Scanner scanner = new Scanner(System.in);

		// Wait for a request of user
		int option = -1;
		chunkSize = 32 * 1024;
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
					TrackerMessage msg = TrackerManager.getDownloadInfo(fileName, port);
					fileSize = msg.getFileSize();
					peerList = msg.getPeerList();
					peerNumber = peerList.size();
					totalChunkNumber = (int) Math.ceil(fileSize / (double) chunkSize);
					inprogress = new BitSet(totalChunkNumber); // <-- need to load file
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
					totalChunkNumber = (int) Math.ceil(fileSize / (double) chunkSize);
					inprogress = new BitSet(totalChunkNumber);
					inprogress.flip(0, totalChunkNumber);
					completed = (BitSet)inprogress.clone();
					TrackerManager.initializeUpload(fileName, fileSize, port);
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

	private static int getDesiredChunkID(BitSet others) {
		boolean firstLoop = true;
		try {
			int start = inprogress.nextClearBit(0);
			int end = inprogress.previousClearBit(totalChunkNumber - 1);
			int random = (int)(Math.random() * (end - start) + start);
			int chunkID = random;
			while (true) {
				System.out.println("Start: " + start);
				System.out.println("End: " + end);
				System.out.println("Random: " + random);
				System.out.println("ChunkID: " + chunkID);
				System.out.println("Firstloop: " + firstLoop);
				if (chunkID >= totalChunkNumber) {
					if (firstLoop) {
						chunkID = start;
						firstLoop = false;
					} else {
						return -1;
					}
				}
				if (!firstLoop && chunkID >= random) return -1;
				chunkID = inprogress.nextClearBit(chunkID);
				if (others.get(chunkID)) break;
			}
			inprogress.set(chunkID);
			return chunkID;
		} catch (IndexOutOfBoundsException e) {
			return -1;
		}
	}

	private static void start(ArrayList<PeerInfo> list) throws IOException {
		DatagramSocket clientSocket = new DatagramSocket(port);

		for (PeerInfo info : list) {
			try {
				Client.sendChunkRequest(-1, clientSocket, InetAddress.getByName(info.getPeerIP()), info.getPeerPort());
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}

		while (true) {
			byte[] bufferForPacket, bufferForPayload, rawChunkList, data;
			DatagramPacket receivedPacket;
			InetAddress peerIP;
			int type, chunkID, sizeOfData, requestedChunkID, peerPort;
			BitSet otherChunkList;
			RandomAccessFile RAFile;
			ByteBuffer bb;

			bufferForPacket = new byte[4+4+4+chunkSize+(totalChunkNumber+7)/8];
			receivedPacket = new DatagramPacket(bufferForPacket, bufferForPacket.length);
			clientSocket.receive(receivedPacket);
			System.out.println("Packet Received");			
			peerIP = receivedPacket.getAddress();
			peerPort = receivedPacket.getPort();
			bufferForPayload = receivedPacket.getData();
			bb = ByteBuffer.wrap(bufferForPayload);
			type = bb.getInt();
			chunkID = bb.getInt();
			sizeOfData = bb.getInt();
			data = new byte[sizeOfData];
			bb.get(data);
			System.out.println(sizeOfData);
			rawChunkList = new byte[(totalChunkNumber+7)/8];
			bb.get(rawChunkList);
			otherChunkList = BitSet.valueOf(rawChunkList);

			try {
				if (type == 0) { //type : update
					System.out.println("Update Message received");
					if (completed.nextClearBit(0) >= totalChunkNumber && otherChunkList.nextClearBit(0) >= Client.totalChunkNumber) {
						System.out.println("Ded");
						continue;
					}
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					System.out.println(requestedChunkID);
					Client.sendChunkRequest(requestedChunkID, clientSocket, peerIP, peerPort);					
					System.out.println("Sent packet");
				} else if (type == 1) { //type : request
					System.out.println("Request Message received");
					Client.sendChunkData(chunkID, clientSocket, peerIP, peerPort);
				} else if (type == 2) { //type : data
					System.out.print("Data Message received");
					Client.writeToFile(chunkID, data);
					System.out.print("Received chunk " + chunkID + " from " + peerIP);
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					Client.sendChunkRequest(requestedChunkID, clientSocket, peerIP, peerPort);
				} else {
					System.out.println("Unknown message.");
				} 
			} catch (IOException error) {
				error.printStackTrace();
			}		
		}
	}

	private static void sendChunkRequest(int desiredChunkNum, DatagramSocket clientSocket, InetAddress peerIP, int peerPort) throws IOException {
	ByteBuffer bb = ByteBuffer.allocate(4+4+4+chunkSize+(totalChunkNumber+7)/8); //type, chunkID, sizeOfData, data(byte array), BitSet
	if (desiredChunkNum == -1) {
		bb.putInt(0); //0 for update, 1 for request, 2 for data
		bb.putInt(-1);
	} else {
		bb.putInt(1);
		bb.putInt(desiredChunkNum);
	}
	bb.putInt(chunkSize);
	bb.put(new byte[chunkSize]);
	bb.put(completed.toByteArray());
	clientSocket.send(new DatagramPacket(bb.array(), bb.array().length, peerIP, peerPort));
	bb.clear();
	}

	private static void sendChunkData(int chunkID, DatagramSocket clientSocket, InetAddress peerIP, int peerPort) throws IOException{
		byte[] data = readFromFile(chunkID);
		ByteBuffer bb = ByteBuffer.allocate(4+4+4+data.length+(totalChunkNumber+7)/8); //type, chunkID, sizeOfData, data(byte array), BitSet
 		bb.putInt(2);
		bb.putInt(chunkID);
		bb.putInt(data.length);
		bb.put(data);
		bb.put(completed.toByteArray());
		clientSocket.send(new DatagramPacket(bb.array(), bb.array().length, peerIP, peerPort));
		bb.clear();
	}

	private static void writeToFile(int id, byte[] data) throws IOException {
		File file = new File(fileName);
		RandomAccessFile RAFile;
		byte[] bytes;	
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			RAFile = new RandomAccessFile(file, "rwd");
			RAFile.seek(id*Client.chunkSize);
			RAFile.write(data);
			completed.set(id);
			RAFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}

	private static byte[] readFromFile(int id) throws IOException {
		File file = new File(fileName);
		RandomAccessFile RAFile;
		byte[] bytes;	
		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			RAFile = new RandomAccessFile(file, "rwd");
			RAFile.seek(id*chunkSize);
			if (id == Client.totalChunkNumber - 1) {
				bytes = new byte[(int)(RAFile.length() - (long)id*chunkSize)];
			} else {
				bytes = new byte[chunkSize];
			}
			RAFile.read(bytes);
			RAFile.close();
			return bytes;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}

/*	static class InitialPacketProcessor implements Runnable {
		private int desiredChunkNum;
		private DatagramSocket clientSocket;
		private InetAddress peerIP;
		private int peerPort;

		public InitialPacketProcessor(int desiredChunkNum, DatagramSocket clientSocket, String peerIP, int peerPort) throws IOException {
			this.desiredChunkNum = desiredChunkNum;
			this.clientSocket = clientSocket;
			this.peerIP = InetAddress.getByName(peerIP);
			this.peerPort = peerPort;
		}

		public void run() {
			try {
			Client.sendChunkRequest(desiredChunkNum, clientSocket, peerIP, peerPort);
			} catch (IOException error) {
				error.printStackTrace();
			} 
		}
	}

	static class PacketProcessor implements Runnable {
		private DatagramSocket socket;
		private DatagramPacket receivedPacket;
		private BitSet otherChunkList;
		private int requestedChunkID = -1; //current requested 
		private RandomAccessFile RAFile;

		public PacketProcessor(DatagramSocket clientSocket, DatagramPacket receivedPacket) throws IOException {
			this.socket = clientSocket;
			this.receivedPacket = receivedPacket;
		}

		public void run(){
			InetAddress peerIP = receivedPacket.getAddress();
			int peerPort = receivedPacket.getPort();
			byte[] buffer = receivedPacket.getData();
			ByteBuffer bb = ByteBuffer.wrap(buffer);
			int type = bb.getInt();
			int chunkID = bb.getInt();
			int sizeOfData = bb.getInt();
			byte[] data = new byte[sizeOfData];
			bb.get(data);
			System.out.println(sizeOfData);
			byte[] rawChunkList = new byte[(totalChunkNumber+7)/8];
			bb.get(rawChunkList);
			otherChunkList = BitSet.valueOf(rawChunkList);

			try {
				if (type == 0) { //type : update
					System.out.println("Update Message received");
					if (Client.completed.nextClearBit(0) >= Client.totalChunkNumber && otherChunkList.nextClearBit(0) >= Client.totalChunkNumber) {
						System.out.println("Ded");
						return; //break out of the while loop since both sides have full copies of the file
					}
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					System.out.println(requestedChunkID);
					Client.sendChunkRequest(requestedChunkID, socket, peerIP, peerPort);					
					System.out.println("Sent packet");
				} else if (type == 1) { //type : request
					System.out.println("Request Message received");
					Client.sendChunkData(chunkID, socket, peerIP, peerPort);
				} else if (type == 2) { //type : data
					System.out.print("Data Message received");
					Client.writeToFile(chunkID, data);
					System.out.print("Received chunk " + chunkID + " from " + peerIP);
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					Client.sendChunkRequest(requestedChunkID, socket, peerIP, peerPort);
				} else {
					System.out.println("Unknown message.");
				} 
			} catch (IOException error) {
				error.printStackTrace();
			}

		}

	}
}
*/

/*
//To access the bitset from the main class, use Client.chunkList to access.
	static class Peer implements Runnable {
		private Socket socket;
		private ObjectOutputStream out;
		private ObjectInputStream in;
		private BitSet otherChunkList;
		private int currentChunkID = -1;
		private RandomAccessFile RAFile;

		//private ArrayList<Integer> desiredChunkList;

		public Peer(String fileName, Socket socket) throws IOException {
			this.socket = socket;
			out = new ObjectOutputStream(socket.getOutputStream());
			in = new ObjectInputStream(socket.getInputStream());

			File file = new File(fileName);
			try {
				if (!file.exists()) {
					file.createNewFile();
				}
				RAFile = new RandomAccessFile(file, "rwd");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void run() {
			try {
				sendChunkRequest(-1);

				ClientMessage msg;
				while (true) {
					System.out.println("going");
					msg = receiveMsg();
					if (msg.getType() == ClientMessage.MODE.DATA) {
						writeToFile(msg.getChunkID(), msg.getData());
						System.out.println("Received chunk " + msg.getChunkID() + " from " + socket.getInetAddress().getHostAddress());
						otherChunkList = msg.getChunkList();
						currentChunkID = getDesiredChunkID(otherChunkList);
						sendChunkRequest(currentChunkID);
					} else if (msg.getType() == ClientMessage.MODE.REQUEST) {
						int chunkRequest = msg.getChunkID();
						sendChunks(chunkRequest);
					} else if (msg.getType() == ClientMessage.MODE.UPDATE) {
						otherChunkList = msg.getChunkList();
						if (Client.completed.nextClearBit(0) >= Client.totalChunkNumber && otherChunkList.nextClearBit(0) >= Client.totalChunkNumber) {
							socket.close();
							return;
						}
						currentChunkID = getDesiredChunkID(otherChunkList);
						sendChunkRequest(currentChunkID);
					} else {
						System.out.println("Unknown message.");
					}
					try {
						Client.completed.nextClearBit(0);
					} catch (IndexOutOfBoundsException e) {
						break;
					}
				}
				System.out.println("Connection closed.");
			} catch (Exception e) {
				System.out.println("Connection closed.");
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
			byte[] data = readFromFile(id);
			out.writeObject(new ClientMessage(id, data, Client.completed));
			System.out.println("Sent chunk " + id + " to " + socket.getInetAddress().getHostAddress());
		}

		private int getDesiredChunkID(BitSet others) {
			return Client.getDesiredChunkID(others);
		}

		private int getChunkOffset(int number) {
			return 0;
		}

		private void writeToFile(int id, byte[] data) throws IOException {
			RAFile.seek(id*Client.chunkSize);
			RAFile.write(data);
			completed.set(id);
		}

		private byte[] readFromFile(int id) throws IOException {
			byte[] bytes;
			RAFile.seek(id*chunkSize);
			if (id == Client.totalChunkNumber - 1) {
				bytes = new byte[(int)(RAFile.length() - (long)id*chunkSize)];
			} else {
				bytes = new byte[chunkSize];
			}
			RAFile.read(bytes);
			return bytes;
		}

	}
}
*/

class TrackerManager {

	private static final String TRACKER_ADDRESS = "172.25.96.138";
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

	public static void initializeUpload(String fileName, long fileSize, int port) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.UPLOAD);
		msg.setFileName(fileName);
		msg.setFileSize(fileSize);
		msg.setPeerPort(port);
		tracker.send(msg); //0 - getFileList; 1 - download; 2 - upload
		tracker.receive();
	}

	public static TrackerMessage getDownloadInfo(String fileName, int port) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.DOWNLOAD);
		msg.setFileName(fileName);
		msg.setPeerPort(port);
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

    public ClientMessage(int chunkID, byte[] data, BitSet chunkList) {
		this.chunkID = chunkID;
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
