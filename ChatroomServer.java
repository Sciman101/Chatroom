import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ChatroomServer {

	private static final int PORT = 3030;//Port to use

	private static HashSet<PrintWriter> writers;
	private static HashSet<String> usernames;

	/**
	* Main program entrypoint
	*/
	public static void main(String[] args) throws IOException{

		ServerSocket listener = new ServerSocket(PORT);
		writers = new HashSet<PrintWriter>();
		usernames = new HashSet<String>();
		System.out.println("Server started on port " + PORT);

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

				//First, get client username
				while (true) {
					out.println("GETUSERNAME");
					String line = in.readLine();
					if (line != null) {
						username = line;//Get username
						break;
					}
				}

				//Add username and writers
				usernames.add(username);
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
						System.out.println("Message sent by " + username + ": " + line.substring(7));
						String msg = "MESSAGE<b>[" + username + "]</b> " + line.substring(7);
						for (PrintWriter w : writers) {
							w.println(msg);
						}

					}else if (line.startsWith("STARTTYPE") || line.startsWith("STOPTYPE")) {
						for (PrintWriter w : writers) {
							if (w != out)
								w.println(line);
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