import java.util.ArrayList;
import java.io.*;

public class FileInfo implements Serializable {
	private ArrayList<Long> peerIDList = new ArrayList<Long>();
	private int totalChunkNumber;
	private long fileSize;
	private final double CHUNK_SIZE = 256; 
	
	public FileInfo (long seederPeerID, long fileSize) {
		this.peerIDList.add(seederPeerID);
		this.fileSize = fileSize;
		this.totalChunkNumber = (int)Math.ceil(fileSize/CHUNK_SIZE);
	}

	public ArrayList<Long> getPeerIDList() {
		return peerIDList;
	}

	public void addPeerID(long peerID) {
		this.peerIDList.add(peerID);
	}

	public void deletePeerID(long peerID) {
		this.peerIDList.remove(peerID);
	}

	public long getFileSize() {
		return fileSize;
	}

	
}
