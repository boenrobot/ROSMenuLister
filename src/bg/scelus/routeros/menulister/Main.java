package bg.scelus.routeros.menulister;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class Main {

    public static BufferedReader in;
    public static PrintWriter out;
    public static Channel channel;
    public static Session session;

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage:");
            String jar = new java.io.File(
                    Main.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath())
                    .getName();
            System.out.println(
                jar + " [output-file.json] [hostname] [username] [password]? [port]?"
            );
        } else {
            String outfile = args[0];
            String hostname = args[1];
            String username = args[2];
            String password = "";
            int port = 22;
            if (args.length > 3 && !args[3].isEmpty()) {
                password = args[3];
            }
            if (args.length > 4 && !args[4].isEmpty()) {
                port = int.class.cast(args[4]);
            }
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, hostname, port);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setPassword(password);
                session.connect();

                channel = session.openChannel("shell");

                in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(channel.getOutputStream()));
                Thread parseThread = new Thread(new Parser(in, out, outfile));

                channel.connect();
                parseThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
