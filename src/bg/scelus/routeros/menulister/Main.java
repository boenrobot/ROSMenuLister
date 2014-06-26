package bg.scelus.routeros.menulister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class Main {
	public static BufferedReader in;
	public static PrintWriter out;
	public static Channel channel;
	public static Session session;
	
	// connection configuration
	final static String hostname = "192.168.56.101";
	final static int port = 22;
	final static String username = "admin";
	
	public static void main(String[] args) {
		try {
			JSch jsch = new JSch();
			session = jsch.getSession(username, hostname, port);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();

			channel = session.openChannel("shell");
			channel.setInputStream(System.in);
			channel.setOutputStream(System.out);
			
			in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
			out = new PrintWriter (new OutputStreamWriter(channel.getOutputStream()));
			Thread parseThread = new Thread(new Parser(in, out));
			
			channel.connect();
			parseThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void closeAll() {
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		out.close();
		channel.disconnect();
		session.disconnect();
	}
}
