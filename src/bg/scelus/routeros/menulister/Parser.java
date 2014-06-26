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

	public Parser(BufferedReader in, PrintWriter out) {
		this.in = in;
		this.out = out;
	}

	public ArrayList<String> getResponse() throws IOException {
		ArrayList<String> response = new ArrayList<String>();
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

			MenuItem mainMenu = new MenuItem();
			mainMenu.name = "main";
			mainMenu.description = "list of all commands and submenus";
			mainMenu.level = 0;
			LinkedList<MenuItem> queue = new LinkedList<MenuItem>();
			LinkedList<Command> cmds = new LinkedList<Command>();
			LinkedList<Argument> args = new LinkedList<Argument>();
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

			PrintWriter writer = new PrintWriter("json.txt", "UTF-8");
			writer.println(mainMenu.getJSON().toJSONString());
			writer.close();
			System.out.println("JSON generated");

			System.out.println("Exiting");
			Main.closeAll();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getCommand(MenuItem menu) {
		if (menu.level > 0)
			return new String(getCommand(menu.parent) + " " + menu.name);
		else
			return "";
	}

	private String getCommand(Command command) {
		return new String(getCommand(command.parent) + " " + command.name);
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
		arg.keyword = keyword;
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
					Argument arg = new Argument();
					arg.name = line.substring("[m[32m".length(),
							line.indexOf("[m[33m --"));
					arg.optional = false;
					arg.parent = command;

					if (line.indexOf("-- [m") > 0)
						arg.description = line.substring(
								line.indexOf("-- [m") + "-- [m".length(),
								line.length()).replace("'", "\'");

					command.arguments.add(arg);
				} else if (line.startsWith("[m[33m<[m[32m")) {
					Argument arg = new Argument();
					arg.name = line.substring("[m[33m<[m[32m".length(),
							line.indexOf("[m[33m>"));
					arg.optional = true;
					arg.parent = command;

					if (line.indexOf("-- [m") > 0)
						arg.description = line.substring(
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

		ArrayList<MenuItem> result = new ArrayList<MenuItem>();
		ArrayList<String> response = new ArrayList<String>();

		if (menu.level > 0) {
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
				MenuItem item = new MenuItem();
				item.name = line.substring("[m[36m".length(),
						line.indexOf("[m[33m"));

				if (line.indexOf("-- [m") > 0)
					item.description = line.substring(
							line.indexOf("-- [m") + "-- [m".length(),
							line.length()).replace("'", "\'");
				item.parent = menu;
				item.level = menu.level + 1;

				if (item.name.equals(".."))
					continue;

				menu.subMenus.add(item);
				result.add(item);
			} else if (line.startsWith("[m[35")) {
				Command cmd = new Command();
				cmd.name = line.substring("[m[35m".length(),
						line.indexOf("[m[33m"));

				if (line.indexOf("-- [m") > 0)
					cmd.description = line.substring(
							line.indexOf("-- [m") + "-- [m".length(),
							line.length()).replace("'", "\'");
				cmd.parent = menu;

				menu.commands.add(cmd);
			}
		}

		// up the menus
		if (menu.level > 0) {
			String back = "";
			for (int i = 0; i < menu.level; i++) {
				back += ".. ";
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