package bg.scelus.routeros.menulister;

import bg.scelus.routeros.menulister.models.Argument;
import bg.scelus.routeros.menulister.models.Command;
import bg.scelus.routeros.menulister.models.Menu;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser implements Runnable {

    /**
     * Gets the full path of a menu item.
     *
     * @param item The item to get the full path of.
     *
     * @return The full path to the item, relative to the up most parent.
     */
    public static String getFullPath(MenuItem item) {
        if (null != item.parent) {
            return getFullPath(item.parent) + " " + item.name;
        } else {
            return "";
        }
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

    private final CharBuffer buff = CharBuffer.allocate(10240);

    private Menu mainMenu = null;
    private String promptString = null;

    private ChannelShell channel;
    private PrintWriter out;
    private BufferedReader in;
    private AbstractQueue<String> messages;

    /**
     * Creates a parser instance.
     *
     * @param session The already connected session, using Jsch as the SSH
     * library.
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
     * @param session The already connected session, using Jsch as the SSH
     * library.
     * @param messages A menus to receive status messages to.
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
    public Menu getMainMenu() {
        return mainMenu;
    }

    @Override
    public void run() {
        try {
            Menu rootMenu = new Menu("", null);

            messages.add("Pressing Enter (to clear the command line and find out the command prompt).");
            rootMenu.summary = parsePrePrompt();

            //promptString = response;
            messages.add("Staring to parse.");

            LinkedList<Menu> menus = new LinkedList<>();
            LinkedList<Command> cmds = new LinkedList<>();
            LinkedList<Argument> args = new LinkedList<>();
            menus.add(rootMenu);

            while (!menus.isEmpty()) {
                Menu item = menus.remove(0);
//				if (!item.name.equals("main") && !item.name.equals("tool") && !item.name.equals("traffic-generator") && !item.name.equals("raw-packet-template"))
//					continue;
                menus.addAll(startMenuList(item));
                cmds.addAll(item.commands);
            }
            messages.add("Listed all menus");

            for (Command cmd : cmds) {
//				if (!item.name.equals("find"))
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

    protected String parsePrePrompt() throws IOException {
        out.println();
        out.flush();
        String prePrompt = getAll(500);

        out.print(" \001\013");
        out.flush();
        String postPrompt = getAll(20);
        Pattern rosVersionPattern = Pattern.compile(".*MikroTik RouterOS (.+) \\(c\\).*");
        Pattern lineSplit = Pattern.compile("\\r|\\n");

        promptString = lineSplit.split(postPrompt)[1];

        for (String prePromptLine : lineSplit.split(prePrompt)) {
            Matcher matcher = rosVersionPattern.matcher(prePromptLine);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    protected String getAll(int maxBlockings) throws IOException {
        StringBuilder response = new StringBuilder();
        int blockings = 0;
        while (true) {
            if (in.ready()) {
                blockings = 0;
                do {
                    in.read(buff);
                } while (in.ready() && buff.hasRemaining());
                buff.rewind();
                response.append(buff.toString());
                buff.clear();
            } else {
                if (blockings > maxBlockings) {
                    break;
                }
                try {
                    Thread.sleep(20);
                    ++blockings;
                } catch (InterruptedException ex) {
                }
            }
        }
        return response.toString();
    }

    protected ArrayList<Menu> startMenuList(Menu menu) throws IOException {
        String fullMenu = getFullPath(menu);
        messages.add("Parsing menu \"" + fullMenu + "\"");

        ArrayList<Menu> result = new ArrayList<>();
        MenuItem item = null;
        for (String line : getResponseHelp(fullMenu + " ")) {
            if (line.startsWith("[m[36m")) {
                item = new Menu(
                        line.substring(
                                "[m[36m".length(),
                                line.indexOf("[m[33m")
                        ),
                        menu
                );

                if (line.indexOf("-- [m") > 0) {
                    item.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()
                    ).replace("'", "\'");
                }

                if (item.name.equals("..")) {
                    continue;
                }

                menu.subMenus.add((Menu) item);
                result.add((Menu) item);
            } else if (line.startsWith("[m[35")) {
                item = new Command(
                        line.substring(
                                "[m[35m".length(),
                                line.indexOf("[m[33m")
                        ),
                        menu
                );

                if (line.indexOf("-- [m") > 0) {
                    item.summary = line.substring(
                            line.indexOf("-- [m") + "-- [m".length(),
                            line.length()).replace("'", "\'");
                }

                menu.commands.add((Command) item);
            } else {
                if (line.endsWith("[m")) {
                    line = line.substring(0, line.lastIndexOf("[m"));
                }
                if (null == item) {
                    menu.description = menu.description + line;
                } else {
                    item.summary = item.summary + line;
                }
            }
        }

        return result;
    }

    protected void startCommandList(Command command) throws IOException {
        parseCommand(getFullPath(command) + " ", command);
    }

    protected void parseCommand(String fullCommand, Command command) throws IOException {
        messages.add("Parsing command \"" + fullCommand + "\"");
        Argument arg = null;
        for (String line : getResponseHelp(fullCommand)) {
            if (line.startsWith("[m[32m")) {
                if (null != arg) {
                    if (arg.summary.endsWith("[m")) {
                        arg.summary = arg.summary.substring(0, arg.summary.lastIndexOf("[m"));
                    }
                    command.arguments.add(arg);
                }
                arg = new Argument(
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
            } else if (line.startsWith("[m[33m<[m[32m")) {
                if (null != arg) {
                    if (arg.summary.endsWith("[m")) {
                        arg.summary = arg.summary.substring(0, arg.summary.lastIndexOf("[m"));
                    }
                    command.arguments.add(arg);
                }
                arg = new Argument(
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
            } else {
                if (null == arg) {
                    command.description = command.description + line;
                } else {
                    arg.summary = arg.summary + line;
                }
            }
        }

        //Add last argument
        if (null != arg) {
            if (arg.summary.endsWith("[m")) {
                arg.summary = arg.summary.substring(0, arg.summary.lastIndexOf("[m"));
            }
            command.arguments.add(arg);
        }
    }

    protected void startArgumentList(Argument arg) throws IOException {
        String fullArg = getFullPath(arg);

        messages.add("Parsing argument \"" + fullArg + "\"");
        if (!arg.isUnnamed) {
            StringBuilder unnamedArgStr = new StringBuilder(getFullPath(arg.parent));
            arg.parent.arguments.forEach((Argument cmdArg) -> {
                if (cmdArg.isUnnamed) {
                    unnamedArgStr.append(" \"\"");
                }
            });
            fullArg = unnamedArgStr.append(" ").append(arg.name).toString();
        }
        ArrayList<String> response = getResponseTab(fullArg);
        String promptLine = response.remove(response.size() - 1).substring(promptString.length());
        arg.isSpecial = !promptLine.endsWith("=");

        if (arg.isSpecial) {
            //Determine if empty or keyword
            Menu cmdAsMenu = new Menu(arg.parent.name, arg.parent.parent);
            Command argAsCmd = new Command(arg.name, cmdAsMenu);
            parseCommand(fullArg + " ", argAsCmd);
            ArrayList<Argument> cmdArgs = arg.parent.arguments;

            argAsCmd.arguments.removeIf((Argument pArg) -> {
                return cmdArgs.stream().anyMatch((cmdArg) -> (cmdArg.name.equals(pArg.name)));
            });
            if (argAsCmd.arguments.isEmpty() && argAsCmd.description.equals(arg.parent.description)) {
                //Output with argument is the same as the command itself => empty argument
                response.clear();
            } else {
                response = getResponseHelp(fullArg + " ");
            }
        } else {
            response = getResponseHelp(fullArg + "=");
            StringBuilder description = new StringBuilder();
            String line;
            for (int i = 0, l = response.size(); i < l; ++i) {
                line = response.get(0);
                if (line.startsWith("\033[m")) {
                    break;
                }
                description.append(line);
                description.append('\n');
                response.remove(0);
            }
            //ArrayList<String> tabResponse = getResponseTab(fullArg + "=");
            arg.description = description.toString().trim();
        }

        StringBuilder values = new StringBuilder();
        response.forEach((String line) -> {
            values.append(line);
            values.append('\n');
        });
        String valuesString = values.toString().trim();
        if (valuesString.length() > 0) {
            arg.values = valuesString;
        }
    }

    protected ArrayList<String> getResponseHelp(String line) throws IOException {

        out.print(line + "?");
        out.flush();

        ArrayList<String> response = getResponse();
        response.remove(0);
        response.remove(response.size() - 1);

        //clear the line
        out.print("\001\013");
        out.flush();
        if ((promptString.length() + line.length()) >= 80) {
            response = getResponse();
            response.remove(response.size() - 1);
            for (int i = response.size() - 1, l = -1; i > l; --i) {
                String lastLine = response.get(i);
                if (lastLine.startsWith("<")) {
                    response.remove(i);
                } else {
                    break;
                }
            }
            for (int i = 0, l = response.size() - 1; i < l; ++i) {
                String lastLine = response.get(0);
                if (lastLine.startsWith("<")) {
                    response.remove(0);
                } else {
                    break;
                }
            }
        } else {
            getResponse();
        }
        getAll(1);
        return response;
    }

    protected ArrayList<String> getResponse() throws IOException {
        ArrayList<String> response = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith(promptString)) {
                response.add(line);
                break;
            } else {
                response.add(line);
            }
        }
        return response;
    }

    protected ArrayList<String> getResponseTab(String line) throws IOException {
        out.print(line + "\t");
        out.flush();

        //clear the line
        out.print("\001\013");
        out.flush();
        ArrayList<String> response = getResponse();
        response.remove(0);
        String lastPromptLine = response.remove(response.size() - 1);
        String promptLine = lastPromptLine;
        while (!lastPromptLine.equals(promptString)) {
            promptLine = lastPromptLine;
            response.addAll(getResponse());
            lastPromptLine = response.remove(response.size() - 1);
        }
        getResponse();

        while (!response.isEmpty() && response.get(response.size() - 1).endsWith("[K")) {
            response.remove(response.size() - 1);
        }

        if (!response.isEmpty() && promptLine.endsWith(">")) {
            String promptLineTail = response.remove(0);
            for (int i = 0, l = response.size(); i < l; ++i) {
                if (response.get(0).startsWith("<")) {
                    promptLineTail = response.remove(0);
                } else {
                    break;
                }
            }
            promptLineTail = promptLineTail.substring(1);
            String promptLineTailMark = promptLineTail.substring(0, promptLineTail.indexOf(" "));
            promptLine = promptString + line.substring(0, line.lastIndexOf(promptLineTailMark) + promptLineTailMark.length()) + promptLineTail.substring(promptLineTailMark.length());
            for (int i = response.size() - 1, l = -1; i > l; --i) {
                String lastLine = response.get(i);
                if (lastLine.startsWith("<") || lastLine.startsWith("\033[m<")) {
                    response.remove(i);
                } else {
                    break;
                }
            }
        }

        response.add(promptLine);
        getAll(1);
        return response;
    }
}
