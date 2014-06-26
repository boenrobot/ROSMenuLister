package bg.scelus.routeros.menulister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;

import bg.scelus.routeros.menulister.models.Argument;
import bg.scelus.routeros.menulister.models.Command;
import bg.scelus.routeros.menulister.models.MenuItem;

public class Parser implements Runnable {
	private PrintWriter out;
	private BufferedReader in;
        private String outfile;

	public Parser(BufferedReader in, PrintWriter out, String outfile) {
		this.in = in;
		this.out = out;
                this.outfile = outfile;
	}

	public ArrayList<String> getResponse() throws IOException {
		ArrayList<String> response = new ArrayList<>();
		String line;
		while ((line = in.readLine()) != null) {
			response.add(line);
			if (line.contains("> ")) {
				break;
			}
		}
		return response;
	}

	@Override
	public void run() {
		try {
			System.out.println("Logging in...");
			out.println();
			out.flush();

			getResponse();
			System.out.println("Logged in");

			MenuItem mainMenu = new MenuItem("");
			mainMenu.summary = "";
			LinkedList<MenuItem> queue = new LinkedList<>();
			LinkedList<Command> cmds = new LinkedList<>();
			LinkedList<Argument> args = new LinkedList<>();
			queue.add(mainMenu);

			while (!queue.isEmpty()) {
				MenuItem item = queue.remove(0);
//				if (!item.name.equals("main") && !item.name.equals("tool") && !item.name.equals("traffic-generator") && !item.name.equals("raw-packet-template"))
//					continue;
				queue.addAll(startList(item));
				cmds.addAll(item.commands);
			}
			System.out.println("Listed all menus");

			getResponse();
			getResponse();
			for (Command cmd : cmds) {
//				if (!cmd.name.equals("find"))
//					continue;
				startCommandList(cmd);
				args.addAll(cmd.arguments);
			}
			System.out.println("Listed all commands");

			for (Argument arg : args)
				startArgumentList(arg);
			System.out.println("Listed all arguments");

                        try (PrintWriter writer = new PrintWriter(outfile, "UTF-8")) {
                            writer.println(mainMenu.getJSON().toJSONString());
                        } catch (Exception e) {
                            System.err.println("Failed to write file.");
                        }
			System.out.println("JSON generated");

			System.out.println("Exiting");
			Main.closeAll();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getCommand(MenuItem menu) {
		if (null != menu.parent)
			return getCommand(menu.parent) + " " + menu.name;
		else
			return "";
	}

	private String getCommand(Command command) {
		return getCommand(command.parent) + " " + command.name;
	}

	private void startArgumentList(Argument arg) throws IOException {
		String cmd = getCommand(arg.parent) + " " + arg.name;
		System.out.println("Parsing argument " + cmd);

		boolean keyword = true;

		out.print(cmd + "=?");
		out.flush();

		// clear the line
		boolean keepDeleting = true;
		boolean longLine = false;
		for (int i = 0; i < cmd.length() + 2; i++)
			out.write((char) 8);	
		out.flush();
		
		do {
			for (String line : getResponse()) {
				if (longLine) {
					out.print("\r\n");
					out.flush();
					longLine = false;
				}
				else if (line.contains("[K")) {
					keyword = false;
					break;
				} else if (line.endsWith(">") && line.length() >= 79) {
					out.print("\r\n");
					out.flush();
					longLine = true;
				} else if (line.endsWith("] > ")) {
					keepDeleting = false;
					break;
				}
			}
			out.write((char) 8);	
			out.flush();
		} while (keepDeleting);

		getResponse();
		arg.isKeyword = keyword;
	}

	private void startCommandList(Command command) throws IOException {
		String cmd = getCommand(command);
		out.print(cmd + " ?");
		out.flush();

		System.out.println("Parsing command " + cmd);
		boolean parsed = false;
		do {
			for (String line : getResponse()) {
				if (line.startsWith("[m[32m")) {
					Argument arg = new Argument(
                                            line.substring(
                                               "[m[32m".length(),
                                               line.indexOf("[m[33m --")
                                            ),
                                            command
                                        );

					if (line.indexOf("-- [m") > 0)
						arg.summary = line.substring(
								line.indexOf("-- [m") + "-- [m".length(),
								line.length()).replace("'", "\'");

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

					if (line.indexOf("-- [m") > 0)
						arg.summary = line.substring(
								line.indexOf("-- [m") + "-- [m".length(),
								line.length()).replace("'", "\'");

					command.arguments.add(arg);
				} else if (line.endsWith("[K")) {
					parsed = true;
					break;
				}
			}
		} while (!parsed);

		// clear the line
		boolean keepDeleting = true;
		do {
			for (int i = 0; i < cmd.length() + 2; i++)
				out.write((char) 8);
			out.flush();

			for (String line : getResponse()) {
				if (line.endsWith("> ")) {
					keepDeleting = false;
					break;
				}
			}
		} while (keepDeleting);
	}

	private ArrayList<MenuItem> startList(MenuItem menu) throws IOException {

		String command = getCommand(menu);

		ArrayList<MenuItem> result = new ArrayList<>();
		ArrayList<String> response = new ArrayList<>();

		if (null != menu.parent) {
			response.addAll(getResponse());
			out.print(command + " ?\r\n");
		} else {
			out.print(" ?\r\n");
		}
		out.flush();
		response.addAll(getResponse());
		response.addAll(getResponse());
		response.addAll(getResponse());

		System.out.println("Parsing menu " + command);
		for (String line : response) {
			if (line.startsWith("[m[36m")) {
				MenuItem item = new MenuItem(
                                    line.substring(
                                        "[m[36m".length(),
					line.indexOf("[m[33m")
                                    )
                                );

				if (line.indexOf("-- [m") > 0)
					item.summary = line.substring(
							line.indexOf("-- [m") + "-- [m".length(),
							line.length()).replace("'", "\'");
				item.parent = menu;

				if (item.name.equals(".."))
					continue;

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

				if (line.indexOf("-- [m") > 0)
					cmd.summary = line.substring(
							line.indexOf("-- [m") + "-- [m".length(),
							line.length()).replace("'", "\'");

				menu.commands.add(cmd);
			}
		}

		// up the menus
		if (null != menu.parent) {
                        MenuItem parent = menu.parent;
			String back = "";
			while (null != parent) {
				back += ".. ";
                                parent = parent.parent;
			}
			out.print(back + "\r\n");
			out.flush();
			getResponse();
			getResponse();
			getResponse();
			getResponse();
		}
		return result;
	}
}