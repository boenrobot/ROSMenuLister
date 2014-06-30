package bg.scelus.routeros.menulister;

import bg.scelus.routeros.menulister.models.Argument;
import bg.scelus.routeros.menulister.models.Command;
import bg.scelus.routeros.menulister.models.MenuItem;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Parser implements Runnable {

    private ChannelShell channel;
    private PrintWriter out;
    private BufferedReader in;
    private MenuItem mainMenu = null;
    private AbstractQueue<String> messages;
    private Pattern prompt = Pattern.compile("\\A\\[[^@]+@[^\\]]+\\] \\> .*\\z", Pattern.DOTALL);

    /**
     * Creates a parser instance.
     *
     * @param session The already connected session, using Jsch as the SSH library.
     *
     * @throws java.io.IOException If unable to get the input or output streams
     * out of the channel.
     * @throws com.jcraft.jsch.JSchException If the session is not connected.
     */
    public Parser(Session session) throws IOException, JSchException {
        this(
                session,
                new SynchronousQueue<String>() {
                    @Override
                    public boolean add(String element) {
                        return true;
                    }
                }
        );
    }

    /**
     * Creates a parser instance.
     *
     * @param session The already connected session, using Jsch as the SSH library.
     * @param messages A queue to receive status messages to.
     *
     * @throws java.io.IOException If unable to get the input or output streams
     * out of the channel.
     * @throws com.jcraft.jsch.JSchException If the session is not connected.
     */
    public Parser(Session session, AbstractQueue<String> messages) throws IOException, JSchException {

        messages.add("Logging into shell...");
        channel = (ChannelShell) session.openChannel("shell");
        channel.connect();
        messages.add("OK.");
        in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(channel.getOutputStream()));
        this.messages = messages;
    }

    /**
     * Get the generated main menu.
     *
     * @return The generated main menu, or NULL if not generated yet.
     */
    public MenuItem getMainMenu() {
        return mainMenu;
    }

    /**
     * Gets the full path of a menu.
     *
     * @param menu The menu to get the full path of.
     *
     * @return The full path to the menu, relative to the up most parent.
     */
    public static String getFullPath(MenuItem menu) {
        if (null != menu.parent) {
            return getFullPath(menu.parent) + " " + menu.name;
        } else {
            return "";
        }
    }

    /**
     * Gets the full path of a fullMenu.
     *
     * @param command The fullMenu to get the full path of.
     *
     * @return The full path to the fullMenu, relative to the up most parent.
     */
    public static String getFullPath(Command command) {
        return getFullPath(command.parent) + " " + command.name;
    }

    /**
     * Gets the full path of an argument.
     *
     * @param arg The argument to get the full path of.
     *
     * @return The full path to the argument, relative to the up most parent.
     */
    public static String getFullPath(Argument arg) {
        return getFullPath(arg.parent) + " " + arg.name;
    }

    @Override
    public void run() {
        try {
            messages.add("Pressing Enter (to clear the command line).");
            out.println();
            out.flush();

            getResponse();
            messages.add("Staring to parse.");

            MenuItem rootMenu = new MenuItem("");
            LinkedList<MenuItem> queue = new LinkedList<>();
            LinkedList<Command> cmds = new LinkedList<>();
            LinkedList<Argument> args = new LinkedList<>();
            queue.add(rootMenu);

            while (!queue.isEmpty()) {
                MenuItem item = queue.remove(0);
//				if (!item.name.equals("main") && !item.name.equals("tool") && !item.name.equals("traffic-generator") && !item.name.equals("raw-packet-template"))
//					continue;
                queue.addAll(startList(item));
                cmds.addAll(item.commands);
            }
            messages.add("Listed all menus");

            for (Command cmd : cmds) {
//				if (!cmd.name.equals("find"))
//					continue;
                startCommandList(cmd);
                args.addAll(cmd.arguments);
            }
            messages.add("Listed all commands");

            for (Argument arg : args) {
                startArgumentList(arg);
            }
            messages.add("Listed all arguments");
            this.mainMenu = rootMenu;
        } catch (IOException e) {
            messages.add(e.toString());
        }
        channel.disconnect();
    }

    protected ArrayList<String> getResponse() throws IOException {
        return getResponse(-1);
    }

    protected ArrayList<String> getResponse(int promptChars) throws IOException {
        ArrayList<String> response = new ArrayList<>();
        String line;
        while (!in.ready()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
            }
        }
        while ((line = in.readLine()) != null) {
            if (prompt.matcher(line).matches()) {
                if (promptChars > -1) {
                    if (line.endsWith(">")) {
                        line = line.substring(0, line.length() - 1);
                    }
                    StringBuilder fullLine = new StringBuilder(line);
                    String newLineDelim, newLine;
                    while((promptChars -= 80) >= 0) {
                        in.readLine();
                        newLineDelim = in.readLine();
                        while (!(newLine = in.readLine()).startsWith(newLineDelim)) {
                            response.add(newLine);
                        }
                        fullLine.append(newLineDelim.substring(1));
                    }
                    line = fullLine.toString();
                }
                response.add(line);
                break;
            } else {
                response.add(line);
            }
        }
        return response;
    }

    protected void startArgumentList(Argument arg) throws IOException {
        String fullArg = getFullPath(arg);

        out.print(fullArg + "=?");
        out.flush();
        int outLength = fullArg.length() + 1;
        int promptLineFullLength = -1;

        messages.add("Parsing argument \"" + fullArg + "\"");

        CharBuffer buff = CharBuffer.allocate(1024 * 1024);
        in.mark(1024 * 1024);
        do  {
            in.read(buff);
            try {
                Thread.sleep(2 * outLength);
            } catch (InterruptedException ex) {
                break;
            }
        } while(buff.hasRemaining() && in.ready());
        buff.rewind();
        String buffContents = buff.toString().replace(">\r<", "");
        in.reset();

        if (buffContents.contains("\n") || buffContents.contains("\r")) {
            getResponse();

            ArrayList<String> response = getResponse();
            String promptString = response.remove(response.size() - 1).trim();
            promptString = promptString.substring(0, promptString.indexOf(">") + 2);
            promptLineFullLength = promptString.length() + outLength;
            //clear the line
            out.print((char)1);
            out.print((char)11);
            out.flush();
            if (promptLineFullLength >= 80) {
                response = getResponse();
                if (response.size() < 5) {
                    arg.isKeyword = true;
                    response.clear();
                } else {
                    response.remove(response.size() - 1);
                    response.remove(response.size() - 1);
                    response.remove(response.size() - 1);
                    response.remove(0);
                    response.remove(0);
                }
            } else {
                getResponse();
            }
            getResponse();
            getResponse();

            StringBuilder argInfo = new StringBuilder();
            response.forEach((String line) -> {
                argInfo.append(line);
                argInfo.append('\n');
            });
            if (argInfo.length() > 0) {
                arg.values = argInfo.toString().trim();
            }
        } else {
            arg.isKeyword = true;
        
            //clear the line
            out.print((char)1);
            out.print((char)11);
            out.flush();
            getResponse();
            getResponse();
            getResponse();
        }
    }

    protected void startCommandList(Command command) throws IOException {
        String fullCommand = getFullPath(command);
        out.print(fullCommand + " ?");
        out.flush();

        messages.add("Parsing command \"" + fullCommand + "\"");
        getResponse();
        for (String line : getResponse()) {
            if (line.startsWith("[m[32m")) {
                Argument arg = new Argument(
                        line.substring(
                                "[m[32m".length(),
                                line.indexOf("[m[33m --")
                        ),
                        command
                );

                if (line.indexOf("-- [m") > 0) {
                    arg.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()).replace("'", "\'");
                }

                command.arguments.add(arg);
            } else if (line.startsWith("[m[33m<[m[32m")) {
                Argument arg = new Argument(
                        line.substring(
                                "[m[33m<[m[32m".length(),
                                line.indexOf("[m[33m>")
                        ),
                        command
                );
                arg.isUnnamed = true;

                if (line.indexOf("-- [m") > 0) {
                    arg.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()).replace("'", "\'");
                }

                command.arguments.add(arg);
            }
        }
        
        //clear the line
        out.print((char)1);
        out.print((char)11);
        out.flush();
        getResponse();
        getResponse();
        getResponse();
    }

    protected ArrayList<MenuItem> startList(MenuItem menu) throws IOException {
        String fullMenu = getFullPath(menu);

        ArrayList<MenuItem> result = new ArrayList<>();

        out.write(fullMenu + " ?");
        out.flush();
        messages.add("Parsing menu \"" + fullMenu + "\"");

        getResponse();
        for (String line : getResponse()) {
            if (line.startsWith("[m[36m")) {
                MenuItem item = new MenuItem(
                        line.substring(
                                "[m[36m".length(),
                                line.indexOf("[m[33m")
                        )
                );

                if (line.indexOf("-- [m") > 0) {
                    item.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()
                    ).replace("'", "\'");
                }
                item.parent = menu;

                if (item.name.equals("..")) {
                    continue;
                }

                menu.subMenus.add(item);
                result.add(item);
            } else if (line.startsWith("[m[35")) {
                Command cmd = new Command(
                        line.substring(
                                "[m[35m".length(),
                                line.indexOf("[m[33m")
                        ),
                        menu
                );

                if (line.indexOf("-- [m") > 0) {
                    cmd.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()).replace("'", "\'");
                }

                menu.commands.add(cmd);
            }
        }
        
        //clear the line
        out.print((char)1);
        out.print((char)11);
        out.flush();
        getResponse();
        getResponse();
        getResponse();

        return result;
    }
}
