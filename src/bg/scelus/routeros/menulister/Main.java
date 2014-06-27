package bg.scelus.routeros.menulister;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
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
                channel.connect();

                Parser parser = new Parser(channel);
                parser.run();

                channel.disconnect();
                session.disconnect();

                try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
                    writer.println(parser.getMainMenu().getJSON().toJSONString());
                    System.out.println("JSON generated");
                } catch (Exception e) {
                    System.err.println("Failed to write file.");
                }

                System.out.println("Exiting");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
