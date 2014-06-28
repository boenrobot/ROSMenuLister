package bg.scelus.routeros.menulister;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.PrintWriter;
import java.util.concurrent.SynchronousQueue;

public class Main {

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
                Session session = jsch.getSession(username, hostname, port);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setPassword(password);
                System.out.print("Connecting...");
                session.connect();
                System.out.println("OK.");

                System.out.print("Logging into shell...");
                ChannelShell channel = (ChannelShell) session.openChannel("shell");
                channel.connect();
                System.out.println("OK.");

                SynchronousQueue<String> messages = new SynchronousQueue<String>() {
                    @Override
                    public boolean add(String element) {
                        System.out.println(element);
                        return true;
                    }
                };
                Parser parser = new Parser(channel, messages);
                parser.run();

                channel.disconnect();
                session.disconnect();

                try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
                    writer.println(parser.getMainMenu().getJSON().toJSONString());
                    System.out.println("JSON generated");
                } catch (Exception e) {
                    System.err.println(
                            "Failed to write JSON file. Details: " + e.toString()
                    );
                }

                System.out.println("Exiting");
            } catch (Exception e) {
                System.err.println(e.toString());
            }
        }
    }
}
