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
	private static final int TYPECHECK_DELAY = 500;
	
	//JFrame components
	private JTextField messageField;
	private JButton sendButton;
	private JTextPane messagePane, userList;
	private JMenuBar menu;
	private JMenu userMenu;
	private JPanel userPanel;
	private JLabel typingLabel;

	//Networking components
	private Socket mySocket;
	private PrintWriter out;
	private BufferedReader in;

	private boolean connected;
	private String username;
	private String serverName;

	private HashSet<String> usernames;
	private ArrayList<String> typingUsers;

	private boolean isTyping;
	private TypeTimer timer;
	private int lastType = 0;

	/**
	 * Create a new chatroom object
	 */
	public ChatroomClient() throws IOException {

		//Configure jframe
		setSize(640,480);
		setTitle("Chatroom - Disconnected");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		
		//Initialize all the JFrame stuff
		initComponents();
		
		setVisible(true);

		usernames = new HashSet<String>();
		typingUsers = new ArrayList<String>();

		timer = new TypeTimer();
		timer.start();

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
		typingLabel = new JLabel();
		userList = new JTextPane();

		menu = new JMenuBar();
		
		//Configure user list and message pane
		userList.setEditable(false);
		userList.setBorder(new CompoundBorder(new BevelBorder(0), new BevelBorder(1)));
		messagePane.setContentType("text/html");
		messagePane.setText("<html>");
		messagePane.setEditable(false);
		messagePane.setBorder(new CompoundBorder(new BevelBorder(0), new BevelBorder(1)));

		//Create action listener for sending messages
		ActionListener al = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String msg = messageField.getText();
				setIsTyping(false);
				handleMessageOut(msg);
            }
		};

		//Create key listener for showing typing message
		messageField.addKeyListener(new KeyAdapter() {
			@Override
		    public void keyTyped(KeyEvent e) {
		    	if (e.getKeyChar() != KeyEvent.VK_ENTER)
			    	setIsTyping(true);
		    }
		});

		//Add hyperlink listener so that we can open hyperlinks
		//https://stackoverflow.com/a/3693604 Credit for code
		messagePane.addHyperlinkListener(new HyperlinkListener() {
		    public void hyperlinkUpdate(HyperlinkEvent e) {
		        if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
		        	//If the desktop is a supported function...
		           if(Desktop.isDesktopSupported()) {
			           	try {
			           		//Open that URL
						    Desktop.getDesktop().browse(e.getURL().toURI());
						}catch (Exception ex) {
			          		ex.printStackTrace();
						}
					}
		        }
		    }
		});

		//Apply send button action listener to appropriate objects
		sendButton.addActionListener(al);
		messageField.addActionListener(al);


		//Configure menu bar
		userMenu = new JMenu("Disconnected");
		menu.add(userMenu);

		JMenuItem itemName = new JMenuItem("Change Name");
		JMenuItem itemDC = new JMenuItem("Disconnect");

		userMenu.add(itemDC);
		userMenu.add(itemName);

		//Add action listener to change name
		itemName.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//Send out username
				String newUsername = getString("Enter user name:");
				out.println("CHANGENAME"+username+"\\|"+newUsername);
				username = newUsername;
				//Update menu so that we show we're connected and what our name is
				updateMenu();
			}
		});

		//Add action listener to disconnect
		itemDC.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				disconnect();
			}
		});

		//Create formatting menu
		JMenu formatMenu = new JMenu("Format");
		menu.add(formatMenu);

		JMenuItem itemItalic = new JMenuItem("Italics");
		JMenuItem itemBold = new JMenuItem("Bold");
		JMenuItem itemHeader = new JMenuItem("Header");

		formatMenu.add(itemItalic);
		formatMenu.add(itemBold);
		formatMenu.add(itemHeader);

		//Create formatting action listeners
		itemItalic.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {formatSelection(0);}});
		itemBold.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {formatSelection(1);}});
		itemHeader.addActionListener(new ActionListener() {public void actionPerformed(ActionEvent e) {formatSelection(2);}});
		
		//------------------------------------------------------------------------------------------------------------------------

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
		getContentPane().add(menu, BorderLayout.NORTH);


		//Add on close window listener to disconnect from server
		addWindowListener(new WindowAdapter(){
            public void windowClosing(WindowEvent e){
            	disconnect();
            }
        });
		
	}

	/**
	* Disconnect the user from the server
	*/
	private void disconnect() {
		if (connected) {
            int i = JOptionPane.showConfirmDialog(null, "Disconnect?","Disconnect?",JOptionPane.YES_NO_OPTION);
            if(i==JOptionPane.YES_OPTION) {
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

	/**
	* Handle an outgoing message
	* @param msg The message
	*/
	private void handleMessageOut(String msg) {
		//Is the message obscenely long?
		if (msg.length() > 2000) {
			printMessage(username + ", keep messages under 2000 characters to be courteous to others!",2);
    	}else{
    		//Send message to other clients and clear text box
    		out.println("MESSAGE"+msg);
        	messageField.setText("");
        	setIsTyping(false);
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
	* Format text in the message box
	* @param type The type of formatting to apply
	*/
	private void formatSelection(int type) {

		int startPoint = messageField.getSelectionStart();
		int endPoint = messageField.getSelectionEnd();
		//Format text
		if (startPoint != endPoint) {

			String formatType = "";
			switch (type) {
				case 0: formatType = "i"; break;
				case 1: formatType = "b"; break;
				case 2: formatType = "h1"; break;
			}

			//Get message
			String full = messageField.getText();

			String start = full.substring(0,startPoint);
			String end = full.substring(endPoint,full.length());
			String select = full.substring(startPoint,endPoint);

			if (formatType.length() > 0) {
				messageField.setText(start + "<" + formatType + ">" + select + "</" + formatType + ">" + end);
				messageField.setSelectionStart(startPoint);
				messageField.setSelectionEnd(endPoint+5+(formatType.length()*2));
			}else{
				printMessage("Please select text to format!",2);
			}

		}

	}

	/**
	* Start client
	*/
	private void run() throws IOException {

		String ip;
		while (true) {
			//Wait until we get an actual, valid ip
			try {
				//Get the ip address to connect to
				ip = getString("Enter ip address:");
				//If the ip isn't present, substitute our local address
				if (ip.length() <= 0) ip = "127.0.0.1";
				//Initialize socket
				mySocket = new Socket(ip,PORT);

				break;
			}catch (Exception e) {
				System.out.println("Error: invalid IP address");
			}
		}
		

		//Initialize stream handlers
		out = new PrintWriter(mySocket.getOutputStream(),true);
		in = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));

		//Start main connection loop
		connected = true;
		while (connected) {

			String line = in.readLine();
			if (line == null) continue;//Skip empty lines

			//Based on the message header (the first word in the message), handle the message differently
			if (line.startsWith("GETUSERNAME")) {
				//Send out username
				username = getString("Enter user name:");
				out.println(username);
				//Update menu so that we show we're connected and what our name is
				updateMenu();

			}else if (line.startsWith("SERVERNAME")) {
				//Get server name
				serverName = line.substring(10);
				updateMenu();

			}else if (line.startsWith("USERLIST")) {
				//Get list of users
				String[] list = line.substring(8).split("\\|");
				for (String n : list)
					usernames.add(n);
				updateNameList();

			}else if (line.startsWith("NEWUSER")) {
				//We've got a new user
				String name = line.substring(7);
				usernames.add(name);
				updateNameList();
				printMessage(name + " has joined the server",1);

			}else if (line.startsWith("CHANGENAME")) {
				//Change the username of a person
				int i = line.indexOf("\\|");
				String originalName = line.substring(10,i);
				String newName = line.substring(i+2,line.length());

				usernames.remove(originalName);
				usernames.add(newName);

				updateNameList();

				printMessage(originalName + " has changed their name to " + newName,2);

			}else if (line.startsWith("BADNAME")) {
				//We tried to change our names to something not allowed
				username = line.substring(7);
				//Show a warning
				printMessage(username + ", that username is not allowed",2);
				//Update menu so that we show we're connected and what our name is
				updateMenu();

			}else if (line.startsWith("DISCONNECT")) {
				//Disconnect user
				String name = line.substring(10);
				usernames.remove(name);
				updateNameList();
				printMessage(name + " has left the server",1);

			}else if (line.startsWith("MESSAGE")) {
				//Handle incoming message
				String user = line.substring(7);//Username is sent first along with message identifier
				String msg = in.readLine();//Message is sent next

				String finalMsg = "";
				//Cycle through every word and test if it's a hyperlink
				for (String word : msg.split(" ")) {
					try {
					//Is the message recieved a hyperlink?
					URL url = new URL(word);

					//Append link to message
					finalMsg += "<a href=\""+word+"\">"+word+"</a> ";

					}catch (Exception e) {

						finalMsg += word + " ";

					}
				}

				//Print message normally
				printMessage(user,finalMsg,0);

			}else if (line.startsWith("STARTTYPE")) {
				//A user has started to type
				String user = line.substring(9);
				if (typingUsers.indexOf(user) == -1)
					typingUsers.add(line.substring(9));
				updateTypingLabel();

			}else if (line.startsWith("STOPTYPE")) {
				//A user has stopped typing
				typingUsers.remove(line.substring(8));
				updateTypingLabel();
			}


		}

	}

	/**
	* Set whether the client is typing or not
	* @param typing If we're typing or not
	*/
	private void setIsTyping(boolean typing) {
		isTyping = typing;
		out.println((typing ? "START" : "STOP") + "TYPE" + username);
		if (typing) {
			lastType = (int)System.currentTimeMillis();
		}
		//We don't update the label since we don't see text for this anyways
	}

	/**
	* Update the JLabel that says if a user is typing or not
	*/
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
				txt += " are";
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
	* @param usr The user who sent the message
	* @param msg The message to append
	* @param style Set to an int between 0-2 to determine stylization of message
	*/
	private void printMessage(String usr,String msg, int style) {
		if (style == 1) msg = "<b>"+msg+"</b>";
		if (style == 2) msg = "<i>"+msg+"</i>";
		if (usr.length() != 0) usr = "<b>["+usr+"]</b> ";
		append(messagePane,usr+msg+"<br>");
	}
	//Override that doesn't require username
	private void printMessage(String msg, int style) {
		printMessage("",msg,style);
	}

	/**
	* Update the list of usernames connected to the server
	*/
	private void updateNameList() {
		String txt = "";
		for (String n : usernames)
			txt += n + "\n";
		userList.setText(txt);
	}

	/**
	* Update the menu to say if we're connected or not
	*/
	private void updateMenu() {
		userMenu.setText(connected ? (username != null && username.length() > 0 ? username : "No Name") : "Disconnected");
		setTitle((serverName != null ? serverName : "Unnamed Chatroom") + " - " + userMenu.getText());
	}

	//Simple hidden class to hide the 'User is typing' message after an amount of time
	private class TypeTimer extends Thread {
		public void run() {

			try {
				while (true) {
					sleep(TYPECHECK_DELAY);
					if (isTyping && (int)System.currentTimeMillis() - lastType > TYPECHECK_DELAY) {
						setIsTyping(false);
					}
				}
			}catch (Exception e) {
				e.printStackTrace();
			}

		}

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