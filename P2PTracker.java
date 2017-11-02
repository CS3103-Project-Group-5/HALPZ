import java.io.*;
import java.net.*;
import java.util.*;

class P2PTracker {

	private static HashMap<Long, PeerInfo> peerMap = new HashMap<Long, PeerInfo>();
	private static HashMap<String, FileInfo> fileList = new HashMap<String, FileInfo>();
	private static final int NUM_PEERS_TO_SEND = 10;

	public static void main(String[] args) throws IOException, ClassNotFoundException {
		int port = 1234, clientPort;
		String clientIP;
		byte[] buffer = new byte[256];
		TrackerMessage incomingMessage, outgoingMessage;
		ObjectInputStream ois;
		ObjectOutputStream oos;

		ServerSocket welcomeSocket = new ServerSocket(port);

		while (true) {
			Socket connectionSocket = welcomeSocket.accept();
			System.out.println("Connected to a client ... ");
			ois = new ObjectInputStream(connectionSocket.getInputStream()); 
			oos = new ObjectOutputStream(connectionSocket.getOutputStream());
			clientIP = (connectionSocket.getInetAddress()).getHostAddress();
			clientPort = connectionSocket.getPort();

			incomingMessage = (TrackerMessage)ois.readObject();
			outgoingMessage = processMessage(incomingMessage, clientIP, clientPort);
			oos.writeObject(outgoingMessage);		
		}
	}

	private static TrackerMessage processMessage(TrackerMessage incomingMessage, String peerIP, int peerPort) {
		TrackerMessage.MODE cmd = incomingMessage.getCmd();
		long peerID = incomingMessage.getPeerID();
		TrackerMessage outgoingMessage = new TrackerMessage();

		switch(cmd) {			
			case LIST: 
				outgoingMessage.setFileList(new HashSet<String>(fileList.keySet()));
				break;

			case DOWNLOAD:
				String requestedFile = incomingMessage.getFileName();
				ArrayList<PeerInfo> peerListToSend = getPeerInfoListToSend(requestedFile);
				long requestedFileSize = getFileSize(requestedFile);
				outgoingMessage.setPeerList(peerListToSend);
				outgoingMessage.setFileSize(requestedFileSize);
				createNewPeerRecord(peerID, new PeerInfo(peerID, peerIP, peerPort, requestedFile));				
				associatePeerWithFile(peerID, requestedFile);
				break;

			case UPLOAD: //no need to input any fields in the return message --> send an empty TrackerMessage object as ACK.
				String newFileName = incomingMessage.getFileName();
				long newFileSize = incomingMessage.getFileSize();
				createNewPeerRecord(peerID, new PeerInfo(peerID, peerIP, peerPort, newFileName));
				createNewFileRecord(newFileName, new FileInfo(peerID, newFileSize));
				break;
		}
		return outgoingMessage;
	}
	

	private static void createNewPeerRecord(long peerID, PeerInfo newPeer) {
		peerMap.put(peerID, newPeer);
	}

	private static void createNewFileRecord(String fileName, FileInfo newFile) {
		fileList.put(fileName, newFile);
	}

	private static void associatePeerWithFile(long peerID, String fileName) {
		FileInfo targetFile = fileList.get(fileName);
		targetFile.addPeerID(peerID);
	}

	private static void detachPeerFromFiles(long peerID) {
		PeerInfo targetPeer = peerMap.get(new Long(peerID));
		String targetFileName = targetPeer.getFileName();
		FileInfo targetFileInfo = fileList.get(targetFileName);
		targetFileInfo.deletePeerID(peerID);
	}

	private static long getFileSize(String fileName) {
		return (fileList.get(fileName)).getFileSize();
	}

	private static ArrayList<PeerInfo> getPeerInfoListToSend(String fileName) {
		FileInfo requiredFile = fileList.get(fileName);
		ArrayList<Long> peerIDList = requiredFile.getPeerIDList();
		Collections.shuffle(peerIDList); //we can use other shuffle algorithms if the peerIDList gets too large to maintain.
		ArrayList<PeerInfo> peerInfoList = new ArrayList<PeerInfo>();
		int possibleNumPeerToSend = Math.min(peerIDList.size(), NUM_PEERS_TO_SEND);
		for (int i = 0; i <=possibleNumPeerToSend; i++) {
			peerInfoList.add(peerMap.get(peerIDList.get(i)));
		}
		return peerInfoList;
	}

	private static boolean peerIDListGreaterThanNumPeersToSend(int size) {
		return size>NUM_PEERS_TO_SEND;
	}
}
