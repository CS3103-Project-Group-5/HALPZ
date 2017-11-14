import java.io.*;
import java.net.*;
import java.util.*;

class Tracker {

	private static HashMap<Long, PeerInfo> peerMap = new HashMap<Long, PeerInfo>();
	private static HashMap<String, FileInfo> fileList = new HashMap<String, FileInfo>();
	private static final int NUM_PEERS_TO_SEND = 10;

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        int port = 1234, clientPort;
        InetAddress clientIP;
        TrackerMessage incomingMessage, outgoingMessage;
        ObjectInputStream ois;
        ObjectOutputStream oos;
        ByteArrayInputStream in;
        ByteArrayOutputStream out;
        byte[] receiveData = new byte[1024*64];
        
        DatagramSocket serverSocket = new DatagramSocket(port);
        
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            byte[] data = receivePacket.getData();
            in = new ByteArrayInputStream(data);
            ois = new ObjectInputStream (in);
            incomingMessage = (TrackerMessage)ois.readObject();
            System.out.println("Tracker message received");
            clientIP = receivePacket.getAddress();
            clientPort = receivePacket.getPort();
            System.out.println("Connected to " + clientIP + " at port " + clientPort);
            
            outgoingMessage = processMessage(incomingMessage, clientIP.getHostAddress(), clientPort);
            out = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(out);
            oos.writeObject(outgoingMessage);
            byte[] sendData = out.toByteArray();
            DatagramPacket sendPacket = new DatagramPacket(sendData,sendData.length,clientIP,clientPort);
            serverSocket.send(sendPacket);
            System.out.println("Reply to " + clientIP + " at port " + clientPort);
        }
    }


	private static TrackerMessage processMessage(TrackerMessage incomingMessage, String peerIP, int peerPort) {
		TrackerMessage.MODE cmd = incomingMessage.getCmd();
        long peerID =incomingMessage.getPeerID();
        int peerPrivatePort = incomingMessage.getPrivatePort();
        if (peerID == 0){
            peerID = generateID();
        }
		TrackerMessage outgoingMessage = new TrackerMessage();
		String requestedFile;
		String peerPrivateIP;

		switch(cmd) {
			case FILELIST:
				outgoingMessage.setFileList(new HashSet<String>(fileList.keySet()));
				break;

			case FILEINFO:
				requestedFile = incomingMessage.getFileName();
				long fileSize = getFileSize(requestedFile);
				outgoingMessage.setFileSize(fileSize);
				outgoingMessage.setFileName(requestedFile);
				break;

			case DOWNLOAD:
				requestedFile = incomingMessage.getFileName();
				ArrayList<PeerInfo> peerListToSend = getPeerInfoListToSend(requestedFile);
				long requestedFileSize = getFileSize(requestedFile);
				peerPrivateIP = incomingMessage.getPrivateIP();
				outgoingMessage.setPeerList(peerListToSend);
                outgoingMessage.setPeerID(peerID);
                outgoingMessage.setPublicIP(peerIP);
				outgoingMessage.setFileSize(requestedFileSize);
				if (peerListToSend.isEmpty()) break;
				createNewPeerRecord(peerID, new PeerInfo(peerID, peerIP, peerPort, peerPrivateIP,peerPrivatePort,requestedFile));
				associatePeerWithFile(peerID, requestedFile);
				break;

			case UPLOAD: //no need to input any fields in the return message --> send an empty TrackerMessage object as ACK.
                String newFileName = incomingMessage.getFileName();
                outgoingMessage.setPeerID(peerID);
                outgoingMessage.setPublicIP(peerIP);
                long newFileSize = incomingMessage.getFileSize();
                peerPrivateIP = incomingMessage.getPrivateIP();
                createNewPeerRecord(peerID, new PeerInfo(peerID, peerIP, peerPort, peerPrivateIP,peerPrivatePort,newFileName));
                createNewFileRecord(newFileName, new FileInfo(peerID, newFileSize));
                break;
                
            case UPDATE:
                //update peer port
                peerPrivateIP = incomingMessage.getPrivateIP();
                PeerInfo peer = peerMap.get(new Long(peerID));
                peer.setPeerPrivateIP(peerPrivateIP);
                peer.setPeerPort(peerPort);
                peer.setPeerPrivatePort(peerPrivatePort);
                break;

            case EXIT: 
            	peerID = incomingMessage.getPeerID();
            	if (peerMap.containsKey(peerID)){
            		PeerInfo p = peerMap.get(new Long(peerID));
            		String fn = p.getFileName();
            		if (fileList.containsKey(fn)){
            			FileInfo f = fileList.get(fn);
            			f.deletePeerID(peerID);
            		}
            		peerMap.remove(peerID);
            	} 
                break;
		}
		return outgoingMessage;
	}
    
    private static long generateID() {
        Random rnd = new Random();
        long peerID =rnd.nextLong();
        while (peerMap.containsKey(peerID)){
            peerID = rnd.nextLong();
        }
        return peerID;
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
		if (fileList.get(fileName) == null) {
			return -1;
		}
		return (fileList.get(fileName)).getFileSize();
	}

	private static ArrayList<PeerInfo> getPeerInfoListToSend(String fileName) {
		FileInfo requiredFile = fileList.get(fileName);
		if (requiredFile == null) return new ArrayList<PeerInfo>() ;
		ArrayList<Long> peerIDList = requiredFile.getPeerIDList();
		Collections.shuffle(peerIDList); //we can use other shuffle algorithms if the peerIDList gets too large to maintain.
		ArrayList<PeerInfo> peerInfoList = new ArrayList<PeerInfo>();
		int possibleNumPeerToSend = Math.min(peerIDList.size(), NUM_PEERS_TO_SEND);
		for (int i = 0; i < possibleNumPeerToSend; i++) {
			peerInfoList.add(peerMap.get(peerIDList.get(i)));
		}
		return peerInfoList;
	}


	private static boolean peerIDListGreaterThanNumPeersToSend(int size) {
		return size>NUM_PEERS_TO_SEND;
	}
}
