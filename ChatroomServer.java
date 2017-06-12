import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ChatroomServer {

	private static final int PORT = 3030;//Port to use

	private static HashSet<PrintWriter> writers;
	private static HashSet<String> usernames;

	//Server properties
	private static String serverName = "Chatroom Server";
	private static ArrayList<String> blacklistedPhrases;

	/**
	* Main program entrypoint
	*/
	public static void main(String[] args) throws IOException{

		if (args.length > 0) serverName = args[0];

		ServerSocket listener = new ServerSocket(PORT);
		writers = new HashSet<PrintWriter>();
		usernames = new HashSet<String>();

		loadBlacklist();

		System.out.println("Server started on port " + PORT);
		if (serverName != null)
			System.out.println("Server name: " + serverName);

		try {
			while (true) {
				new Handler(listener.accept()).start();
			}
		}finally{
			//shut down server
			System.out.println("Shutting down server...");
			listener.close();
		}

	}

	/**
	* Load all blacklisted phrases
	*/
	private static void loadBlacklist() {

		blacklistedPhrases = new ArrayList<String>();

		try {
			//Create bufferedreader to read blacklisted phrases file
			BufferedReader reader = new BufferedReader(new FileReader("blacklisted_phrases.txt"));
			String line;
			while ((line = reader.readLine()) != null) {
				blacklistedPhrases.add(line);
			}
		}catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	* The handler class deals with a single client at a time
	*/
	private static class Handler extends Thread {

		private String username;
		private Socket mySocket;
		//Stream handlers
		private PrintWriter out;
		private BufferedReader in;

		private boolean connected;

		/**
		* Constructor
		* @param s The socket to assign the handler
		*/
		public Handler(Socket s) {
			mySocket = s;
		}

		/**
		* Thread start
		*/
		public void run() {

			try {
				//Initialize stream handlers
				out = new PrintWriter(mySocket.getOutputStream(),true);
				in = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

				System.out.println("New user connected, getting username...");
				out.println("SERVERNAME"+serverName);

				//First, get client username
				while (true) {
					out.println("GETUSERNAME");
					String line = in.readLine();
					//Line cannot be empty, in the current username list, or a blacklisted word
					if (line != null && !usernames.contains(line) && blacklistedPhrases.indexOf(line) == -1) {
						username = line;//Get username
						synchronized (usernames) {
							usernames.add(username);
						}
						break;
					}
				}

				//Add username and writers
				synchronized (writers) {
					writers.add(out);
				}

				//Broadcast connect message to all other clients
				for (PrintWriter w : writers) {
					w.println("NEWUSER" + username);
				}

				//Tell user to update user list
				String users = "";
				for (String n : usernames)
					users += n + "|";
				out.println("USERLIST"+users);

				System.out.println(username + " has connected");

				//Now, try and handle incoming data
				while (true) {

					String line = in.readLine();
					//Handle different incoming messages
					if (line.startsWith("DISCONNECT")) {
						//Disconnect user
						for (PrintWriter w : writers) {
							w.println("DISCONNECT"+username);
						}
						break;

					}else if (line.startsWith("MESSAGE")) {
						//Broadcast message to all other clients

						//Filter blocked words from message
						String msg = line.substring(7);
						String finalMsg = "";

						//Cycle through all words
						for (String word : msg.split(" ")) {
							//Check if a word is in the blacklisted message list
							if (blacklistedPhrases.indexOf(word.toLowerCase().replaceAll("\\<.*?>","")) != -1)
								for (int i=0;i<word.length();i++)
									finalMsg += "*";
							else
								finalMsg += word;
							finalMsg += " ";
						}

						//Print the message header and username, followed by the actual message
						for (PrintWriter w : writers) {
							w.println("MESSAGE" + username);
							w.println(finalMsg);
						}

					}else if (line.startsWith("STARTTYPE") || line.startsWith("STOPTYPE")) {
						//Forward message to start or stop typing
						for (PrintWriter w : writers) {
							if (w != out)
								w.println(line);
						}
					}else if (line.startsWith("CHANGENAME")) {
						//A user wants to change their name..
						int i = line.indexOf("\\|");
						String originalName = line.substring(10,i);
						String newName = line.substring(i+2,line.length());

						//Is that name not allowed?
						if (usernames.contains(newName) || blacklistedPhrases.indexOf(newName.toLowerCase()) != -1) {

							out.println("BADNAME"+originalName);
							System.out.println(originalName + " has tried to change their name to " + newName);

						}else{

							synchronized (usernames) {
								usernames.remove(originalName);
								usernames.add(newName);
							}

							username = newName;

							System.out.println(originalName + " has changed their name to " + newName);

							for (PrintWriter w : writers) {
								w.println("CHANGENAME"+originalName+"\\|"+newName);
							}

						}
					}

				}

			}catch (IOException e) {
				e.printStackTrace();
			}finally{
				//Close client
				if (out != null)
					writers.remove(out);
				if (username != null)
					usernames.remove(username);
				try {
					mySocket.close();
				}catch (Exception e) {
					System.out.println("Whoops, couldn't close client");
				}
				System.out.println(username + " has disconnected");
			}

		}

	}
	
}