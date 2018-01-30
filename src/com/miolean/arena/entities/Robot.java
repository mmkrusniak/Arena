package com.miolean.arena.entities;

import com.miolean.arena.framework.Global;
import com.miolean.arena.framework.UByte;

import java.awt.*;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Scanner;

import static com.miolean.arena.framework.Global.ARENA_SIZE;
import static com.miolean.arena.framework.UByte.ub;
import static com.miolean.arena.framework.UByte.ubDeepCopy;

/**
 * Created by commandm on 5/13/17.
 */

/* Memories:
 *
 * K: Genome (all possible genes)
 * U: Super memory; executed rarely
 * P: Program memory; executed regularly
 * S: Storage; mundane
 * W: Registries; used in calculations
 * I: No memory; refers to immediate (literal) values
 */

//Robot has all sorts of methods that appear unused but are actually reflected.
@SuppressWarnings("unused")
public class Robot extends Entity implements Comparable<Robot>{

    //General constants
    protected static final int SIZE = 40;
    private static final int DEFAULT_STAT_VALUE = 10;
    private static final int MAX_BULLET_RECHARGE = 40;
    private static final int INITIAL_COGS = 40;
    private static final int HARD_COG_DEFICIT_LIMIT = -40;
    private static final double DIFFICULTY = 0.05;

    //Memories
    public static final Gene[] KMEM;
    private UByte[][] UMEM;
    private UByte[][] PMEM;
    private UByte[][] SMEM;
    private UByte[] WMEM;
    private UByte[][] CURRENT = PMEM;

    //Program index:
    private int index = 0;
    private int loaded = 0;

    //Comparison flags:
    private boolean equal = false;
    private boolean greater = false;

    //Stat constants
    static final int STAT_HASTE = 0x0; //shot speed
    static final int STAT_DAMAGE = 0x1; //shot damage
    static final int STAT_REGEN = 0x2; //regeneration
    static final int STAT_SPEED = 0x3; //acceleration (translates to speed due to drag)
    static final int STAT_TOUGHNESS = 0x4; //body damage
    static final int STAT_ROTATE_SPEED = 0x5;
    static final int STAT_BULLET_SPEED = 0x6;
    static final int STAT_MAX_HEALTH = 0x7;
    static final int STAT_BULLET_SPREAD = 0x8;

    //Type constant
    static final int TYPE_TANK = 0x0;
    static final int TYPE_COG = 0x4;
    static final int TYPE_BULLET = 0x8;
    static final int TYPE_WALL = 0x12;


    protected UByte[] stats = new UByte[9];

    //Flash color:
    private int flashR = 0x00;
    private int flashG = 0xBB;
    private int flashB = 0x00;

    //State variables:
    private double fitness = 0;
    private int generation = 0;
    private double cogs = 0;



    private String name = "";

    private int viewDistance = 10;
    private long lastFireTime = Global.time;
    private static int totalKWeight;
    private boolean generateLog = false;

    static {
        KMEM = new Gene[256];
        Scanner in = new Scanner(Robot.class.getClassLoader().getResourceAsStream("cfg/origin.txt"));
        in.useDelimiter("/");

        in.next();
        short opcode = (short) Integer.parseInt(in.next().trim(), 16);
        String method;
        String description;
        double cost;
        int weight;

        while(opcode < 0xFF) {
            method = in.next().trim();
            description = in.next().trim();
            weight = Integer.parseInt(in.next().trim());
            cost = Double.parseDouble(in.next().trim());

            KMEM[opcode] = new Gene(method, description, cost, weight);

            in.next();
            opcode = (short) Integer.parseInt(in.next().trim(), 16);
        }

        for(Gene g: KMEM) {
            if(g != null) totalKWeight += g.weight;
        }
    }

    //Create a totally blank Robot (for whatever reason)
    Robot(Field field) {
        super(SIZE, SIZE, 10, field);
    }

    //Create a Robot from a parent
    public Robot(Robot parent, Field field) {
        super(SIZE, SIZE, DEFAULT_STAT_VALUE, field);
        name = Global.wordRandom.nextWord();
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        generation = parent.generation + 1;

        UMEM = ubDeepCopy(parent.UMEM);
        PMEM = ubDeepCopy(parent.PMEM);
        SMEM = ubDeepCopy(parent.SMEM);
        WMEM = new UByte[256];
        for(int i = 0; i < 256; i++) WMEM[i] = ub(0);
        //KMEM is immutable
        //IMEM doesn't exist

        //Stats
        for(int i = 0; i < stats.length; i++) stats[i] = ub(DEFAULT_STAT_VALUE);

        int maxOffset = Global.ARENA_SIZE / 4;
        setX(parent.getX() + maxOffset * (Global.random.nextFloat()*2-1));
        setY(parent.getY() + maxOffset * (Global.random.nextFloat()*2-1));
        cogs = INITIAL_COGS;
    }

    //Create a Robot from a file
    public Robot(InputStream file, Field field) {

        super(SIZE, SIZE, DEFAULT_STAT_VALUE, field);
        //0: Initial values.

        name = "Unnamed";
        generation = 0;

        //1: Initialize memories.
        UMEM = new UByte[256][];
        PMEM = new UByte[256][];
        SMEM = new UByte[256][];
        WMEM = new UByte[256];

        UMEM[0] = new UByte[256];
        PMEM[0] = new UByte[256];
        SMEM[0] = new UByte[256];


        //2: Initialize stats.
        for(int i = 0; i < stats.length; i++) stats[i] = ub(10);

        //3: Grab memories from file.
        compile(file);

        //4: Anything else?
        cogs = INITIAL_COGS;
    }

    private void compile(InputStream file) {
        Scanner in = new Scanner(file);
        String next;

        int loadIndex = 0;
        int loadMode = 0;
        int loadMemory = 0;

        while(in.hasNext()) {
            next = undecorate(in.next()).trim();

            //If next is a comment: Run through it until it ends
            if(next.equals("##")) {
                do next = in.next().trim();
                while (! next.equals("#/"));
                next = undecorate(in.next()).trim();
            }

            //If next is a command: Translate it into its number
            if(next.startsWith("_")) {
                if(loadMode < 2) throwCompileError("Command in passive memory.");
                for(int i = 0; i < KMEM.length; i++) {
                    if(KMEM[i] != null && KMEM[i].getMeaning().getName().equals(next)) next = i + "";
                }
            }

            if(next.charAt(0) == '-') next = "0";

            //If next is a define statement, define a new memory
            if(next.equals("define")) {
                int defMemory;
                int defLength;

                try {
                    next = undecorate(in.next()).trim();
                    defMemory = Integer.parseInt(undecorate(in.next()).trim());

                    switch(next) {
                        case "registry":
                            throwCompileError("Cannot define new registry.");
                            break;
                        case "storage":
                            SMEM[defMemory] = new UByte[256];
                            break;
                        case "program":
                            PMEM[defMemory] = new UByte[256];
                            break;
                        case "meta":
                            UMEM[defMemory] = new UByte[256];
                            break;
                    }

                } catch(NumberFormatException e) {throwCompileError("Memory and length expected after \"define\"");}
            }

            //If next is a edit statement: load the memory it specifies, and reset the index
            else if(next.equals("edit")) {
                loadIndex = 0;
                next = undecorate(in.next()).trim();
                switch(next) {
                    case "registry": loadMode = 0; break;
                    case "storage": loadMode = 1; break;
                    case "program": loadMode = 2; break;
                    case "meta": loadMode = 3; break;
                }

                //Load multi-memory if it's that kind of memory
                if(! next.equals("registry")) {
                    next = undecorate(in.next()).trim();
                    loadMemory = Integer.parseInt(next);

                    if(loadMemory > 255) throwCompileError("Memory number out of bounds; must be between 0 and 255");

                    switch (loadMode) {
                        case 1:
                            if(SMEM[loadMemory] == null) throwCompileError("No storage defined at " + loadMemory);
                            break;
                        case 2:
                            if(PMEM[loadMemory] == null) throwCompileError("No program defined at " + loadMemory);
                            break;
                        case 3:
                            if(UMEM[loadMemory] == null) throwCompileError("No meta defined at " + loadMemory);
                            break;

                    }
                }
            }

            //If next is an at statement: move the index to what is specified
            else if(next.equals(("at"))) {
                next = undecorate(in.next()).trim();
                try {loadIndex = Integer.parseInt(next);}
                catch (NumberFormatException e) {throwCompileError("Index expected after \"at\"; instead received \"" + next + "\"");}
                if(loadIndex > 255) throwCompileError("\"at\" index invalid (must be between 0 and 255)");
            }

            //If next is the "name" command: name the tank
            else if(next.equals("name")) {
                next = undecorate(in.next()).trim();
                this.name = next;
            }
            //If next is the "print" command: print everything up to the next "#"
            else if(next.equals("print")) {
                do {
                    next = in.next().trim();
                    System.out.print(next + " ");
                } while (! next.equals("#"));
                System.out.println();
            }

            //At this point this is pretty clearly an entry.
            else {
                try {
                    switch (loadMode) {
                        case 0:
                            if(WMEM[loadIndex] != null) throwCompileError("Registry at " + loadIndex + " already defined.");
                            WMEM[loadIndex] = ub(Integer.parseInt(next));
                            break;
                        case 1:
                            if(SMEM[loadMemory][loadIndex] != null) throwCompileError("Storage " + loadMemory + " at " + loadIndex + " already defined.");
                            if(loadIndex >= SMEM[loadMemory].length) throwCompileError("Storage " + loadMemory + " index out of bounds.");
                            SMEM[loadMemory][loadIndex] = ub(Integer.parseInt(next));
                            break;
                        case 2:
                            if(PMEM[loadMemory][loadIndex] != null) throwCompileError("Program " + loadMemory + " at " + loadIndex + " already defined.");
                            if(loadIndex >= PMEM[loadMemory].length) throwCompileError("Program " + loadMemory + " index out of bounds.");
                            PMEM[loadMemory][loadIndex] = ub(Integer.parseInt(next));
                            break;
                        case 3:
                            if(UMEM[loadMemory][loadIndex] != null) throwCompileError("Meta " + loadMemory + " at " + loadIndex + " already defined.");
                            if(loadIndex >= UMEM[loadMemory].length) throwCompileError("Meta " + loadMemory + " index out of bounds.");
                            UMEM[loadMemory][loadIndex] = ub(Integer.parseInt(next));
                            break;
                    }
                    loadIndex++;
                } catch(NumberFormatException e) {throwCompileError("Entry expected; instead received \"" + next + "\"");}
            }
        }

        for(int i = 0; i < 256; i++) if(WMEM[i] == null) WMEM[i] = ub(0);

        for(UByte[] a: SMEM) if(a != null) {
            for(int i = 0; i < 256; i++) if (a[i] == null) a[i] = ub(0);
        }

        for(UByte[] a: PMEM) if(a != null) {
            for(int i = 0; i < 256; i++) if (a[i] == null) a[i] = ub(0);
        }

        for(UByte[] a: UMEM) if(a != null) {
            for(int i = 0; i < 256; i++) if (a[i] == null) a[i] = ub(0);
        }
    }

    private String undecorate(String input) {
        input = input.replace(';', ' ');
        input = input.replace(':', ' ');
        input = input.replace(',', ' ');
        input = input.replace('{', ' ');
        input = input.replace('}', ' ');
        return input;
    }

    @Override
    public void render(Graphics g) {

        g.setColor(Color.black);
        g.drawOval((int) (getX() - SIZE/2), (int) (getY() - SIZE/2), SIZE, SIZE);


        //Trigonometric functions are expensive so let's use math to minimize
        //how many times we have to calculate them.
        //And yeah, r is rotation. Not radius. Sorry about that, but my keyboard
        //doesn't have a theta. (not that Java would support it probably anyway)
        double sinR = Math.sin(getR());
        double cosR = Math.cos(getR());
        double sind5 = .47943; //As in sine decimal five, or sin(0.5). There is no pi and that is not a mistake.
        double cosd5 = .87758;
        double sind2 = .19867;
        double cosd2 = .98007;
        //sin(r +- u) = sin(r)cos(u) +- sin(u)cos(r)
        //cos(r +- u) = cos(r)cos(u) +- sin(r)sin(u)
        //tan(r +- u) = sin(r +- u) / cos(r +- u) [not that division is so much better]

        double x = getX();
        double y = getY();

        //Wheels!
        int[] wheelXPoints = {
                (int) (x+SIZE*.7*(sinR*cosd5 - sind5*cosR)),
                (int) (x+SIZE*.7*(sinR*cosd5 + sind5*cosR)),
                (int) (x-SIZE*.7*(sinR*cosd5 - sind5*cosR)),
                (int) (x-SIZE*.7*(sinR*cosd5 + sind5*cosR))
        };

        int[] wheelYPoints = {
                (int) (y+SIZE*.7*(cosR*cosd5 + sinR*sind5)),
                (int) (y+SIZE*.7*(cosR*cosd5 - sinR*sind5)),
                (int) (y-SIZE*.7*(cosR*cosd5 + sinR*sind5)),
                (int) (y-SIZE*.7*(cosR*cosd5 - sinR*sind5))
        };

        //Gun barrel!
        int[] gunXPoints = {
                (int) (x+SIZE*.7*(cosR*cosd2 - sinR*sind2)), //y + size * a little bit more * cos(r - .5)
                (int) (x+SIZE*.7*(cosR*cosd2 + sinR*sind2)), //y + size * a little bit more * cos(r - .5)
                (int) (x)
        };

        int[] gunYPoints = {
                (int) (y-SIZE*.7*(sinR*cosd2 + sind2*cosR)),
                (int) (y-SIZE*.7*(sinR*cosd2 - sind2*cosR)),
                (int) (y)

        };


        g.setColor(Color.DARK_GRAY);
        g.fillPolygon(wheelXPoints, wheelYPoints, 4); //Wheels
        g.setColor(Color.BLACK);
        g.drawPolygon(wheelXPoints, wheelYPoints, 4); //Wheel outline

        g.setColor(Color.GRAY);
        g.fillPolygon(gunXPoints, gunYPoints, 3); //Barrel

        double healthPercent = (getHealth() / stats[STAT_MAX_HEALTH].val());
        if(healthPercent < 0) healthPercent = 0;
        if(healthPercent > 1) healthPercent = 1;
        g.setColor(new Color((int) (healthPercent * 100 + 100), (int) (healthPercent * 100 + 100), (int) (healthPercent * 100 + 100)));
        g.fillOval((int) x - SIZE/2, (int) y - SIZE/2, SIZE, SIZE); //Body

        g.setColor(Color.GRAY);
        g.fillOval((int) x - SIZE/8, (int) y - SIZE/8, SIZE/4, SIZE/4); //Beacon
        g.setColor(new Color(flashR, flashG, flashB));
        g.fillOval((int) x - SIZE/10, (int) y - SIZE/10, SIZE/5, SIZE/5); //Beacon

        g.setColor(Color.black);
        g.drawString(name, (int) x - SIZE, (int) y - SIZE);
    }

    @Override
    public void update() {

        double initialCogs = cogs;

        //Apply physics
        applyPhysics();

        //Run the loaded P memory!
        runGenes(PMEM);

        //Congratulations on not dying! Used cogs go towards fitness, excluding the survival ones
        //Unless you actually are dying. Then we have a problem.
        if(cogs > 0) fitness += initialCogs - cogs - DIFFICULTY;

        //Make sure health is valid
        if(getHealth() > stats[STAT_MAX_HEALTH].val()) setHealth(stats[STAT_MAX_HEALTH].val());
        if(cogs <= 0) damage(1);
    }

    void runGenes(UByte[][] genes) {

        CURRENT = genes;
        cogs -= DIFFICULTY;

        greater = false;
        equal = false;

        index = 0;
        loaded = 0;

        for(; index < genes.length-3; index++) {
            //For every entry in this list of genes (excluding the ones at the end that don't have enough others after them as arguments)

            if(genes==null) System.err.println("List of memories: NULL");
            if(genes[loaded]==null) System.err.println("Loaded: NULL");
            if(genes[loaded][index]==null) System.err.println("Index: NULL");


            if(genes[loaded] == null) loaded = 0;
            try {
                if(genes[loaded][index].val() == 0x00) continue; //Don't even bother with opcode 0x00, standing for "do nothing"
            } catch(Exception e) {
                while(true) System.out.println("Something horrible has happened...");
            }
            if(KMEM[genes[loaded][index].val()] == null) continue; //If the opcode doesn't actually stand for something meaningful, skip it too

            //Since everything appears to be in order, let's try to run that as a gene.
            try {
                //System.out.printf("%s(%d, %d, %d)\n", KMEM[genes[index].val()].getMeaning().getName(), genes[index+1].val(), genes[index+2].val(), genes[index+3].val());

                cogs -= KMEM[genes[loaded][index].val()].cost;
                if(cogs < 0) break;

                KMEM[genes[loaded][index].val()].getMeaning().invoke(this, genes[loaded][index+1].val(), genes[loaded][index+2].val(), genes[loaded][index+3].val());


            } catch (IllegalAccessException | InvocationTargetException e) { e.printStackTrace(); }


            //Assuming nothing went wrong we've completed a command by now. (If something did go wrong, we'll at least have a stack trace.)
            index += 3; //We don't want to run the arguments by accident, so let's skip them.

        }
    }

    private void throwCompileError(String reason) {
        System.err.println("Error compiling Robot " + name + ":");
        System.err.println(reason);
        System.exit(0);
    }

    //Instantiate memory number [number] as a UByte[256].
    private void createMemory(UByte[][] memory, UByte number) {
        memory[number.val()] = new UByte[256];
        for(int i = 0; i < 256; i++) {
            memory[number.val()][i] = ub(0);
        }
    }

    //Destroy memory number [number], making it null.
    void destroyMemory(UByte[][] memory, UByte number) {
        //Never ever destroy memory 0.
        if(number.val() != 0) {
            memory[number.val()] = null;
        }
        if(number.val() == loaded) loaded = 0;
    }

    @Override
    public boolean intersectsWith(Entity e) {
        //Seeing that Tanks don't actually do anything upon intersection, saying they intersect anyway
        //is a massive waste of resources...
        //but it turns out we use this function for other things than checking whether to actually intersect.
        //We can strike a compromise by only caring about TrackerDots


        if(e == null) return false;
        if(! (e instanceof TrackerDot)) return false;
        double distanceSquared = (getX() - e.getX())*(getX() - e.getX())+(getY() - e.getY())*(getY() - e.getY());
        double minDistance = (SIZE/2.0 + e.getWidth()/2.0) * (SIZE/2.0 + e.getWidth()/2.0);

        return distanceSquared <= minDistance;
    }

    @Override
    public void intersect(Entity e) {
    }

    @Override
    public String toString() {
        return name + getUUID();
    }

    void fire() {
        if(lastFireTime + MAX_BULLET_RECHARGE - stats[STAT_HASTE].val() < Global.time) {
            Bullet bullet = new Bullet(this, getField());
            add(bullet);
            lastFireTime = Global.time;
        }
    }

    void forward(int force) {
        //Essentially, accelerate the tank in the direction it's facing.
        //This will typically take a tank to its max speed (based on drag.)
        //To go slower a tank has to monitor when it's moving forwards.
        if(force > stats[STAT_SPEED].val()) force = stats[STAT_SPEED].val(); //Tanks can't move faster than a certain limit
        if(force < -stats[STAT_SPEED].val()) force = -stats[STAT_SPEED].val(); //Tanks can't move faster than a certain limit

        //Translate polar force into cartesian vector
        setAccX(force *  Math.cos(getR()) / 16); //Scaling!
        setAccY(force * -Math.sin(getR()) / 16);
    }

    void rotate(int force) {
        if(force > stats[STAT_ROTATE_SPEED].val()) force = stats[STAT_ROTATE_SPEED].val(); //Tanks can't rotate faster than a certain limit
        if(force < -stats[STAT_ROTATE_SPEED].val()) force = -stats[STAT_ROTATE_SPEED].val(); //Tanks can't rotate faster than a certain limit

        setAccR( ((double) force )/512 );
    }

    private void upgrade(UByte stat, int amount) {
        //TODO manage conversion problems with signed UBytes
        cogs -= amount;
        int newValue = amount + stats[Math.abs(stat.val()>>5)].val();
        stats[Math.abs(stat.val()>>5)] = (amount > 255)? ub(255):ub(amount);
    }

    private int typeOf(Entity entity) {
        if (entity instanceof Robot) return TYPE_TANK;
        //if (entity instanceof Cog) return TYPE_COG;
        //if (entity instanceof Bullet) return TYPE_BULLET;
        //if (entity instanceof Wall) return TYPE_WALL;
        return 0;
    }

    void reproduce() {
        Robot offspring = new Robot(this, getField());
        add(offspring);
    }

    public void onDeath() {
    }

    @Override
    public String toHTML() {
        //TODO
        return "This should be HTML";
    }

    public void onBirth() {
        //Run the loaded U memory!
        runGenes(UMEM);
    }

    private String activeMemoryToString(UByte[] genes, boolean color) {
        if(genes == null) return "§r No memory exists here.";

        String result = "";

        //You'll notice we stole this from runGenes(). That's intentional.

        for(int i = 0; i < genes.length-3; i++) {
            //For every entry in this list of genes (excluding the ones at the end that don't have enough others after them as arguments)

            if(genes[i] != null) { //we really can't afford to lose track of null genes
                if (genes[i].val() == 0x00)
                    continue; //Don't even bother with opcode 0x00, standing for "do nothing"
                if (KMEM[genes[i].val()] == null)
                    continue; //If the opcode doesn't actually stand for something meaningful, skip it too
            }

            //Since everything appears to be in order, let's try to parse that as a gene. (Normally we'd run it.)
            result += "§k" + i + " ";
            result += (genes[i] == null)? "§rNULL \n" : "§g" + KMEM[genes[i].val()].getMeaning().getName() + " §k(";
            result += (genes[i+1] == null)? "§rNULL \n" : "§b" + genes[i+1].val() + " §k[" + WMEM[genes[i+1].val()].val() + "], ";
            result += (genes[i+2] == null)? "§rNULL \n" : "§b" + genes[i+2].val() + " §k[" + WMEM[genes[i+2].val()].val() + "],";
            result += (genes[i+3] == null)? "§rNULL \n" : "§b" + genes[i+3].val() + " §k[" + WMEM[genes[i+3].val()].val() + "]) \n";


            //Assuming nothing went wrong we've completed a command by now. (If something did go wrong, we'll at least have a stack trace.)
            i += 3; //We don't want to run the arguments by accident, so let's skip them.

        }

        if(! color) {
            result = result.replaceAll("§.", "");
        }

        return result;
    }

    private String passiveMemoryToString(UByte[] genes, boolean color) {
        if(genes == null) return "§r No memory exists here.";
        String result = "§b";

        for(int i = 0; i < genes.length; i++) {
            if(i%4 == 0) result += "\n" + i/16 + i%16 + "\t";
            result += "§k|  " + genes[i].val() +"\t§b";
        }

        if(! color) {
            result.replaceAll("§\\D", "");
        }

        return result;
    }

    public String stringUMEM(int memory) {return activeMemoryToString(UMEM[memory], true);}
    public String stringPMEM(int memory) {return activeMemoryToString(PMEM[memory], true);}
    public String stringSMEM(int memory) {return passiveMemoryToString(SMEM[memory], true);}
    public String stringWMEM() {return passiveMemoryToString(WMEM, true);}
    /*-----------------------------------------------------------------
     * Reflected methods (genes) are below.
     * Beware. Not intended for human consumption.
     * All arguments are intended to be within the range [0, 255].
     */
    // 0 (DEV ONLY)
    public void _SNO (int arg0, int arg1, int arg2) {/* Significant nothing */}
    public void _PRINT(int arg0, int arg1, int arg2) {/*System.out.printf("%s says: %s, %s, %s.\n", name, WMEM[arg0].val(), WMEM[arg1].val(), WMEM[arg2].val());*/}
    // 1
    public void _GOTO (int arg0, int arg1, int arg2) {index = WMEM[arg0].val() - 4;}
    public void _GOE  (int arg0, int arg1, int arg2) {if(equal) index = WMEM[arg0].val() - 4;}
    public void _GOG  (int arg0, int arg1, int arg2) {if(greater) index = WMEM[arg0].val() - 4;}
    public void _IGOTO(int arg0, int arg1, int arg2) {index = arg0 - 4;}
    public void _IGOE (int arg0, int arg1, int arg2) {if(equal) index = arg0 - 4;}
    public void _IGOG (int arg0, int arg1, int arg2) { if(greater) index = arg0 - 4;}
    public void _COMP (int arg0, int arg1, int arg2) {
        equal = (WMEM[arg0].val() == WMEM[arg1].val());
        greater = (WMEM[arg0].val() > WMEM[arg1].val());
    }
    // 2
    public void _MOV  (int arg0, int arg1, int arg2) {WMEM[arg0] = WMEM[arg1];}
    public void _SSTO (int arg0, int arg1, int arg2) {if(SMEM[WMEM[arg0].val()] != null) SMEM[WMEM[arg0].val()][WMEM[arg1].val()] = WMEM[arg2];}
    public void _PSTO (int arg0, int arg1, int arg2) {if(PMEM[WMEM[arg0].val()] != null) PMEM[WMEM[arg0].val()][WMEM[arg1].val()] = WMEM[arg2];}
    public void _USTO (int arg0, int arg1, int arg2) {if(UMEM[WMEM[arg0].val()] != null) UMEM[WMEM[arg0].val()][WMEM[arg1].val()] = WMEM[arg2];}
    public void _SGET (int arg0, int arg1, int arg2) {if(SMEM[WMEM[arg1].val()] != null) WMEM[arg0] = SMEM[WMEM[arg1].val()][WMEM[arg2].val()];}
    public void _PGET (int arg0, int arg1, int arg2) {if(PMEM[WMEM[arg1].val()] != null) WMEM[arg0] = PMEM[WMEM[arg1].val()][WMEM[arg2].val()];}
    public void _UGET (int arg0, int arg1, int arg2) {if(UMEM[WMEM[arg1].val()] != null) WMEM[arg0] = UMEM[WMEM[arg1].val()][WMEM[arg2].val()];}
    public void _IMOV (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1);}
    public void _ISSTO(int arg0, int arg1, int arg2) {if(SMEM[arg0] != null) SMEM[arg0][arg1] = ub(arg2);}
    public void _IPSTO(int arg0, int arg1, int arg2) {if(PMEM[arg0] != null) PMEM[arg0][arg1] = ub(arg2);}
    public void _IUSTO(int arg0, int arg1, int arg2) {if(UMEM[arg0] != null) UMEM[arg0][arg1] = ub(arg2);}
    public void _WCLR (int arg0, int arg1, int arg2) {for(; arg1 < arg2 && arg1 < 256; arg1++) WMEM[arg1] = ub(0);}
    public void _SCLR (int arg0, int arg1, int arg2) {if(SMEM[WMEM[arg0].val()] != null) for(; arg1 < arg2 && arg1 < 256; arg1++) SMEM[WMEM[arg0].val()][WMEM[arg1].val()] = ub(0);}
    public void _PCLR (int arg0, int arg1, int arg2) {if(PMEM[WMEM[arg0].val()] != null) for(; arg1 < arg2 && arg1 < 256; arg1++) PMEM[WMEM[arg0].val()][WMEM[arg1].val()] = ub(0);}
    public void _UCLR (int arg0, int arg1, int arg2) {if(UMEM[WMEM[arg0].val()] != null) for(; arg1 < arg2 && arg1 < 256; arg1++) UMEM[WMEM[arg0].val()][WMEM[arg1].val()] = ub(0);}
    // 3
    public void _POSX (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) (getX()/ARENA_SIZE*255));}
    public void _VELX (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getVelX());}
    public void _ACCX (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getAccX());}
    public void _POSY (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) (getY()/ARENA_SIZE*255));}
    public void _VELY (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getVelY());}
    public void _ACCY (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getAccY());}
    public void _POSR (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) (getR()/2*Math.PI*255));}
    public void _VELR (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getVelR());}
    public void _ACCR (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getAccR());}
    // 4 TODO Implement new Sight Block methods
    public void _VIEW (int arg0, int arg1, int arg2) {viewDistance = WMEM[arg0].val();}
    public void _NEAR (int arg0, int arg1, int arg2) {}
    public void _NRST (int arg0, int arg1, int arg2) {}
    // 5
    public void _HEAL (int arg0, int arg1, int arg2) {heal(1);}
    public void _FORWD(int arg0, int arg1, int arg2) {forward(WMEM[arg0].val());}
    public void _REVRS(int arg0, int arg1, int arg2) {forward(-WMEM[arg0].val());}
    public void _FIRE (int arg0, int arg1, int arg2) {fire();}
    public void _TURNL(int arg0, int arg1, int arg2) {rotate(WMEM[arg0].val());}
    public void _TURNR(int arg0, int arg1, int arg2) {rotate(-WMEM[arg0].val());}
    // 6
    public void _ADD  (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 + arg2);}
    public void _SUB  (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 - arg2);}
    public void _PROD (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 * arg2);}
    public void _QUOT (int arg0, int arg1, int arg2) {if(arg2 != 0) WMEM[arg0] = ub(arg1 / arg2);}
    public void _BWOR (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 | arg2);}
    public void _BWAND(int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 & arg2);}
    public void _BWXOR(int arg0, int arg1, int arg2) {WMEM[arg0] = ub(arg1 ^ arg2);}
    public void _INCR (int arg0, int arg1, int arg2) {WMEM[arg0] = ub(WMEM[arg0].val() + 1);}
    // 7
    public void _OTYPE(int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot) WMEM[arg0] = ub(typeOf(getField().fromUUID(WMEM[arg1],WMEM[arg2])));}
    public void _OHP  (int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot) WMEM[arg0] = ub((int) (getField().fromUUID(WMEM[arg1],WMEM[arg2]).getHealth()));}
    public void _OCOG (int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot) WMEM[arg0] = ub((int)((Robot) getField().fromUUID(WMEM[arg1],WMEM[arg2])).cogs);}
    // 8
    public void _WALL (int arg0, int arg1, int arg2) {} //TODO Implement _WALL() [when Walls exist]
    public void _SPIT (int arg0, int arg1, int arg2) {} //TODO Implement _SPIT() [when Cogs exist]
    public void _LED (int arg0, int arg1, int arg2) {flashR = (WMEM[arg0].val()); flashG = (WMEM[arg1].val()); flashB = (WMEM[arg2].val());}
    public void _OLEDR(int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot)  WMEM[arg0] = ub(((Robot) getField().fromUUID(WMEM[arg1],WMEM[arg2])).flashR);}
    public void _OLEDG(int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot) WMEM[arg0] = ub(((Robot) getField().fromUUID(WMEM[arg1],WMEM[arg2])).flashG);}
    public void _OLEDB(int arg0, int arg1, int arg2) {if(getField().fromUUID(WMEM[arg1],WMEM[arg2]) instanceof Robot) WMEM[arg0] = ub(((Robot) getField().fromUUID(WMEM[arg1],WMEM[arg2])).flashB);}
    // getField
    public void _UUIDM(int arg0, int arg1, int arg2) {WMEM[arg0] = ub(getUUID());}
    public void _UUIDL(int arg0, int arg1, int arg2) {WMEM[arg0] = ub(getUUID());}
    public void _HP   (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) getHealth());}
    public void _COG  (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) cogs);}
    public void _PNT  (int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) fitness);}
    // A
    public void _UPG  (int arg0, int arg1, int arg2) {upgrade(WMEM[arg0], WMEM[arg1].val());}
    public void _STAT (int arg0, int arg1, int arg2) {WMEM[arg0] = stats[Math.abs(arg1>>5)];}
    public void _KWGT (int arg0, int arg1, int arg2) {if(KMEM[WMEM[arg1].val()] != null) WMEM[arg0] = ub(KMEM[WMEM[arg1].val()].weight);}
    public void _COST (int arg0, int arg1, int arg2) {if(KMEM[WMEM[arg1].val()] != null) WMEM[arg0] = ub((int)(KMEM[WMEM[arg1].val()].cost*4));}
    // B

    // C
    public void _OPOSX(int arg0, int arg1, int arg2) {}
    public void _OVELX(int arg0, int arg1, int arg2) {}
    public void _OACCX(int arg0, int arg1, int arg2) {}
    public void _OPOSY(int arg0, int arg1, int arg2) {}
    public void _OVELY(int arg0, int arg1, int arg2) {}
    public void _OACCY(int arg0, int arg1, int arg2) {}
    public void _OPOSR(int arg0, int arg1, int arg2) {}
    public void _OVELR(int arg0, int arg1, int arg2) {}
    public void _OACCR(int arg0, int arg1, int arg2) {}
    // D
    public void _LSMEM(int arg0, int arg1, int arg2) {}
    public void _SMEMX(int arg0, int arg1, int arg2) {}
    public void _DEFS (int arg0, int arg1, int arg2) {if(SMEM[arg0] == null) createMemory(SMEM, ub(arg0));}
    public void _SDEL (int arg0, int arg1, int arg2) {}
    public void _LPMEM(int arg0, int arg1, int arg2) {}
    public void _PMEMX(int arg0, int arg1, int arg2) {}
    public void _DEFP (int arg0, int arg1, int arg2) {if(PMEM[arg0] == null) createMemory(PMEM, ub(arg0));}
    public void _PDEL (int arg0, int arg1, int arg2) {}
    public void _LUMEM(int arg0, int arg1, int arg2) {}
    public void _UMEMX(int arg0, int arg1, int arg2) {}
    public void _DEFU (int arg0, int arg1, int arg2) {if(UMEM[arg0] == null) createMemory(UMEM, ub(arg0));}
    public void _UDEP (int arg0, int arg1, int arg2) {}
    public void _SWAP(int arg0, int arg1, int arg2) {
        if(CURRENT[arg0] != null) {
            loaded = arg0;
            index = arg1 - 4;
        }
    }
    // E
    public void _TARG(int arg0, int arg1, int arg2) {}
    public void _SCOPY(int arg0, int arg1, int arg2) {}
    public void _PCOPY(int arg0, int arg1, int arg2) {}
    public void _UCOPY(int arg0, int arg1, int arg2) {}

    // F
    public void _REP  (int arg0, int arg1, int arg2) {reproduce();}
    public void _TWK  (int arg0, int arg1, int arg2) {if(KMEM[arg0] != null) KMEM[arg0].weight = WMEM[arg1].val();}
    public void _KRAND(int arg0, int arg1, int arg2) {WMEM[arg0] = randomGene();}
    public void _URAND(int arg0, int arg1, int arg2) {if(UMEM[arg1] != null) WMEM[arg0] = randomExists(UMEM[arg1]);}
    public void _PRAND(int arg0, int arg1, int arg2) {if(PMEM[arg1] != null) WMEM[arg0] = randomExists(PMEM[arg1]);}
    public void _SRAND(int arg0, int arg1, int arg2) {if(SMEM[arg1] != null) WMEM[arg0] = randomExists(SMEM[arg1]);}
    public void _WRAND(int arg0, int arg1, int arg2) {WMEM[arg0] = randomExists(WMEM);}
    public void _IRAND(int arg0, int arg1, int arg2) {WMEM[arg0] = ub((int) (Global.random.nextFloat() * 255));}


    static UByte randomExists(UByte[] memory) {

        int totalExist = 0;
        for(UByte u: memory) if(u != ub(0)) totalExist++;

        int selection = (int) (totalExist * Global.random.nextFloat());
        int i;
        for(i = 0; selection > 0; i++) {
            if(memory[i] != ub(0)) selection--;
        }

        return ub(i);
    }

    static UByte randomGene() {

        int rand = (int) (Global.random.nextFloat() * totalKWeight);
        int selection = 0;
        while(rand > 0 && selection < KMEM.length) {
            if(KMEM[selection] != null) rand -= KMEM[selection].weight;
            selection++;
        }

        return ub(selection);


    }

    int umemAt(int memory, int address) {return UMEM[memory][address].val();}
    int pmemAt(int memory, int address) {return PMEM[memory][address].val();}
    int smemAt(int memory, int address) {return SMEM[memory][address].val();}
    int wmemAt(int address) {return WMEM[address].val();}

    public double getFitness() { return fitness; }
    public void setFitness(double fitness) { this.fitness = fitness; }
    public int getGeneration() { return generation; }
    public void setGeneration(int generation) { this.generation = generation; }
    public double getCogs() { return cogs; }
    public void setCogs(double cogs) { this.cogs = cogs; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public int compareTo(Robot o) {
        return (int) (fitness - o.getFitness());
    }
}