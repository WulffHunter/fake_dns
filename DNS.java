import java.io.*;
import java.net.*;
import java.util.*;

class QueryResolution {
	Record record;
	Boolean isNS;
	Boolean isAType;

	public QueryResolution(Record recordIn, Boolean isNSIn, Boolean isATypeIn) {
		record = recordIn;
		isNS = isNSIn;
		isAType = isATypeIn;
	}

	public Record getRecord() {
		return record;
	}

	public boolean getIsNS() {
		return isNS;
	}

	public boolean getIsAType() {
		return isAType;
	}

	public String toString() {
		return "(" + record + "," + isNS + "," + isAType + ")";
	}
}

class DNServer {
	ArrayList<Record> entries;

	InetAddress myIP;
	String dnsName;
	DatagramSocket socket;
	int myPort;

	boolean booted;

	byte[] receiveData;
	byte[] sendData;

	boolean received;

	boolean waitingOnQuery;
	InetAddress waitIP;
	InetAddress askerIP;
	int askerPort;

	public DNServer() {
		booted = false;
		myPort = -1;
	}

	private boolean insert(String name, String value, String type) {
		try {
			entries.add(new Record(name, value, type));
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private boolean insert(Record rec) {
		if (rec == null) {
			return false;
		}
		entries.add(rec);
		return true;
	}

	private int remove(String name) {
		int i;
		for (Record rec : entries) {
			if (rec.getName().equals(name)) {
				i = entries.indexOf(rec);
				entries.remove(rec);
				return i;
			}
		}
		return -1;
	}

	private QueryResolution query(String name) {
		Record rec = null;
		String tempname = name;
		boolean isNS = false;
		boolean justStarted = true;
		boolean set = false;
		while (justStarted || (rec != null && set)) {
			set = false;
			for (Record temprec : entries) {
				if (temprec.getName().equals(tempname)) {
					set = true;
					rec = temprec;
					tempname = rec.getValue();
					if (rec.getType().equals("NS")) {
						isNS = true;
					}
				}
			}
			justStarted = false;
		}
		if (rec != null) {
			return new QueryResolution(rec, isNS, rec.getType().equals("A"));
		} else {
			return null;
		}
	}

	private boolean exists(String name) {
		boolean found = false;
		for (Record rec : entries) {
			if (rec.getName().equals(name)) {
				found = true;
			}
		}
		return found;
	}

	public void boot(Scanner s) {
		System.out.println("Beginning boot process");
		entries = new ArrayList<Record>();
		System.out.println("Please enter machine name");
		myIP = null;
		while (myIP == null) {
			try {
				myIP = InetAddress.getByName(s.nextLine());
			} catch (Exception e) {
				System.err.println("Host Not Found: " + e.getMessage());
			}
		}
		System.out.println("This computer's IP address is " + myIP);
		System.out.println("To load cache, please enter system name.");
		dnsName = s.nextLine();
		load(dnsName + ".txt");
		System.out.println("Do you want to manually insert A-type records?");
		boolean more = false;
		boolean set = false;
		while (!set) {
			try {
				more = s.nextBoolean();
				s.nextLine();
				set = true;
			} catch (Exception e) {
				s.nextLine();
				more = false;
				set = false;
				System.err.println("Input not understood, please try again.");
			}
		}
		while (more) {
			set = false;
			System.out.println("Please enter the name:");
			String name = s.nextLine();
			System.out.println("Please enter the value:");
			String value = s.nextLine();
			if (!insert(name, value, "A")) {
				System.out.println("Record contains commas, could not be inserted.");
			}
			System.out.println("Do you want to insert more A-type records?");
			while (!set) {
				try {
					more = s.nextBoolean();
					s.nextLine();
					set = true;
				} catch (Exception e) {
					s.nextLine();
					more = false;
					set = false;
					System.err.println("Input not understood, please try again.");
				}
			}
		}
		set = false;
		System.out.println("Please enter the desired port number:");
		while (!set) {
			try {
				myPort = s.nextInt();
				set = setSocket(myPort);
				System.out.println((set) ? "Socket created on port " + myPort + "." : "Socket was not created.");
				s.nextLine();
			} catch (Exception e) {
				s.nextLine();
				System.err.println("Please enter a socket number.");
			}
		}
		booted = true;
		System.out.println("Boot complete");
	}

	public String list() {
		String list = "";
		for (Record rec : entries) {
			list += rec.toString() + "\n";
		}
		return list;
	}

	public boolean load(String filename) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line;
			String[] parts;
			while ((line = br.readLine()) != null) {
				insert(new Record(line));
			}
			return true;
		} catch (Exception e){
			return false;
		}
	}

	public boolean export(String filename) {
		try {
			FileWriter writer = new FileWriter(filename);
			writer.write(list());
			writer.close();
			return true;
		} catch (Exception e) {
			System.err.println(e);
			return false;
		}
	}

	public void setName(String name) {
		dnsName = name;
	}

	public boolean setSocket(int port) {
		try {
			myPort = port;
			socket = new DatagramSocket(myPort);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public DatagramPacket handleRequest(DatagramPacket receivePacket, boolean wasWaitingFor) {
		String data = (new String(receivePacket.getData()));
		Record rec = null;
		boolean isAType = false;
		try {
			rec = new Record(data);
			if (!rec.getType().equals("A")){
				data = rec.getValue();
			} else {
				isAType = true;
				System.out.println("Is A-type");
				if (!wasWaitingFor) {
					data = rec.getName();
				}
			}
		} catch (Exception e) { }
		QueryResolution res;
		String[] url = data.trim().toLowerCase().replace("http://", "").replace("https://", "").replace("www.", "").split("/");
		System.out.println(url[0]);
		if (isAType && rec != null && wasWaitingFor) {
			res = new QueryResolution(rec, false, true);
		} else {
			res = query(url[0]);
		}
		System.out.println(res);
		if (res == null) {
			System.out.println("Url " + url[0] + " could not be found.");
		} else {
			System.out.println("FOUND");
			System.out.println(res);
			if (res.getIsNS() && res.getIsAType()) {
				System.out.println("Is A-type");
				boolean waitIPcreated = false;
				waitingOnQuery = true;
				int waitPort = myPort;
				try {
					waitIP = myIP;
					waitPort = new Integer(res.getRecord().getValue());
					waitIPcreated = true;
				} catch (NumberFormatException e) {
					waitIPcreated = false;
				}
				if (!waitIPcreated) {
					try {
						waitIP = InetAddress.getByName(res.getRecord().getValue());
						waitIPcreated = true;
					} catch (UnknownHostException e) {
						waitIPcreated = false;
					}
				}
				if (waitIPcreated) {
					if (!wasWaitingFor) {
						askerIP = receivePacket.getAddress();
						askerPort = receivePacket.getPort();
					}
					sendData = res.getRecord().toString().getBytes();
					return new DatagramPacket(sendData, sendData.length, waitIP, waitPort);
				} else {
					System.err.println("Error: " + res.getRecord().getValue() + " could not be resolved to an IP address.");
				}
			} else {
				String sendQuery = res.getRecord().toString();
				sendData = sendQuery.getBytes();
				if (wasWaitingFor) {
					waitingOnQuery = false;
					return new DatagramPacket(sendData, sendData.length, askerIP, askerPort);
				} else {
					return new DatagramPacket(sendData, sendData.length, receivePacket.getAddress(), receivePacket.getPort());
				}
			}
		}
		return null;
	}

	public void server() {
		if (dnsName != null && myIP != null && socket != null && myPort != -1) {
			receiveData = new byte[2048];
			sendData = new byte[2048];
			System.out.println("Waiting on port " + myPort);
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			DatagramPacket queryPacket;
			try {
				socket.receive(receivePacket);
				received = true;
			} catch (Exception e) {
				System.err.println(e);
				received = false;
			}
			if (received) {
				System.out.println("PACKET RECEIVED: " + new String(receivePacket.getData()));
				queryPacket = handleRequest(receivePacket, (waitingOnQuery && receivePacket.getAddress().equals(waitIP)));
				if (queryPacket != null) {
					try {
						socket.send(queryPacket);
					} catch (IOException e) {
						System.err.println("Packet failed to send. " + e.getMessage());
					}
				}
			}
		}
	}

	public void loop() {
		boolean loop = true;
		boolean success = true;
		Scanner s = new Scanner(System.in);
		System.out.println("Please type boot to initialize the DNS Server.");
		while (loop) {
			String input = s.nextLine().toLowerCase();
			switch (input) {
				case "boot": boot(s);
					break;
				case "load": System.out.println("Please enter filename:");
					load(s.nextLine() + ".txt");
					break;
				case "name": System.out.println("Please enter the machine name:");
					setName(s.nextLine());
					break;
				case "export": System.out.println("Please enter the filename:");
					success = export(s.nextLine() + ".txt");
					System.out.println((success) ? "Exported successfully." : "File could not be written.");
					break;
				case "ls": System.out.print(list());
					break;
				case "exists": System.out.println("Please enter a record name:");
					success = exists(s.nextLine());
					System.out.println((success) ? "Entry found." : "Entry not found.");
					break;
				case "query": System.out.println("Please enter a record name:");
					QueryResolution q = query(s.nextLine());
					System.out.println(q);
					break;
				case "ins": System.out.println("Please enter the new record name:");
					String name = s.nextLine();
					System.out.println("Please enter the record value:");
					String value = s.nextLine();
					System.out.println("Please enter the record type:");
					String type = s.nextLine();
					success = insert(name, value, type);
					System.out.println((success) ? "Entry created." : "Record contains commas, could not be inserted.");
					break;
				case "del": System.out.println("Please enter the record you wish to delete:");
					success = (remove(s.nextLine()) > -1);
					System.out.println((success) ? "Entry " + success + " deleted" : "Entry not found");
					break;
				case "port": System.out.println("Please enter the desired port number:");
					success = false;
					while (!success) {
						try {
							success = setSocket(s.nextInt());
							s.nextLine();
							System.out.println((success) ? "Socket created." : "Socket was not created.");
						} catch (Exception e) {
							s.nextLine();
							System.err.println("Please enter a socket number.");
						}
					}
					break;
				case "run": if (booted) {
						loop = false;
					} else {
						System.out.println("Please boot before running server.");
						loop = true;
					}
					break;
				default: System.out.println("Input not understood.");
					break;
			}
		}
		while (true) {
			server();
		}
	}
}

public class DNS {
	public static void main(String args[]) throws Exception {
		DNServer dns = new DNServer();
		dns.loop();
	}
}