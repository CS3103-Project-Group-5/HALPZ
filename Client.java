import java.io.File;
import java.net.InetAddress;
import java.util.*;
import java.io.*;
import java.net.*;

public class Client {

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        // Wait for a request of user
        int option = -1, chunkSize = 256 * 1024;
        String input;
        long fileSize;
        ArrayList<PeerInfo> peerList;
        BitSet chunkList;
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
                    input = scanner.nextLine();
                    fileSize = TrackerManager.getFileSize(input);
					if (fileSize == -1) {
                    	System.out.println("File does not exist.");
					} else {
                    	System.out.println("Filename: " + input + ", Filesize: " + fileSize);
                    }
					break;

                case 3:
                    System.out.println("Please input file name.");
                    input = scanner.nextLine();
                    TrackerMessage msg = TrackerManager.getDownloadInfo(input);
                    fileSize = msg.getFileSize();
                    peerList = msg.getPeerList();
                    chunkList = new BitSet((int) Math.ceil(fileSize / (double) chunkSize)); // <-- need to load file
                    /* call peer class to do the p2p */
                    break;

                case 4:
                    System.out.println("Please input location of file to upload.");
                    input = scanner.nextLine();
                    File file = new File(input);
                    if (!file.exists()) {
                        System.out.println("File not found, exiting...");
                        break;
                    }
                    fileSize = file.length();
                    chunkList = new BitSet((int) Math.ceil(fileSize / (double) chunkSize));
                    chunkList.flip(0, chunkList.length());
                    TrackerManager.initializeUpload(input, fileSize);
                    break;

            }
        }

        scanner.close();
    }

    /* Helper Methods */
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
}
class TrackerManager {

	private static final String TRACKER_ADDRESS = "128.199.108.79";
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
