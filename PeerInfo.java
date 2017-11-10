import java.io.*;

public class PeerInfo implements Serializable {
	private long peerID;
	private String peerIP;
    private String peerPrivateIP;
	private int peerPort;
	private String fileName;
	
	public PeerInfo(long peerID, String peerIP, int peerPort, String peerPrivateIP, String fileName){
		this.peerID = peerID;
		this.peerIP = peerIP;
		this.peerPort = peerPort;
		this.fileName = fileName;
        this.peerPrivateIP = peerPrivateIP;
	}
	
	public long getPeerID(){
		return this.peerID;
	}
    public String getPeerPrivateIP(){
        return this.peerPrivateIP;
    }
	public String getPeerIP(){
		return this.peerIP;
	}
	public int getPeerPort(){
		return this.peerPort;
	}
    public void setPeerPort(int peerPort){
        this.peerPort= peerPort;
    }
	public String getFileName(){
		return this.fileName;
	}
    public void setPeerPrivateIP(String peerPrivateIP){
        this.peerPrivateIP = peerPrivateIP;
    }
	
}
