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
	private static int port = 40000;
	private static DatagramSocket clientSocket;
	private static long myID = 0;
	private static String local;
	private static String publicIP;
	private static boolean closeThread = false;
	private static int maxQueueSize = 0;

	public static void main(String[] args) throws Exception, IOException {
		local = InetAddress.getLocalHost().toString().split("/")[1];
		clientSocket = new DatagramSocket();
		System.out.println("My local IP: " + local);
		System.out.println("My local port: " + clientSocket.getLocalPort());

		Scanner scanner = new Scanner(System.in);

		int option = -1;
		chunkSize = 32 * 1024;
		long fileSize;
		ArrayList<PeerInfo> peerList;
		TrackerMessage.MODE mode;
		TrackerMessage msg;

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
						printFileList(TrackerManager.getFileList(clientSocket));
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
					msg = TrackerManager.getDownloadInfo(clientSocket, fileName, clientSocket.getLocalPort(), local);
					publicIP = msg.getPublicIP();
					myID = msg.getPeerID();
					fileSize = msg.getFileSize();
					peerList = msg.getPeerList();
					if (peerList.isEmpty()) {
						System.out.println("Requested file not found");
						break;
					}
					peerNumber = peerList.size();
					totalChunkNumber = (int) Math.ceil(fileSize / (double) chunkSize);
					inprogress = new BitSet(totalChunkNumber);
					completed = (BitSet)inprogress.clone();
					start(peerList, clientSocket);
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
					msg = TrackerManager.initializeUpload(clientSocket, fileName, fileSize, clientSocket.getLocalPort(), local);
					myID = msg.getPeerID();
					publicIP = msg.getPublicIP();
					start(new ArrayList<PeerInfo>(), clientSocket);
					break;
			}

			clientSocket = new DatagramSocket();
		}
		closeThread = true;
		TrackerManager.exit(myID);
		scanner.close();
		System.exit(1);
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
			if (end < start) return -1;
			int random = (int)(Math.random() * (end - start) + start);
			int chunkID = random;
			while (true) {
				/*
				System.out.println("Start: " + start);
				System.out.println("End: " + end);
				System.out.println("Random: " + random);
				System.out.println("Firstloop: " + firstLoop);
				*/
				if (chunkID >= totalChunkNumber) {
					if (firstLoop) {
						chunkID = start;
						firstLoop = false;
					} else {
						return -1;
					}
				}
				if (!firstLoop && chunkID >= random) return -1;

				//System.out.println("ChunkID: " + chunkID);
				chunkID = inprogress.nextClearBit(chunkID);
				//System.out.println("New ChunkID: " + chunkID);
				if (others.get(chunkID)) break;
				chunkID++;
			}
			return chunkID;
		} catch (IndexOutOfBoundsException e) {
			return -1;
		}
	}
	
	private static void start(ArrayList<PeerInfo> list, DatagramSocket clientSocket) throws IOException {
		connect(clientSocket);
		
		for (PeerInfo info : list) {
			try {
				DatagramSocket s = new DatagramSocket();
				new Thread() {
					public void run() {
						process(s);
					}
				}.start();
				new Thread() {
					public void run() {
						try {
							if (info.getPeerIP().equals(publicIP)) { // same NAT
								System.out.println("Send request to peer " + info.getPeerPrivateIP() + " with port " + info.getPeerPrivatePort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerPrivateIP()), info.getPeerPrivatePort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerPrivateIP()), info.getPeerPrivatePort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerPrivateIP()), info.getPeerPrivatePort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerPrivateIP()), info.getPeerPrivatePort());
							} else {
								System.out.println("Send request to peer " + info.getPeerIP() + " with port " + info.getPeerPort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerIP()), info.getPeerPort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerIP()), info.getPeerPort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerIP()), info.getPeerPort());
								Client.sendChunkRequest(-1, s, InetAddress.getByName(info.getPeerIP()), info.getPeerPort());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}
	}

	private static void connect(DatagramSocket clientSocket) throws IOException {
		new Thread() {
			public void run() {
				try {
					DatagramPacket receivedPacket = new DatagramPacket(new byte[1], 1);
					clientSocket.receive(receivedPacket);
					System.out.println("Peer connected from " + receivedPacket.getAddress());
					clientSocket.send(new DatagramPacket(new byte[1], 1, receivedPacket.getAddress(), receivedPacket.getPort()));
					DatagramSocket s = new DatagramSocket();
					TrackerManager.update(myID, s, s.getLocalPort(), local);
					connect(s);
				} catch (Exception e) {
					e.printStackTrace();
				}
				process(clientSocket);
			}
		}.start();
	}

	private static void process(DatagramSocket clientSocket) {
		RandomAccessFile RAFile;
		try {
			File file = new File(fileName);
			if (!file.exists()) {
				file.createNewFile();
			}
			RAFile = new RandomAccessFile(file, "rwd");
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		byte[] bufferForPacket, bufferForPayload, rawChunkList, data;
		InetAddress peerIP;
		DatagramPacket receivedPacket;;
		int type, chunkID, sizeOfData, requestedChunkID, peerPort;
		BitSet otherChunkList;
		ByteBuffer bb;
		bufferForPacket = new byte[4+4+4+chunkSize+(totalChunkNumber+7)/8];
		receivedPacket = new DatagramPacket(bufferForPacket, bufferForPacket.length);
		LinkedList<Integer> queue = new LinkedList<Integer>();
		while (true) {
			if (closeThread == true) {
					try {
						RAFile.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				return;
			}
			try {
				clientSocket.receive(receivedPacket);
				System.out.println("Packet Received");			
			} catch (IOException e) {
				e.printStackTrace();
			}
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
			System.out.println("Queue size = " + queue.size());
			if (queue.size() > maxQueueSize) {
				maxQueueSize = queue.size();
			}
			try {
				if (type == 0) { //type : update
					System.out.println("Update Message received");
					if (completed.nextClearBit(0) >= totalChunkNumber && otherChunkList.nextClearBit(0) >= totalChunkNumber) {
						Client.sendChunkRequest(-1, clientSocket, peerIP, peerPort);					
						System.out.println("Ded");
						break;
					}
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					if (requestedChunkID != -1) {
						queue.offer(requestedChunkID);
					} else if (!queue.isEmpty()) {
						requestedChunkID = queue.poll();
					}
					Client.sendChunkRequest(requestedChunkID, clientSocket, peerIP, peerPort);					
				} else if (type == 1) { //type : request
					System.out.println("Request Message received from " + peerIP);
					Client.sendChunkData(chunkID, clientSocket, peerIP, peerPort);
				} else if (type == 2) { //type : data
					System.out.println("Data Message received from " + peerIP);
					Client.writeToFile(RAFile, chunkID, data);
					if (!new Integer(chunkID).equals(queue.peek()) && queue.size() > 10) {
						inprogress.clear(queue.poll());
					}
					queue.remove(new Integer(chunkID));
					System.out.println("Received chunk " + chunkID + " from " + peerIP);
					requestedChunkID = Client.getDesiredChunkID(otherChunkList);
					if (requestedChunkID != -1) {
						queue.offer(requestedChunkID);
					} else if (!queue.isEmpty()) {
						requestedChunkID = queue.poll();
					}
					Client.sendChunkRequest(requestedChunkID, clientSocket, peerIP, peerPort);
				} else {
					System.out.println("Unknown message.");
				} 
			} catch (IOException error) {
				error.printStackTrace();
			}		
		}
		try {
			RAFile.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Max queue size = " + maxQueueSize);
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

	private static void writeToFile(RandomAccessFile RAFile, int id, byte[] data) throws IOException {
		if (completed.get(id)) {
			return;
		}
		try {
			RAFile.seek(id*Client.chunkSize);
			RAFile.write(data);
			completed.set(id);
			inprogress.set(id);
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

class TrackerManager {

	private static final String TRACKER_ADDRESS = "172.25.105.20";
	private static final int TRACKER_PORT = 1234;
	private DatagramSocket socket;
	private ObjectOutputStream out;
	private ObjectInputStream in;
	private ByteArrayOutputStream dataout;
	private ByteArrayInputStream datain;

	private TrackerManager(DatagramSocket socket) throws Exception {
		this.socket = socket;
	}

	private TrackerManager() throws Exception {
		this.socket = new DatagramSocket();
	}

	private void send(TrackerMessage msg) throws Exception {
		dataout = new ByteArrayOutputStream();
		out = new ObjectOutputStream(dataout);
		out.writeObject(msg);
		byte[] sendbuff = dataout.toByteArray();
		DatagramPacket packet = new DatagramPacket(sendbuff, sendbuff.length, InetAddress.getByName(TRACKER_ADDRESS), TRACKER_PORT);
		socket.send(packet);
		out.close();
	}

	private TrackerMessage receive() throws Exception {
		byte[] recvBuff = new byte[1024*32];
		DatagramPacket packet = new DatagramPacket(recvBuff, recvBuff.length);
		socket.receive(packet);
		datain = new ByteArrayInputStream(recvBuff);
		in = new ObjectInputStream(datain);
		TrackerMessage msg = (TrackerMessage)in.readObject();
		in.close();
		return msg;
	}

	public static void update(long id, DatagramSocket socket, int port, String local) throws Exception {
		TrackerManager tracker = new TrackerManager(socket);
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.UPDATE);
		msg.setPeerID(id);
		msg.setPrivatePort(port);
		msg.setPrivateIP(local);
		tracker.send(msg);
		tracker.receive();
	}

	public static void exit(long id) throws Exception {
		TrackerManager tracker = new TrackerManager();
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.EXIT);
		msg.setPeerID(id);
		tracker.send(msg);
		tracker.receive();
	}

	public static TrackerMessage initializeUpload(DatagramSocket socket, String fileName, long fileSize, int port, String local) throws Exception {
		TrackerManager tracker = new TrackerManager(socket);
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.UPLOAD);
		msg.setFileName(fileName);
		msg.setFileSize(fileSize);
		msg.setPrivatePort(port);
		msg.setPrivateIP(local);
		tracker.send(msg);
		return tracker.receive();
	}

	public static TrackerMessage getDownloadInfo(DatagramSocket socket, String fileName, int port, String local) throws Exception {
		TrackerManager tracker = new TrackerManager(socket);
		TrackerMessage msg = new TrackerMessage();
		msg.setCmd(TrackerMessage.MODE.DOWNLOAD);
		msg.setFileName(fileName);
		msg.setPrivatePort(port);
		msg.setPrivateIP(local);
		tracker.send(msg);
		return tracker.receive();
	}

	public static Set<String> getFileList(DatagramSocket socket) throws Exception {
		TrackerManager tracker = new TrackerManager(socket);
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
