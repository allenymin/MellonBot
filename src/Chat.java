import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class Chat {
	
	public class Message {
		
		public String name, message;
		public String[] words;
		public boolean mod;
		
		public Message(String line) {
			if (line.charAt(line.indexOf("mod=")+4) == '1') mod = true;
			else mod = false;
			name = line.substring(line.indexOf("display-name=")+13, line.indexOf(";emotes=")).toLowerCase();
			String tmp = line.substring(line.indexOf("PRIVMSG"));
			message = tmp.substring(tmp.indexOf(':')+1);
			words = message.split(" ");
		}
		
		public String getName() { return this.name; }
		public String getMessage() { return this.message; }
		public boolean isMod() { return mod; }
		public String getCommand() { return this.words[0]; }
		public String[] getWords() { return this.words; }
	}
	
	public class Challenge {
		public int amount;
		public String challenger;
		public long time;
		
		public Challenge(int amt, String ch, long t) {
			amount = amt;
			challenger = ch;
			time = t;
		}
	}
	
	public class Melon {
		
		public int numMelons;
		public long cooldown;
		public Challenge c;
		
		public Melon() {
			c = null;
			cooldown = System.currentTimeMillis();
			numMelons = 1000;
		}
		
		public Melon(int m) {
			c = null;
			cooldown = System.currentTimeMillis();
			numMelons = m;
		}
		
		public int gamble(int m) {
			int r = (int)(Math.random() * 100) + 1;
			if (r > 50) numMelons += m;
			else numMelons -= m;
			cooldown = System.currentTimeMillis() + 30000;
			return r;
		}
	}
	
	public class Boss {
		
		public int hp;
		public int loot;
		public HashMap<String, Long> cooldowns;
		
		public Boss(int h, int l) {
			hp = h;
			loot = l;
			cooldowns = new HashMap<String, Long>();
		}
		
	}
	
	static String server = "irc.twitch.tv";
	static int port = 6667;
	HashMap<String, String> cmds;
	HashSet<String> raffle;
	boolean raffleOpen = false;
	String raffleWord = "!raffle";
	int KappaCount;
	HashMap<String, Melon> melonList;
	HashSet<String> userList;
	HashSet<String> challengeList;
	int rate;
	boolean run = false;
	Boss boss;
	int melonpot;
	
	String name, channel, oauth;
	BufferedWriter writer;
	BufferedReader reader;
	
	public Chat(String n, String c) {
		this.name = n;
		this.channel = "#"+c;
		raffle = new HashSet<String>();
		cmds = new HashMap<String, String>();
		melonList = new HashMap<String, Melon>();
		userList = new HashSet<String>();
		challengeList = new HashSet<String>();
		rate = 5;
		melonpot = 0;
		load_melons();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			public void run() {
				String s = "";
				for (String n : userList) {
					s += n + ",";
					melonList.get(n).numMelons += rate;
				}
				System.out.println("Gave "+s+" "+rate+" melons");
			}
		}, 0, 60000);
	}
	
	public void save_melons() {
		try {
			PrintWriter writer = new PrintWriter(channel+"_melons.txt");
			for (String n : melonList.keySet()) {
				writer.println(n + ":" + melonList.get(n).numMelons);
			}
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void load_melons() {
		File f = new File(channel+"_melons.txt");
		try {
			Scanner scan = new Scanner(f);
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				int ind = line.indexOf(':');
				if (ind > 0) melonList.put(line.substring(0, ind), new Melon(Integer.parseInt(line.substring(ind+1))));
			}
		} catch (FileNotFoundException e) {
			System.out.println("No melon txt file found");
		}
		
	}
	
	public void connect() throws UnknownHostException, IOException {
		BufferedReader bf = new BufferedReader(new FileReader(name+".txt"));
		oauth = bf.readLine();
		bf.close();
		Socket s = new Socket(server, port);
		writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
		reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
	}
	
	public void send(String s) throws IOException {
		writer.write(s+"\r\n");
		writer.flush();
	}
	
	public void privmsg(String s) throws IOException {
		send("PRIVMSG "+channel+" :"+s);
	}
	
	public void run() throws IOException, InterruptedException {
		send("PASS "+oauth);
		send("NICK "+name);
		String line = null;
		send("CAP REQ :twitch.tv/membership");
		send("CAP REQ :twitch.tv/tags");
		send("JOIN "+channel);
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
			if (line.startsWith("PING ")) {
				send("PONG "+line.substring(5));
			} else if (line.contains("PRIVMSG")) {
				Message m = new Message(line);
				otherChatParse(m);
				runCommands(m);
			} else if (line.contains("JOIN "+channel)) {
				String n = parseName(line);
				userList.add(n);
				addToMelonList(n);
			} else if (line.contains("PART "+channel)) {
				String n = parseName(line);
				userList.remove(n);
				addToMelonList(n);
			}
		}
	}
	
	public void addToMelonList(String n) {
		if (!melonList.containsKey(n)) melonList.put(n, new Melon());
	}
	
	public String parseName(String line) {
		int i = line.indexOf("!");
		return line.substring(1, i);
	}
	
	public void otherChatParse(Message msg) {
		userList.add(msg.name);
		addToMelonList(msg.name);
		if (msg.getMessage().contains("Kappa")) KappaCount++;
	}
	
	public void runCommands(Message msg) throws IOException{
		String com = msg.getCommand();
		if (!run) {
			if (com.equals("!startmelon") && (msg.name.equals("allenmelon") || msg.name.equals("xaghant"))) {
				run = true;
				melonList = new HashMap<String, Melon>();
				userList = new HashSet<String>();
				challengeList = new HashSet<String>();
				load_melons();
				privmsg("MellonBot activated");
			}
		} else if (com.equals("!kappa")) {
			privmsg("Kappa count: "+KappaCount);
		} else if (com.equals(("!followers"))) {
			try {
				int index = Integer.parseInt(msg.getWords()[1]);
				String fi = Test.followers.get(index-1);
				privmsg("Follower "+(index)+" is "+fi+" with "+Test.fc.get(fi)+" followers.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (com.equals("!openRaffle")) {
			if (raffleOpen) {
				privmsg("Raffle is already open. Close current raffle first.");
				return;
			}
			raffle.clear();
			if (msg.getWords().length == 2) raffleWord = "!"+msg.getWords()[1];
			else raffleWord = "!raffle";
			privmsg("New raffle opened with keyword: "+raffleWord);
			raffleOpen = true;
		} else if (com.equals("!closeRaffle")) {
			raffleOpen = false;
		} else if (raffleOpen && msg.getMessage().equals(raffleWord)) {
			System.out.println(msg.getName()+" added to raffle");
			raffle.add(msg.getName());
		} else if (raffleOpen && com.equals("!roll") && raffle.size() > 0) {
			int index = (int) (Math.random()*(raffle.size()));
			System.out.println(index +" "+ raffle.size());
			String winner = (String) raffle.toArray()[index];
			privmsg("Winner is "+winner+"!");
		} else if (com.equals("!mod")) {
			if (msg.isMod()) privmsg(msg.getName()+" is a mod");
			else privmsg(msg.getName()+" is not a mod");
		} else if (com.equals("!addCommand")) {
			String[] words = msg.getWords();
			if (words.length < 3) return;
			StringBuilder s = new StringBuilder();
			for (int i = 2; i < msg.getWords().length; i++)	s.append(words[i]+" ");
			cmds.put("!"+words[1], s.toString());
		} else if (com.equals("!noodles")) {
			//privmsg(msg.getName()+" has 0 noodles. Loser Kappa");
		} else if (com.equals("!hax")) {
			//String n = msg.getName();
			//if (!melonList.containsKey(n)) melonList.put(n, new Melon());
			//melonList.get(n).numMelons += 1000;
		} else if (com.equals("!melons")) {
			String n = msg.getName();
			if (!melonList.containsKey(n)) melonList.put(n, new Melon());
			privmsg(n+" has "+melonList.get(n).numMelons+" melons");
		} else if (com.equals("!mgamble")) {
			String s = gamble(msg);
			privmsg(s);
		} else if (com.equals("!challenge")) {
			String s = challenge(msg);
			privmsg(s);
		} else if (com.equals("!accept")) {
			String s = accept(msg);
			privmsg(s);
		} else if (com.equals("!killbot") && (msg.name.equals("allenmelon") || msg.name.equals("xaghant"))) {
			save_melons();
			run = false;
		} else if (com.equals("!boss")) {
			String s = boss();
			privmsg(s);
		} else if (com.equals("!attack")) {
			String s = attack(msg);
			privmsg(s);
		} else if (com.equals("!spawnboss") && (msg.name.equals("allenmelon") || msg.name.equals("xaghant"))) {
			String s = spawnboss();
			privmsg(s);
		} else if (com.equals("!melonpot")) {
			privmsg("The melon pot is currently at " + melonpot + " melons.");
		} else if (cmds.containsKey(com)) {
			privmsg(cmds.get(com));
		}
	}
	
	public String spawnboss() {
		if (boss != null) {
			return "Boss is already spawned.";
		} else {
			boss = new Boss(50, melonpot);
			melonpot = 0;
			return "Boss is spawned with "+boss.hp+" hp and "+boss.loot+" melons.";
		}
	}
	
	public String attack(Message msg) {
		if (boss == null) {
			return "There is no boss to attack.";
		}
		String n = msg.getName();
		if (boss.cooldowns.containsKey(n) && (System.currentTimeMillis() - boss.cooldowns.get(n) < 10000)) {
			return "You can only attack once every ten seconds.";
		}
		boss.cooldowns.put(n, System.currentTimeMillis());
		int dmg = (int)(Math.random() * 5) + 1;
		boss.hp -= dmg;
		if (boss.hp <= 0) {
			int l = boss.loot;
			boss = null;
			melonList.get(n).numMelons += l;
			return msg.getName() + " has dealt " + dmg + " damage to the boss and slain it for " + l + " melons!";
		} else {
			return msg.getName() + " has dealt " + dmg + " damage to the boss. Boss has " + boss.hp + " hp.";
		}
	}
	
	public String boss() {
		if (boss == null) {
			return "There is currently no boss";
		} else {
			return "Boss has " + boss.hp + " hp and " + boss.loot + " melons.";
		}
	}
	
	public String accept(Message msg) {
		String n = msg.getName();
		Challenge c = melonList.get(n).c;
		int m = melonList.get(n).numMelons;
		if (c == null) return n+", you don't have a pending challenge";
		if (m < c.amount) return n+", you don't have enough melons to accept the challenge";
		if (melonList.get(c.challenger).numMelons < c.amount) return c.challenger+" doesn't have enough melons";
		int r = (int)(Math.random() * 2);
		if (r == 1) {
			melonList.get(n).numMelons += c.amount;
			melonList.get(c.challenger).numMelons -= c.amount;
			melonList.get(n).c = null;
			return n+" has won the challenge and took "+c.amount+" melons from "+c.challenger+"!";
		} else {
			melonList.get(n).numMelons -= c.amount;
			melonList.get(c.challenger).numMelons += c.amount;
			melonList.get(n).c = null;
			return c.challenger+" has won the challenge and took "+c.amount+" melons from "+n+"!";
		}
	}
	
	public String challenge(Message msg) {
		String n = msg.getName();
		if (msg.getWords().length != 3) return "Use !challenge opponent x to challenge opponent for x melons";
		String opp = msg.getWords()[1].toLowerCase();
		if (!userList.contains(opp)) return opp+" doesn't seem to be here";
		if (challengeList.contains(opp)) return opp+" has a pending challenge already";
		try {
			int m = Integer.parseInt(msg.getWords()[2]);
			if (melonList.get(n).numMelons < m) return n+", you don't have enough melons";
			long x = System.currentTimeMillis();
			melonList.get(opp).c = new Challenge(m, n, x);
			challengeList.add(opp);
			Timer t = new Timer();
			t.schedule(new TimerTask() {
				public void run() {
					Challenge ch = melonList.get(opp).c;
					if (ch != null && ch.time == x) {
						try {
							privmsg(n+"'s challenge against "+opp+" for "+m+" melons has expired");
						} catch (IOException e) {
							e.printStackTrace();
						}
						challengeList.remove(opp);
						melonList.get(opp).c = null;
					}
				}
			}, 60000);
			return n+" has challenged "+opp+" for "+m+" melons! Type !accept to accept the challenge. Challenge expires in one minute";
		} catch (NumberFormatException e) {
			return "Use !challenge opponent x to challenge opponent for x melons";
		}
	}
	
	public String gamble(Message msg) {
		String n = msg.getName();
		if (msg.getWords().length != 2) return "Use !mgamble x to gamble x melons";
		if (melonList.get(n).cooldown > System.currentTimeMillis()) return "You can only gamble once every 30 seconds";
		try {
			int m = Integer.parseInt(msg.getWords()[1]);
			if (m < 10 || m > 100000) return "You can only gamble between 10 and 100000 melons";
			if (melonList.get(n).numMelons < m) return n+", you don't have enough melons";
			int r = melonList.get(n).gamble(m);
			int t = melonList.get(n).numMelons;
			if (r > 50) return n+" has rolled "+r+" and won "+m+" melons and now has "+t+" melons";
			else  {
				melonpot += m;
				return n+" has rolled "+r+" and lost "+m+" melons and now has "+t+" melons";
			}
		} catch (NumberFormatException e) {
			return "Use !mgamble x to gamble x melons";
		}
	}
}
