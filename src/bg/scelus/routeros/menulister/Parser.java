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
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.SynchronousQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser implements Runnable {

    private ChannelShell channel;
    private PrintWriter out;
    private BufferedReader in;
    private MenuItem mainMenu = null;
    private AbstractQueue<String> messages;
    private String promptString = null;
    private Pattern prompt = Pattern.compile("\\A\\[.+@.+\\] \\> .*\\z");

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

            ArrayList<String> enterResponse = getResponseMatch();
        
            Pattern rosVersionPattern = Pattern.compile(".*MikroTik RouterOS (.+) \\(c\\).*");
            StringBuilder rosVersion = new StringBuilder();
            enterResponse.forEach((String line) -> {
                Matcher matcher = rosVersionPattern.matcher(line);
                if (matcher.matches()) {
                    rosVersion.append(matcher.group(1));
                }
            });
            
            out.print(" \001\013");
            out.flush();
            getResponseMatch();
            
            ArrayList<String> homeResponse = getResponseMatch();
            String homePrompt = homeResponse.remove(homeResponse.size() - 1).trim().concat(" ");
            
            ArrayList<String> clearResponse = getResponseMatch();
            String clearPrompt = clearResponse.remove(clearResponse.size() - 1).trim().concat(" ");
            
            boolean clearMayBeHome = true;
            while (!homePrompt.equals(clearPrompt)) {
                if (clearMayBeHome) {
                    homeResponse = clearResponse;
                    homePrompt = clearPrompt;
                    clearResponse = getResponseMatch();
                    clearPrompt = clearResponse.remove(clearResponse.size() - 1).trim().concat(" ");
                } else {
                    clearResponse = homeResponse;
                    clearPrompt = homePrompt;
                    homeResponse = getResponseMatch();
                    homePrompt = homeResponse.remove(homeResponse.size() - 1).trim().concat(" ");
                }
            }
            promptString = clearPrompt;
            messages.add("Staring to parse.");

            MenuItem rootMenu = new MenuItem("");
            rootMenu.summary = rosVersion.toString();
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
    
    protected ArrayList<String> getResponseMatch() throws IOException {
        ArrayList<String> response = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            if (prompt.matcher(line).matches()) {
                response.add(line);
                break;
            } else {
                response.add(line);
            }
        }
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

        out.print(fullArg + "\t?");
        out.flush();

        
        getResponse();
        getResponse();

        //clear the line
        out.print("\001\013");
        out.flush();

        ArrayList<String> response = getResponse();
        String promptLine = response.remove(response.size() - 1);
        if (promptString.length() + fullArg.length() + 1 >= 80) {
            while (!promptLine.endsWith(">")) {
                getResponse();
                response = getResponse();
                promptLine = response.remove(response.size() - 1);
            }
        }
        if (promptLine.endsWith(">")) {
            arg.isSpecial = !response.remove(response.size() - 1).endsWith("=");
            response.remove(response.size() - 1);
            for (int i = 0, l = response.size(); i<l; ++i) {
                if (response.get(0).startsWith("<")) {
                    response.remove(0);
                } else {
                    break;
                }
            }
        } else {
            response = getResponse();
            response.remove(response.size() - 1);
            arg.isSpecial = !getResponse().get(0).endsWith("=");
        }
        if (!arg.isSpecial || promptLine.endsWith(fullArg + " ")) {
            getResponse();
            getResponse();
        }
        
        if (arg.isSpecial) {
            //Determine if empty or keyword
            MenuItem cmdAsMenu = new MenuItem(arg.parent.name);
            cmdAsMenu.parent = arg.parent.parent;
            Command argAsCmd = new Command(arg.name, cmdAsMenu);
            parseCommand(fullArg.trim(), argAsCmd);
            ArrayList<Argument> cmdArgs = arg.parent.arguments;
            
            argAsCmd.arguments.removeIf((Argument pArg) -> {
                return cmdArgs.stream().anyMatch((cmdArg) -> (cmdArg.name.equals(pArg.name)));
            });
            if (argAsCmd.arguments.isEmpty() && argAsCmd.description.equals(arg.parent.description)) {
                //Output with argument is the same as the command itself => empty argument
                response.clear();
            }
        } else {
            StringBuilder description = new StringBuilder();
            String line;
            for (int i = 0, l = response.size(); i<l; ++i) {
                line = response.get(0);
                if (line.startsWith("\033[m")) {
                    break;
                }
                description.append(line);
                description.append('\n');
                response.remove(0);
            }
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

    protected void parseCommand(String fullCommand, Command command) throws IOException {
        out.print(fullCommand + " ?");
        out.flush();

        messages.add("Parsing command \"" + fullCommand + "\"");
        getResponse();

        ArrayList<String> response = getResponse();
        response.remove(response.size() - 1);
        
        //clear the line
        out.print("\001\013");
        out.flush();
        getResponse();
        getResponse();
        if (fullCommand.length() >= 79) {
            response = getResponse();
            response.remove(response.size() - 1);
            response.remove(response.size() - 1);
            response.remove(response.size() - 1);
            response.remove(0);
            response.remove(0);
        } else {
            getResponse();
        }
        Argument arg = null;
        for (String line : response) {
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

    protected void startCommandList(Command command) throws IOException {
        parseCommand(getFullPath(command), command);
    }

    protected ArrayList<MenuItem> startList(MenuItem menu) throws IOException {
        String fullMenu = getFullPath(menu);

        ArrayList<MenuItem> result = new ArrayList<>();

        out.write(fullMenu + " ?");
        out.flush();
        messages.add("Parsing menu \"" + fullMenu + "\"");

        getResponse();
        boolean lastOneWasAMenu = true;
        ArrayList<String> response = getResponse();
        response.remove(response.size() - 1);
        for (String line : response) {
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
                
                lastOneWasAMenu = true;

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
                lastOneWasAMenu = false;

                menu.commands.add(cmd);
            } else {
                if (line.endsWith("[m")) {
                    line = line.substring(0, line.lastIndexOf("[m"));
                }
                if (menu.subMenus.isEmpty() && menu.commands.isEmpty()) {
                    menu.description = menu.description + line;
                } else {
                    if (lastOneWasAMenu) {
                        MenuItem lastMenu = menu.subMenus.get(menu.subMenus.size() - 1);
                        lastMenu.summary = lastMenu.summary + line;
                    } else {
                        Command lastCommand = menu.commands.get(menu.commands.size() - 1);
                        lastCommand.summary = lastCommand.summary + line;
                    }
                }
            }
        }
        
        //clear the line
        out.print("\001\013");
        out.flush();
        getResponse();
        getResponse();
        getResponse();

        return result;
    }
}
