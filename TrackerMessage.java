import java.util.*;
import java.io.*;

public class TrackerMessage implements Serializable {

	public enum MODE {
		LIST, UPLOAD, DOWNLOAD
	}

	private MODE cmd; //0 - getFileList; 1 - download; 2 - upload
	private long peerID;
	private String fileName;
	private long fileSize;
	private ArrayList<PeerInfo> peerList;
	private Set<String> fileList;
	
	public TrackerMessage() {
	}

	public MODE getCmd() {
		return this.cmd;
	}
	
	public long getPeerID() {
		return this.peerID;
	}

	public String getFileName(){
		return this.fileName;
	}

	public long getFileSize() {
		return this.fileSize;
	}

	public ArrayList<PeerInfo> getPeerList() {
		return this.peerList;
	}

	public Set<String> getFileList() {
		return this.fileList;
	}

	public void setCmd(MODE cmd) {
		this.cmd = cmd;
	}
	
	public void setPeerID(long id) {
		this.peerID = id;
	}

	public void setFileName(String name) {
		this.fileName = name;
	}

	public void setFileSize(long size) {
		this.fileSize = size;
	}

	public void setPeerList(ArrayList<PeerInfo> list) {
		this.peerList = list;
	}

	public void setFileList(Set<String> list) {
		this.fileList = list;
	}
	
}
