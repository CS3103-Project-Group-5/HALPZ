import java.io.*;

public class PeerInfo implements Serializable {
	private long peerID;
	private String peerIP;
	private int peerPort;
	private String fileName;
	
	public PeerInfo(long peerID, String peerIP, int peerPort, String fileName){
		this.peerID = peerID;
		this.peerIP = peerIP;
		this.peerPort = peerPort;
		this.fileName = fileName;
	}
	
	public long getPeerID(){
		return this.peerID;
	}
	public String getPeerIP(){
		return this.peerIP;
	}
	public int getPeerPort(){
		return this.peerPort;
	}
	public String getFileName(){
		return this.fileName;
	}
	
}
