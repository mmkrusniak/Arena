package com.miolean.arena.framework;

import com.miolean.random.WordRandom;

import java.util.Random;

/**
 * Created by commandm on 5/21/17.
 *
 */


public class Global {

    //Number of ticks in-simulation
    public static int time;

    //Milliseconds per tick, for each of the different cycles
    public static int updateCycle = 50;
    public static int displayCycle = 100;
    public static int renderCycle = 10;
    public static int distributeCycle = 20;
    public static double distributeRatio = 0.3; //distributes per update

    //Publicly accessible random instance
    public static Random random = new Random();
    public static WordRandom wordRandom = new WordRandom();

    //1 pixel= 1 tank-meter, I suppose
    public static final int ARENA_SIZE = 2*1024;

    public static final int BORDER = 20;

    public static boolean[] KEY = new boolean[9];


    //Key constants
    public static final int KEY_Q = 0;
    public static final int KEY_W = 1;
    public static final int KEY_E = 2;
    public static final int KEY_R = 3;
    public static final int KEY_A = 4;
    public static final int KEY_S = 5;
    public static final int KEY_D = 6;
    public static final int KEY_F = 7;
    public static final int KEY_SPACE = 8;

}