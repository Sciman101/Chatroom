import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.Socket;
import java.net.URL;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.swing.text.html.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;


/*
 * Class declaration for a chatroom client object
 */
public class ChatroomClient extends JFrame {

	private static final int PORT = 3030;//Port to use
	
	//JFrame components
	private JTextField messageField;
	private JButton sendButton;
	private JTextPane messagePane;
	private JLabel statusLabel;
	private JLabel typingLabel;
	private JTextPane userList;

	//Networking components
	private Socket mySocket;
	private PrintWriter out;
	private BufferedReader in;

	private boolean connected;
	private String username;

	private HashSet<String> usernames;
	private ArrayList<String> typingUsers;

	private boolean isTyping;

	/**
	 * Create a new chatroom object
	 */
	public ChatroomClient() throws IOException {

		//Configure jframe
		setSize(640,480);
		setTitle("Chatroom - Disconnected");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		
		initComponents();
		
		setVisible(true);

		usernames = new HashSet<String>();
		typingUsers = new ArrayList<String>();

		//Initialize networking components
		run();
	}
	
	/**
	 * Initialize all JFrame components
	 */
	private void initComponents() {
		
		//Initialize JFrame components
		messageField = new JTextField("");
		sendButton = new JButton("Send");
		messagePane = new JTextPane();
		statusLabel = new JLabel("Disconnected");
		typingLabel = new JLabel();
		userList = new JTextPane();
		
		userList.setEditable(false);
		userList.setBorder(new CompoundBorder(new BevelBorder(0), new BevelBorder(1)));
		messagePane.setContentType("text/html");
		messagePane.setEditable(false);
		messagePane.setBorder(new CompoundBorder(new BevelBorder(0), new BevelBorder(1)));

		//Create action listener
		ActionListener al = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String msg = messageField.getText();
				setIsTyping(false);
				handleMessageOut(msg);
            }
		};

		//Create key listener
		messageField.addKeyListener(new KeyAdapter() {
			@Override
		    public void keyTyped(KeyEvent e) {
		    	if (e.getKeyChar() == KeyEvent.VK_BACK_SPACE) {
		    		setIsTyping(false);
		    	}else if (!isTyping) {
			        setIsTyping(true);
		    	}
		    }
		});

		sendButton.addActionListener(al);
		messageField.addActionListener(al);
		
		//Add elements to jframe
		JPanel messagePanel = new JPanel();
		messagePanel.setLayout(new BorderLayout());
		messagePanel.add(messageField,BorderLayout.CENTER);
		messagePanel.add(sendButton,BorderLayout.EAST);
		messagePanel.add(typingLabel,BorderLayout.NORTH);
		
		JPanel userPanel = new JPanel();
		userPanel.setLayout(new BorderLayout());
		userPanel.add(new JLabel("Connected Users:"),BorderLayout.NORTH);
		userPanel.add(new JScrollPane(userList),BorderLayout.CENTER);
		userList.setPreferredSize(new Dimension(128,-1));
		
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(messagePanel,BorderLayout.SOUTH);
		getContentPane().add(userPanel, BorderLayout.EAST);
		getContentPane().add(new JScrollPane(messagePane), BorderLayout.CENTER);
		getContentPane().add(statusLabel, BorderLayout.NORTH);


		//Add on close window listener
		addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
            	if (connected) {
	                int i = JOptionPane.showConfirmDialog(null, "Disconnect?");
	                if(i==0) {
	                	//Disconnect and close client
	                	out.println("DISCONNECT"+username);
	                	dispose();
	                    System.exit(0);
	                }
            	}else{
            		//No need to DC, just close
            		dispose();
	                System.exit(0);
            	}
            }
        });
		
	}

	/**
	* Handle an outgoing message
	* @param msg The message
	*/
	private void handleMessageOut(String msg) {
		//Is the message obscenely long?
		if (msg.length() > 2000) {
			printMessage(username + ", keep messages under 2000 characters to be courteous to others!",2);
    	}else{
    		//Is the message a hyperlink?
    		try {
    			URL url = new URL(msg);
    			msg = "<a href=\""+msg+"\">"+msg+"</a>";//TODO Make this actually work
    		}catch (Exception e) {
    			//It's not a hyperlink
    		}

    		//Send message to other clients and clear text box
    		out.println("MESSAGE"+msg);
        	messageField.setText("");
    	}
	}

	/**
	* Get a string from the user
	* @param prompt What to prompt the user with
	*/
	private String getString(String prompt) {
		return JOptionPane.showInputDialog(
            this,
            prompt,
            prompt,
            JOptionPane.QUESTION_MESSAGE);
	}

	/**
	* Start client
	*/
	private void run() throws IOException {

		String ip = getString("Enter ip address:");
		if (ip.length() <= 0) ip = "127.0.0.1";
		mySocket = new Socket(ip,PORT);

		out = new PrintWriter(mySocket.getOutputStream(),true);
		in = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

		//Start main connection loop
		connected = true;
		while (connected) {

			String line = in.readLine();
			if (line == null) continue;

			if (line.startsWith("GETUSERNAME")) {
				//Send out username
				username = getString("Enter user name:");
				out.println(username);
				//Update display so that we show we're connected and what our name is
				updateConnectionLabel();

			}else if (line.startsWith("USERLIST")) {
				//Get list of users
				String[] list = line.substring(8).split("\\|");
				for (String n : list)
					usernames.add(n);
				updateNameDisplay();

			}else if (line.startsWith("NEWUSER")) {
				//We've got a new user
				String name = line.substring(7);
				usernames.add(name);
				updateNameDisplay();
				printMessage(name + " has joined the server",1);

			}else if (line.startsWith("DISCONNECT")) {
				//Disconnect user
				String name = line.substring(10);
				usernames.remove(name);
				updateNameDisplay();
				printMessage(name + " has left the server",1);

			}else if (line.startsWith("MESSAGE")) {
				//Print message!
				printMessage(line.substring(7),0);

			}else if (line.startsWith("STARTTYPE")) {
				typingUsers.add(line.substring(9));
				updateTypingLabel();

			}else if (line.startsWith("STOPTYPE")) {
				typingUsers.remove(line.substring(8));
				updateTypingLabel();
			}


		}

	}

	private void setIsTyping(boolean typing) {
		isTyping = typing;
		out.println((typing ? "STARTTYPE" : "STOPTYPE") + username);
	}

	private void updateTypingLabel() {
		if (typingUsers.size() == 0) {
			typingLabel.setVisible(false);
		}else{
			typingLabel.setVisible(true);
			String txt = "";
			int size = typingUsers.size();
			if (size == 1) {
				txt = typingUsers.get(0) + " is";
			}else{
				for (int i=0;i<size;i++) {
					txt += typingUsers.get(i);
					if (i < size-2)
						txt += ", ";
					else if (i == size-2)
						txt += " and ";
				}
				txt += "are";
			}
			txt += " typing";
			typingLabel.setText(txt);
		}
	}

	/**
	* Append text to a JTextPane
	* @param pane The pane to append to
	* @param str The string to append to the pane
	*/
	private void append(JTextPane pane, String str) {
		HTMLDocument doc = (HTMLDocument)pane.getDocument();
		HTMLEditorKit editorKit = (HTMLEditorKit)pane.getEditorKit();
		try {
			editorKit.insertHTML(doc, doc.getLength(), str, 0, 0, null);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	* Print a message to the message log
	* @param msg The message to append
	* @param style Set to an int between 0-2 to determine stylization of message
	*/
	private void printMessage(String msg, int style) {
		if (style == 1) msg = "<b>"+msg+"</b>";
		if (style == 2) msg = "<i>"+msg+"</i>";
		append(messagePane,msg+"<br>");
	}

	private void updateNameDisplay() {
		String txt = "";
		for (String n : usernames)
			txt += n + "\n";
		userList.setText(txt);
	}

	/**
	* Update the labels to say if we're connected or not
	*/
	private void updateConnectionLabel() {
		statusLabel.setText(connected ? "Connected as " + username : "Disconnected");
		setTitle("Chatroom - " + (connected ? "Connected" : "Disconnected"));
	}

	/**
	 * Main program entrypoint
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		//Create new chat client object
		@SuppressWarnings("unused")
		ChatroomClient client = new ChatroomClient();
	}
	
}
//10.107.55.16